package com.ptut.expense.play

import com.ptut.expense.ViewModelTest
import com.ptut.expense.domain.play.FakePlayBook
import com.ptut.expense.domain.play.ObserveStampUseCase
import com.ptut.expense.domain.play.RevealStampUseCase
import com.ptut.expense.domain.play.Stamp
import com.ptut.expense.domain.play.StampKind
import com.ptut.expense.domain.play.stampId
import com.ptut.expense.ui.play.StampTone
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

class StampRevealViewModelTest : ViewModelTest() {

    private val day = LocalDate(2026, 11, 25)
    private val owl = Stamp(stampId(StampKind.NightOwl, "2026-11-25"), StampKind.NightOwl, "2026-11-25", day)
    private var nowMillis = 1_000L
    private val clock = object : Clock {
        override fun now(): Instant = Instant.fromEpochMilliseconds(nowMillis)
    }

    private fun viewModel(book: FakePlayBook, id: String = owl.id) =
        StampRevealViewModel(id, ObserveStampUseCase(book), RevealStampUseCase(book, clock))

    private fun TestScope.latest(state: StateFlow<RevealUiState>): RevealUiState {
        backgroundScope.launch { state.collect {} }
        runCurrent()
        return state.value
    }

    @Test
    fun `a face-down card says to tap it and knows its stamp`() = runTest {
        val state = assertIs<RevealUiState.Loaded>(latest(viewModel(FakePlayBook(stamps = listOf(owl))).uiState))

        assertFalse(state.turned)
        assertEquals("Tap the card to turn it over", state.hint)
        assertEquals(StampTone.Sky, state.stamp.tone)
        assertEquals("Cleared at 22:00 or later", state.stamp.rareLine)
    }

    @Test
    fun `turning keeps the first time and shows the set`() = runTest {
        val book = FakePlayBook(stamps = listOf(owl))
        val reveal = viewModel(book)
        latest(reveal.uiState)

        reveal.turn()
        runCurrent()
        nowMillis = 2_000L
        reveal.turn()
        runCurrent()

        val state = assertIs<RevealUiState.Loaded>(reveal.uiState.value)
        assertTrue(state.turned)
        assertEquals(1_000L, book.stamps.value.single().revealedAtEpochMillis)
        assertEquals("Night and day: 1 of 3", state.set.line)
        assertEquals("2 more finish the set", state.set.note)
    }

    @Test
    fun `a stamp that is not there says so`() = runTest {
        assertIs<RevealUiState.Missing>(latest(viewModel(FakePlayBook(), id = "Monday:2020-01-06").uiState))
    }
}
