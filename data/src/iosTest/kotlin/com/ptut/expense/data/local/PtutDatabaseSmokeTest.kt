package com.ptut.expense.data.local

import androidx.room.Room
import com.ptut.expense.data.local.entity.ImportRunEntity
import com.ptut.expense.domain.Category
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Proves the schema is real rather than merely compilable: a database opens, all six tables are
 * created, and every DAO query executes against them. `commonTest` cannot do this — the Android
 * host test has no `Context` to build a database with — so it lives here.
 *
 * Nothing gates `iosSimulatorArm64Test` yet; issue #21 puts it in CI.
 */
class PtutDatabaseSmokeTest {

    @Test
    fun everyTableIsCreatedAndEveryQueryRuns() = runTest {
        val database = Room.inMemoryDatabaseBuilder<PtutDatabase>().openPtutDatabase()

        try {
            assertTrue(database.transactionDao().observeSince(0L).first().isEmpty())
            assertTrue(database.transactionDao().observeBetween(0L, Long.MAX_VALUE).first().isEmpty())
            // Seeded on open: transactions.categoryId is a foreign key, so an empty table
            // would fail every save with a constraint error.
            assertEquals(
                Category.seed().map { it.id }.sorted(),
                database.categoryDao().observeAll().first().map { it.id }.sorted(),
            )
            assertTrue(database.ruleDao().observeAll().first().isEmpty())
            assertTrue(database.queuedSlipDao().observeAll().first().isEmpty())
            assertFalse(database.importedSlipDao().isImported("no-such-slip"))
            assertNull(
                database.importRunDao()
                    .observeLastSuccessfulFinishedAt(ImportRunEntity.OUTCOME_SUCCESS)
                    .first(),
            )
        } finally {
            database.close()
        }
    }
}
