package com.ptut.expense.platform.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ptut.expense.domain.ImportOutcome
import com.ptut.expense.importer.SlipImporter
import com.ptut.expense.shared.RepeatRunner
import com.ptut.expense.sync.SharedSync
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * FR-2: one import run, off the main thread and outside any screen's lifetime.
 *
 * It owns no rules. WorkManager builds it by reflection, so it takes the importer out of the graph
 * the Application started rather than through its constructor — the one place in the app that does,
 * and only because the framework leaves no other seam.
 */
class SlipImportWorker(context: Context, parameters: WorkerParameters) :
    CoroutineWorker(context, parameters), KoinComponent {

    private val importer: SlipImporter by inject()

    /** #182: repeats due today add themselves in the same background run. */
    private val repeats: RepeatRunner by inject()

    /** Null in a graph without sync, as on the app's own start. */
    private val sync: SharedSync? by lazy { getKoin().getOrNull<SharedSync>() }

    /**
     * A failed run is reported, not retried. The importer has already journalled why, US-8's banner
     * already says so, and the commonest failure is photo access being off — which no amount of
     * backing off will fix. The periodic schedule survives a failed run, so the fifteen-minute
     * cadence is the retry.
     */
    override suspend fun doWork(): Result {
        val outcome = importer.run().outcome
        // Pull first, as the app does when it opens: a repeat entry another phone already added
        // arrives before this phone makes its own copy under the same id.
        runCatching { sync?.syncAll() }
        runCatching { repeats.run() }
        return if (outcome == ImportOutcome.Success) Result.success() else Result.failure()
    }
}
