package com.ptut.expense.domain

/** #69: why the newest scan stopped, so Today names it and offers the fix that matches. */
sealed interface ImportStopReason {
    data object PhotoAccessOff : ImportStopReason

    /** The library gave no answer; the next scan asks again, so nothing waits on the reader. */
    data object LibraryNoAnswer : ImportStopReason

    /** Any other stop, in the journal's own words, as Activity shows them. */
    data class Other(val said: String) : ImportStopReason
}

/**
 * The sentences a stop is told apart by. [accessDenied] and [noAnswer] hold every language's, since a row from before
 * schema 23 kept the importer's sentence in the scan's language; [unnamed] stands in for a failure with no reason.
 */
data class StopSentences(val accessDenied: Set<String>, val noAnswer: Set<String>, val unnamed: String)

/** Newest first, as the journal lists them. A run still going has not answered yet. Null unless the newest failed. */
fun List<ImportRun>.latestStop(sentences: StopSentences): ImportStopReason? {
    val latest = firstOrNull { it.outcome != ImportOutcome.Running } ?: return null
    if (latest.outcome != ImportOutcome.Failure) return null
    when (latest.failureKind) {
        ImportFailureKind.PhotoAccessDenied -> return ImportStopReason.PhotoAccessOff
        ImportFailureKind.LibraryNoAnswer -> return ImportStopReason.LibraryNoAnswer
        ImportFailureKind.Other, null -> Unit
    }
    val said = latest.failureReason?.takeIf { it.isNotBlank() } ?: return ImportStopReason.Other(sentences.unnamed)
    return if (latest.failureKind == null) sentences.reasonBefore23(said) else ImportStopReason.Other(said)
}

private fun StopSentences.reasonBefore23(said: String): ImportStopReason = when (said) {
    in accessDenied -> ImportStopReason.PhotoAccessOff
    in noAnswer -> ImportStopReason.LibraryNoAnswer
    else -> ImportStopReason.Other(said)
}

/** Access goes back on in the system's page; a library that did not answer is asked again by the next scan. */
fun ImportStopReason.fix(): ImportFix? = when (this) {
    ImportStopReason.PhotoAccessOff, is ImportStopReason.Other -> ImportFix.AllowAllPhotos
    ImportStopReason.LibraryNoAnswer -> null
}
