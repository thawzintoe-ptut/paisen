package com.ptut.expense.data.repository

import com.ptut.expense.data.local.dao.CatalogBackupDao
import com.ptut.expense.data.local.dao.CategoryDao
import com.ptut.expense.data.local.dao.LedgerMergeDao
import com.ptut.expense.data.local.dao.SharedDao
import com.ptut.expense.data.local.dao.TransactionDao
import com.ptut.expense.data.local.dao.withCheckedSubCategory
import com.ptut.expense.data.local.entity.CategoryEntity
import com.ptut.expense.data.local.entity.ExpenseLinkEntity
import com.ptut.expense.data.local.entity.ExpenseShareEntity
import com.ptut.expense.data.local.entity.ExpenseVersionEntity
import com.ptut.expense.data.local.entity.GroupMemberEntity
import com.ptut.expense.data.local.entity.PersonEntity
import com.ptut.expense.data.local.entity.RepeatRuleEntity
import com.ptut.expense.data.local.entity.RuleEntity
import com.ptut.expense.data.local.entity.SharedGroupEntity
import com.ptut.expense.data.local.entity.SharedPaymentEntity
import com.ptut.expense.data.local.entity.SubCategoryEntity
import com.ptut.expense.data.local.entity.TransactionEntity
import com.ptut.expense.domain.backup.BackupCategory
import com.ptut.expense.domain.backup.BackupExpenseVersion
import com.ptut.expense.domain.backup.BackupGroup
import com.ptut.expense.domain.backup.BackupLink
import com.ptut.expense.domain.backup.BackupMember
import com.ptut.expense.domain.backup.BackupPayment
import com.ptut.expense.domain.backup.BackupPerson
import com.ptut.expense.domain.backup.BackupRepeat
import com.ptut.expense.domain.backup.BackupShare
import com.ptut.expense.domain.backup.BackupShared
import com.ptut.expense.domain.backup.BackupShopRule
import com.ptut.expense.domain.backup.BackupSubCategory
import com.ptut.expense.domain.backup.BackupTransaction
import com.ptut.expense.domain.backup.LedgerArchive
import com.ptut.expense.domain.backup.LedgerBackup
import com.ptut.expense.domain.backup.MergeReport
import kotlin.time.Clock

/**
 * FR-22 over Room. It maps rows to the file shape and back, and leaves every rule about what a
 * duplicate is to [LedgerMergeDao] — the same two keys the importer dedupes on, so a row that
 * arrived by scan and by file is one row either way.
 */
