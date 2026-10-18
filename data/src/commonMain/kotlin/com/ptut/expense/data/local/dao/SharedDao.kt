package com.ptut.expense.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import com.ptut.expense.data.local.entity.CurrencyRateEntity
import com.ptut.expense.data.local.entity.ExpenseLinkEntity
import com.ptut.expense.data.local.entity.ExpenseShareEntity
import com.ptut.expense.data.local.entity.ExpenseVersionEntity
import com.ptut.expense.data.local.entity.GroupMemberEntity
import com.ptut.expense.data.local.entity.PaymentRemovalEntity
import com.ptut.expense.data.local.entity.PersonEntity
import com.ptut.expense.data.local.entity.RepeatRuleEntity
import com.ptut.expense.data.local.entity.SharedGroupEntity
import com.ptut.expense.data.local.entity.SharedPaymentEntity
import com.ptut.expense.data.local.entity.SyncOutboxEntity
import com.ptut.expense.data.local.entity.TransactionEntity
import com.ptut.expense.domain.shared.ExpenseCause
import kotlinx.coroutines.flow.Flow

/**
 * #133. The expense and payment tables are a log: rows are inserted and never updated or deleted,
 * save a repeat's entry that sync replaces with the server's copy ([receiveVersion]) and a row the
 * group refused for good ([takeBackVersion]). Groups, members and people are the only other rows
 * that change in place.
 */
@Dao
interface SharedDao {
    @Query("SELECT * FROM shared_group ORDER BY createdAtEpochMillis DESC")
    fun observeGroups(): Flow<List<SharedGroupEntity>>

    @Query("SELECT * FROM group_member ORDER BY createdAtEpochMillis, name")
    fun observeMembers(): Flow<List<GroupMemberEntity>>

    @Query("SELECT * FROM person ORDER BY name")
    fun observePeople(): Flow<List<PersonEntity>>

    @Query("SELECT * FROM expense_version ORDER BY createdAtEpochMillis, versionId")
    fun observeVersions(): Flow<List<ExpenseVersionEntity>>

    @Query("SELECT * FROM expense_share")
    fun observeShares(): Flow<List<ExpenseShareEntity>>

    @Query("SELECT * FROM shared_payment ORDER BY occurredAtEpochMillis")
    fun observePayments(): Flow<List<SharedPaymentEntity>>

    @Query("SELECT * FROM expense_link")
    fun observeLinks(): Flow<List<ExpenseLinkEntity>>

    @Query("SELECT * FROM payment_removal ORDER BY createdAtEpochMillis, id")
    fun observePaymentRemovals(): Flow<List<PaymentRemovalEntity>>

    @Insert
    suspend fun insertGroup(group: SharedGroupEntity)

    @Insert
    suspend fun insertMembers(members: List<GroupMemberEntity>)

    @Upsert
    suspend fun upsertPeople(people: List<PersonEntity>)

    /** A group, the people it brings, and its members land together or not at all. */
    @Transaction
    suspend fun createGroup(group: SharedGroupEntity, people: List<PersonEntity>, members: List<GroupMemberEntity>) {
        insertGroup(group)
        upsertPeople(people)
        insertMembers(members)
    }

    @Transaction
    suspend fun addMembers(people: List<PersonEntity>, members: List<GroupMemberEntity>) {
        upsertPeople(people)
        insertMembers(members)
    }

    @Query("UPDATE shared_group SET simplifyDebts = :on WHERE id = :groupId")
    suspend fun setSimplifyDebts(groupId: String, on: Boolean)

    @Query("UPDATE shared_group SET name = :name, type = :type, icon = :icon WHERE id = :groupId")
    suspend fun editGroup(groupId: String, name: String, type: String, icon: String)

    @Query("UPDATE person SET archived = :archived WHERE id = :personId")
    suspend fun setPersonArchived(personId: String, archived: Boolean)

    @Query("SELECT * FROM group_member WHERE personId = :personId")
    suspend fun membersOfPerson(personId: String): List<GroupMemberEntity>

    @Query("UPDATE group_member SET promptPayId = :promptPayId WHERE id = :memberId")
    suspend fun setPromptPay(memberId: String, promptPayId: String?)

    @Query("UPDATE group_member SET archived = :archived WHERE id IN (:memberIds)")
    suspend fun setMembersArchived(memberIds: List<String>, archived: Boolean)

