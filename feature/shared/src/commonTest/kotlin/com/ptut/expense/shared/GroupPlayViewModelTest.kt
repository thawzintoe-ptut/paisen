package com.ptut.expense.shared

import com.ptut.expense.ViewModelTest
import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.play.EarnAllSquareUseCase
import com.ptut.expense.domain.play.FakePlayBook
import com.ptut.expense.domain.play.GetGroupAwardsUseCase
import com.ptut.expense.domain.play.GetTripRecapUseCase
import com.ptut.expense.domain.play.ReactToPaymentUseCase
import com.ptut.expense.domain.play.SetTripEndedUseCase
import com.ptut.expense.domain.play.StampKind
import com.ptut.expense.domain.shared.GroupType
import com.ptut.expense.domain.shared.ObserveSharedSnapshotUseCase
import com.ptut.expense.domain.shared.ObserveSyncStatusUseCase
import com.ptut.expense.domain.shared.PaymentMethod
import com.ptut.expense.domain.shared.PaymentReaction
import com.ptut.expense.domain.shared.ReactionKind
import com.ptut.expense.domain.shared.SharedGroup
import com.ptut.expense.domain.shared.SharedPayment
import com.ptut.expense.domain.today.GetHomeTodayUseCase
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class GroupPlayViewModelTest : ViewModelTest() {

    /** Flat 12 shared: Ko Ko holds "me", Aye holds "aye". Aye paid back the 240 dinner share, so all are square. */
    private val shared = flat12.copy(remote = true, members = flat12.members.map { it.copy(claimedByUid = "uid-${it.id}") })
    private val dinner = shared.dinner(paidBy = "me", wholeMinor = 48_000)
    private val payBack = SharedPayment("p1", "g1", "aye", "me", Satang(24_000), PaymentMethod.Cash, 1, "aye", 1)

    private fun repository(group: SharedGroup = shared, payments: List<SharedPayment> = listOf(payBack), reactions: List<PaymentReaction> = emptyList()) =
        FakeSharedRepository(group).also { it.snapshot.value = it.snapshot.value.copy(expenseVersions = listOf(dinner), payments = payments, reactions = reactions) }

    private fun TestScope.play(repository: FakeSharedRepository, book: FakePlayBook = FakePlayBook()): GroupPlayViewModel {
        val today = GetHomeTodayUseCase()
        val play = GroupPlayViewModel(
            groupId = "g1",
            observeSnapshot = ObserveSharedSnapshotUseCase(repository),
            observeSyncStatus = ObserveSyncStatusUseCase(),
            playOn = { true },
            earnAllSquare = EarnAllSquareUseCase(book, today),
            reactToPayment = ReactToPaymentUseCase(repository),
            setTripEnded = SetTripEndedUseCase(repository, today),
        )
        backgroundScope.launch { play.uiState.collect {} }
        runCurrent()
        return play
    }

    @Test
    fun `a settled group shows all square and earns the stamp once`() = runTest {
        val book = FakePlayBook()

        val state = play(repository(), book).uiState.value

        assertNotNull(state.allSquare)
        assertEquals(listOf("AllSquare:g1:p1"), book.stamps.value.map { it.id })
        assertEquals(StampKind.AllSquare, book.stamps.value.single().kind)
    }

    @Test
    fun `a member who turned stamps off sees no all square and earns nothing`() = runTest {
        val book = FakePlayBook()
        val optedOut = shared.copy(members = shared.members.map { if (it.id == "me") it.copy(playOff = true) else it })

        assertNull(play(repository(optedOut), book).uiState.value.allSquare)
        assertEquals(emptyList(), book.stamps.value)
    }

    @Test
    fun `while anyone owes there is no all square`() = runTest {
        assertNull(play(repository(payments = emptyList())).uiState.value.allSquare)
    }

    @Test
    fun `reactions name who sent them and mark mine`() = runTest {
        val reactions = listOf(
            PaymentReaction("p1_uid-aye", "g1", "p1", "uid-aye", ReactionKind.Thanks),
            PaymentReaction("p1_uid-me", "g1", "p1", "uid-me", ReactionKind.Cheers),
        )

        val words = assertNotNull(play(repository(reactions = reactions)).uiState.value.reactions["p1"])

        assertEquals(listOf("Thanks from Aye", "Cheers from Ko Ko"), words.sent)
        assertEquals(ReactionKind.Cheers, words.options.single { it.chosen }.kind)
    }

    @Test
    fun `only a trip can be marked ended`() = runTest {
        assertNull(play(repository()).uiState.value.tripEnded)
        assertEquals(false, play(repository(shared.copy(type = GroupType.Trip))).uiState.value.tripEnded)
    }

    @Test
    fun `awards wait for an up-to-date phone and say when the month has too little`() = runTest {
        val awards = GroupAwardsViewModel("g1", ObserveSharedSnapshotUseCase(repository()), ObserveSyncStatusUseCase(), GetGroupAwardsUseCase(), GetHomeTodayUseCase())
        backgroundScope.launch { awards.uiState.collect {} }
        runCurrent()

        assertEquals("Not enough this month for awards yet.", awards.uiState.value?.line)
    }

    @Test
    fun `a trip recap leads with the cost per person per day and offers a jar for a trip like it`() = runTest {
        val trip = shared.copy(type = GroupType.Trip)
        val recap = TripRecapViewModel("g1", ObserveSharedSnapshotUseCase(repository(trip)), GetTripRecapUseCase(), shareAmounts = flowOf(false))
        backgroundScope.launch { recap.uiState.collect {} }
        runCurrent()

        val state = assertIs<TripRecapUiState.Loaded>(recap.uiState.value)
        assertEquals("฿240.00", state.perPersonPerDay)
        assertEquals("1 day, 2 people", state.line)
        assertEquals("300.00", state.jarTarget)
        assertEquals(false, state.shareAmounts)
    }
}