class RoomLedgerArchive(
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao,
    private val mergeDao: LedgerMergeDao,
    /** #133. Null where only the ledger is backed up, as in the archive's own tests. */
    private val sharedDao: SharedDao? = null,
    /** #215. Null for the same reason: categories, sub-categories and shop rules. */
    private val catalogDao: CatalogBackupDao? = null,
    private val clock: Clock = Clock.System,
) : LedgerArchive {

    override suspend fun export(): String = LedgerBackup.encode(
        LedgerBackup(
            format = LedgerBackup.FORMAT,
            exportedAtEpochMillis = clock.now().toEpochMilliseconds(),
            transactions = transactionDao.all().map(TransactionEntity::toBackup),
            shared = sharedDao?.let { exportShared(it) },
            categories = catalogDao?.categories().orEmpty().map(CategoryEntity::toBackup),
            subCategories = catalogDao?.subCategories().orEmpty().map { BackupSubCategory(it.id, it.categoryId, it.name) },
            shopRules = catalogDao?.rules().orEmpty().map { BackupShopRule(it.id, it.counterparty, it.categoryId, it.createdAtEpochMillis) },
        ),
    )

    override suspend fun restore(json: String): MergeReport {
        val backup = LedgerBackup.decode(json)
        // Categories before rows, so a row filed under one the reader made keeps it (#215).
        val known = catalogDao?.restore(
            categories = backup.categories.map(BackupCategory::toEntity),
            subCategories = backup.subCategories.map { SubCategoryEntity(it.id, it.categoryId, it.name) },
            rules = backup.shopRules.map { RuleEntity(it.id, it.counterparty, it.categoryId, it.createdAtEpochMillis) },
        ) ?: categoryDao.ids().toSet()
        val rows = backup.transactions.map { transactionDao.withCheckedSubCategory(it.toEntity(known)) }
        val lost = backup.transactions.filter { it.categoryId != null && it.categoryId !in known }.map { it.id }.toSet()
        val added = mergeDao.merge(rows)
        // After the transactions, so a link finds the row it points at.
        val groupsAdded = backup.shared?.let { shared -> sharedDao?.let { restoreShared(it, shared, known) } } ?: 0
        return MergeReport(
            added = added.size,
            alreadyHeld = rows.size - added.size,
            groupsAdded = groupsAdded,
            lostCategory = added.count { it in lost },
        )
    }

    private suspend fun exportShared(dao: SharedDao): BackupShared {
        val groups = dao.localGroups()
        val ids = groups.map { it.id }
        return BackupShared(
            people = dao.people().map { BackupPerson(it.id, it.name, it.archived) },
            groups = groups.map { BackupGroup(it.id, it.name, it.type, it.icon, it.simplifyDebts, it.createdAtEpochMillis, it.meMemberId) },
            members = dao.membersIn(ids).map { BackupMember(it.id, it.groupId, it.name, it.personId, it.archived, it.createdAtEpochMillis, it.promptPayId) },
            versions = dao.versionsIn(ids).map(ExpenseVersionEntity::toBackup),
            shares = dao.sharesIn(ids).map { BackupShare(it.versionId, it.memberId, it.weight, it.amountMinor, it.amountSatang) },
            payments = dao.paymentsIn(ids).map(SharedPaymentEntity::toBackup),
            links = dao.linksIn(ids).map { BackupLink(it.expenseId, it.transactionId) },
            repeats = dao.repeatsIn(ids).map {
                BackupRepeat(
                    it.id, it.groupId, it.kind, it.frequency, it.startDate, it.endKind, it.endDate, it.endTimes, it.remind, it.active,
                    it.lastAdded, it.description, it.categoryId, it.currency, it.amountMinor, it.rateUnscaled, it.rateScale,
                    it.payerMemberId, it.splitMode, it.weights, it.createdAtEpochMillis,
                )
            },
        )
    }

    private suspend fun restoreShared(dao: SharedDao, shared: BackupShared, knownCategories: Set<String>): Int = dao.restoreShared(
        people = shared.people.map { PersonEntity(it.id, it.name, it.archived) },
        groups = shared.groups.map { SharedGroupEntity(it.id, it.name, it.type, it.icon, it.simplifyDebts, it.createdAtEpochMillis, it.meMemberId) },
        members = shared.members.map { GroupMemberEntity(it.id, it.groupId, it.name, it.personId, it.archived, it.createdAtEpochMillis, promptPayId = it.promptPayId) },
        versions = shared.versions.map { it.toEntity(knownCategories) },
        shares = shared.shares.map { ExpenseShareEntity(it.versionId, it.memberId, it.weight, it.amountMinor, it.amountSatang) },
        payments = shared.payments.map { SharedPaymentEntity(it.id, it.groupId, it.fromMemberId, it.toMemberId, it.amountSatang, it.method, it.occurredAtEpochMillis, it.authorMemberId, it.createdAtEpochMillis) },
        links = shared.links.map { ExpenseLinkEntity(it.expenseId, it.transactionId) },
        repeats = shared.repeats.map {
            RepeatRuleEntity(
                it.id, it.groupId, it.kind, it.frequency, it.startDate, it.endKind, it.endDate, it.endTimes, it.remind, it.active,
                it.lastAdded, it.description, it.categoryId, it.currency, it.amountMinor, it.rateUnscaled, it.rateScale,
                it.payerMemberId, it.splitMode, it.weights, it.createdAtEpochMillis,
            )
        },
    )

    /**
     * A category this install does not hold becomes no category, which is what the column's own
     * foreign key does when one is deleted: the money was still spent. Letting it through would
     * abort the whole merge on a constraint, losing a file that is otherwise fine over one row.
     * [restore] does the same for a sub-category, through the DAO's check. [LedgerBackup.decode]
     * has already refused a file with an unknown direction or source.
     */
    private fun BackupTransaction.toEntity(knownCategories: Set<String>) = TransactionEntity(
        id = id,
        occurredAtEpochMillis = occurredAtEpochMillis,
        amountMinor = amountMinor,
        direction = direction,
        categoryId = categoryId?.takeIf { it in knownCategories },
        subCategoryId = subCategoryId,
        counterparty = counterparty,
        source = source,
        reference = reference,
        editedAt = editedAt,
        memo = memo,
        sourceFolder = sourceFolder,
        ruleId = ruleId,
    )
}

