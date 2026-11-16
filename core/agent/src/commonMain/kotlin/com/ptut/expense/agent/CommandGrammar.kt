package com.ptut.expense.agent

import com.ptut.expense.domain.Category
import com.ptut.expense.domain.Direction
import com.ptut.expense.domain.Home
import com.ptut.expense.domain.HomeTime
import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.SubCategory
import com.ptut.expense.domain.forDirection
import com.ptut.expense.domain.pow10
import com.ptut.expense.domain.shared.SharedGroup
import com.ptut.expense.domain.shared.money
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.minus
import kotlin.time.Instant

/** How much of a sentence [CommandGrammar] understood. The eval report counts these three (#205). */
sealed interface GrammarReading {
    data class Full(val call: ToolCall) : GrammarReading

    /** A call with [missing] parameters still to fill, by [ToolParameter.name]. The sheet asks for them. */
    data class Partial(val call: ToolCall, val missing: Set<String>) : GrammarReading

    data object Missed : GrammarReading
}

/** A category, and the sub-category under it when the words named one (#111). */
data class CategoryChoice(val category: Category, val subCategory: SubCategory? = null)

/**
 * #205: reads a short command without a model. Amount, income or expense, today or yesterday, a
 * category, and the words left over as the row's note. Text arrives through [TextNormaliser].
 *
 * Money never meets a float: the digits become [Satang] one group at a time.
 */
class CommandGrammar(private val words: GrammarWords = GrammarWords) {

    fun read(
        text: String,
        categories: List<Category>,
        groups: List<SharedGroup>,
        now: Instant,
        subCategories: List<SubCategory> = emptyList(),
    ): GrammarReading {
        val amount = amountIn(text)
        if (amount == null && words.howMuch.any { text.has(it) }) {
            return GrammarReading.Full(ReadCall.SpendSummary(windowIn(text) ?: SpendWindow.Today))
        }
        if (words.share.any { text.has(it) }) return readGroupExpense(text, groups)

        val direction = if (words.income.any { text.has(it) }) Direction.Income else Direction.Expense
        val offered = categories.forDirection(direction)
        val choice = choiceIn(text, offered, subCategories)
        // Income has one category on most phones, so naming none still files it there.
        val category = choice?.category ?: offered.singleOrNull()?.takeIf { direction == Direction.Income }
        if (amount == null && category == null) return GrammarReading.Missed

        val call = ProposedAction.AddTransaction(
            amount = amount?.value,
            direction = direction,
            categoryId = category?.id,
            subCategoryId = choice?.subCategory?.id,
            memo = noteIn(text, amount?.range, drop = listOfNotNull(category?.name, choice?.subCategory?.name)),
            occurredAtEpochMillis = daysBack(text)?.let { now.minus(it, DateTimeUnit.DAY, HomeTime).toEpochMilliseconds() },
        )
        val missing = buildSet {
            if (amount == null) add("amount")
            if (category == null) add("category")
        }
        return if (missing.isEmpty()) GrammarReading.Full(call) else GrammarReading.Partial(call, missing)
    }

    /**
     * The reader's own names first, so a category called Coffee wins over the seed's food words.
     * Then the seed's words in three languages, in the reader's order, for a seed id they still have.
     */
    fun categoryIn(text: String, categories: List<Category>): Category? = choiceIn(text, categories, emptyList())?.category

    /**
     * [categoryIn] with the reader's sub-categories between the two: a category's own name, then a
     * sub-category's, which brings its parent, then the seed's words. So "coffee 60" files under
     * Coffee's parent even where the seed's words say Food & drink. A sub-category under a category
     * the words named otherwise is left unpicked.
     */
    fun choiceIn(text: String, categories: List<Category>, subCategories: List<SubCategory>): CategoryChoice? {
        val named = categories.firstOrNull { text.names(it.name) }
        val subCategory = subCategories
            .filter { sub -> if (named != null) sub.categoryId == named.id else categories.any { it.id == sub.categoryId } }
            .firstOrNull { text.names(it.name) }
        val category = named
            ?: subCategory?.let { sub -> categories.first { it.id == sub.categoryId } }
            ?: categories.firstOrNull { category -> words.categories[category.id].orEmpty().any { text.has(it) } }
            ?: return null
        return CategoryChoice(category, subCategory)
    }

    /** The amount as Siri or a form typed it, "120" or "1,250.50", in a currency with [digits] decimals. */
    fun amountOf(text: String, digits: Int = Home.money.digits): Satang? = amountIn(text, digits)?.value

    /** #48: the amount is read in the named group's money, so "share 5000 with Trip" is K5,000 in a kyat group. */
    private fun readGroupExpense(text: String, groups: List<SharedGroup>): GrammarReading {
        val group = groups.firstOrNull { text.names(it.name) }
        val digits = group?.money?.digits ?: Home.money.digits
        val amount = amountIn(text, digits)
        val call = ProposedAction.AddGroupExpense(
            groupId = group?.id,
            amount = amount?.value,
            description = noteIn(text, amount?.range, drop = listOfNotNull(group?.name) + words.share),
            amountDigits = digits,
        )
        val missing = buildSet {
            if (group == null) add("group")
            if (amount == null) add("amount")
        }
        return if (missing.isEmpty()) GrammarReading.Full(call) else GrammarReading.Partial(call, missing)
    }

