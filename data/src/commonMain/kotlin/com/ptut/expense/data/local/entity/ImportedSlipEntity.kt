package com.ptut.expense.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * F3 tier one, file identity: the SHA-256 of the image bytes.
 *
 * Kept forever, and deliberately outliving its image. NFR-4 deletes the picture after seven
 * days; if the hash went with it, every swept slip would become re-importable. The row holds no
 * PII, so keeping it costs nothing.
 */
@Entity(tableName = "imported_slip")
data class ImportedSlipEntity(
    @PrimaryKey val hash: String,
    val importedAtEpochMillis: Long,
)
