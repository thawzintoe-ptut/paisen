package com.ptut.expense.shared

import com.ptut.expense.ViewModelTest
import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.shared.GetSettlementUseCase
import com.ptut.expense.domain.shared.Member
import com.ptut.expense.domain.shared.ObserveSharedSnapshotUseCase
import com.ptut.expense.domain.shared.ObserveSyncStatusUseCase
import com.ptut.expense.domain.shared.SavePromptPayUseCase
import com.ptut.expense.domain.shared.SetSimplifyDebtsUseCase
import com.ptut.expense.domain.shared.Share
import com.ptut.expense.domain.shared.SharedExpense
import com.ptut.expense.domain.shared.SharedGroup
import com.ptut.expense.domain.shared.SplitMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class WhoPaysWhomViewModelTest : ViewModelTest() {

    /** Me, Aye and Bo; Aye paid 900 for all three, so Me and Bo each owe Aye 300. */
    private val group = flat12.copy(members = flat12.members + Member(id = "bo", groupId = "g1", name = "Bo", personId = null))

    private val dinner = SharedExpense(
        id = "e1",
        versionId = "e1-v1",
        version = 1,
        groupId = "g1",
        description = "Dinner",
        occurredAtEpochMillis = 0,
        categoryId = null,
        subCategoryId = null,
        note = null,
        currency = "THB",
        amountMinor = 90_000,
        rate = null,
        satang = Satang(90_000),
        payerMemberId = "aye",
        splitMode = SplitMode.Equally,
        shares = listOf("me", "aye", "bo").map { Share(it, 1, 30_000, Satang(30_000)) },
        authorMemberId = "aye",
        createdAtEpochMillis = 0,
        removed = false,
    )

    private fun TestScope.loaded(planOn: Boolean = true, group: SharedGroup = this@WhoPaysWhomViewModelTest.group): WhoPaysWhomUiState.Loaded {
        val repository = FakeSharedRepository(group)
        repository.snapshot.value = repository.snapshot.value.copy(expenseVersions = listOf(dinner))
        val who = WhoPaysWhomViewModel(
            groupId = "g1",
            observeSnapshot = ObserveSharedSnapshotUseCase(repository),
            getSettlement = GetSettlementUseCase(),
            savePromptPay = SavePromptPayUseCase(repository),
            setSimplifyDebts = SetSimplifyDebtsUseCase(repository),
            work = Dispatchers.Unconfined,
            observeSyncStatus = ObserveSyncStatusUseCase(),
            planOn = planOn,
        )
        backgroundScope.launch { who.uiState.collect {} }
        runCurrent()
        return assertIs<WhoPaysWhomUiState.Loaded>(who.uiState.value)
    }

    @Test
    fun `the plan puts my part first with its badge and the amount Record payment takes`() = runTest {
        val plan = assertNotNull(loaded().plan)

        assertEquals("2 payments settle everyone", plan.headline)
        assertEquals("me", plan.cards.first().fromId)
        assertEquals(plan.cards.first(), plan.myPart)
        assertEquals("300.00", plan.myAmount)
        assertEquals("Your part", plan.badge)
    }

    @Test
    fun `no plan while Simplify debts is off or play is off`() = runTest {
        assertNull(loaded(group = group.copy(simplifyDebts = false)).plan)
        assertNull(loaded(planOn = false).plan)
    }

    @Test
    fun `when I pay nothing there is no part to settle`() = runTest {
        val plan = assertNotNull(loaded(group = group.copy(meMemberId = "aye")).plan)

        assertNull(plan.myPart)
        assertNull(plan.myAmount)
    }
}