    private fun windowIn(text: String): SpendWindow? = when {
        words.week.any { text.has(it) } -> SpendWindow.Week
        words.month.any { text.has(it) } -> SpendWindow.Month
        words.today.any { text.has(it) } -> SpendWindow.Today
        else -> null
    }

    /** Null for today, which is when a row with no time lands anyway. */
    private fun daysBack(text: String): Int? = when {
        words.twoDaysAgo.any { text.has(it) } -> 2
        words.yesterday.any { text.has(it) } -> 1
        else -> null
    }

    /**
     * When a sentence holds two numbers the larger is the amount: "2 coffees 120" is ฿120, not ฿2.
     * Digits inside a shop's name, 7-11, are not a number.
     */
    private fun amountIn(text: String, digits: Int = Home.money.digits): Found? {
        var searched = text
        words.numberedNames.forEach { name -> searched = searched.replace(name, " ".repeat(name.length), ignoreCase = true) }
        return NUMBER.findAll(searched)
            .mapNotNull { match -> minorOf(match.value, digits)?.let { Found(it, match.range) } }
            .filter { it.value > Satang.Zero }
            .maxByOrNull { it.value }
    }

    /**
     * #45: "60" is ฿60 in a baht ledger and K60 in a kyat one, never K6,000. A fraction the currency
     * does not have ("60.5" in kyat) is not an amount, so it is refused rather than misread.
     */
    private fun minorOf(number: String, digits: Int): Satang? {
        val whole = number.substringBefore('.').replace(",", "")
        if (whole.length > MAX_WHOLE_DIGITS) return null
        val fraction = number.substringAfter('.', "")
        if (fraction.length > digits) return null
        val wholeValue = whole.toLongOrNull() ?: return null
        val fractionValue = fraction.padEnd(digits, '0').ifEmpty { "0" }.toLongOrNull() ?: return null
        return Satang.ofMinorUnits(wholeValue * pow10(digits) + fractionValue)
    }

    /**
     * What is left once the amount, the day and the filler are gone: "spent 60 on coffee today" is
     * "coffee". A note that only repeats the category name is no note.
     */
    private fun noteIn(text: String, amount: IntRange?, drop: List<String>): String? {
        var rest = if (amount != null) text.replaceRange(amount, " ") else text
        (words.twoDaysAgo + words.yesterday + words.today + drop).forEach { rest = rest.without(it) }
        val note = rest.split(' ')
            .mapNotNull(::cleanToken)
            .joinToString(" ")
            .trim(' ', ',', '.', '-', ':', '!', '?')
        return note.takeIf { it.any(Char::isLetter) && drop.none { name -> name.equals(it, ignoreCase = true) } }
    }

    private fun cleanToken(token: String): String? {
        if (token.lowercase() in words.englishFiller) return null
        var word = token
        words.thaiLeadingVerbs.firstOrNull { word.startsWith(it) }?.let { word = word.removePrefix(it) }
        words.thaiTrailingParticles.firstOrNull { word.endsWith(it) && word.length > it.length }?.let { word = word.removeSuffix(it) }
        var trimmed = true
        while (trimmed) {
            trimmed = false
            words.burmeseTrailing.firstOrNull { word.endsWith(it) }?.let {
                word = word.removeSuffix(it)
                trimmed = true
            }
        }
        return word.ifBlank { null }
    }

    /** A word of ASCII letters matches whole words without case; Thai and Burmese match anywhere. */
    private fun String.has(word: String): Boolean = indexOfWord(word) >= 0

    /** A name the reader gave, long enough that one letter cannot match it by chance. */
    private fun String.names(name: String): Boolean = name.length >= MIN_NAME && has(name)

    private fun String.without(word: String): String {
        var result = this
        var at = result.indexOfWord(word)
        while (at >= 0) {
            result = result.replaceRange(at, at + word.length, " ")
            at = result.indexOfWord(word)
        }
        return result
    }

    private fun String.indexOfWord(word: String): Int {
        if (word.isEmpty()) return -1
        if (!word.all { it.code < ASCII_LIMIT }) return indexOf(word)
        var from = 0
        while (true) {
            val at = indexOf(word, from, ignoreCase = true)
            if (at < 0) return -1
            val before = getOrNull(at - 1)
            val after = getOrNull(at + word.length)
            if (!before.isAsciiWordChar() && !after.isAsciiWordChar()) return at
            from = at + 1
        }
    }

    private fun Char?.isAsciiWordChar(): Boolean = this != null && (this in 'a'..'z' || this in 'A'..'Z' || this in '0'..'9')

    private class Found(val value: Satang, val range: IntRange)

    private companion object {
        val NUMBER = Regex("""\d{1,3}(?:,\d{3})+(?:\.\d{1,2})?|\d+(?:\.\d{1,2})?""")

        /** Ten digits of money is more than anyone types, and keeps the minor units inside a Long. */
        const val MAX_WHOLE_DIGITS = 10
        const val MIN_NAME = 2
        const val ASCII_LIMIT = 128
    }
}
