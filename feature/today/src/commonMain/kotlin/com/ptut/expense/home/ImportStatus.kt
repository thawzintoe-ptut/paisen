package com.ptut.expense.home

import com.ptut.expense.domain.ImportFreshness
import com.ptut.expense.domain.ImportStopReason
import com.ptut.expense.domain.StopSentences
import com.ptut.expense.domain.folders.WatchedFolder
import com.ptut.expense.domain.folders.watched
import com.ptut.expense.domain.today.ImportHealth
import com.ptut.expense.locale.AppText
import com.ptut.expense.locale.HomeCopy
import com.ptut.expense.locale.Language
import com.ptut.expense.ui.elapsedPhrase

/**
 * What the freshness line says. The span lives inside the states that have one, so no screen can
 * print an elapsed time for an import that never ran — the case a fresh install always hits.
 */
sealed interface ImportStatusUiModel {
    /** No import has ever finished successfully. Distinct from [Broken]: not stopped, not started. */
    data object NeverRun : ImportStatusUiModel

    data class Fresh(val since: String) : ImportStatusUiModel

    data class Stale(val since: String) : ImportStatusUiModel

    data class Broken(val since: String) : ImportStatusUiModel

    /** The newest scan failed. A success an hour ago says nothing about the scans since (US-8). */
    data class Failing(val reason: ImportStopReason) : ImportStatusUiModel {
        /** What stopped, then what still counts (#69). */
        val body: String get() = AppText.copy.home.let { text -> "${reason.line(text)} ${text.stillCounts}" }
    }

    /** Android's "Select photos": scans succeed, but a new slip stays hidden until it is picked. */
    data object OnlyPicked : ImportStatusUiModel
}

/** "Watching K PLUS · checked 4 min ago", or a count once more than one folder is watched. */
internal fun List<WatchedFolder>.watchLine(sinceLastImportMillis: Long?): String {
    val text = AppText.copy.home
    val on = watched()
    val checked = sinceLastImportMillis?.let { text.checkedAgo(elapsedPhrase(it)) } ?: text.noRunYet
    return when (on.size) {
        0 -> text.noFolderWatched
        1 -> text.watchingOne(on.single().name, checked)
        else -> text.watchingMany(on.size, checked)
    }
}

/** Never having run is the one state carrying no span. */
internal fun ImportHealth.asImportStatus(): ImportStatusUiModel = when (this) {
    is ImportHealth.Stopped -> ImportStatusUiModel.Failing(reason)

    ImportHealth.OnlyPicked -> ImportStatusUiModel.OnlyPicked

    ImportHealth.NeverRun -> ImportStatusUiModel.NeverRun

    is ImportHealth.Ran -> {
        val since = elapsedPhrase(sinceLastImportMillis)
        when (freshness) {
            ImportFreshness.Fresh -> ImportStatusUiModel.Fresh(since)
            ImportFreshness.Stale -> ImportStatusUiModel.Stale(since)
            ImportFreshness.Broken -> ImportStatusUiModel.Broken(since)
        }
    }
}

/** The sentences [latestStop] tells a stop apart by, in every language, from the copy. */
internal fun stopSentences(): StopSentences {
    val folders = Language.entries.map { it.copy.folders }
    return StopSentences(
        accessDenied = folders.flatMap { listOf(it.androidAccessDenied, it.iosAccessDenied) }.toSet(),
        noAnswer = folders.map { it.libraryNoAnswer }.toSet(),
        unnamed = AppText.copy.slips.importerStopped,
    )
}

private fun ImportStopReason.line(text: HomeCopy): String = when (this) {
    ImportStopReason.PhotoAccessOff -> text.stopAccessOff
    ImportStopReason.LibraryNoAnswer -> text.stopNoAnswer
    is ImportStopReason.Other -> said.replaceFirstChar { it.uppercase() }
}
