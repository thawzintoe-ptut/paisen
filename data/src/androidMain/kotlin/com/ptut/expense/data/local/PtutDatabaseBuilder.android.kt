package com.ptut.expense.data.local

import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.Dispatchers
import okio.FileSystem
import kotlin.coroutines.CoroutineContext

internal actual val databaseQueryContext: CoroutineContext = Dispatchers.IO

/**
 * `applicationContext`, not the caller's: a database outlives any Activity that opens it, and
 * holding an Activity here would leak it for the life of the process.
 */
actual fun PtutDatabaseContext.ptutDatabaseBuilder(): RoomDatabase.Builder<PtutDatabase> =
    Room.databaseBuilder<PtutDatabase>(context = applicationContext, name = ptutDatabasePath())

actual fun PtutDatabaseContext.ptutDatabasePath(): String = applicationContext.getDatabasePath(PTUT_DATABASE_FILE).absolutePath

actual fun ledgerFiles(): LedgerFiles = OkioLedgerFiles(FileSystem.SYSTEM)
