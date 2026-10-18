package com.ptut.expense.domain

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals

class TransactionMetaTest {

    private val bangkok = TimeZone.of("Asia/Bangkok")

    @Test
    fun namesTheCategoryTheBangkokTimeAndThatTheAppFiledIt() {
        val transaction = transaction(
            occurredAt = at(2026, 8, 14, hour = 18, minute = 30),
            categoryId = "groceries",
            source = TransactionSource.Scan,
        )

        assertEquals("Groceries · 18:30 · auto", transaction.metaLine(Category.seed(), bangkok))
    }

    /**
     * No time on a typed row. Its instant is when the app was opened rather than when the money
     * was spent, so printing it would answer a question nobody asked with a number that is wrong.
     */
    @Test
    fun saysAddedByHandAndNoTimeWhenThePersonTypedItRatherThanTheImporter() {
        val transaction = transaction(
            occurredAt = at(2026, 8, 14, hour = 9, minute = 5),
            categoryId = "transport",
            source = TransactionSource.Manual,
        )

        assertEquals("Transport · added by hand", transaction.metaLine(Category.seed(), bangkok))
    }

    @Test
    fun callsARowWithNoCategoryUncategorisedRatherThanPrintingNothing() {
        val transaction = transaction(
            occurredAt = at(2026, 8, 14, hour = 8, minute = 12),
            categoryId = null,
            source = TransactionSource.Scan,
        )

        assertEquals(
            "Uncategorised · 08:12 · auto",
            transaction.metaLine(Category.seed(), bangkok),
        )
    }

    /** #156: a Sky row puts the time under the amount, so the parts come apart before the join. */
    @Test
    fun splitsAnImportedRowIntoItsCategoryItsBangkokTimeAndAuto() {
        val transaction = transaction(
            occurredAt = at(2026, 8, 14, hour = 18, minute = 30),
            categoryId = "groceries",
            source = TransactionSource.Scan,
        )

        assertEquals(
            MetaParts(category = "Groceries", time = "18:30", provenance = Provenance.Auto),
            transaction.metaParts(Category.seed(), bangkok),
        )
    }

    @Test
    fun givesATypedRowNoTimeAndSaysThePersonAddedIt() {
        val transaction = transaction(
            occurredAt = at(2026, 8, 14, hour = 9, minute = 5),
            categoryId = "transport",
            source = TransactionSource.Manual,
        )

        assertEquals(
            MetaParts(category = "Transport", time = null, provenance = Provenance.ByHand),
            transaction.metaParts(Category.seed(), bangkok),
        )
    }

    /** p34: "Groceries › Supermarket". "›" because "·" already separates the parts (#112). */
    @Test
    fun namesTheSubCategoryBesideItsCategory() {
        val transaction = transaction(
            occurredAt = at(2026, 8, 14, hour = 18, minute = 30),
            categoryId = "groceries",
            source = TransactionSource.Scan,
        ).copy(subCategoryId = "supermarket")

        assertEquals(
            "Groceries › Supermarket · 18:30 · auto",
            transaction.metaLine(Category.seed(), bangkok, subCategories),
        )
        assertEquals("Groceries › Supermarket", transaction.filedAs(Category.seed(), subCategories))
    }

    @Test
    fun leavesOutASubCategoryThatIsNotUnderTheRowsCategory() {
        val transaction = transaction(
            occurredAt = at(2026, 8, 14, hour = 18, minute = 30),
            categoryId = "groceries",
            source = TransactionSource.Scan,
        ).copy(subCategoryId = "bts")

        assertEquals("Groceries", transaction.filedAs(Category.seed(), subCategories))
    }

    private val subCategories = listOf(
        SubCategory("supermarket", "groceries", "Supermarket"),
        SubCategory("bts", "transport", "BTS"),
    )

    @Test
    fun drawsSpendingWithTheGlyphOfTheCategoryItIsFiledUnder() {
        val transaction = transaction(
            occurredAt = at(2026, 8, 14, hour = 18, minute = 30),
            categoryId = "groceries",
            source = TransactionSource.Scan,
        )

        assertEquals("local_grocery_store", transaction.iconName(Category.seed()))
    }

    @Test
    fun drawsSpendingWithNoCategoryAsUncategorisedRatherThanAsNothing() {
        val transaction = transaction(
            occurredAt = at(2026, 8, 14, hour = 8, minute = 12),
            categoryId = null,
            source = TransactionSource.Scan,
        )

        assertEquals("more_horiz", transaction.iconName(Category.seed()))
    }

    /**
     * The row the income branch exists for. A restored backup drops a category this install does
     * not hold, and money in has to keep reading as money in once its filing is gone.
     */
    @Test
    fun keepsIncomeOnThePaymentsGlyphWhenItsCategoryNoLongerResolves() {
        val transaction = transaction(
            occurredAt = at(2026, 8, 10, hour = 9, minute = 0),
            categoryId = null,
            source = TransactionSource.Manual,
            direction = Direction.Income,
        )

        assertEquals("payments", transaction.iconName(Category.seed()))
    }

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        LocalDateTime(year, month, day, hour, minute).toInstant(bangkok).toEpochMilliseconds()

    private fun transaction(
        occurredAt: Long,
        categoryId: String?,
        source: TransactionSource,
        direction: Direction = Direction.Expense,
    ) = Transaction(
        id = "t1",
        occurredAtEpochMillis = occurredAt,
        amount = Satang(64_250),
        direction = direction,
        categoryId = categoryId,
        counterparty = "Foodland Rama IV",
        source = source,
    )
}
