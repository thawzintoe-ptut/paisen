package com.ptut.expense.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ptut.expense.domain.QueuedSlip
import com.ptut.expense.domain.ReviewReason
import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.slip.SlipField

/**
 * A slip the importer read but would not file on its own.
 *
 * FR-7: [amountMinor] stays null when the reader could not make out the number. A pre-filled
 * figure invites a careless confirm, which is the failure the null exists to prevent.
 */
@Entity(
    tableName = "queued_slip",
    // F3: an unreadable slip has no reference and no amount, so it cannot form the content
    // key. The file hash is the only thing stopping a second scan queueing it twice.
    indices = [Index(value = ["slipHash"], unique = true)],
)
data class QueuedSlipEntity(
    @PrimaryKey val id: String,
    val slipHash: String,
    val merchant: String,
    val occurredAtEpochMillis: Long,
    val note: String,
    val amountMinor: Long?,
    val reason: String,
    /** NFR-4: the image is swept seven days after this. */
    val capturedAtEpochMillis: Long,
    /** The image has been swept but the entry stays — a known spend beats a silent deletion. */
    val evidenceExpired: Boolean,
    /**
     * The fields the parser was unsure of, comma-joined, empty when it was sure of all of them.
     *
     * A string rather than a table: this is at most three names read once by one screen, and a
     * join is a worse trade than a column nobody has to migrate twice. Added in schema 3, and
     * every row written before it reads as "the parser was sure", which is the safer default —
     * a mark asks for a second look, and asking for one on old rows nobody can check is noise.
     */
    @ColumnInfo(defaultValue = "")
    val lowConfidence: String = "",
    /** The watched folder the slip came from. Added in schema 10. */
    val sourceFolder: String? = null,
)

fun QueuedSlipEntity.toDomain(): QueuedSlip = QueuedSlip(
    id = id,
    merchant = merchant,
    occurredAtEpochMillis = occurredAtEpochMillis,
    note = note,
    amount = amountMinor?.let(Satang::ofMinorUnits),
    reason = ReviewReason.valueOf(reason),
    capturedAtEpochMillis = capturedAtEpochMillis,
    lowConfidence = lowConfidence.toSlipFields(),
    sourceFolder = sourceFolder,
)

/**
 * An unknown name is dropped rather than thrown on. A mark is a hint to look at the picture, so
 * losing one costs a second glance; refusing to render the row costs the reader their slip.
 */
internal fun String.toSlipFields(): Set<SlipField> =
    split(",").mapNotNullTo(mutableSetOf()) { name ->
        SlipField.entries.firstOrNull { it.name == name }
    }

internal fun Set<SlipField>.asColumn(): String = joinToString(",") { it.name }
