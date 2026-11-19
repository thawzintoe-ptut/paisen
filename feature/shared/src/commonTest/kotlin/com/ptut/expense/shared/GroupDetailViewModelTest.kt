package com.ptut.expense.shared

import com.ptut.expense.ViewModelTest
import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.shared.Member
import com.ptut.expense.domain.shared.Share
import com.ptut.expense.domain.shared.SharedExpense
import com.ptut.expense.domain.shared.SharedGroup
import com.ptut.expense.domain.shared.SharedSnapshot
import com.ptut.expense.domain.shared.SplitMode
import com.ptut.expense.locale.AppText
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/** Flat 12 with Mali too, so two people can owe you and the largest debt can be told apart. */
private val flatOfThree = flat12.copy(
    simplifyDebts = false,
    lastSeenAtEpochMillis = SEEN,
    members = flat12.members + Member(id = "mali", groupId = "g1", name = "Mali", personId = null),
)

private const val SEEN = 1_000L
private const val NOW = 5_000L

private val fixedClock = object : Clock {
    override fun now(): Instant = Instant.fromEpochMilliseconds(NOW)
}

class GroupDetailViewModelTest : ViewModelTest() {

    @Test
    fun `Settle up names the largest debt that involves you`() = runTest {
        // You owe Aye 300, and Mali owes you 150.
        val state = loaded(
            expense("rent", payer = "aye", "me" to 30_000, "aye" to 30_000, "mali" to 30_000),
            expense("snacks", payer = "me", "me" to 15_000, "mali" to 15_000),
        )

        val settle = assertNotNull(state.settleUp)
        assertEquals("me", settle.fromId)
        assertEquals("aye", settle.toId)
        assertTrue(settle.line.startsWith("with Aye"))
        assertTrue(settle.line.contains("300"))
        assertNull(state.balanceChip)
        assertTrue(state.hasActivity)
    }

    @Test
    fun `Settle up is hidden and the chip says all settled up when everyone is square`() = runTest {
        val state = loaded(expense("lunch", payer = "me", "me" to 20_000))

        assertNull(state.settleUp)
        assertEquals(AppText.copy.shared.detail.allSettledUp, state.balanceChip)
    }

    @Test
    fun `A group with no expenses says nothing to settle yet`() = runTest {
        val state = loaded()

        assertNull(state.settleUp)
        assertEquals(AppText.copy.shared.detail.nothingToSettle, state.balanceChip)
        assertFalse(state.hasActivity)
        assertEquals(AppText.copy.shared.detail.nothingSpent("THB"), state.spendLine)
    }

    @Test
    fun `A group of one says just you so far`() = runTest {
        val alone = flatOfThree.copy(members = flatOfThree.members.take(1))
        val state = loaded(group = alone)

        assertEquals(AppText.copy.shared.detail.justYou, state.memberLine)
    }

    @Test
    fun `The group opens on Expenses`() = runTest {
        val viewModel = viewModel()

        assertEquals(GroupTab.Expenses, viewModel.tab.value)
    }

    @Test
    fun `Activity counts from the last visit until you leave the tab`() = runTest {
        val viewModel = viewModel()
        assertEquals(SEEN, viewModel.activitySince.value)

        viewModel.showTab(GroupTab.Activity)
        runCurrent()
        assertEquals(SEEN, viewModel.activitySince.value)

        viewModel.showTab(GroupTab.Balances)
        runCurrent()
        assertEquals(NOW, viewModel.activitySince.value)
        assertEquals(GroupTab.Balances, viewModel.tab.value)
    }

    @Test
    fun `Moving between Expenses and Balances keeps the count`() = runTest {
        val viewModel = viewModel()

        viewModel.showTab(GroupTab.Balances)
        viewModel.showTab(GroupTab.Expenses)
        runCurrent()

        assertEquals(SEEN, viewModel.activitySince.value)
    }

    private fun TestScope.loaded(vararg expenses: SharedExpense, group: SharedGroup = flatOfThree): GroupDetailUiState.Loaded =
        assertIs<GroupDetailUiState.Loaded>(viewModel(*expenses, group = group).uiState.value)

    private fun TestScope.viewModel(vararg expenses: SharedExpense, group: SharedGroup = flatOfThree): GroupDetailViewModel {
        val repository = FakeSharedRepository(group)
        repository.snapshot.value = SharedSnapshot(groups = listOf(group), people = emptyList(), expenseVersions = expenses.toList(), payments = emptyList(), links = emptyMap())
        val viewModel = GroupDetailViewModel(groupId = group.id, repository = repository, categories = emptyList(), clock = fixedClock)
        backgroundScope.launch { viewModel.uiState.collect {} }
        runCurrent()
        return viewModel
    }
}

/** An expense in baht, each share in minor units; the whole is what the shares add up to. */
internal fun expense(id: String, payer: String, vararg shares: Pair<String, Long>, author: String = payer, createdAt: Long = 0): SharedExpense {
    val whole = shares.sumOf { it.second }
    return SharedExpense(
        id = id,
        versionId = "$id-v1",
        version = 1,
        groupId = "g1",
        description = id,
        occurredAtEpochMillis = createdAt,
        categoryId = null,
        subCategoryId = null,
        note = null,
        currency = "THB",
        amountMinor = whole,
        rate = null,
        satang = Satang.ofMinorUnits(whole),
        payerMemberId = payer,
        splitMode = SplitMode.Equally,
        shares = shares.map { (member, minor) -> Share(member, 1, minor, Satang.ofMinorUnits(minor)) },
        authorMemberId = author,
        createdAtEpochMillis = createdAt,
        removed = false,
    )
}
