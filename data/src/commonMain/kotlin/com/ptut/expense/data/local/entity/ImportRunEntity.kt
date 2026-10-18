package com.ptut.expense.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ptut.expense.domain.ImportOutcome
import com.ptut.expense.domain.ImportRun

/**
 * F5. Freshness is derived from this table rather than stored as a `lastImportAt` scalar: a
 * stored scalar drifts, and a drifted freshness indicator is worse than none.
 *
 * The distinction that carries US-8: a run that scanned and found nothing is a **success** — it
 * proves the pipe still works. A run that could not scan at all is a failure. Collapsing the two
 * is how an import breaks silently while the UI stays green.
 */
@Entity(
    tableName = "import_run",
    // Freshness reads the newest successful finish, so the index carries both columns.
    indices = [Index(value = ["outcome", "finishedAtEpochMillis"])],
)
data class ImportRunEntity(
    @PrimaryKey val id: String,
    val startedAtEpochMillis: Long,
    /** Null while the run is still going. */
    val finishedAtEpochMillis: Long?,
    val outcome: String,
    val slipsFound: Int,
    val failureReason: String?,
    /** Added in schema 10: what the run filed and queued, and the folders they came from, comma-joined. */
    @ColumnInfo(defaultValue = "0")
    val filed: Int = 0,
    @ColumnInfo(defaultValue = "0")
    val queued: Int = 0,
    @ColumnInfo(defaultValue = "")
    val folders: String = "",
) {
    companion object {
        /**
         * The one spelling of a successful outcome. The DAO takes it as a parameter rather than
         * inlining the literal, so the query and the writer cannot drift apart.
         */
        const val OUTCOME_SUCCESS: String = "Success"

        /** A run that could not scan at all. US-8: this is not the same as scanning and finding none. */
        const val OUTCOME_FAILURE: String = "Failure"

        /** Still going, or died before it could finish. Never counts towards freshness. */
        const val OUTCOME_RUNNING: String = "Running"
    }
}

/**
 * An unknown outcome reads as Running rather than throwing. Unlike a [com.ptut.expense.domain.Direction],
 * a mis-stored outcome cannot turn money into the wrong number — and the Activity screen showing
 * one run as in-progress is a better failure than the whole journal refusing to render.
 */
fun ImportRunEntity.toDomain(): ImportRun = ImportRun(
    id = id,
    startedAtEpochMillis = startedAtEpochMillis,
    finishedAtEpochMillis = finishedAtEpochMillis,
    outcome = when (outcome) {
        ImportRunEntity.OUTCOME_SUCCESS -> ImportOutcome.Success
        ImportRunEntity.OUTCOME_FAILURE -> ImportOutcome.Failure
        else -> ImportOutcome.Running
    },
    slipsFound = slipsFound,
    failureReason = failureReason,
    filed = filed,
    queued = queued,
    folders = folders.split(FOLDER_SEPARATOR).filter { it.isNotEmpty() },
)

/** The unit separator, which no folder name holds, so a comma inside a name cannot split it. */
internal const val FOLDER_SEPARATOR = "\u001F"
