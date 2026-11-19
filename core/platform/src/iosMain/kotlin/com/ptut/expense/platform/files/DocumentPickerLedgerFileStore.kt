package com.ptut.expense.platform.files

import com.ptut.expense.locale.AppText
import com.ptut.expense.platform.toNSData
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CompletableDeferred
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSNumber
import platform.Foundation.NSString
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.stringWithContentsOfURL
import platform.Foundation.writeToURL
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIViewController
import platform.UniformTypeIdentifiers.UTTypeJSON
import platform.darwin.NSObject

/**
 * FR-22 on iOS, through `UIDocumentPickerViewController`.
 *
 * The system picker rather than a path the app picks, for the same reason as Android: NFR-1 says
 * nothing leaves the device unless the person says so, and the picker is where they say it. It
 * also keeps the app out of the Files sandbox — the grant is the one file they chose.
 *
 * Export copies through a file in the temporary directory. `forExportingURLs` takes a file that
 * already exists, and the temporary directory is the only place this app may write unasked.
 */
class DocumentPickerLedgerFileStore : LedgerFileStore {

    @OptIn(ExperimentalForeignApi::class)
    override suspend fun write(suggestedName: String, contents: String): String? {
        val staged = NSURL.fileURLWithPath(NSTemporaryDirectory() + suggestedName)
        if (!contents.encodeToByteArray().toNSData().writeToURL(staged, atomically = true)) {
            error(AppText.copy.folders.fileNotStaged)
        }

        // The picker saves a copy, so the staged one has no job left when it closes, saved or backed out.
        // Left in tmp it is the whole ledger in plain text, outside the place the reader chose.
        val saved = try {
            present { UIDocumentPickerViewController(forExportingURLs = listOf(staged), asCopy = true) }
        } finally {
            NSFileManager.defaultManager.removeItemAtURL(staged, null)
        } ?: return null
        return saved.lastPathComponent ?: suggestedName
    }

    @OptIn(ExperimentalForeignApi::class)
    override suspend fun read(): String? {
        val picked = present {
            UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypeJSON), asCopy = true)
        } ?: return null

        // A picked file is handed over inside a security scope, and reading outside it fails on
        // a file the app has no other claim to.
        val opened = picked.startAccessingSecurityScopedResource()
        try {
            if (picked.fileBytes() > MAX_BACKUP_BYTES) error(AppText.copy.phrases.backupNotReadable)
            return NSString.stringWithContentsOfURL(picked, NSUTF8StringEncoding, null)
                ?: error(AppText.copy.folders.fileNotRead)
        } finally {
            if (opened) picked.stopAccessingSecurityScopedResource()
        }
    }

    /**
     * Presents [build] and suspends until the picker closes. Null when it was dismissed, which is
     * an ordinary answer and never reported as a failure.
     *
     * [presenting] holds the delegate for as long as the picker is up. UIKit keeps only a weak
     * reference to a delegate, so without a strong one here the object is collectable while the
     * picker is still on screen — and a collected delegate is a picker that answers nothing.
     */
    private suspend fun present(build: () -> UIDocumentPickerViewController): NSURL? {
        val answer = CompletableDeferred<NSURL?>()
        val delegate = PickerDelegate(answer)
        val picker = build()
        picker.delegate = delegate
        presenting = delegate

        val root = UIApplication.sharedApplication.keyWindow?.rootViewController
            ?: error(AppText.copy.folders.noScreenForPicker)
        root.topMost().presentViewController(picker, animated = true, completion = null)

        return try {
            answer.await()
        } finally {
            presenting = null
        }
    }

    /** Strong reference to the delegate UIKit only holds weakly. Null while nothing is up. */
    private var presenting: PickerDelegate? = null

    /** Presenting on a controller that already has one showing does nothing at all. */
    private fun UIViewController.topMost(): UIViewController =
        presentedViewController?.topMost() ?: this
}

/** Years of a ledger are a few megabytes. Past this the file is not one, and reading it could end the process. */
private const val MAX_BACKUP_BYTES = 50L * 1024 * 1024

/** The file's size on disk, or 0 when it cannot be asked: the read that follows then says what is wrong. */
@OptIn(ExperimentalForeignApi::class)
private fun NSURL.fileBytes(): Long {
    val attributes = path?.let { NSFileManager.defaultManager.attributesOfItemAtPath(it, null) }
    return (attributes?.get(NSFileSize) as? NSNumber)?.longLongValue ?: 0L
}

private class PickerDelegate(
    private val answer: CompletableDeferred<NSURL?>,
) : NSObject(), UIDocumentPickerDelegateProtocol {

    override fun documentPicker(
        controller: UIDocumentPickerViewController,
        didPickDocumentsAtURLs: List<*>,
    ) {
        answer.complete(didPickDocumentsAtURLs.firstOrNull() as? NSURL)
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
        answer.complete(null)
    }
}
