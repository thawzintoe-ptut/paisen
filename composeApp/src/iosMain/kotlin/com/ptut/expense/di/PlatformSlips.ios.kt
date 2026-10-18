package com.ptut.expense.di

import com.ptut.expense.data.local.PtutDatabaseContext
import com.ptut.expense.platform.ocr.VisionSlipTextReader
import com.ptut.expense.platform.photos.CompositeSlipSource
import com.ptut.expense.platform.photos.PhotoLibraryAccess
import com.ptut.expense.platform.photos.PhotoLibrarySlipSource
import com.ptut.expense.platform.photos.SharedSlipInbox
import com.ptut.expense.platform.share.SharedSlipReceiver
import com.ptut.expense.platform.work.SlipImportSchedule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.Module
import org.koin.dsl.module

/** See [CompositeSlipSource]: a name here is written into every queued slip's id, so it is fixed. */
private const val ALBUM_SLIPS = "album"
private const val SHARED_SLIPS = "shared"

/**
 * #18: every watched album through `PHPhotoLibrary`, the share sheet's inbox, and Vision reading
 * both.
 *
 * [SharedSlipInbox] is bound on its own as well as inside the composite, because the entry point
 * hands a shared picture to the same instance the importer scans. Two instances would leave the
 * share sheet writing into a folder nothing reads.
 *
 * [context] is unused here and stays in the signature because the seam is shared: iOS resolves its
 * own directories, so there is no handle for a platform to pass across.
 */
actual fun platformSlipModule(context: PtutDatabaseContext): Module = module {
    single { SharedSlipInbox() }

    single {
        SlipSeams(
            source = CompositeSlipSource(
                mapOf(
                    ALBUM_SLIPS to PhotoLibrarySlipSource(
                        // Built here rather than resolved: the gate holds no state — every call
                        // reads `PHPhotoLibrary` — so a binding of its own would be graph nobody
                        // shares. `photoAccessRequest` hands the same type to first run.
                        isAllowed = PhotoLibraryAccess()::isGranted,
                        folders = get(),
                    ),
                    SHARED_SLIPS to get<SharedSlipInbox>(),
                ),
            ),
            reader = VisionSlipTextReader(),
        )
    }

    single {
        SlipImportSchedule(
            importer = get(),
            onboarding = get(),
            toasts = get(),
            // Its own scope, like the repositories': a run started as the app came back has to
            // finish whether or not the screen that was showing is still there.
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        )
    }

    single {
        SharedSlipReceiver(
            inbox = get(),
            imports = get(),
            importer = get(),
            toasts = get(),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        )
    }
}
