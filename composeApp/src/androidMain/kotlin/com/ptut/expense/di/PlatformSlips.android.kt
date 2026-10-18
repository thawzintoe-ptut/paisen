package com.ptut.expense.di

import com.ptut.expense.data.local.PtutDatabaseContext
import com.ptut.expense.platform.ocr.MlKitSlipTextReader
import com.ptut.expense.platform.photos.CompositeSlipSource
import com.ptut.expense.platform.photos.MediaStoreSlipSource
import com.ptut.expense.platform.photos.SharedSlipInbox
import com.ptut.expense.platform.share.SharedSlipReceiver
import com.ptut.expense.platform.work.BackgroundImport
import com.ptut.expense.platform.work.WorkManagerBackgroundImport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.Module
import org.koin.dsl.module

/** See [CompositeSlipSource]: a name here is written into every queued slip's id, so it is fixed. */
private const val FOLDER_SLIPS = "folder"
private const val SHARED_SLIPS = "shared"

/**
 * #17: MediaStore over every watched folder, the share sheet's inbox, and ML Kit reading both.
 *
 * [SharedSlipInbox] is bound on its own as well as inside the composite, because the Activity hands
 * a shared picture to the same instance the importer scans. Two instances would leave the share
 * sheet writing into a folder nothing reads.
 *
 * `applicationContext` throughout: a worker outlives every Activity, and one held here would be
 * leaked for the life of the process.
 */
actual fun platformSlipModule(context: PtutDatabaseContext): Module = module {
    val application = context.applicationContext

    single { SharedSlipInbox(application) }

    /** FR-3's switch. Only Android binds one — iOS has no periodic window to start or stop. */
    single<BackgroundImport> { WorkManagerBackgroundImport(application) }

    single {
        SlipSeams(
            source = CompositeSlipSource(
                mapOf(
                    FOLDER_SLIPS to MediaStoreSlipSource(application, get()),
                    SHARED_SLIPS to get<SharedSlipInbox>(),
                ),
            ),
            reader = MlKitSlipTextReader(),
        )
    }

    single {
        SharedSlipReceiver(
            context = application,
            inbox = get(),
            importer = get(),
            toasts = get(),
            // Its own scope, like the repositories': the copy has to finish whether or not the
            // Activity that received the intent is still there.
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        )
    }
}
