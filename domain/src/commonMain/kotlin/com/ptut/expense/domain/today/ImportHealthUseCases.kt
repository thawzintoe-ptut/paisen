package com.ptut.expense.domain.today

import com.ptut.expense.domain.ImportFix
import com.ptut.expense.domain.ImportFreshness
import com.ptut.expense.domain.ImportRun
import com.ptut.expense.domain.ImportStopReason
import com.ptut.expense.domain.StopSentences
import com.ptut.expense.domain.fix
import com.ptut.expense.domain.importFix
import com.ptut.expense.domain.importFreshness
import com.ptut.expense.domain.latestStop

/**
 * What Today's freshness line stands on (US-8), the most pressing first: a failed scan, then photos
 * shared only as picked, then how long since a scan last worked.
 */
sealed interface ImportHealth {
    /** What the reader can do about it, or null while offering an action would overstate it. */
    val fix: ImportFix?

    /** The newest scan failed (#69). A success an hour ago says nothing about the scans since. */
    data class Stopped(val reason: ImportStopReason) : ImportHealth {
        override val fix: ImportFix? get() = reason.fix()
    }

    /** Android's "Select photos": scans succeed, but a new slip stays hidden until it is picked. */
    data object OnlyPicked : ImportHealth {
        override val fix: ImportFix get() = ImportFix.AllowAllPhotos
    }

    /** No import has ever finished successfully: not stopped, not started. */
    data object NeverRun : ImportHealth {
        override val fix: ImportFix? get() = importFix(null)
    }

    data class Ran(val sinceLastImportMillis: Long, val freshness: ImportFreshness) : ImportHealth {
        override val fix: ImportFix? get() = importFix(sinceLastImportMillis)
    }
}

/** Asks [onlyPickedPhotos] on every call, since the reader changes it in the system's pages. */
class GetImportHealthUseCase(private val onlyPickedPhotos: () -> Boolean = { false }) {
    operator fun invoke(sinceLastImportMillis: Long?, runs: List<ImportRun>, sentences: StopSentences): ImportHealth {
        runs.latestStop(sentences)?.let { return ImportHealth.Stopped(it) }
        if (onlyPickedPhotos()) return ImportHealth.OnlyPicked
        val since = sinceLastImportMillis ?: return ImportHealth.NeverRun
        return ImportHealth.Ran(since, importFreshness(since))
    }
}
