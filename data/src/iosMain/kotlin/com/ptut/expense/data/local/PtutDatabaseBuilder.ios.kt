package com.ptut.expense.data.local

import androidx.room.Room
import androidx.room.RoomDatabase
import com.ptut.expense.platform.files.documentsDirectory
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.newSingleThreadContext
import kotlin.coroutines.CoroutineContext

/**
 * Native has no `Dispatchers.IO`. Its own thread rather than `Dispatchers.Default`, because the
 * bundled driver blocks and the default pool is sized for CPU work. The thread lives as long as
 * the database does, which is the whole process.
 */
@OptIn(DelicateCoroutinesApi::class)
internal actual val databaseQueryContext: CoroutineContext = newSingleThreadContext("ptut-db")

actual fun PtutDatabaseContext.ptutDatabaseBuilder(): RoomDatabase.Builder<PtutDatabase> =
    Room.databaseBuilder<PtutDatabase>(name = "${documentsPath()}/$PTUT_DATABASE_FILE")

/** Room takes a path and not a URL, and a URL with no path is a directory that does not exist. */
private fun documentsPath(): String =
    requireNotNull(documentsDirectory().path) { "iOS documents directory has no path" }
