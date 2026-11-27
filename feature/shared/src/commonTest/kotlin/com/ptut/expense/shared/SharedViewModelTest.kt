package com.ptut.expense.shared

import com.ptut.expense.ViewModelTest
import com.ptut.expense.domain.shared.CreateGroupUseCase
import com.ptut.expense.domain.shared.CreatePersonGroupUseCase
import com.ptut.expense.domain.shared.DismissRefusedChangeUseCase
import com.ptut.expense.domain.shared.GetSharedHomeUseCase
import com.ptut.expense.domain.shared.GroupType
import com.ptut.expense.domain.shared.Member
import com.ptut.expense.domain.shared.NewMember
import com.ptut.expense.domain.shared.ObserveRefusedChangesUseCase
import com.ptut.expense.domain.shared.ObserveSharedSnapshotUseCase
import com.ptut.expense.domain.shared.ObserveSyncStatusUseCase
import com.ptut.expense.domain.shared.PollSyncUseCase
import com.ptut.expense.domain.shared.SharedExpense
import com.ptut.expense.domain.shared.SharedGroup
import com.ptut.expense.domain.shared.SharedRepository
import com.ptut.expense.domain.shared.SharedSnapshot
import com.ptut.expense.domain.today.GetHomeTodayUseCase
import com.ptut.expense.sync.RefusedChange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

class SharedViewModelTest : ViewModelTest() {

    /** #48: ฿240 and K24,000 are not ฿24,240, so the home shows one figure per money, the home's first. */
    @Test
    fun `a baht group and a kyat group give two overall lines and two rows for the same person`() = runTest {
        val repository = FakeSharedRepository()
        repository.snapshot.value = SharedSnapshot(
            groups = listOf(yangonTrip, flat12),
            people = emptyList(),
            expenseVersions = listOf(flat12.dinner(paidBy = "me", wholeMinor = 48_000), yangonTrip.dinner(paidBy = "me2", wholeMinor = 48_000)),
            payments = emptyList(),
            links = emptyMap(),
        )
        val viewModel = SharedViewModel(
            observeSnapshot = ObserveSharedSnapshotUseCase(repository),
            observeSyncStatus = ObserveSyncStatusUseCase(),
            pollSync = PollSyncUseCase(),
            getSharedHome = GetSharedHomeUseCase(GetHomeTodayUseCase()),
            createGroupUseCase = CreateGroupUseCase(repository),
            createPersonGroup = CreatePersonGroupUseCase(repository),
        )
        backgroundScope.launch { viewModel.uiState.collect {} }
        runCurrent()

        val ready = assertIs<SharedUiState.Ready>(viewModel.uiState.value)
        assertEquals(listOf("THB" to "You're owed ฿240.00", "MMK" to "You're owed K24,000"), ready.overall.map { it.money.code to it.owedLine })
        assertEquals(listOf("Overall, you are owed", "Overall, you are owed"), ready.overall.map { it.label })
        assertEquals(listOf(null, null), ready.overall.map { it.oweLine })
        // Owed only: the line under the net would say the same figure again.
        assertEquals(listOf(false, false), ready.overall.map { it.showHalves })
        assertEquals("Aye", ready.peopleLine)
        assertNull(ready.payNext)
    }

    @Test
    fun `before any group the tab is the first visit`() = runTest {
        val viewModel = sharedViewModel(FakeSharedRepository().also { it.snapshot.value = it.snapshot.value.copy(groups = emptyList()) })
        backgroundScope.launch { viewModel.uiState.collect {} }
        runCurrent()

        assertIs<SharedUiState.Empty>(viewModel.uiState.value)
    }

