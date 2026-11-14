package com.ptut.expense.domain.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class QuickSplitTest {

    private val me = SplitMember("me", "Me", isMe = true)
    private val thaw = SplitMember("thaw", "Thaw", isMe = false)
    private val aye = SplitMember("aye", "Aye", isMe = false)
    private val min = SplitMember("min", "Min", isMe = false)

    @Test
    fun owedInFullGivesEveryoneButThePayerOneShare() {
        assertEquals(mapOf("thaw" to 1L), quickSplitWeights("me", listOf(me, thaw)))
        assertEquals(mapOf("me" to 1L, "aye" to 1L, "min" to 1L), quickSplitWeights("thaw", listOf(me, thaw, aye, min)))
    }

    @Test
    fun owedInFullSplitsTheWholeTotalBetweenTheOthers() {
        val members = listOf(me, thaw, aye, min)
        val check = split(50_000, SplitMode.Shares, members, quickSplitWeights("me", members))
        assertEquals(mapOf("aye" to 16_667L, "min" to 16_667L, "thaw" to 16_666L), check.parts)
        assertEquals(true, check.complete)
    }

    @Test
    fun readsBackTheChoiceThatWasSaved() {
        val members = listOf(me, thaw, aye)
        assertEquals(QuickSplit.Equally, quickSplitOf(SplitMode.Equally, "me", members, emptyMap()))
        assertEquals(QuickSplit.OwedInFull, quickSplitOf(SplitMode.Shares, "thaw", members, quickSplitWeights("thaw", members)))
    }

    @Test
    fun aSplitTypedByHandIsNotAQuickChoice() {
        val members = listOf(me, thaw, aye)
        assertNull(quickSplitOf(SplitMode.Shares, "me", members, mapOf("thaw" to 2L, "aye" to 1L)))
        assertNull(quickSplitOf(SplitMode.Shares, "me", members, mapOf("me" to 1L, "thaw" to 1L, "aye" to 1L)))
        assertNull(quickSplitOf(SplitMode.Shares, "me", members, mapOf("thaw" to 1L)))
        assertNull(quickSplitOf(SplitMode.Exact, "me", members, mapOf("thaw" to 500L)))
        assertNull(quickSplitOf(SplitMode.Percent, "me", members, mapOf("thaw" to WHOLE_PERCENT)))
    }

    @Test
    fun aGroupOfOneHasNobodyToOweTheFullAmount() {
        assertEquals(emptyMap(), quickSplitWeights("me", listOf(me)))
        assertNull(quickSplitOf(SplitMode.Shares, "me", listOf(me), emptyMap()))
    }
}
