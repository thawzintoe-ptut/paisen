package com.ptut.expense.domain.play

import com.ptut.expense.domain.BangkokTime
import com.ptut.expense.domain.QueuedSlip
import com.ptut.expense.domain.ReviewReason
import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.ledger.FakeLedger
import com.ptut.expense.domain.ledger.ObserveTransactionsUseCase
import com.ptut.expense.domain.settings.AppChoices
import com.ptut.expense.domain.settings.ObserveAppChoicesUseCase
import com.ptut.expense.domain.settings.PlayChoices
import com.ptut.expense.domain.settings.appSettings
import com.ptut.expense.domain.slip.ObserveQueuedSlipsUseCase
import com.ptut.expense.domain.today.ObserveScanningUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

private val SETTLE = 3.seconds

class WatchTrayUseCaseTest {

    private val noon: Instant = LocalDateTime(2026, 11, 25, 12, 0).toInstant(BangkokTime)
    private val today = LocalDate(2026, 11, 25)
    private val book = FakePlayBook()
    private val pending = MutableStateFlow(emptySet<String>())
    private val scanning = MutableStateFlow(false)

    private fun TestScope.watch(queued: List<QueuedSlip> = emptyList(), play: PlayChoices = PlayChoices()) {
        val ledger = FakeLedger(queued = queued)
        val watch = WatchTrayUseCase(
            queued = ObserveQueuedSlipsUseCase(ledger, pending),
            scanning = ObserveScanningUseCase(scanning),
            playOn = ObservePlayOnUseCase(ObserveAppChoicesUseCase(appSettings(AppChoices(play = play))), PlayGate(flagOn = true, debug = true)),
            transactions = ObserveTransactionsUseCase(ledger),
            book = book,
            clock = object : Clock {
                override fun now(): Instant = noon
            },
            settle = SETTLE,
        )
        backgroundScope.launch { watch() }
        settled()
    }

    /** Past the settle window, short of the next midnight, so only the tray decides. */
    private fun TestScope.settled() {
        runCurrent()
        advanceTimeBy(SETTLE + 1.seconds)
        runCurrent()
    }

    @Test
    fun `an empty tray marks today once with its weekday stamp`() = runTest {
        watch()

        assertEquals(listOf(today), book.days.value.map { it.date })
        assertEquals(listOf(StampKind.Wednesday), book.stamps.value.map { it.kind })
    }

    @Test
    fun `a waiting slip holds the day until it is gone`() = runTest {
        watch(queued = listOf(slip("q1")))
        assertTrue(book.days.value.isEmpty())

        pending.value = setOf("q1")
        settled()

        assertEquals(listOf(today), book.days.value.map { it.date })
    }

    @Test
    fun `a running scan holds the day until it ends`() = runTest {
        scanning.value = true
        watch()
        assertTrue(book.days.value.isEmpty())

        scanning.value = false
        settled()

        assertEquals(1, book.days.value.size)
    }

    @Test
    fun `a scan that starts inside the settle window keeps the day open`() = runTest {
        val ledger = FakeLedger()
        val watch = WatchTrayUseCase(
            queued = ObserveQueuedSlipsUseCase(ledger, pending),
            scanning = ObserveScanningUseCase(scanning),
            playOn = ObservePlayOnUseCase(ObserveAppChoicesUseCase(appSettings()), PlayGate(flagOn = true, debug = true)),
            transactions = ObserveTransactionsUseCase(ledger),
            book = book,
            clock = object : Clock {
                override fun now(): Instant = noon
            },
            settle = SETTLE,
        )
        backgroundScope.launch { watch() }
        runCurrent()

        scanning.value = true
        settled()

        assertTrue(book.days.value.isEmpty())
    }

    @Test
    fun `an Undo that brings a slip back keeps the day cleared`() = runTest {
        pending.value = setOf("q1")
        watch(queued = listOf(slip("q1")))

        pending.value = emptySet()
        settled()

        assertEquals(1, book.days.value.size)
        assertEquals(1, book.stamps.value.size)
    }

    @Test
    fun `with the run switched off nothing is written`() = runTest {
        watch(play = PlayChoices(run = false))

        assertTrue(book.days.value.isEmpty())
    }

    private fun slip(id: String) = QueuedSlip(
        id = id,
        merchant = "Cafe",
        occurredAtEpochMillis = 0L,
        note = "",
        amount = Satang(5_000),
        reason = ReviewReason.NewMerchant,
        capturedAtEpochMillis = 0L,
    )
}
