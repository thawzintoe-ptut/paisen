package com.ptut.expense.domain.slip

import com.ptut.expense.domain.QueuedSlip
import com.ptut.expense.domain.ReviewReason
import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.ledger.FakeLedger
import com.ptut.expense.domain.shared.Currencies
import com.ptut.expense.domain.shared.GroupType
import com.ptut.expense.domain.shared.Member
import com.ptut.expense.domain.shared.RecordingSharedRepository
import com.ptut.expense.domain.shared.Share
import com.ptut.expense.domain.shared.SharedExpense
import com.ptut.expense.domain.shared.SharedGroup
import com.ptut.expense.domain.shared.SharedSnapshot
import com.ptut.expense.domain.shared.SplitMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class SlipUseCasesTest {

    private val now = Instant.parse("2026-11-20T12:00:00Z")

    private val clock = object : Clock {
        override fun now(): Instant = this@SlipUseCasesTest.now
    }

    private fun slip(id: String, capturedAt: Instant, amount: Satang = Satang(48_500)) = QueuedSlip(
        id = id,
        merchant = "MK Restaurant",
        occurredAtEpochMillis = capturedAt.toEpochMilliseconds(),
        note = "",
        amount = amount,
        reason = ReviewReason.NewMerchant,
        capturedAtEpochMillis = capturedAt.toEpochMilliseconds(),
    )

    @Test
    fun aSlipWaitingOnItsDiscardIsLeftOut() = runTest {
        val pending = MutableStateFlow(setOf("q2"))
        val observe = ObserveQueuedSlipsUseCase(FakeLedger(queued = listOf(slip("q1", now), slip("q2", now))), pending)

        assertEquals(listOf("q1"), observe().first().map { it.id })

        pending.value = emptySet()
        assertEquals(listOf("q1", "q2"), observe().first().map { it.id })
    }

    @Test
    fun theSoonestDeadlineIsTheOldestSlips() {
        val soonest = GetSoonestEvidenceExpiryUseCase(clock)
        val slips = listOf(slip("new", now - 1.hours), slip("old", now - 5.days - 1.hours))

        assertEquals(2, soonest(slips, retention = 7.days))
    }

    @Test
    fun noSlipsWaitingMeansNoDeadline() {
        assertNull(GetSoonestEvidenceExpiryUseCase(clock)(emptyList(), retention = 7.days))
    }

    @Test
    fun aSlipPastItsWindowHasNoDaysLeft() {
        assertEquals(0, GetSoonestEvidenceExpiryUseCase(clock)(listOf(slip("gone", now - 8.days)), retention = 7.days))
    }

    @Test
    fun aSlipForWhatIOweFindsThatDebtInTheGroupsOnThisPhone() {
        val find = FindSlipDebtsUseCase(enabled = true, shared = RecordingSharedRepository(ayePaidDinner()))

        assertEquals(listOf("Aye"), find(slip("q1", now, amount = HALF)).map { it.toName })
    }

    @Test
    fun noDebtIsOfferedWhileTheSwitchIsOff() {
        val find = FindSlipDebtsUseCase(enabled = false, shared = RecordingSharedRepository(ayePaidDinner()))

        assertTrue(find(slip("q1", now, amount = HALF)).isEmpty())
    }

    @Test
    fun aBuildWithNoGroupsOffersNoDebt() {
        assertTrue(FindSlipDebtsUseCase(enabled = true, shared = null)(slip("q1", now, amount = HALF)).isEmpty())
    }

    /** Aye paid a ฿480 dinner split equally with me, so I owe Aye ฿240. */
    private fun ayePaidDinner(): SharedSnapshot {
        val group = SharedGroup(
            id = "flat",
            name = "Flat 12",
            type = GroupType.Flat,
            icon = "home",
            simplifyDebts = true,
            createdAtEpochMillis = 0,
            meMemberId = "me",
            members = listOf(Member(id = "me", groupId = "flat", name = "Ko Ko", personId = null), Member(id = "aye", groupId = "flat", name = "Aye", personId = null)),
        )
        val whole = HALF + HALF
        val dinner = SharedExpense(
            id = "dinner",
            versionId = "dinner-v1",
            version = 1,
            groupId = "flat",
            description = "Dinner",
            occurredAtEpochMillis = 0,
            categoryId = null,
            subCategoryId = null,
            note = null,
            currency = Currencies.Thb.code,
            amountMinor = whole.toMinorUnits(),
            rate = null,
            satang = whole,
            payerMemberId = "aye",
            splitMode = SplitMode.Equally,
            shares = listOf(Share("me", 1, HALF.toMinorUnits(), HALF), Share("aye", 1, HALF.toMinorUnits(), HALF)),
            authorMemberId = "aye",
            createdAtEpochMillis = 0,
            removed = false,
        )
        return SharedSnapshot(groups = listOf(group), people = emptyList(), expenseVersions = listOf(dinner), payments = emptyList(), links = emptyMap())
    }

    private companion object {
        val HALF = Satang.ofMinorUnits(24_000)
    }
}
