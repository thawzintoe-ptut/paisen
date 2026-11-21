package com.ptut.expense.domain.settings

import app.cash.turbine.test
import com.ptut.expense.domain.EvidenceRetention
import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.WeeklyTarget
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.days

class AppSettingsTest {

    @Test
    fun `a fresh install imports in the background and keeps a failed slip for seven days`() {
        val settings = AppSettings(FakeAppSettingsStore())

        assertEquals(AppChoices(backgroundImport = true, retention = EvidenceRetention), settings.choices.value)
    }

    @Test
    fun `what the reader chose is what the next launch reads back`() {
        val store = FakeAppSettingsStore()
        AppSettings(store).setRetention(30.days)

        assertEquals(30.days, AppSettings(store).choices.value.retention)
    }

    @Test
    fun `changing one choice leaves the other where it was`() {
        val settings = AppSettings(FakeAppSettingsStore())

        settings.setBackgroundImport(false)
        settings.setRetention(3.days)

        assertEquals(AppChoices(backgroundImport = false, retention = 3.days), settings.choices.value)
    }

    /** A screen re-emitting on a tap that changed nothing would redraw the whole list for it. */
    @Test
    fun `choosing what is already chosen writes nothing and emits nothing`() = runTest {
        val store = FakeAppSettingsStore()
        val settings = AppSettings(store)

        settings.choices.test {
            assertEquals(AppChoices(), awaitItem())
            settings.setRetention(EvidenceRetention)
            expectNoEvents()
        }
        assertEquals(0, store.writes)
    }

    @Test
    fun `the store is written before the change is announced`() {
        val store = FakeAppSettingsStore()
        AppSettings(store).setBackgroundImport(false)

        assertEquals(1, store.writes)
        assertEquals(false, store.read().backgroundImport)
    }

    /** NFR-1 bounds the list: a slip picture carries an account number, so it always has an end. */
    @Test
    fun `every retention on offer has an end`() {
        assertEquals(listOf(3L, 7L, 14L, 30L), RetentionChoices.map { it.inWholeDays })
    }

    @Test
    fun `a new home clears the weekly target typed in the old money and the same home keeps it`() {
        val target = WeeklyTarget("food", Satang(200_000))
        val settings = AppSettings(FakeAppSettingsStore(AppChoices(weeklyTarget = target, homeCurrency = "THB")))

        settings.setHome("THB", "Asia/Bangkok")
        assertEquals(target, settings.choices.value.weeklyTarget)

        settings.setHome("MMK", "Asia/Yangon")
        assertEquals(null, settings.choices.value.weeklyTarget)
    }

    @Test
    fun `a converted target travels with the new home in one write`() {
        val store = FakeAppSettingsStore(AppChoices(homeCurrency = "THB", weeklyTarget = WeeklyTarget("food", Satang(200_000))))
        val settings = AppSettings(store)

        settings.setHome("MMK", "Asia/Yangon", converted = WeeklyTarget("food", Satang(250_000)))

        assertEquals(Satang(250_000), settings.choices.value.weeklyTarget?.amount)
        assertEquals("MMK", store.read().homeCurrency)
        assertEquals(Satang(250_000), store.read().weeklyTarget?.amount)
    }

    @Test
    fun `play ships on with amounts and the reminder off`() {
        val play = AppSettings(FakeAppSettingsStore()).choices.value.play

        assertEquals(PlayChoices(on = true, run = true, guess = true, challenges = true, groupStamps = true, shareAmounts = false, weeklyReminder = false), play)
    }

    @Test
    fun `the play switches are what the next launch reads back`() {
        val store = FakeAppSettingsStore()
        AppSettings(store).setPlay(PlayChoices(on = false, guess = false, weeklyReminder = true))

        assertEquals(PlayChoices(on = false, guess = false, weeklyReminder = true), AppSettings(store).choices.value.play)
    }

    @Test
    fun `a phone that finished first run before keeps play on`() {
        assertEquals(true, playOnWhenUnset(firstRunFinished = true))
    }

    @Test
    fun `a fresh install starts with play off`() {
        assertEquals(false, playOnWhenUnset(firstRunFinished = false))
    }
}
