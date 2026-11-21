package com.ptut.expense.platform.crash

import com.ptut.expense.data.local.PtutDatabaseContext
import java.io.File

private class FolderCrashFiles(private val folder: File) : CrashFiles {
    override fun list(): List<String> = folder.list()?.toList().orEmpty()
    override fun write(name: String, text: String) {
        folder.mkdirs()
        File(folder, name).writeText(text)
    }
    override fun read(name: String): String = File(folder, name).readText()
    override fun delete(name: String) {
        File(folder, name).delete()
    }
    override fun rename(from: String, to: String) {
        File(folder, from).renameTo(File(folder, to))
    }
}

actual fun crashFiles(context: PtutDatabaseContext): CrashFiles = FolderCrashFiles(File(context.filesDir, "crash"))

actual fun CrashJournal.install() {
    val previous = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { thread, error ->
        runCatching { record(thread.name, error) }
        previous?.uncaughtException(thread, error)
    }
}
