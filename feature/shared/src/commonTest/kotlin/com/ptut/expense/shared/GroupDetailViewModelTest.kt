package com.ptut.expense.shared

import com.ptut.expense.ViewModelTest
import com.ptut.expense.domain.HomeTime
import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.shared.Member
import com.ptut.expense.domain.shared.Share
import com.ptut.expense.domain.shared.SharedExpense
import com.ptut.expense.domain.shared.SharedGroup
import com.ptut.expense.domain.shared.SharedSnapshot
import com.ptut.expense.domain.shared.SplitMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Clock
import kotlin.time.Instant

/** Flat 12 with Mali too, so two people can owe you and the largest debt can be told apart. */
internal val flatOfThree = flat12.copy(
    simplifyDebts = false,
    lastSeenAtEpochMillis = SEEN,
    members = flat12.members + Member(id = "mali", groupId = "g1", name = "Mali", personId = null),
)

internal const val SEEN = 1_000L
internal const val NOW = 5_000L

private val fixedClock = object : Clock {
    override fun now(): Instant = Instant.fromEpochMilliseconds(NOW)
}

class GroupDetailViewModelTest : ViewModelTest() {

    @Test
    fun `the group opens on Expenses`() = runTest {
        val viewModel = viewModel()

        assertEquals(GroupTab.Expenses, viewModel.tab.value)
    }

    @Test
    fun `activity counts from the last visit until you leave the tab`() = runTest {
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
    fun `moving between Expenses and Balances keeps the count`() = runTest {
        val viewModel = viewModel()

        viewModel.showTab(GroupTab.Balances)
        viewModel.showTab(GroupTab.Expenses)
        runCurrent()

        assertEquals(SEEN, viewModel.activitySince.value)
    }

    @Test
    fun `the screen shows the group it opened`() = runTest {
        val state = loaded()

        assertEquals("g1", state.groupId)
        assertEquals("Flat 12", state.name)
    }

    private fun TestScope.loaded(vararg expenses: SharedExpense, group: SharedGroup = flatOfThree): GroupDetailUiState.Loaded =
        assertIs<GroupDetailUiState.Loaded>(viewModel(*expenses, group = group).uiState.value)

    private fun TestScope.viewModel(vararg expenses: SharedExpense, group: SharedGroup = flatOfThree): GroupDetailViewModel {
        val repository = FakeSharedRepository(group)
        repository.snapshot.value = SharedSnapshot(groups = listOf(group), people = emptyList(), expenseVersions = expenses.toList(), payments = emptyList(), links = emptyMap())
        val viewModel = GroupDetailViewModel(groupId = group.id, repository = repository, categories = emptyList(), clock = fixedClock, work = Dispatchers.Main)
        backgroundScope.launch { viewModel.uiState.collect {} }
        runCurrent()
        return viewModel
    }
}

/** Noon in Bangkok on 12 November of [year]; 2020 and 2026 both fall on a Thursday. */
internal fun noon(year: Int): Long = LocalDateTime(year, 11, 12, 12, 0).toInstant(HomeTime).toEpochMilliseconds()

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
