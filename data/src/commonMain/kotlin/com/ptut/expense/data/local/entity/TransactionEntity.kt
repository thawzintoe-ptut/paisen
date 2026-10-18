package com.ptut.expense.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ptut.expense.domain.Direction
import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.Transaction
import com.ptut.expense.domain.TransactionDraft
import com.ptut.expense.domain.TransactionSource

/**
 * FR-9: money is [amountMinor], an INTEGER count of satang. No column here is REAL.
 *
 * F2: [occurredAtEpochMillis] is a UTC instant, never a date string. Day maths happens in
 * Kotlin against a Bangkok date, because SQLite's own date functions read the device zone.
 */
@Entity(
    // "transaction" is a SQLite keyword — naming the table that would put backticks in every
    // hand-written query, which is one typo away from a silent failure.
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            // A deleted category does not un-spend the money, so the row outlives it. The screen
            // asks where the transactions should go before it deletes, so this is the net rather
            // than the plan.
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = SubCategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["subCategoryId"],
            // FR-13: removing a sub-category keeps its spending in the parent. The row keeps its
            // category and loses only the finer label, which is exactly what SET_NULL does.
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index("occurredAtEpochMillis"),
        Index("categoryId"),
        Index("subCategoryId"),
        // F3 tier two, content identity. SQLite counts NULLs as distinct, so rows without a
        // reference never collide here — exactly the "where reference is non-null" rule.
        Index(value = ["reference", "amountMinor", "occurredAtEpochMillis"], unique = true),
    ],
)
data class TransactionEntity(
    @PrimaryKey val id: String,
    val occurredAtEpochMillis: Long,
    val amountMinor: Long,
    val direction: String,
    val categoryId: String?,
    val subCategoryId: String? = null,
    val counterparty: String?,
    val source: String,
    /** The slip's reference number when it had one. Half of the F3 content key. */
    val reference: String?,
    /** Null until someone corrects the row. Added in schema 2. */
    val editedAt: Long? = null,
    /** The reader's own words about this row. Never slip meta. Added in schema 4. */
    val memo: String? = null,
    /**
     * What the parser read off the slip, kept even after the amount is corrected. Null on a row
     * no slip produced, and on any row filed before schema 4 — page 11c draws no slip line for
     * either, which is the honest answer in both cases.
     */
    val slipAmountMinor: Long? = null,
    /**
     * When the row entered the ledger. Null on any row written before schema 9, which Home's
     * Saved today block then leaves out rather than guessing a day for it.
     */
    val filedAt: Long? = null,
    /** The watched folder the slip came from. Null on hand-typed rows and on rows before schema 10. */
    val sourceFolder: String? = null,
    /** The shop rule that filed the row without asking (#117). Added in schema 11; null on every other row. */
    val ruleId: String? = null,
)

/**
 * A value this app wrote itself, so an unknown name is a bug rather than a user condition —
 * it throws instead of defaulting, because guessing a [Direction] would turn income into spend.
 */
fun TransactionEntity.toDomain(): Transaction = Transaction(
    id = id,
    occurredAtEpochMillis = occurredAtEpochMillis,
    amount = Satang.ofMinorUnits(amountMinor),
    direction = Direction.valueOf(direction),
    categoryId = categoryId,
    subCategoryId = subCategoryId,
    counterparty = counterparty,
    source = TransactionSource.valueOf(source),
    reference = reference,
    editedAt = editedAt,
    memo = memo,
    slipAmount = slipAmountMinor?.let(Satang::ofMinorUnits),
    sourceFolder = sourceFolder,
    filedAt = filedAt,
    ruleId = ruleId,
)

/**
 * The write half of [toDomain]: money crosses as minor units, the enums cross as their names.
 *
 * [TransactionEntity.reference] stays null. It is a slip's number and half of the F3 content key,
 * and a row somebody typed has no slip to be a duplicate of.
 */
fun TransactionDraft.toEntity(
    id: String,
    occurredAtEpochMillis: Long,
    filedAt: Long,
) = TransactionEntity(
    id = id,
    occurredAtEpochMillis = occurredAtEpochMillis,
    amountMinor = amount.toMinorUnits(),
    direction = direction.name,
    categoryId = categoryId,
    subCategoryId = subCategoryId,
    counterparty = counterparty,
    source = source.name,
    reference = null,
    memo = memo,
    filedAt = filedAt,
)

/**
 * The whole row back, for FR-21's undo. Unlike [TransactionDraft.toEntity] this mints nothing:
 * the id, the reference and both stamps come from the record that was deleted, because an undo
 * that returned a new id would leave the F3 content key pointing at a row nobody has.
 */
fun Transaction.toEntity() = TransactionEntity(
    id = id,
    occurredAtEpochMillis = occurredAtEpochMillis,
    amountMinor = amount.toMinorUnits(),
    direction = direction.name,
    categoryId = categoryId,
    subCategoryId = subCategoryId,
    counterparty = counterparty,
    source = source.name,
    reference = reference,
    editedAt = editedAt,
    memo = memo,
    slipAmountMinor = slipAmount?.toMinorUnits(),
    filedAt = filedAt,
    sourceFolder = sourceFolder,
    ruleId = ruleId,
)
