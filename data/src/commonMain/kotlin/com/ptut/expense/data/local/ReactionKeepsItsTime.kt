package com.ptut.expense.data.local

import androidx.room.migration.AutoMigrationSpec
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * Schema 25 adds the nullable play_day.pickedFirst. Schema 24 reached phones in two shapes: the first
 * play build named the reaction's time column syncedAtEpochMillis, a later one createdAtEpochMillis,
 * both under version 24. A phone on the first shape has the column renamed here, once.
 */
class ReactionKeepsItsTime : AutoMigrationSpec {

    override fun onPostMigrate(connection: SQLiteConnection) {
        val oldName = connection.prepare("SELECT 1 FROM pragma_table_info('payment_reaction') WHERE name = 'syncedAtEpochMillis'").use { it.step() }
        if (oldName) connection.execSQL("ALTER TABLE payment_reaction RENAME COLUMN syncedAtEpochMillis TO createdAtEpochMillis")
    }
}
