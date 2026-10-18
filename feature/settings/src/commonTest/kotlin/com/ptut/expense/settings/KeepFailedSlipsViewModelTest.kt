package com.ptut.expense.settings

import com.ptut.expense.ViewModelTest
import com.ptut.expense.domain.BangkokTime
import com.ptut.expense.domain.QueuedSlip
import com.ptut.expense.domain.ReviewReason
import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.ledger.FakeLedger
import com.ptut.expense.domain.settings.ImportChoices
import com.ptut.expense.domain.settings.ImportSettings
import com.ptut.expense.domain.settings.importSettings
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

private const val DAY = 24L * 60 * 60 * 1000

class KeepFailedSlipsViewModelTest : ViewModelTest() {

    private val now = LocalDateTime(2026, 8, 14, 19, 41).toInstant(BangkokTime)

    @Test
    fun `the shipped choice is the seven days NFR-4 names`() = runTest {
        val chosen = state().options.single { it.chosen }

        assertEquals(7, chosen.days)
        assertEquals("7 days", chosen.label)
    }

    @Test
    fun `choosing a window writes it`() = runTest {
        val settings = importSettings()

        viewModel(settings = settings).choose(30)

        assertEquals(30.days, settings.choices.value.retention)
    }

    /** The list is the offer. A window nobody offered would leave the screen with nothing marked. */
    @Test
    fun `a window nobody offered is refused`() = runTest {
        val settings = importSettings()

        viewModel(settings = settings).choose(days = 365)

        assertEquals(7.days, settings.choices.value.retention)
    }

    @Test
    fun `an empty queue says nothing is being kept and names no deadline`() = runTest {
        val state = state()

        assertEquals("No slips are waiting — nothing is being kept", state.kept)
        assertNull(state.deadline)
    }

    @Test
    fun `one waiting slip is counted in the singular`() = runTest {
        assertEquals("1 slip is waiting for you", state(listOf(slip("q1", ageMillis = 0))).kept)
    }

    /** The soonest to expire is the deadline the reader has; the newest would promise more time. */
    @Test
    fun `the deadline names the oldest slip and not the newest`() = runTest {
        val state = state(
            listOf(slip("old", ageMillis = 5 * DAY), slip("new", ageMillis = 0)),
        )

        assertEquals("2 slips are waiting for you", state.kept)
        assertEquals("The oldest has 2 days left", state.deadline)
    }

    @Test
    fun `a shortened window moves the deadline in`() = runTest {
        val state = state(
            queued = listOf(slip("old", ageMillis = 2 * DAY)),
            settings = importSettings(ImportChoices(retention = 3.days)),
        )

        assertEquals("The oldest has 1 day left", state.deadline)
    }

    @Test
    fun `a slip past its window has today rather than a negative count`() = runTest {
        val state = state(listOf(slip("gone", ageMillis = 9 * DAY)))

        assertEquals("The oldest loses its image today", state.deadline)
    }

    /**
     * Shared `WhileSubscribed`, so `uiState.value` is the initial value until something collects.
     * Reading it straight would answer for an empty queue every time.
     */
    private fun TestScope.state(
        queued: List<QueuedSlip> = emptyList(),
        settings: ImportSettings = importSettings(),
    ): KeepFailedSlipsUiState {
        val uiState = viewModel(queued, settings).uiState
        backgroundScope.launch { uiState.collect { } }
        runCurrent()
        return uiState.value
    }

    private fun viewModel(
        queued: List<QueuedSlip> = emptyList(),
        settings: ImportSettings = importSettings(),
    ) = KeepFailedSlipsViewModel(
        ledger = FakeLedger(queued = queued),
        settings = settings,
        clock = FixedClock(now),
    )

    private fun slip(id: String, ageMillis: Long) = QueuedSlip(
        id = id,
        merchant = "Foodland",
        occurredAtEpochMillis = now.toEpochMilliseconds(),
        note = "ref 0142K9",
        amount = Satang(12_000),
        reason = ReviewReason.AmountUnclear,
        capturedAtEpochMillis = now.toEpochMilliseconds() - ageMillis,
    )

    private class FixedClock(private val at: Instant) : Clock {
        override fun now(): Instant = at
    }
}
