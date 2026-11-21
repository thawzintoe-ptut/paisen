package com.ptut.expense.domain.shared

import com.ptut.expense.domain.BangkokTime
import com.ptut.expense.domain.HomeMoney
import com.ptut.expense.domain.Satang
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GroupPlayTest {

    private val today = LocalDate(2026, 11, 25)
    private fun day(d: Int) = LocalDate(2026, 11, d)

    @Test
    fun `a fair wheel gives whoever paid fewer rounds the bigger slice`() {
        val snapshot = playSnapshot(
            playGroup("me", "aye", "bo"),
            listOf(playExpense("e1", "me", 30_000, day(20), "me", "aye", "bo"), playExpense("e2", "me", 30_000, day(21), "me", "aye", "bo"), playExpense("e3", "aye", 30_000, day(22), "me", "aye", "bo")),
        )

        val slices = snapshot.wheelSlices("g1", today, fair = true, zone = BangkokTime)

        assertEquals(listOf(WheelSlice("aye", 2, 1), WheelSlice("bo", 3, 0), WheelSlice("me", 1, 2)), slices)
        assertEquals(listOf(1, 1, 1), snapshot.wheelSlices("g1", today, fair = false, zone = BangkokTime).map { it.weight })
    }

    @Test
    fun `the same group in another order draws the same wheel`() {
        val one = playSnapshot(playGroup("me", "aye", "bo")).wheelSlices("g1", today, fair = true, zone = BangkokTime)
        val other = playSnapshot(playGroup("bo", "me", "aye")).wheelSlices("g1", today, fair = true, zone = BangkokTime)

        assertEquals(one, other)
    }

    @Test
    fun `the wheel lands on the slice under the pointer`() {
        val slices = listOf(WheelSlice("a", 1, 0), WheelSlice("b", 3, 0))

        assertEquals("a", slices.landing(0.2f))
        assertEquals("b", slices.landing(0.25f))
        assertEquals("b", slices.landing(1f))
        assertNull(emptyList<WheelSlice>().landing(0.5f))
    }

    @Test
    fun `a usual round is the median expense of the last 60 days`() {
        val snapshot = playSnapshot(
            playGroup("me", "aye"),
            listOf(playExpense("e1", "me", 10_000, day(20), "me"), playExpense("e2", "me", 30_000, day(21), "me"), playExpense("e3", "me", 90_000, day(22), "me")),
        )

        assertEquals(Satang(30_000), snapshot.usualRound("g1", today, BangkokTime))
        assertNull(playSnapshot(playGroup("me")).usualRound("g1", today, BangkokTime))
    }

    @Test
    fun `all square names the payment that settled everyone`() {
        val dinner = playExpense("e1", "me", 60_000, day(20), "me", "aye")
        val owing = playSnapshot(playGroup("me", "aye"), listOf(dinner))
        val settled = playSnapshot(playGroup("me", "aye"), listOf(dinner), listOf(playPayment("p1", "aye", "me", 30_000, day(21))))

        assertNull(owing.allSquareAt("g1"))
        assertEquals("p1", settled.allSquareAt("g1"))
        assertNull(playSnapshot(playGroup("me", "aye")).allSquareAt("g1"))
    }

    @Test
    fun `awards praise need two members and never name one who opted out`() {
        val expenses = listOf(
            playExpense("e1", "me", 60_000, day(2), "me", "aye", "bo"),
            playExpense("e2", "aye", 60_000, day(3), "me", "aye", "bo"),
        )
        val payments = listOf(
            playPayment("p1", "aye", "me", 20_000, day(3)),
            playPayment("p2", "bo", "me", 20_000, day(9)),
            playPayment("p3", "me", "aye", 20_000, day(4)),
        )

        val awards = playSnapshot(playGroup("me", "aye", "bo"), expenses, payments).groupAwards("g1", "2026-11", BangkokTime)

        assertEquals(Award(AwardKind.FastestToSettle, "aye", 1), awards.single { it.kind == AwardKind.FastestToSettle })
        assertEquals(Award(AwardKind.KeptTheBooks, "aye", 2), awards.single { it.kind == AwardKind.KeptTheBooks })
        val optedOut = playSnapshot(playGroup("me", "aye", "bo", playOff = setOf("aye")), expenses, payments).groupAwards("g1", "2026-11", BangkokTime)
        assertEquals(emptyList(), optedOut.filter { it.memberId == "aye" })
    }

    @Test
    fun `one qualifying member gets no award`() {
        val awards = playSnapshot(
            playGroup("me", "aye"),
            listOf(playExpense("e1", "me", 60_000, day(2), "me", "aye")),
            listOf(playPayment("p1", "aye", "me", 30_000, day(3))),
        ).groupAwards("g1", "2026-11", BangkokTime)

        assertNull(awards.firstOrNull { it.kind == AwardKind.FastestToSettle })
    }

    @Test
    fun `a trip recap counts every day to the end and splits the total per person per day`() {
        val group = playGroup("me", "aye", endedOn = day(24))
        val recap = playSnapshot(
            group,
            listOf(playExpense("e1", "me", 40_000, day(20), "me", "aye"), playExpense("e2", "aye", 40_000, day(22), "me", "aye")),
        ).tripRecap("g1", BangkokTime)

        assertEquals(Satang(80_000), recap?.total)
        assertEquals(5, recap?.days)
        assertEquals(Satang(8_000), recap?.perPersonPerDay)
        assertEquals(listOf(Satang(40_000), Satang.Zero, Satang(40_000), Satang.Zero, Satang.Zero), recap?.byDay?.map { it.second })
        assertNull(playSnapshot(group).tripRecap("g1", BangkokTime))
    }

    @Test
    fun `the next trip rounds the share up to a hundred and spreads it over the weeks`() {
        val next = TripRecap(Satang(80_050), days = 5, people = 2, perPersonPerDay = Satang(8_005), byDay = emptyList()).nextTrip(HomeMoney.Baht, weeks = 12)

        assertEquals(Satang(50_000), next.estimate)
        assertEquals(Satang(4_200), next.perWeek)
    }

    @Test
    fun `a yen trip rounds its estimate in yen and not in the home money`() {
        val yen = HomeMoney.Baht.copy(code = "JPY", digits = 0)

        val next = TripRecap(Satang(1_201), days = 1, people = 1, perPersonPerDay = Satang(1_201), byDay = emptyList()).nextTrip(yen, weeks = 12)

        assertEquals(Satang(1_300), next.estimate)
        assertEquals(Satang(109), next.perWeek)
    }

    @Test
    fun `a name removed after the trip still counts as one of the people who shared it`() {
        // "lin" shared the trip and was removed after; the group now lists two names.
        val recap = playSnapshot(
            playGroup("me", "aye", endedOn = day(20)),
            listOf(playExpense("e1", "me", 30_000, day(20), "me", "aye", "lin")),
        ).tripRecap("g1", BangkokTime)

        assertEquals(3, recap?.people)
        assertEquals(Satang(10_000), recap?.perPersonPerDay)
    }
}
