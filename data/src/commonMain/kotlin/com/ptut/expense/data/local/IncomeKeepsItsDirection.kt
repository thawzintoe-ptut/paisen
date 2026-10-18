package com.ptut.expense.data.local

import androidx.room.migration.AutoMigrationSpec
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * Schema 7 gives `category` a direction that defaults to Expense, which is right for every row
 * that existed before it — except the one the seed installed as income.
 *
 * The seed cannot repair it. It inserts or ignores, so on an upgrade the row is already there and
 * the fix is stepped over; an install that had been running for a month would quietly start
 * offering Income as a way to spend. This runs once, as part of the migration, and never again —
 * so a reader who later points that category the other way keeps their answer.
 */
class IncomeKeepsItsDirection : AutoMigrationSpec {

    override fun onPostMigrate(connection: SQLiteConnection) {
        connection.execSQL("UPDATE category SET direction = 'Income' WHERE id = 'income'")
    }
}
