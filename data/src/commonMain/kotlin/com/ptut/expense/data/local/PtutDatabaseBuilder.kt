package com.ptut.expense.data.local

import androidx.room.RoomDatabase
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.ptut.expense.domain.Category
import kotlin.coroutines.CoroutineContext

internal const val PTUT_DATABASE_FILE = "ptut-expense.db"

/**
 * Where queries run. `Dispatchers.IO` is not visible from common code, and the bundled driver
 * blocks the thread it is called on, so each platform names its own rather than borrowing the
 * CPU-bound default.
 */
internal expect val databaseQueryContext: CoroutineContext

expect fun PtutDatabaseContext.ptutDatabaseBuilder(): RoomDatabase.Builder<PtutDatabase>

/**
 * Bundled SQLite rather than the platform's own, so Android, iOS and a test host all run the
 * same engine — a query that works on one cannot quietly fail on another.
 */
fun RoomDatabase.Builder<PtutDatabase>.openPtutDatabase(): PtutDatabase =
    setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(databaseQueryContext)
        .addCallback(SeedCategories)
        .build()

private const val INSERT_CATEGORY =
    "INSERT OR IGNORE INTO category (id, name, icon, direction, color, sortOrder) " +
        "SELECT ?, ?, ?, ?, ?, ? WHERE NOT EXISTS (SELECT 1 FROM deleted_built_in WHERE id = ?)"

/**
 * The six categories, installed with the schema. `transactions.categoryId` is a foreign key, so
 * until this table holds them every save fails on a constraint — which reads to the person saving
 * as the app losing their money rather than as a missing row.
 *
 * `onOpen` and `INSERT OR IGNORE` rather than `onCreate`: schema version 1 already exists on disk
 * with an empty table, and an idempotent seed repairs those installs without a migration. Ignoring
 * on conflict also means a category a person has renamed is never quietly reset to the seed name.
 * One they deleted is listed in `deleted_built_in` and skipped, so it stays deleted (#219).
 */
private object SeedCategories : RoomDatabase.Callback() {
    override fun onOpen(connection: SQLiteConnection) {
        val statement = connection.prepare(INSERT_CATEGORY)
        try {
            Category.seed().forEach { category ->
                statement.bindText(1, category.id)
                statement.bindText(2, category.name)
                statement.bindText(3, category.icon)
                statement.bindText(4, category.direction.name)
                statement.bindText(5, category.color)
                statement.bindInt(6, category.sortOrder)
                statement.bindText(7, category.id)
                statement.step()
                statement.reset()
            }
        } finally {
            statement.close()
        }
    }
}
