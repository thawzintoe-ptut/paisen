package com.ptut.expense.domain

/**
 * US-8's central distinction. A run that scanned and found nothing is a **success** — it proves
 * the pipe still works. A run that could not scan at all is a failure. Collapsing the two is how
 * an import breaks silently while the UI stays green.
 */
enum class ImportOutcome { Running, Success, Failure }

/**
 * One pass of the importer: what the journal records, and what the Activity screen reads. FR-5.
 *
 * [slipsFound] counts candidates seen, not rows written — "found nothing" and "found ten
 * duplicates" are different stories and the screen tells them apart.
 *
 * [finishedAtEpochMillis] is null while a run is still going — or if it died before it could
 * finish, which is itself evidence and why the row is written when the run starts.
 */
data class ImportRun(
    val id: String,
    val startedAtEpochMillis: Long,
    val finishedAtEpochMillis: Long?,
    val outcome: ImportOutcome,
    val slipsFound: Int,
    val failureReason: String?,
    /** What the run wrote: rows filed without asking, and slips it queued for the reader (#170). */
    val filed: Int = 0,
    val queued: Int = 0,
    /** The folders those came from, in the order first met, for Activity's line (#170). */
    val folders: List<String> = emptyList(),
)