    /** You owe Aye 300 and nobody else: the card names the only payment. */
    @Test
    fun `one debt gives one next step that opens the payment to that person`() = runTest {
        val viewModel = sharedViewModel(repository(expense("rent", payer = "aye", "me" to 30_000, "aye" to 30_000, "mali" to 30_000)))
        backgroundScope.launch { viewModel.uiState.collect {} }
        runCurrent()

        val ready = assertIs<SharedUiState.Ready>(viewModel.uiState.value)
        val pay = assertNotNull(ready.payNext)
        assertEquals("Pay Aye ฿300.00", pay.title)
        assertEquals("The only payment you owe", pay.line)
        assertEquals(Triple("g1", "me", "aye"), Triple(pay.groupId, pay.fromMemberId, pay.toMemberId))
        assertEquals("you owe ฿300.00", ready.overall.single().oweLine)
        // People keep the domain's order: who owes you first, then who you owe.
        assertEquals("Mali, Aye", ready.peopleLine)
    }

    /** You owe Aye 300 and Mali 50: the largest comes first, the rest is a count. */
    @Test
    fun `several debts name the largest and count the others`() = runTest {
        val viewModel = sharedViewModel(
            repository(
                expense("rent", payer = "aye", "me" to 30_000, "aye" to 30_000, "mali" to 30_000),
                expense("taxi", payer = "mali", "me" to 5_000, "mali" to 5_000),
            ),
        )
        backgroundScope.launch { viewModel.uiState.collect {} }
        runCurrent()

        val pay = assertNotNull(assertIs<SharedUiState.Ready>(viewModel.uiState.value).payNext)
        assertEquals("Pay Aye ฿300.00", pay.title)
        assertEquals("and 1 more", pay.line)
        assertEquals("aye", pay.toMemberId)
    }

    /** You owe Aye 500 on the trip and she owes you 300 in the flat: the card pays the trip's 500, not the net 200. */
    @Test
    fun `the card shows the debt in the group it pays when the person owes you back elsewhere`() = runTest {
        val trip = bahtTrip(ayeName = "Aye")
        val viewModel = sharedViewModel(snapshotRepository(listOf(flat12, trip), flat12.dinner(paidBy = "me", wholeMinor = 60_000), trip.dinner(paidBy = "aye3", wholeMinor = 100_000)))
        backgroundScope.launch { viewModel.uiState.collect {} }
        runCurrent()

        val pay = assertNotNull(assertIs<SharedUiState.Ready>(viewModel.uiState.value).payNext)
        assertEquals("Pay Aye ฿500.00", pay.title)
        assertEquals("500.00", pay.amount)
        assertEquals(Triple("g3", "me3", "aye3"), Triple(pay.groupId, pay.fromMemberId, pay.toMemberId))
    }

    /** You owe Aye in baht and in dollars: she is one person, so nobody else is counted. */
    @Test
    fun `and N more counts people not currencies`() = runTest {
        val trip = bahtTrip(ayeName = "Aye").copy(currency = "USD")
        val viewModel = sharedViewModel(snapshotRepository(listOf(flat12, trip), flat12.dinner(paidBy = "aye", wholeMinor = 60_000), trip.dinner(paidBy = "aye3", wholeMinor = 10_000)))
        backgroundScope.launch { viewModel.uiState.collect {} }
        runCurrent()

        val pay = assertNotNull(assertIs<SharedUiState.Ready>(viewModel.uiState.value).payNext)
        assertEquals("The only payment you owe", pay.line)
    }

    @Test
    fun `a name written in another case in another group still finds the person to pay`() = runTest {
        val trip = bahtTrip(ayeName = "aye")
        val viewModel = sharedViewModel(snapshotRepository(listOf(flat12, trip), flat12.dinner(paidBy = "me", wholeMinor = 20_000), trip.dinner(paidBy = "aye3", wholeMinor = 100_000)))
        backgroundScope.launch { viewModel.uiState.collect {} }
        runCurrent()

        val pay = assertNotNull(assertIs<SharedUiState.Ready>(viewModel.uiState.value).payNext)
        assertEquals("Pay aye ฿500.00", pay.title)
        assertEquals("aye3", pay.toMemberId)
    }

