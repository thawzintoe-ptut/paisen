package com.ptut.expense.ui

import com.ptut.expense.domain.Category
import com.ptut.expense.domain.HomeTime
import com.ptut.expense.domain.MetaParts
import com.ptut.expense.domain.Provenance
import com.ptut.expense.domain.SubCategory
import com.ptut.expense.domain.Transaction
import com.ptut.expense.domain.category
import com.ptut.expense.domain.isAutomatic
import com.ptut.expense.domain.rules.ShopNames
import com.ptut.expense.domain.shared.Currencies
import com.ptut.expense.domain.shared.formatMinor
import com.ptut.expense.domain.slip.isReaderNoise
import com.ptut.expense.domain.timeOfDayIn
import com.ptut.expense.domain.under
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
 * It sits in core/ui because Home, History, Detail, Activity and the day receipt all name rows,
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

/** Who filed a row, in the reader's words. */
val Provenance.label: String
    get() = when (this) {
        Provenance.Auto -> AppText.copy.phrases.auto
        Provenance.ByHand -> AppText.copy.phrases.addedByHand
    }

/** The line under a Sky row's name, "Groceries · auto"; the time sits under the amount (#146, K7). */
fun MetaParts.categoryAndProvenance(): String = listOfNotNull(category, foreign, provenance.label).joinToString(" · ")

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
    zone: TimeZone = HomeTime,
    subCategories: List<SubCategory> = emptyList(),
): MetaParts = MetaParts(
    category = filedAs(categories, subCategories).takeIf { ownName() != null },
    time = if (source.isAutomatic) occurredAtEpochMillis.timeOfDayIn(zone) else null,
    provenance = if (source.isAutomatic) Provenance.Auto else Provenance.ByHand,
    foreign = foreign?.let { formatMinor(it.originalMinor, Currencies.of(it.currency)) },
)

/** The line under a merchant name on a row: [metaParts] joined, "Groceries · 18:30 · auto". */
fun Transaction.metaLine(
    categories: List<Category>,
    zone: TimeZone = HomeTime,
    subCategories: List<SubCategory> = emptyList(),
): String = metaParts(categories, zone, subCategories).joined()

private fun MetaParts.joined(): String = listOfNotNull(category, foreign, time, provenance.label).joinToString(" · ")

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
