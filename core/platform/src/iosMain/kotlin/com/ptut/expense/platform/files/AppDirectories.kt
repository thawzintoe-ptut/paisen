@file:OptIn(ExperimentalForeignApi::class)

package com.ptut.expense.platform.files

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSUserDomainMask

/**
 * Where this app keeps what it must not lose.
 *
 * Documents and not Caches, for the ledger and for a slip waiting to be read alike: the system may
 * empty Caches whenever it is short of space, and both of those are the user's own record.
 *
 * One function rather than one per caller, because "which directory is app data in" is a decision
 * and a second copy of it is a second answer waiting to drift. It also keeps the folder out of
 * device backups, which is why every caller must come through here.
 */
fun documentsDirectory(): NSURL {
    val documents = NSFileManager.defaultManager.URLForDirectory(
        directory = NSDocumentDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = false,
        error = null,
    )
    return requireNotNull(documents) { "iOS documents directory is unavailable" }.also {
        // "Your spending stays on this phone": the ledger, its -wal and -shm files and the slip inbox stay
        // out of iCloud and Finder backups. Set on the folder, so files SQLite creates later are covered too.
        it.setResourceValue(true, forKey = NSURLIsExcludedFromBackupKey, error = null)
    }
}

/** The directory itself, created if this is the first slip to land in it. */
fun documentsSubdirectory(name: String): NSURL {
    val directory = documentsDirectory().URLByAppendingPathComponent(name)
    return requireNotNull(directory) { "The $name directory could not be resolved" }.also {
        NSFileManager.defaultManager.createDirectoryAtURL(
            url = it,
            withIntermediateDirectories = true,
            attributes = null,
            error = null,
        )
    }
}