    @Query("SELECT MAX(version) FROM expense_version WHERE expenseId = :expenseId")
    suspend fun latestVersionNumber(expenseId: String): Int?

    @Query("SELECT * FROM expense_version WHERE expenseId = :expenseId ORDER BY createdAtEpochMillis DESC, versionId DESC LIMIT 1")
    suspend fun latestVersion(expenseId: String): ExpenseVersionEntity?

    @Query("SELECT * FROM expense_version WHERE versionId = :versionId")
    suspend fun version(versionId: String): ExpenseVersionEntity?

    @Query("SELECT * FROM expense_share WHERE versionId = :versionId")
    suspend fun sharesOf(versionId: String): List<ExpenseShareEntity>

    @Query("SELECT * FROM expense_version WHERE expenseId = :expenseId ORDER BY createdAtEpochMillis DESC, versionId DESC")
    suspend fun versionsOf(expenseId: String): List<ExpenseVersionEntity>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun transaction(id: String): TransactionEntity?

    @Insert
    suspend fun insertVersion(version: ExpenseVersionEntity)

    @Insert
    suspend fun insertShares(shares: List<ExpenseShareEntity>)

    @Insert
    suspend fun insertTransaction(transaction: TransactionEntity)

    @Upsert
    suspend fun upsertLink(link: ExpenseLinkEntity)

    @Query("SELECT * FROM expense_link WHERE expenseId = :expenseId")
    suspend fun link(expenseId: String): ExpenseLinkEntity?

    @Query("DELETE FROM expense_link WHERE transactionId = :transactionId")
    suspend fun unlinkTransaction(transactionId: String)

    /**
     * One version with its shares, and when this phone's member paid a new expense, the
     * transaction and the link to it (#32). Nothing half-written survives a failure.
     */
    @Transaction
    suspend fun writeVersion(
        version: ExpenseVersionEntity,
        shares: List<ExpenseShareEntity>,
        newTransaction: TransactionEntity?,
    ) {
        insertVersion(version)
        insertShares(shares)
        if (newTransaction != null) {
            insertTransaction(newTransaction)
            upsertLink(ExpenseLinkEntity(expenseId = version.expenseId, transactionId = newTransaction.id))
        }
    }

    @Insert
    suspend fun insertPayment(payment: SharedPaymentEntity)

    @Query("SELECT * FROM shared_payment WHERE id = :id")
    suspend fun payment(id: String): SharedPaymentEntity?

    @Query("DELETE FROM shared_payment WHERE id = :id")
    suspend fun deletePayment(id: String)

    @Insert
    suspend fun insertPaymentRemoval(removal: PaymentRemovalEntity)

    @Query("SELECT * FROM payment_removal WHERE id = :id")
    suspend fun paymentRemoval(id: String): PaymentRemovalEntity?

    @Query("DELETE FROM payment_removal WHERE id = :id")
    suspend fun deletePaymentRemoval(id: String)

    @Query("DELETE FROM expense_version WHERE versionId = :versionId")
    suspend fun deleteVersion(versionId: String)

    @Query("DELETE FROM expense_link WHERE expenseId = :expenseId AND NOT EXISTS (SELECT 1 FROM expense_version WHERE expenseId = :expenseId)")
    suspend fun unlinkIfGone(expenseId: String)

    /**
     * A version the group refused for good, off this phone too; its shares follow by cascade. The
     * expense's link to your own transaction goes when no version is left, and the transaction stays.
     */
    @Transaction
    suspend fun takeBackVersion(versionId: String) {
        val version = version(versionId) ?: return
        deleteVersion(versionId)
        unlinkIfGone(version.expenseId)
    }

    @Query("SELECT COUNT(*) FROM sync_outbox WHERE id = :outboxId")
    suspend fun queued(outboxId: String): Int

    // ---- #189: sync ------------------------------------------------------------------------

    @Query("SELECT * FROM sync_outbox ORDER BY createdAtEpochMillis")
    fun observeOutbox(): Flow<List<SyncOutboxEntity>>

    @Query("SELECT * FROM sync_outbox ORDER BY createdAtEpochMillis")
    suspend fun outbox(): List<SyncOutboxEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun queue(rows: List<SyncOutboxEntity>)

    @Query("DELETE FROM sync_outbox WHERE id = :id")
    suspend fun dequeue(id: String)

    @Query("SELECT * FROM shared_group WHERE id = :groupId")
    suspend fun group(groupId: String): SharedGroupEntity?