private fun CategoryEntity.toBackup() = BackupCategory(
    id = id,
    name = name,
    icon = icon,
    direction = direction,
    color = color,
    sortOrder = sortOrder,
)

private fun BackupCategory.toEntity() = CategoryEntity(
    id = id,
    name = name,
    icon = icon,
    direction = direction,
    color = color,
    sortOrder = sortOrder,
)

private fun TransactionEntity.toBackup() = BackupTransaction(
    id = id,
    occurredAtEpochMillis = occurredAtEpochMillis,
    amountMinor = amountMinor,
    direction = direction,
    categoryId = categoryId,
    counterparty = counterparty,
    source = source,
    reference = reference,
    editedAt = editedAt,
    sourceFolder = sourceFolder,
    subCategoryId = subCategoryId,
    memo = memo,
    ruleId = ruleId,
)

private fun ExpenseVersionEntity.toBackup() = BackupExpenseVersion(
    versionId = versionId,
    expenseId = expenseId,
    groupId = groupId,
    version = version,
    description = description,
    occurredAtEpochMillis = occurredAtEpochMillis,
    categoryId = categoryId,
    subCategoryId = subCategoryId,
    note = note,
    currency = currency,
    amountMinor = amountMinor,
    rateUnscaled = rateUnscaled,
    rateScale = rateScale,
    amountSatang = amountSatang,
    payerMemberId = payerMemberId,
    splitMode = splitMode,
    authorMemberId = authorMemberId,
    createdAtEpochMillis = createdAtEpochMillis,
    removed = removed,
    cause = cause,
)

/** A category this install lacks becomes none, as it does for a transaction above. */
private fun BackupExpenseVersion.toEntity(knownCategories: Set<String>) = ExpenseVersionEntity(
    versionId = versionId,
    expenseId = expenseId,
    groupId = groupId,
    version = version,
    description = description,
    occurredAtEpochMillis = occurredAtEpochMillis,
    categoryId = categoryId?.takeIf { it in knownCategories },
    subCategoryId = subCategoryId.takeIf { categoryId in knownCategories },
    note = note,
    currency = currency,
    amountMinor = amountMinor,
    rateUnscaled = rateUnscaled,
    rateScale = rateScale,
    amountSatang = amountSatang,
    payerMemberId = payerMemberId,
    splitMode = splitMode,
    authorMemberId = authorMemberId,
    createdAtEpochMillis = createdAtEpochMillis,
    removed = removed,
    cause = cause,
)

private fun SharedPaymentEntity.toBackup() = BackupPayment(
    id = id,
    groupId = groupId,
    fromMemberId = fromMemberId,
    toMemberId = toMemberId,
    amountSatang = amountSatang,
    method = method,
    occurredAtEpochMillis = occurredAtEpochMillis,
    authorMemberId = authorMemberId,
    createdAtEpochMillis = createdAtEpochMillis,
)