    @Test
    fun `a second start while the first group is saving creates one group`() = runTest {
        val saving = CompletableDeferred<String>()
        var creates = 0
        val fake = FakeSharedRepository()
        val repository = object : SharedRepository by fake {
            override suspend fun createGroup(name: String, type: GroupType, icon: String, members: List<NewMember>, currency: String): String {
                creates++
                return saving.await()
            }
        }
        val viewModel = SharedViewModel(
            observeSnapshot = ObserveSharedSnapshotUseCase(fake),
            observeSyncStatus = ObserveSyncStatusUseCase(),
            pollSync = PollSyncUseCase(),
            getSharedHome = GetSharedHomeUseCase(GetHomeTodayUseCase()),
            createGroupUseCase = CreateGroupUseCase(repository),
            createPersonGroup = CreatePersonGroupUseCase(fake),
        )
        val opened = mutableListOf<String>()

        viewModel.createGroup("Trip", GroupType.Trip, "work", emptyList(), "THB") { opened += it }
        runCurrent()
        viewModel.createGroup("Trip", GroupType.Trip, "work", emptyList(), "THB") { opened += it }
        saving.complete("g9")
        runCurrent()

        assertEquals(1, creates)
        assertEquals(listOf("g9"), opened)
    }

    /** "Trip" in baht: this phone and Aye under a name [ayeName] may spell differently. */
    private fun bahtTrip(ayeName: String) = flat12.copy(
        id = "g3",
        name = "Trip",
        type = GroupType.Trip,
        meMemberId = "me3",
        members = listOf(Member(id = "me3", groupId = "g3", name = "Ko Ko", personId = null), Member(id = "aye3", groupId = "g3", name = ayeName, personId = null)),
    )

    private fun snapshotRepository(groups: List<SharedGroup>, vararg expenses: SharedExpense) = FakeSharedRepository().also {
        it.snapshot.value = SharedSnapshot(groups = groups, people = emptyList(), expenseVersions = expenses.toList(), payments = emptyList(), links = emptyMap())
    }

    private fun repository(vararg expenses: SharedExpense) = FakeSharedRepository(flatOfThree.copy(simplifyDebts = false)).also {
        it.snapshot.value = it.snapshot.value.copy(expenseVersions = expenses.toList())
    }

    private fun sharedViewModel(repository: FakeSharedRepository) = SharedViewModel(
        observeSnapshot = ObserveSharedSnapshotUseCase(repository),
        observeSyncStatus = ObserveSyncStatusUseCase(),
        pollSync = PollSyncUseCase(),
        getSharedHome = GetSharedHomeUseCase(GetHomeTodayUseCase()),
        createGroupUseCase = CreateGroupUseCase(repository),
        createPersonGroup = CreatePersonGroupUseCase(repository),
    )

    /** #194: a member removed while offline: the group is gone, and what they typed must not go unseen. */
    @Test
    fun `a change refused by a group no longer here is listed and Done puts it away`() = runTest {
        val repository = FakeSharedRepository()
        val sync = FakeSync().apply {
            refusedChanges.value = listOf(
                RefusedChange("version:v1", "gone", "QA4", "Dinner", amountMinor = 8_000, currency = "THB"),
                RefusedChange("version:v2", "g1", "Flat", "Rent", amountMinor = 100, currency = "THB"),
            )
        }
        val viewModel = SharedViewModel(
            observeSnapshot = ObserveSharedSnapshotUseCase(repository),
            observeSyncStatus = ObserveSyncStatusUseCase(),
            pollSync = PollSyncUseCase(),
            getSharedHome = GetSharedHomeUseCase(GetHomeTodayUseCase()),
            createGroupUseCase = CreateGroupUseCase(repository),
            createPersonGroup = CreatePersonGroupUseCase(repository),
            observeRefused = ObserveRefusedChangesUseCase(sync),
            dismissRefused = DismissRefusedChangeUseCase(sync),
        )
        backgroundScope.launch { viewModel.uiState.collect {} }
        runCurrent()

        // The group still here ("g1") shows its own on its screen.
        assertEquals(listOf("QA4 didn't accept: Dinner ฿80.00"), viewModel.uiState.value.lost.map { it.line })

        viewModel.dismissLost("version:v1")
        runCurrent()

        assertEquals(emptyList(), viewModel.uiState.value.lost)
    }
}
