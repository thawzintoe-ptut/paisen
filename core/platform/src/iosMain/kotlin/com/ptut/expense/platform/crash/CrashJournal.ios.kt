package com.ptut.expense.platform.crash

import com.ptut.expense.data.local.PtutDatabaseContext
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSThread
import platform.Foundation.NSUserDomainMask
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.ReportUnhandledExceptionHook
import kotlin.native.setUnhandledExceptionHook

private class FolderCrashFiles(private val folder: Path) : CrashFiles {
    private val disk = FileSystem.SYSTEM
    override fun list(): List<String> = if (disk.exists(folder)) disk.list(folder).map { it.name } else emptyList()
    override fun write(name: String, text: String) {
        disk.createDirectories(folder)
        disk.write(folder / name) { writeUtf8(text) }
    }
    override fun read(name: String): String = disk.read(folder / name) { readUtf8() }
    override fun delete(name: String) = disk.delete(folder / name, mustExist = false)
    override fun rename(from: String, to: String) = disk.atomicMove(folder / from, folder / to)
}

/** Application Support, not Documents: it never shows in the Files app. */
actual fun crashFiles(context: PtutDatabaseContext): CrashFiles {
    val support = NSSearchPathForDirectoriesInDomains(NSApplicationSupportDirectory, NSUserDomainMask, true).first() as String
    return FolderCrashFiles(support.toPath() / "crash")
}

@OptIn(ExperimentalNativeApi::class)
actual fun CrashJournal.install() {
    var previous: ReportUnhandledExceptionHook? = null
    previous = setUnhandledExceptionHook { error ->
        runCatching { record(if (NSThread.isMainThread) "main" else "background", error) }
        previous?.invoke(error)
    }
}
