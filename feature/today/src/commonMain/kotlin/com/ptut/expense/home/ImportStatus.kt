package com.ptut.expense.home

import com.ptut.expense.domain.ImportFailureKind
import com.ptut.expense.domain.ImportFix
import com.ptut.expense.domain.ImportFreshness
import com.ptut.expense.domain.ImportOutcome
import com.ptut.expense.domain.ImportRun
import com.ptut.expense.domain.folders.WatchedFolder
import com.ptut.expense.domain.folders.watched
import com.ptut.expense.domain.importFreshness
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
        val body: String get() = AppText.copy.home.let { text -> "${reason.line(text)}\n${text.stillCounts}" }
    }

    /** Android's "Select photos": scans succeed, but a new slip stays hidden until it is picked. */
    data object OnlyPicked : ImportStatusUiModel
}

/** #69: why the newest scan stopped, so Today names it and offers the fix that matches. */
sealed interface ImportStopReason {
    data object PhotoAccessOff : ImportStopReason

    /** The library gave no answer; the next scan asks again, so nothing waits on the reader. */
    data object LibraryNoAnswer : ImportStopReason

    /** Any other stop, in the journal's own words, as Activity shows them. */
    data class Other(val said: String) : ImportStopReason
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

/** Null means no import has ever succeeded, which is the one state carrying no span. */
internal fun Long?.asImportStatus(): ImportStatusUiModel {
    val millis = this ?: return ImportStatusUiModel.NeverRun
    val since = elapsedPhrase(millis)
    return when (importFreshness(millis)) {
        ImportFreshness.Fresh -> ImportStatusUiModel.Fresh(since)
        ImportFreshness.Stale -> ImportStatusUiModel.Stale(since)
        ImportFreshness.Broken -> ImportStatusUiModel.Broken(since)
    }
}

/** Newest first, as the journal lists them. A run still going has not answered yet. Null unless the newest failed. */
internal fun List<ImportRun>.latestStop(): ImportStopReason? {
    val latest = firstOrNull { it.outcome != ImportOutcome.Running } ?: return null
    if (latest.outcome != ImportOutcome.Failure) return null
    when (latest.failureKind) {
        ImportFailureKind.PhotoAccessDenied -> return ImportStopReason.PhotoAccessOff
        ImportFailureKind.LibraryNoAnswer -> return ImportStopReason.LibraryNoAnswer
        ImportFailureKind.Other, null -> Unit
    }
    val said = latest.failureReason?.takeIf { it.isNotBlank() } ?: return ImportStopReason.Other(AppText.copy.slips.importerStopped)
    return if (latest.failureKind == null) stopReasonBefore23(said) else ImportStopReason.Other(said)
}

/**
 * A row journalled before schema 23 keeps only the importer's sentence, in the language the scan ran
 * in, so its stop is told apart by the FoldersCopy sentences the photo sources throw, in every language.
 */
private fun stopReasonBefore23(said: String): ImportStopReason {
    val folders = Language.entries.map { it.copy.folders }
    return when (said) {
        in folders.flatMap { listOf(it.androidAccessDenied, it.iosAccessDenied) } -> ImportStopReason.PhotoAccessOff
        in folders.map { it.libraryNoAnswer } -> ImportStopReason.LibraryNoAnswer
        else -> ImportStopReason.Other(said)
    }
}

private fun ImportStopReason.line(text: HomeCopy): String = when (this) {
    ImportStopReason.PhotoAccessOff -> text.stopAccessOff
    ImportStopReason.LibraryNoAnswer -> text.stopNoAnswer
    is ImportStopReason.Other -> said.replaceFirstChar { it.uppercase() }
}

/** Access goes back on in the system's page; a library that did not answer is asked again by the next scan. */
internal fun ImportStopReason.fix(): ImportFix? = when (this) {
    ImportStopReason.PhotoAccessOff, is ImportStopReason.Other -> ImportFix.AllowAllPhotos
    ImportStopReason.LibraryNoAnswer -> null
}
