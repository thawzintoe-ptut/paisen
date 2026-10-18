package com.ptut.expense.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import com.ptut.expense.data.local.entity.RuleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RuleDao {
    @Query("SELECT * FROM rule ORDER BY counterparty")
    fun observeAll(): Flow<List<RuleEntity>>

    /** The importer reads the table once per run rather than holding a subscription open. */
    @Query("SELECT * FROM rule")
    suspend fun all(): List<RuleEntity>

    /**
     * FR-12 with the count the screen needs, derived rather than stored — a counter column drifts
     * the first time a transaction is deleted, and this is the figure the reader uses to judge how
     * much filing a rule is doing.
     */
    @Query(
        """
        SELECT r.id AS id, r.counterparty AS counterparty, r.categoryId AS categoryId,
               (SELECT COUNT(*) FROM transactions t WHERE t.counterparty = r.counterparty) AS slipCount,
               r.createdAtEpochMillis AS createdAtEpochMillis,
               (SELECT COUNT(*) FROM transactions t WHERE t.ruleId = r.id) AS filedByRule,
               r.displayName AS displayName
        FROM rule r
        ORDER BY r.counterparty
        """,
    )
    fun observeRemembered(): Flow<List<RememberedMerchantRow>>

    /** FR-13: the category of later slips only. No transaction is touched. */
    @Query("UPDATE rule SET categoryId = :categoryId WHERE id = :id")
    suspend fun recategorise(id: String, categoryId: String)

    /** The merchant's history stays filed; it is simply asked about again next time. */
    @Query("DELETE FROM rule WHERE id = :id")
    suspend fun forget(id: String)

    /** The shown name only. The key, and so which slips the rule files, stays as it was. */
    @Query("UPDATE rule SET displayName = :displayName WHERE id = :id")
    suspend fun rename(id: String, displayName: String?)
}

/** The shape [RuleDao.observeRemembered] projects. Not a table — the count has nowhere to live. */
data class RememberedMerchantRow(
    val id: String,
    val counterparty: String,
    val categoryId: String,
    val slipCount: Int,
    val createdAtEpochMillis: Long,
    val filedByRule: Int,
    val displayName: String? = null,
)
