package com.ptut.expense.data.repository

import com.ptut.expense.data.local.entity.RepeatRuleEntity
import com.ptut.expense.data.local.entity.TransactionEntity
import com.ptut.expense.data.local.entity.foreignAmount
import com.ptut.expense.domain.shared.RepeatEnd
import com.ptut.expense.domain.shared.sumsTo
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A row already stored is read at every start, so one this build cannot read is left out rather
 * than thrown on: an import or a sync from before the checks may have put it there.
 */
class StoredRowReadTest {

    @Test
    fun `a repeat as the app saves it reads whole`() {
        val rule = repeat.toDomain()!!

        assertEquals(LocalDate(2026, 1, 31), rule.start)
        assertEquals(RepeatEnd.OnDate(LocalDate(2026, 12, 31)), rule.end)
        assertEquals(mapOf("me" to 1L, "aye" to 2L), rule.weights)
    }

    @Test
    fun `a repeat with a kind or frequency or split no build knows reads as none`() {
        assertNull(repeat.copy(kind = "X").toDomain())
        assertNull(repeat.copy(frequency = "Hourly").toDomain())
        assertNull(repeat.copy(splitMode = "Thirds").toDomain())
    }

    @Test
    fun `a repeat with a date that is no date reads as none`() {
        assertNull(repeat.copy(startDate = "2026-02-31").toDomain())
        assertNull(repeat.copy(endDate = "soon").toDomain())
        assertNull(repeat.copy(lastAdded = "yesterday").toDomain())
    }

    @Test
    fun `a repeat with a rate of zero or half a rate reads as none`() {
        assertNull(repeat.copy(rateUnscaled = 0, rateScale = 2).toDomain())
        assertNull(repeat.copy(rateUnscaled = 5, rateScale = null).toDomain())
    }

    @Test
    fun `a transaction with a rate of zero reads as home money`() {
        val row = TransactionEntity(
            id = "t1", occurredAtEpochMillis = 1, amountMinor = 100, direction = "Expense", categoryId = null, counterparty = null,
            source = "Manual", reference = null, editedAt = null, currency = "MMK", originalMinor = 29_500, rateUnscaled = 0, rateScale = 4,
        )

        assertNull(row.foreignAmount())
        assertEquals(29_500L, row.copy(rateUnscaled = 82).foreignAmount()?.originalMinor)
    }

    @Test
    fun `parts add up only when none is negative and no step wraps`() {
        assertTrue(listOf(4_501L, 4_500L).sumsTo(9_001))
        assertFalse(listOf(4_501L, 4_500L).sumsTo(9_000))
        assertFalse(listOf(9_002L, -1L).sumsTo(9_001))
        assertFalse(List(4) { 1L shl 62 }.sumsTo(0))
        assertFalse(emptyList<Long>().sumsTo(1))
    }

    private val repeat = RepeatRuleEntity(
        id = "r1", groupId = "g1", kind = "Rent", frequency = "Monthly", startDate = "2026-01-31", endKind = END_ON_DATE, endDate = "2026-12-31",
        endTimes = null, remind = true, active = true, lastAdded = "2026-10-31", description = "Rent", categoryId = null, currency = "THB",
        amountMinor = 1_200_000, rateUnscaled = null, rateScale = null, payerMemberId = "me", splitMode = "Shares", weights = "me=1;aye=2",
        createdAtEpochMillis = 1,
    )
}
