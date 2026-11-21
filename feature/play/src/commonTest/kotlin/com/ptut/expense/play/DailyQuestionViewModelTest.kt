package com.ptut.expense.play

import com.ptut.expense.ViewModelTest
import com.ptut.expense.domain.BangkokTime
import com.ptut.expense.domain.Category
import com.ptut.expense.domain.Direction
import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.Transaction
import com.ptut.expense.domain.ledger.FakeLedger
import com.ptut.expense.domain.ledger.ObserveTransactionsUseCase
import com.ptut.expense.domain.play.AnswerDailyQuestionUseCase
import com.ptut.expense.domain.play.FakePlayBook
import com.ptut.expense.domain.play.GetDailyQuestionUseCase
import com.ptut.expense.domain.today.GetHomeTodayUseCase
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

class DailyQuestionViewModelTest : ViewModelTest() {

    private val now = LocalDateTime(2026, 11, 25, 12, 0).toInstant(BangkokTime)
    private val clock = object : Clock {
        override fun now(): Instant = now
    }
    private val book = FakePlayBook()

    private val week = listOf(row("1", "food", 120_000, 20), row("2", "transport", 110_000, 21))

    private fun TestScope.question(rows: List<Transaction> = week): DailyQuestionViewModel {
        val today = GetHomeTodayUseCase(clock)
        val ledger = ObserveTransactionsUseCase(FakeLedger(rows))
        val question = DailyQuestionViewModel(
            getDailyQuestion = GetDailyQuestionUseCase(book, ledger, today),
            answer = AnswerDailyQuestionUseCase(book, clock),
            categories = flowOf(Category.seed()),
        )
        backgroundScope.launch { question.uiState.collect {} }
        runCurrent()
        return question
    }

    @Test
    fun `the two cards ask before they show any amount`() = runTest {
        val state = assertIs<QuestionUiState.Asking>(question().uiState.value)

        assertEquals("Food & drink", state.a.name)
        assertEquals("Transport", state.b.name)
        assertNull(state.a.amount)
    }

    @Test
    fun `a right pick shows both amounts the sky card and the freeze progress`() = runTest {
        val question = question()

        question.pick(first = true)
        runCurrent()

        val state = assertIs<QuestionUiState.Answered>(question.uiState.value)
        assertEquals("You got it", state.verdict)
        assertTrue(state.a.right && state.a.picked)
        assertEquals("฿1,200.00", state.a.amount)
        assertEquals("1 of 3 answers toward a freeze", state.freezeLine)
        assertEquals(true, book.days.value.single().answeredRight)
    }

    @Test
    fun `the first pick of the day stands`() = runTest {
        val question = question()

        question.pick(first = false)
        runCurrent()
        question.pick(first = true)
        runCurrent()

        assertEquals("Not this time", assertIs<QuestionUiState.Answered>(question.uiState.value).verdict)
        assertEquals(false, book.days.value.single().answeredRight)
    }

    @Test
    fun `a week with no fair pair asks nothing`() = runTest {
        val state = assertIs<QuestionUiState.None>(question(rows = listOf(row("1", "food", 100_000, 20))).uiState.value)

        assertEquals("No fair pair in the last 7 days. Come back tomorrow.", state.line)
    }

    private fun row(id: String, category: String, minor: Long, day: Int) = Transaction(
        id = id,
        occurredAtEpochMillis = LocalDateTime(2026, 11, day, 12, 0).toInstant(BangkokTime).toEpochMilliseconds(),
        amount = Satang(minor),
        direction = Direction.Expense,
        categoryId = category,
        counterparty = null,
    )
}
