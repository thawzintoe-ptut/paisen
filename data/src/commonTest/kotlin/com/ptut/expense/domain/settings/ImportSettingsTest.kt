package com.ptut.expense.domain.settings

import app.cash.turbine.test
import com.ptut.expense.domain.EvidenceRetention
import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.WeeklyTarget
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.days

class ImportSettingsTest {

    @Test
    fun `a fresh install imports in the background and keeps a failed slip for seven days`() {
        val settings = ImportSettings(FakeImportSettingsStore())

        assertEquals(ImportChoices(backgroundImport = true, retention = EvidenceRetention), settings.choices.value)
    }

    @Test
    fun `what the reader chose is what the next launch reads back`() {
        val store = FakeImportSettingsStore()
        ImportSettings(store).setRetention(30.days)

        assertEquals(30.days, ImportSettings(store).choices.value.retention)
    }

    @Test
    fun `changing one choice leaves the other where it was`() {
        val settings = ImportSettings(FakeImportSettingsStore())

        settings.setBackgroundImport(false)
        settings.setRetention(3.days)

        assertEquals(ImportChoices(backgroundImport = false, retention = 3.days), settings.choices.value)
    }

    /** A screen re-emitting on a tap that changed nothing would redraw the whole list for it. */
    @Test
    fun `choosing what is already chosen writes nothing and emits nothing`() = runTest {
        val store = FakeImportSettingsStore()
        val settings = ImportSettings(store)

        settings.choices.test {
            assertEquals(ImportChoices(), awaitItem())
            settings.setRetention(EvidenceRetention)
            expectNoEvents()
        }
        assertEquals(0, store.writes)
    }

    @Test
    fun `the store is written before the change is announced`() {
        val store = FakeImportSettingsStore()
        ImportSettings(store).setBackgroundImport(false)

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
        val settings = ImportSettings(FakeImportSettingsStore(ImportChoices(weeklyTarget = target, homeCurrency = "THB")))

        settings.setHome("THB", "Asia/Bangkok")
        assertEquals(target, settings.choices.value.weeklyTarget)

        settings.setHome("MMK", "Asia/Yangon")
        assertEquals(null, settings.choices.value.weeklyTarget)
    }
}
