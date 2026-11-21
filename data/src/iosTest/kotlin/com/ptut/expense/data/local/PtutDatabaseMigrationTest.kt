package com.ptut.expense.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import okio.FileSystem
import okio.Path.Companion.toPath
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class PtutDatabaseMigrationTest {

    private val fileName = (NSTemporaryDirectory().toPath() / "migration-${NSUUID().UUIDString}.db").toString()

    private val helper = MigrationTestHelper(
        schemaDirectoryPath = SCHEMA_DIRECTORY,
        fileName = fileName,
        driver = BundledSQLiteDriver(),
        databaseClass = PtutDatabase::class,
        databaseFactory = { PtutDatabaseConstructor.initialize() },
        autoMigrationSpecs = listOf(IncomeKeepsItsDirection()),
    )

    @AfterTest
    fun deleteDatabase() {
        helper.finished()
        listOf("", "-wal", "-shm", "-journal").forEach { suffix ->
            FileSystem.SYSTEM.delete("$fileName$suffix".toPath(), mustExist = false)
        }
    }

    @Test
    fun theIncomeCategoryKeepsItsDirectionAfterSchema7() {
        helper.createDatabase(6).use { connection ->
            connection.execSQL("INSERT INTO category (id, name, icon) VALUES ('income', 'Income', 'payments')")
            connection.execSQL("INSERT INTO category (id, name, icon) VALUES ('food', 'Food', 'restaurant')")
        }

        helper.runMigrationsAndValidate(7).use { connection ->
            assertEquals(
                mapOf("food" to "Expense", "income" to "Income"),
                connection.directions(),
            )
        }
    }

    @Test
    fun rowsSavedOnSchema1SurviveEveryMigration() {
        helper.createDatabase(1).use { connection ->
            connection.execSQL("INSERT INTO category (id, name, icon) VALUES ('food', 'Food', 'restaurant')")
            connection.execSQL(
                "INSERT INTO transactions " +
                    "(id, occurredAtEpochMillis, amountMinor, direction, categoryId, counterparty, source, reference) " +
                    "VALUES ('t1', $SEEDED_AT, 12345, 'Expense', 'food', 'Cafe', 'Manual', NULL)",
            )
            connection.execSQL(
                "INSERT INTO rule (id, counterparty, categoryId, createdAtEpochMillis) " +
                    "VALUES ('r1', 'Cafe', 'food', $SEEDED_AT)",
            )
            connection.execSQL("INSERT INTO imported_slip (hash, importedAtEpochMillis) VALUES ('h1', $SEEDED_AT)")
        }

        helper.runMigrationsAndValidate(LATEST_SCHEMA).use { connection ->
            assertEquals(
                listOf("t1|12345|Expense|food|Cafe"),
                connection.rows(
                    "SELECT id, amountMinor, direction, categoryId, counterparty FROM transactions",
                ),
            )
            assertEquals(listOf("r1|Cafe|food"), connection.rows("SELECT id, counterparty, categoryId FROM rule"))
            assertEquals(listOf("h1"), connection.rows("SELECT hash FROM imported_slip"))
            assertEquals(mapOf("food" to "Expense"), connection.directions())
        }
    }

    @Test
    fun aFailedRunFromSchema22KeepsItsTextAndHasNoKind() {
        helper.createDatabase(22).use { connection ->
            connection.execSQL(
                "INSERT INTO import_run (id, startedAtEpochMillis, finishedAtEpochMillis, outcome, slipsFound, failureReason) " +
                    "VALUES ('run1', $SEEDED_AT, ${SEEDED_AT + 1_000}, 'Failure', 0, 'Photo access is off')",
            )
        }

        helper.runMigrationsAndValidate(23).use { connection ->
            assertEquals(
                listOf("run1|Failure|Photo access is off|1"),
                connection.rows("SELECT id, outcome, failureReason, failureKind IS NULL FROM import_run"),
            )
        }
    }

    @Test
    fun groupsFromSchema23RunOnWithEveryoneInPlayAndTheTablesAreEmpty() {
        helper.createDatabase(23).use { connection ->
            connection.execSQL(
                "INSERT INTO shared_group (id, name, type, icon, simplifyDebts, createdAtEpochMillis, meMemberId) " +
                    "VALUES ('g1', 'Chiang Mai', 'Trip', 'place', 1, $SEEDED_AT, 'm1')",
            )
            connection.execSQL(
                "INSERT INTO group_member (id, groupId, name, personId, archived, createdAtEpochMillis) " +
                    "VALUES ('m1', 'g1', 'You', NULL, 0, $SEEDED_AT)",
            )
        }

        helper.runMigrationsAndValidate(24).use { connection ->
            assertEquals(listOf("g1|1"), connection.rows("SELECT id, endedOn IS NULL FROM shared_group"))
            assertEquals(listOf("m1|0"), connection.rows("SELECT id, playOff FROM group_member"))
            listOf("play_day", "play_week", "play_month", "play_stamp", "play_goal", "payment_reaction").forEach { table ->
                assertEquals(listOf("0"), connection.rows("SELECT COUNT(*) FROM $table"), table)
            }
        }
    }

    private fun SQLiteConnection.directions(): Map<String, String> = rows("SELECT id, direction FROM category ORDER BY id")
        .associate { row -> row.substringBefore('|') to row.substringAfter('|') }

    private fun SQLiteConnection.rows(sql: String): List<String> {
        val statement = prepare(sql)
        try {
            val rows = mutableListOf<String>()
            while (statement.step()) {
                rows += (0 until statement.getColumnCount()).joinToString("|") { statement.getText(it) }
            }
            return rows
        } finally {
            statement.close()
        }
    }

    private inline fun <T> SQLiteConnection.use(block: (SQLiteConnection) -> T): T = try {
        block(this)
    } finally {
        close()
    }

    private companion object {
        const val LATEST_SCHEMA = 24
        const val SEEDED_AT = 1_700_000_000_000L
    }
}
