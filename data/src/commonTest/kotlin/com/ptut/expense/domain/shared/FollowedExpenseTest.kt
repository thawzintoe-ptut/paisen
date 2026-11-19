package com.ptut.expense.domain.shared

import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.TransactionEdit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FollowedExpenseTest {

    private val members = listOf(
        SplitMember("me", "You", isMe = true),
        SplitMember("aye", "Aye", isMe = false),
        SplitMember("dan", "Dan", isMe = false),
    )

    private val lunch = SharedExpense(
        id = "e1",
        versionId = "v1",
        version = 1,
        groupId = "g1",
        description = "Food",
        occurredAtEpochMillis = 1_000,
        categoryId = "food",
        subCategoryId = null,
        note = "noodles",
        currency = "THB",
        amountMinor = 60_000,
        rate = null,
        satang = Satang(60_000),
        payerMemberId = "me",
        splitMode = SplitMode.Equally,
        shares = listOf(Share("me", 1, 30_000, Satang(30_000)), Share("aye", 1, 30_000, Satang(30_000))),
        authorMemberId = "me",
        createdAtEpochMillis = 1_000,
        removed = false,
    )

    private val before = TransactionEdit(Satang(60_000), "food", "noodles", occurredAtEpochMillis = 1_000)

    private fun SharedExpense.follow(after: TransactionEdit, title: TitleChange? = null, groupCurrency: String = "THB") =
        followed(before, after, title, members, groupCurrency, homeCurrency = "THB")

    @Test
    fun anEditThatMovesNothingTheExpenseShowsWritesNoVersion() {
        assertNull(lunch.follow(before))
    }

    @Test
    fun aNewAmountIsSplitBetweenTheSamePeople() {
        val followed = lunch.follow(before.copy(amount = Satang(90_000)))!!

        assertEquals(90_000, followed.amountMinor)
        assertEquals(Satang(90_000), followed.satang)
        assertEquals(SplitMode.Equally, followed.splitMode)
        assertEquals(listOf("aye" to 45_000L, "me" to 45_000L), followed.shares.map { it.memberId to it.amountMinor }.sortedBy { it.first })
    }

    @Test
    fun anExpenseInOtherMoneyKeepsItsOwnAmount() {
        val inDollars = lunch.copy(currency = "USD", amountMinor = 2_000)

        assertNull(inDollars.follow(before.copy(amount = Satang(90_000)), groupCurrency = "USD"))
    }

    @Test
    fun aFieldTheEditLeftAloneKeepsWhatAnotherPhoneMadeIt() {
        val elsewhere = lunch.copy(occurredAtEpochMillis = 5_000, note = "ramen")

        val followed = elsewhere.follow(before.copy(categoryId = "drinks"))!!

        assertEquals(5_000, followed.occurredAtEpochMillis)
        assertEquals("ramen", followed.note)
        assertEquals("drinks", followed.categoryId)
    }

    @Test
    fun aNewCategoryRenamesATitleThatWasTheOldCategoryName() {
        val followed = lunch.follow(before.copy(categoryId = "drinks"), TitleChange(from = "Food", to = "Drinks"))!!

        assertEquals("Drinks", followed.description)
    }

    @Test
    fun aMemoClearedToSpacesClearsTheNote() {
        assertNull(lunch.follow(before.copy(memo = "  "))!!.note)
    }

    @Test
    fun aDateMovesOnlyWhenTheEditCarriedOne() {
        val noDate = before.copy(occurredAtEpochMillis = null)

        assertNull(lunch.followed(noDate, noDate.copy(occurredAtEpochMillis = 9_000), null, members, "THB", "THB"))
        assertEquals(9_000, lunch.follow(before.copy(occurredAtEpochMillis = 9_000))!!.occurredAtEpochMillis)
    }

    @Test
    fun anExactSplitPutsTheDifferenceOnThePayer() {
        val exact = lunch.copy(
            amountMinor = 90_000,
            satang = Satang(90_000),
            splitMode = SplitMode.Exact,
            shares = listOf(Share("me", 10_000, 10_000, Satang(10_000)), Share("aye", 80_000, 80_000, Satang(80_000))),
        )

        val followed = exact.followed(before.copy(amount = Satang(90_000)), before.copy(amount = Satang(95_000)), null, members, "THB", "THB")!!

        assertEquals(SplitMode.Exact, followed.splitMode)
        assertEquals(mapOf("me" to 15_000L, "aye" to 80_000L), followed.shares.associate { it.memberId to it.amountMinor })
    }

    @Test
    fun anExactSplitThePayerCannotAbsorbFallsBackToSharesByTheOldParts() {
        val exact = lunch.copy(
            amountMinor = 90_000,
            satang = Satang(90_000),
            splitMode = SplitMode.Exact,
            shares = listOf(Share("me", 10_000, 10_000, Satang(10_000)), Share("aye", 80_000, 80_000, Satang(80_000))),
        )

        val followed = exact.followed(before.copy(amount = Satang(90_000)), before.copy(amount = Satang(45_000)), null, members, "THB", "THB")!!

        assertEquals(SplitMode.Shares, followed.splitMode)
        assertEquals(mapOf("me" to 10_000L, "aye" to 80_000L), followed.shares.associate { it.memberId to it.weight })
        assertEquals(45_000L, followed.shares.sumOf { it.amountMinor })
        assertEquals(listOf("aye", "me"), followed.shares.map { it.memberId }.sorted())
    }
}
