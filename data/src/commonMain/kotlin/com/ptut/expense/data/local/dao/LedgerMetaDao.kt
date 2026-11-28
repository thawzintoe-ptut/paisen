package com.ptut.expense.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import com.ptut.expense.data.local.entity.LedgerMetaEntity
import com.ptut.expense.data.local.entity.PlayGoalDayEntity
import com.ptut.expense.data.local.entity.PlayGoalDepositEntity
import com.ptut.expense.data.local.entity.PlayGoalEntity
import com.ptut.expense.data.local.entity.PlayWeekEntity
import com.ptut.expense.data.local.entity.TransactionEntity

/** #41: the one row that says what money the ledger counts in and where its days start. */
@Dao
abstract class LedgerMetaDao {

    /** Null until home is first written: every ledger made before #41 is baht and Bangkok. */
    @Query("SELECT * FROM ledger_meta WHERE id = 0")
    abstract suspend fun read(): LedgerMetaEntity?

    @Upsert
    abstract suspend fun write(meta: LedgerMetaEntity)

    @Query("SELECT COUNT(*) FROM transactions")
    protected abstract suspend fun rowCount(): Long

    @Update
    protected abstract suspend fun updateRows(rows: List<TransactionEntity>)

    /**
     * #41: home changes freely only while the ledger is empty. The count and the write are one
     * transaction, so a slip the importer files in between cannot land in the old money.
     */
    @Transaction
    open suspend fun writeIfEmpty(meta: LedgerMetaEntity): Boolean {
        if (rowCount() > 0) return false
        write(meta)
        return true
    }

    @Query("SELECT * FROM transactions")
    protected abstract suspend fun allRows(): List<TransactionEntity>

    @Query("SELECT * FROM play_goal WHERE currency = :code")
    protected abstract suspend fun goalsIn(code: String): List<PlayGoalEntity>

    @Query("SELECT * FROM play_goal_deposit WHERE goalId IN (:goalIds)")
    protected abstract suspend fun depositsOf(goalIds: List<String>): List<PlayGoalDepositEntity>

    @Query("SELECT * FROM play_goal_day WHERE goalId IN (:goalIds)")
    protected abstract suspend fun goalDaysOf(goalIds: List<String>): List<PlayGoalDayEntity>

    @Query("SELECT * FROM play_week")
    protected abstract suspend fun weeks(): List<PlayWeekEntity>

    @Update
    protected abstract suspend fun updateGoals(goals: List<PlayGoalEntity>)

    @Update
    protected abstract suspend fun updateDeposits(deposits: List<PlayGoalDepositEntity>)

    @Update
    protected abstract suspend fun updateGoalDays(days: List<PlayGoalDayEntity>)

    @Update
    protected abstract suspend fun updateWeeks(weeks: List<PlayWeekEntity>)

    /**
     * #47: every row, play's money and the home itself, or none. The rows are read in here, so a slip
     * filed meanwhile moves too; [move] returning null refuses the change before anything is written.
     */
    @Transaction
    open suspend fun rehome(
        meta: LedgerMetaEntity,
        oldCode: String,
        move: (List<TransactionEntity>) -> List<TransactionEntity>?,
        money: (Long) -> Long,
    ): Boolean {
        val rows = move(allRows()) ?: return false
        updateRows(rows)
        val goals = goalsIn(oldCode)
        val goalIds = goals.map { it.id }
        updateGoals(goals.map { it.copy(targetMinor = money(it.targetMinor), currency = meta.currency) })
        updateDeposits(depositsOf(goalIds).map { it.copy(amountMinor = money(it.amountMinor)) })
        updateGoalDays(goalDaysOf(goalIds).map { day -> day.copy(usualMinor = day.usualMinor?.let(money)) })
        updateWeeks(
            weeks().map { week ->
                week.copy(
                    paisenGuessMinor = week.paisenGuessMinor?.let(money),
                    guessMinor = week.guessMinor?.let(money),
                    totalMinor = week.totalMinor?.let(money),
                    challengeLimitMinor = week.challengeLimitMinor?.let(money),
                )
            },
        )
        write(meta)
        return true
    }
}
