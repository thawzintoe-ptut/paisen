package com.ptut.expense.data.repository

import com.ptut.expense.data.local.entity.PlayStampEntity
import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.play.Challenge
import com.ptut.expense.domain.play.ChallengeLevel
import com.ptut.expense.domain.play.StampKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RoomPlayBookTest {

    private val monday = LocalDate(2026, 11, 23)
    private val tables = PlayTables()
    private val book = RoomPlayBook(tables)

    @Test
    fun `clearing a day twice keeps one day and one stamp`() = runTest {
        assertTrue(book.markCleared(monday, atEpochMillis = 10, stamp = StampKind.Monday))
        assertFalse(book.markCleared(monday, atEpochMillis = 20, stamp = StampKind.NightOwl))

        assertEquals(10L, book.days.first().single().clearedAtEpochMillis)
        assertEquals(listOf(StampKind.Monday), book.stamps.first().map { it.kind })
    }

    @Test
    fun `a turned card keeps the first time it was turned`() = runTest {
        book.markCleared(monday, atEpochMillis = 10, stamp = StampKind.Monday)
        val id = book.stamps.first().single().id

        book.reveal(id, atEpochMillis = 30)
        book.reveal(id, atEpochMillis = 40)

        assertEquals(30L, book.stamps.first().single().revealedAtEpochMillis)
    }

    @Test
    fun `the first answer of a day stands`() = runTest {
        book.answer(monday, "cat:food|cat:transport", right = true, pickedFirst = true, atEpochMillis = 10)
        book.answer(monday, "cat:food|cat:home", right = false, pickedFirst = false, atEpochMillis = 20)

        val day = book.days.first().single()
        assertEquals("cat:food|cat:transport", day.questionKey)
        assertEquals(true, day.answeredRight)
        assertNull(day.clearedAtEpochMillis)
    }

    @Test
    fun `PaiSeN's guess is fixed once and the reveal earns only when the reader was closer`() = runTest {
        assertEquals(Satang(120_000), book.fixPaisenGuess(monday, Satang(120_000)))
        assertEquals(Satang(120_000), book.fixPaisenGuess(monday, Satang(90_000)))

        book.revealGuess(monday, guess = Satang(100_000), total = Satang(101_000), youCloser = true, on = monday)
        book.revealGuess(monday, guess = Satang(50_000), total = Satang(1), youCloser = true, on = monday)

        val week = book.weeks.first().single()
        assertEquals(Satang(100_000), week.guess)
        assertEquals(Satang(101_000), week.total)
        assertEquals(listOf(StampKind.BeatPaiSeN), book.stamps.first().map { it.kind })
    }

    @Test
    fun `a second challenge the same week replaces the first`() = runTest {
        book.pickChallenge(monday, Challenge("food", ChallengeLevel.Easy, Satang(150_000)))
        book.pickChallenge(monday, Challenge("food", ChallengeLevel.Hard, Satang(90_000)))

        assertEquals(Challenge("food", ChallengeLevel.Hard, Satang(90_000)), book.weeks.first().single().challenge)
    }

    @Test
    fun `sealing a month twice earns one stamp`() = runTest {
        book.seal("2026-10", atEpochMillis = 10, on = monday)
        book.seal("2026-10", atEpochMillis = 20, on = monday)

        assertEquals(10L, book.months.first().single().sealedAtEpochMillis)
        assertEquals(listOf("MonthClosed:2026-10"), book.stamps.first().map { it.id })
    }

    @Test
    fun `earning the same thing twice is refused`() = runTest {
        assertTrue(book.earn(StampKind.AllSquare, "g1:p1", monday))
        assertFalse(book.earn(StampKind.AllSquare, "g1:p1", monday))
    }

    @Test
    fun `a stamp kind from a newer build is skipped`() = runTest {
        tables.earn(PlayStampEntity(id = "Comet:x", kind = "Comet", ref = "x", earnedOn = "2026-11-23"))

        assertTrue(book.stamps.first().isEmpty())
    }
}
