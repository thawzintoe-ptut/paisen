package com.ptut.expense.domain

import com.ptut.expense.domain.rules.ShopNames
import com.ptut.expense.domain.slip.isReaderNoise
import com.ptut.expense.locale.AppText
import kotlinx.datetime.TimeZone

/**
 * What a row is called (#210): the shop, else the note's first line, else "Unknown" on an imported
 * row, else the category as filed.
 *
 * An imported row had a merchant the reader could not make out, so "Unknown" is true. A typed row
 * never had one, and the old "Cash" on every such row said nothing and was wrong for a QR payment
 * typed by hand. Its note, or what it was filed as, is the reader's own word for it.
 *
 * It sits in the domain because Home, History, Detail, Activity and the day receipt all name rows,
 * and a second copy is how two screens start disagreeing about what one row is called.
 */
fun Transaction.displayName(categories: List<Category>, subCategories: List<SubCategory>): String =
    ownName() ?: filedAs(categories, subCategories)

/**
 * The name a row carries without its category, or null when the category is all it has.
 * A slip's shop shows the reader's name for it when there is one; noise with none counts as no name.
 */
private fun Transaction.ownName(): String? =
    counterparty?.let { shop -> if (source.isAutomatic) ShopNames.of(shop) ?: shop.takeUnless { it.isReaderNoise() } else shop }
        ?: noteTitle()
        ?: AppText.copy.phrases.unknownShop.takeIf { source.isAutomatic }

private fun Transaction.noteTitle(): String? =
    memo?.lineSequence()?.map(String::trim)?.firstOrNull(String::isNotEmpty)

/** Who filed a row. A row never says "edited": that lives in detail and the Edited filter (#146, K7). */
enum class Provenance {
    Auto,
    ByHand,
    ;

    val label: String
        get() = when (this) {
            Auto -> AppText.copy.phrases.auto
            ByHand -> AppText.copy.phrases.addedByHand
        }
}

/**
 * What [metaLine] joins, kept apart so a Sky row can put the time under the amount (#156).
 * [category] is null when it is already the row's name, so "Food & drink" is not said twice.
 */
data class MetaParts(val category: String?, val time: String?, val provenance: Provenance)

/** The line under a Sky row's name, "Groceries · auto"; the time sits under the amount (#146, K7). */
fun MetaParts.categoryAndProvenance(): String = listOfNotNull(category, provenance.label).joinToString(" · ")

/**
 * What a row was filed as, when, and who filed it.
 *
 * The time is only on an imported row, which is what the design draws and what the two kinds of
 * row deserve. An imported time was printed on the slip, so it is the thing that matches the row
 * to a piece of paper. A typed row has no slip: its instant is whenever the person happened to
 * open the app, which is not when they spent the money and answers nothing anyone would ask.
 */
fun Transaction.metaParts(
    categories: List<Category>,
    zone: TimeZone = BangkokTime,
    subCategories: List<SubCategory> = emptyList(),
): MetaParts = MetaParts(
    category = filedAs(categories, subCategories).takeIf { ownName() != null },
    time = if (source.isAutomatic) occurredAtEpochMillis.timeOfDayIn(zone) else null,
    provenance = if (source.isAutomatic) Provenance.Auto else Provenance.ByHand,
)

/** The line under a merchant name on a row: [metaParts] joined, "Groceries · 18:30 · auto". */
fun Transaction.metaLine(
    categories: List<Category>,
    zone: TimeZone = BangkokTime,
    subCategories: List<SubCategory> = emptyList(),
): String = metaParts(categories, zone, subCategories).joined()

private fun MetaParts.joined(): String = listOfNotNull(category, time, provenance.label).joinToString(" · ")

fun Transaction.category(categories: List<Category>): Category? =
    categories.firstOrNull { it.id == categoryId }

/**
 * What a row is filed as once its category has gone. The table drops the link and keeps the
 * spending — a deleted category does not un-spend the money — so this is a state a row reaches
 * in normal use, and naming the absence beats an empty word on screen.
 */
fun Transaction.categoryName(categories: List<Category>): String =
    category(categories)?.name ?: AppText.copy.phrases.uncategorised

/**
 * "Groceries › Supermarket" when the row's sub-category sits under its category, else the
 * category alone (p34, #112). "›" because "·" already separates the parts of a row's line.
 */
fun Transaction.filedAs(categories: List<Category>, subCategories: List<SubCategory>): String {
    val parent = categoryName(categories)
    val sub = subCategories.under(categoryId).firstOrNull { it.id == subCategoryId } ?: return parent
    return "$parent › ${sub.name}"
}

/**
 * Income carries the payments glyph rather than the category's own. Nothing in the spec asks
 * for it — FR-10 is about filing a known counterparty without asking — so it is a house rule.
 *
 * Here rather than in a screen for the same reason [displayName] is: the Ledger row and the
 * detail card draw the same transaction, and a second copy of this rule is how the two start
 * showing different pictures for one row.
 */
fun Transaction.iconName(categories: List<Category>): String = when (direction) {
    Direction.Income -> INCOME_ICON
    Direction.Expense -> category(categories)?.icon ?: UNCATEGORISED_ICON
}

/**
 * The tint the row's category wears, beside [iconName] for the same reason: one transaction
 * drawn on two screens has to come out the same colour on both.
 *
 * Unlike the glyph, income is not forced. A reader can hold several income categories now, and
 * telling salary from a refund is exactly what a colour is for.
 */
fun Transaction.colorName(categories: List<Category>): String =
    category(categories)?.color ?: DEFAULT_COLOR

/** The glyph for spending whose category names none, and for a category that has gone. */
private const val UNCATEGORISED_ICON = "more_horiz"

private const val INCOME_ICON = "payments"