    @Upsert
    suspend fun upsertGroup(group: SharedGroupEntity)

    @Upsert
    suspend fun upsertMembers(members: List<GroupMemberEntity>)

    @Query("SELECT * FROM group_member WHERE groupId = :groupId")
    suspend fun membersOf(groupId: String): List<GroupMemberEntity>

    @Query("UPDATE group_member SET name = :name, claimedByUid = :uid WHERE id = :memberId")
    suspend fun claimMember(memberId: String, name: String, uid: String?)

    @Query("UPDATE shared_group SET inviteCode = :code, inviteActive = :active WHERE id = :groupId")
    suspend fun setInvite(groupId: String, code: String?, active: Boolean)

    @Query("UPDATE shared_group SET lastSyncedAtEpochMillis = :at WHERE id = :groupId")
    suspend fun markSynced(groupId: String, at: Long)

    /** Only ever forward, so a late write from an older screen cannot bring old rows back as new. */
    @Query(
        "UPDATE shared_group SET lastSeenAtEpochMillis = :at WHERE id = :groupId " +
            "AND (lastSeenAtEpochMillis IS NULL OR lastSeenAtEpochMillis < :at)",
    )
    suspend fun markSeen(groupId: String, at: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertVersionIfNew(version: ExpenseVersionEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSharesIfNew(shares: List<ExpenseShareEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPaymentIfNew(payment: SharedPaymentEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPaymentRemovalIfNew(removal: PaymentRemovalEntity)

    @Update
    suspend fun updateVersion(version: ExpenseVersionEntity)

    @Query("DELETE FROM expense_share WHERE versionId = :versionId")
    suspend fun deleteSharesOf(versionId: String)

    /**
     * A version another phone wrote, with its shares. Already here, nothing changes, except a
     * repeat's entry: every phone makes it under one id, and the server's copy is the one all keep.
     */
    @Transaction
    suspend fun receiveVersion(version: ExpenseVersionEntity, shares: List<ExpenseShareEntity>) {
        if (insertVersionIfNew(version) != -1L) {
            insertSharesIfNew(shares)
            return
        }
        val local = this.version(version.versionId) ?: return
        if (local.cause != ExpenseCause.REPEAT || version.cause != ExpenseCause.REPEAT) return
        if (local == version && sharesOf(version.versionId).toSet() == shares.toSet()) return
        updateVersion(version)
        deleteSharesOf(version.versionId)
        insertShares(shares)
    }

    @Query("SELECT id FROM shared_group WHERE remote = 1")
    suspend fun remoteGroupIds(): List<String>

    @Query("DELETE FROM expense_link WHERE expenseId IN (SELECT expenseId FROM expense_version WHERE groupId = :groupId)")
    suspend fun deleteLinksOf(groupId: String)

    @Query("DELETE FROM expense_version WHERE groupId = :groupId")
    suspend fun deleteVersionsOf(groupId: String)

    @Query("DELETE FROM shared_payment WHERE groupId = :groupId")
    suspend fun deletePaymentsOf(groupId: String)

    @Query("DELETE FROM payment_removal WHERE groupId = :groupId")
    suspend fun deletePaymentRemovalsOf(groupId: String)

    @Query("DELETE FROM sync_outbox WHERE groupId = :groupId")
    suspend fun deleteOutboxOf(groupId: String)

    @Query("DELETE FROM shared_group WHERE id = :groupId")
    suspend fun deleteGroup(groupId: String)

    /**
     * #187: signing out takes the shared groups off this phone. Your own transactions stay; only
     * the links to them go. Shares and members follow their rows by cascade.
     */
    @Transaction
    suspend fun forgetGroup(groupId: String) {
        deleteLinksOf(groupId)
        deleteVersionsOf(groupId)
        deletePaymentsOf(groupId)
        deletePaymentRemovalsOf(groupId)
        deleteOutboxOf(groupId)
        deleteGroup(groupId)
    }

    // #133: backup format 2. Only groups that live on this phone alone; synced ones come back from Firestore.

    @Query("SELECT * FROM person")
    suspend fun people(): List<PersonEntity>

    @Query("SELECT * FROM shared_group WHERE remote = 0")
    suspend fun localGroups(): List<SharedGroupEntity>

    @Query("SELECT * FROM group_member WHERE groupId IN (:groupIds)")
    suspend fun membersIn(groupIds: List<String>): List<GroupMemberEntity>

    @Query("SELECT * FROM expense_version WHERE groupId IN (:groupIds)")
    suspend fun versionsIn(groupIds: List<String>): List<ExpenseVersionEntity>

    @Query("SELECT s.* FROM expense_share s JOIN expense_version v ON v.versionId = s.versionId WHERE v.groupId IN (:groupIds)")
    suspend fun sharesIn(groupIds: List<String>): List<ExpenseShareEntity>

    @Query("SELECT * FROM shared_payment WHERE groupId IN (:groupIds)")
    suspend fun paymentsIn(groupIds: List<String>): List<SharedPaymentEntity>

    @Query("SELECT * FROM expense_link WHERE expenseId IN (SELECT expenseId FROM expense_version WHERE groupId IN (:groupIds))")
    suspend fun linksIn(groupIds: List<String>): List<ExpenseLinkEntity>

    @Query("SELECT id FROM transactions WHERE id IN (:ids)")
    suspend fun heldTransactionIds(ids: List<String>): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPeopleIfNew(people: List<PersonEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertGroupsIfNew(groups: List<SharedGroupEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMembersIfNew(members: List<GroupMemberEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertVersionsIfNew(versions: List<ExpenseVersionEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPaymentsIfNew(payments: List<SharedPaymentEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLinksIfNew(links: List<ExpenseLinkEntity>)

    /**
     * A restore adds what is not here and never replaces (FR-22). Parents go first so every
     * foreign key holds; a link whose transaction this ledger lacks is dropped, not failed on.
     * Returns how many groups were new.
     */
    @Transaction
    suspend fun restoreShared(
        people: List<PersonEntity>,
        groups: List<SharedGroupEntity>,
        members: List<GroupMemberEntity>,
        versions: List<ExpenseVersionEntity>,
        shares: List<ExpenseShareEntity>,
        payments: List<SharedPaymentEntity>,
        links: List<ExpenseLinkEntity>,
        repeats: List<RepeatRuleEntity> = emptyList(),
    ): Int {
        insertPeopleIfNew(people)
        val added = insertGroupsIfNew(groups).count { it != -1L }
        insertMembersIfNew(members)
        insertVersionsIfNew(versions)
        insertSharesIfNew(shares)
        insertPaymentsIfNew(payments)
        val held = links.map { it.transactionId }.chunked(SQLITE_VARIABLES).flatMap { heldTransactionIds(it) }.toSet()
        insertLinksIfNew(links.filter { it.transactionId in held })
        insertRepeatsIfNew(repeats)
        return added
    }

    // #182: repeats.

    @Query("SELECT * FROM repeat_rule ORDER BY createdAtEpochMillis")
    fun observeRepeats(): Flow<List<RepeatRuleEntity>>

    @Query("SELECT * FROM repeat_rule WHERE id = :id")
    suspend fun repeat(id: String): RepeatRuleEntity?

    @Query("SELECT * FROM repeat_rule WHERE active = 1")
    suspend fun activeRepeats(): List<RepeatRuleEntity>

    @Upsert
    suspend fun upsertRepeat(rule: RepeatRuleEntity)

    @Query("UPDATE repeat_rule SET lastAdded = :date WHERE id = :id")
    suspend fun setLastAdded(id: String, date: String)

    @Query("UPDATE repeat_rule SET remind = :on WHERE id = :id")
    suspend fun setRemind(id: String, on: Boolean)

    @Query("SELECT * FROM repeat_rule WHERE groupId IN (:groupIds)")
    suspend fun repeatsIn(groupIds: List<String>): List<RepeatRuleEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRepeatsIfNew(rules: List<RepeatRuleEntity>)

    /** One added date: the same ids on every phone, so a copy that already arrived is left as it is. */
    @Transaction
    suspend fun addRepeatEntry(version: ExpenseVersionEntity, shares: List<ExpenseShareEntity>, ruleId: String, date: String): Boolean {
        val added = insertVersionIfNew(version) != -1L
        if (added) insertSharesIfNew(shares)
        setLastAdded(ruleId, date)
        return added
    }

    @Query("SELECT * FROM currency_rate WHERE currency = :currency")
    suspend fun rate(currency: String): CurrencyRateEntity?

    @Upsert
    suspend fun upsertRate(rate: CurrencyRateEntity)
}

/** SQLite's default cap on bound variables in one statement. */
private const val SQLITE_VARIABLES = 900
