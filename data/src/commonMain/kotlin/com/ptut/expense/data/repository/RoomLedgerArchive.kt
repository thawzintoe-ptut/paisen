package com.ptut.expense.data.repository

import com.ptut.expense.data.local.dao.CatalogBackupDao
import com.ptut.expense.data.local.dao.CategoryDao
import com.ptut.expense.data.local.dao.LedgerMergeDao
import com.ptut.expense.data.local.dao.PlayDao
import com.ptut.expense.data.local.dao.SharedDao
import com.ptut.expense.data.local.dao.TransactionDao
import com.ptut.expense.data.local.dao.withCheckedSubCategory
import com.ptut.expense.data.local.entity.CategoryEntity
import com.ptut.expense.data.local.entity.ExpenseLinkEntity
import com.ptut.expense.data.local.entity.ExpenseShareEntity
import com.ptut.expense.data.local.entity.ExpenseVersionEntity
import com.ptut.expense.data.local.entity.GroupMemberEntity
import com.ptut.expense.data.local.entity.PersonEntity
import com.ptut.expense.data.local.entity.PlayDayEntity
import com.ptut.expense.data.local.entity.PlayGoalDayEntity
import com.ptut.expense.data.local.entity.PlayGoalDepositEntity
import com.ptut.expense.data.local.entity.PlayGoalEntity
import com.ptut.expense.data.local.entity.PlayMonthEntity
import com.ptut.expense.data.local.entity.PlayStampEntity
import com.ptut.expense.data.local.entity.PlayWeekEntity
import com.ptut.expense.data.local.entity.RepeatRuleEntity
import com.ptut.expense.data.local.entity.RuleEntity
import com.ptut.expense.data.local.entity.SharedGroupEntity
import com.ptut.expense.data.local.entity.SharedPaymentEntity
import com.ptut.expense.data.local.entity.SubCategoryEntity
import com.ptut.expense.data.local.entity.TransactionEntity
import com.ptut.expense.domain.Direction
import com.ptut.expense.domain.Home
import com.ptut.expense.domain.HomeChoice
import com.ptut.expense.domain.HomeMoney
import com.ptut.expense.domain.backup.BackupCategory
import com.ptut.expense.domain.backup.BackupDeposit
import com.ptut.expense.domain.backup.BackupExpenseVersion
import com.ptut.expense.domain.backup.BackupGoal
import com.ptut.expense.domain.backup.BackupGoalDay
import com.ptut.expense.domain.backup.BackupGroup
import com.ptut.expense.domain.backup.BackupLink
import com.ptut.expense.domain.backup.BackupMember
import com.ptut.expense.domain.backup.BackupPayment
import com.ptut.expense.domain.backup.BackupPerson
import com.ptut.expense.domain.backup.BackupPlay
import com.ptut.expense.domain.backup.BackupPlayDay
import com.ptut.expense.domain.backup.BackupPlayMonth
import com.ptut.expense.domain.backup.BackupPlayWeek
import com.ptut.expense.domain.backup.BackupPreview
import com.ptut.expense.domain.backup.BackupProblem
import com.ptut.expense.domain.backup.BackupRepeat
import com.ptut.expense.domain.backup.BackupShare
import com.ptut.expense.domain.backup.BackupShared
import com.ptut.expense.domain.backup.BackupShopRule
import com.ptut.expense.domain.backup.BackupStamp
import com.ptut.expense.domain.backup.BackupSubCategory
import com.ptut.expense.domain.backup.BackupTransaction
import com.ptut.expense.domain.backup.BackupUnreadable
import com.ptut.expense.domain.backup.LedgerArchive
import com.ptut.expense.domain.backup.LedgerBackup
import com.ptut.expense.domain.backup.MergeReport
import com.ptut.expense.domain.backup.preview
import com.ptut.expense.domain.home.HomeBook
import com.ptut.expense.domain.shared.Currencies
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
    /** #46: an empty ledger takes a file's home through it. Null where a test binds none. */
    private val homeBook: HomeBook? = null,
    /** #82: play's facts. Null where only the ledger is backed up. */
    private val playDao: PlayDao? = null,
) : LedgerArchive {

    override suspend fun export(): String = LedgerBackup.encode(
        LedgerBackup(
            format = LedgerBackup.FORMAT,
            exportedAtEpochMillis = clock.now().toEpochMilliseconds(),
            transactions = transactionDao.all().map(TransactionEntity::toBackup),
            shared = sharedDao?.let { exportShared(it) },
            categories = catalogDao?.categories().orEmpty().map(CategoryEntity::toBackup),
            subCategories = catalogDao?.subCategories().orEmpty().map { BackupSubCategory(id = it.id, categoryId = it.categoryId, name = it.name) },
            shopRules = catalogDao?.rules().orEmpty().map {
                BackupShopRule(
                    id = it.id,
                    counterparty = it.counterparty,
                    categoryId = it.categoryId,
                    createdAtEpochMillis = it.createdAtEpochMillis,
                    displayName = it.displayName,
                )
            },
            homeCurrency = Home.money.code,
            homeZone = Home.choice.value.zoneId,
            play = playDao?.let { exportPlay(it) },
        ),
    )

    override suspend fun preview(json: String): BackupPreview {
        val backup = LedgerBackup.decode(json)
        refuseOtherHome(backup)
        return backup.preview()
    }

    override suspend fun restore(json: String): MergeReport {
        val backup = LedgerBackup.decode(json)
        // A failure part way must not leave the file's rows under the old home, which refuses the retry.
        lateinit var report: MergeReport
        mergeDao.inOneWrite { report = restoreAll(backup) }
        return report
    }

    private suspend fun restoreAll(backup: LedgerBackup): MergeReport {
        refuseOtherHome(backup)
        // #46: decided before the merge, which is what stops the ledger being empty.
        val fileHome = backup.home()
        val takesFileHome = fileHome != Home.choice.value && homeBook?.isEmpty() == true
        // Categories before rows, so a row filed under one the reader made keeps it (#215).
        val known = catalogDao?.restore(
            categories = backup.categories.map(BackupCategory::toEntity),
            subCategories = backup.subCategories.map { SubCategoryEntity(id = it.id, categoryId = it.categoryId, name = it.name) },
            // #26: the file keeps no direction on a rule, but a money-in rule always files under a money-in
            // category, and the category keeps its own.
            rules = backup.shopRules.map {
                val direction = backup.categories.firstOrNull { category -> category.id == it.categoryId }?.direction ?: Direction.Expense.name
                RuleEntity(
                    id = it.id,
                    counterparty = it.counterparty,
                    categoryId = it.categoryId,
                    createdAtEpochMillis = it.createdAtEpochMillis,
                    displayName = it.displayName,
                    direction = direction,
                )
            },
        ) ?: categoryDao.ids().toSet()
        val rows = backup.transactions.map { transactionDao.withCheckedSubCategory(it.toEntity(known, backup.homeCode)) }
        val lost = backup.transactions.filter { it.categoryId != null && it.categoryId !in known }.map { it.id }.toSet()
        val added = mergeDao.merge(rows)
        // After the transactions, so a link finds the row it points at.
        val groupsAdded = backup.shared?.let { shared -> sharedDao?.let { restoreShared(it, shared, known) } } ?: 0
        // #82: a clash keeps the phone's own row, so a restore never undoes a stamp or a turned card.
        backup.play?.let { play -> playDao?.let { restorePlay(it, play) } }
        // Last of all: the new home rebuilds the shell, and nothing may still be merging under the old one.
        if (takesFileHome) homeBook?.adopt(fileHome)
        return MergeReport(
            added = added.size,
            alreadyHeld = rows.size - added.size,
            groupsAdded = groupsAdded,
            lostCategory = added.count { it in lost },
        )
    }

    private suspend fun exportPlay(dao: PlayDao) = BackupPlay(
        days = dao.allDays().map { BackupPlayDay(it.date, it.clearedAtEpochMillis, it.answeredAtEpochMillis, it.questionKey, it.answeredRight, it.pickedFirst) },
        weeks = dao.allWeeks().map {
            BackupPlayWeek(it.monday, it.paisenGuessMinor, it.guessMinor, it.totalMinor, it.challengeCategoryId, it.challengeLevel, it.challengeLimitMinor)
        },
        months = dao.allMonths().map { BackupPlayMonth(it.month, it.storySeenAtEpochMillis, it.sealedAtEpochMillis) },
        stamps = dao.allStamps().map { BackupStamp(it.id, it.kind, it.ref, it.earnedOn, it.revealedAtEpochMillis) },
        goals = dao.allGoals().map { BackupGoal(it.id, it.name, it.targetMinor, it.currency, it.startDate, it.createdAtEpochMillis, it.archivedAtEpochMillis, it.savingsAccount) },
        goalDays = dao.allGoalDays().map { BackupGoalDay(it.goalId, it.date, it.usualMinor, it.atEpochMillis) },
        deposits = dao.allDeposits().map { BackupDeposit(it.id, it.goalId, it.amountMinor, it.date, it.atEpochMillis, it.source, it.account) },
    )

    private suspend fun restorePlay(dao: PlayDao, play: BackupPlay) = dao.restore(
        days = play.days.map { PlayDayEntity(it.date, it.clearedAtEpochMillis, it.answeredAtEpochMillis, it.questionKey, it.answeredRight, it.pickedFirst) },
        weeks = play.weeks.map {
            PlayWeekEntity(it.monday, it.paisenGuessMinor, it.guessMinor, it.totalMinor, it.challengeCategoryId, it.challengeLevel, it.challengeLimitMinor)
        },
        months = play.months.map { PlayMonthEntity(it.month, it.storySeenAtEpochMillis, it.sealedAtEpochMillis) },
        stamps = play.stamps.map { PlayStampEntity(it.id, it.kind, it.ref, it.earnedOn, it.revealedAtEpochMillis) },
        goals = play.goals.map { PlayGoalEntity(it.id, it.name, it.targetMinor, it.currency, it.startDate, it.createdAtEpochMillis, it.archivedAtEpochMillis, it.savingsAccount) },
        goalDays = play.goalDays.map { PlayGoalDayEntity(it.goalId, it.date, it.usualMinor, it.atEpochMillis) },
        deposits = play.deposits.map { PlayGoalDepositEntity(it.id, it.goalId, it.amountMinor, it.date, it.atEpochMillis, it.source, it.account) },
    )

    /** #46: amounts in other money cannot join a ledger with rows; only an empty one can change home. */
    private suspend fun refuseOtherHome(backup: LedgerBackup) {
        if (backup.homeCode == Home.money.code) return
        if (homeBook?.isEmpty() != true) throw BackupUnreadable(BackupProblem.OtherHome(backup.homeCode, Home.money.code))
    }

    private fun LedgerBackup.home(): HomeChoice = HomeChoice.of(Currencies.of(homeCode).toHomeMoney(), homeZone ?: HomeChoice.BANGKOK)

    private suspend fun exportShared(dao: SharedDao): BackupShared {
        val groups = dao.localGroups()
        val ids = groups.map { it.id }
        return BackupShared(
            people = dao.people().map { BackupPerson(id = it.id, name = it.name, archived = it.archived) },
            groups = groups.map {
                BackupGroup(
                    id = it.id,
                    name = it.name,
                    type = it.type,
                    icon = it.icon,
                    simplifyDebts = it.simplifyDebts,
                    createdAtEpochMillis = it.createdAtEpochMillis,
                    meMemberId = it.meMemberId,
                    currency = it.currency,
                )
            },
            members = dao.membersIn(ids).map {
                BackupMember(
                    id = it.id,
                    groupId = it.groupId,
                    name = it.name,
                    personId = it.personId,
                    archived = it.archived,
                    createdAtEpochMillis = it.createdAtEpochMillis,
                    promptPayId = it.promptPayId,
                )
            },
            versions = dao.versionsIn(ids).map(ExpenseVersionEntity::toBackup),
            shares = dao.sharesIn(ids).map {
                BackupShare(
                    versionId = it.versionId,
                    memberId = it.memberId,
                    weight = it.weight,
                    amountMinor = it.amountMinor,
                    amountSatang = it.amountSatang,
                )
            },
            payments = dao.paymentsIn(ids).map(SharedPaymentEntity::toBackup),
            links = dao.linksIn(ids).map { BackupLink(expenseId = it.expenseId, transactionId = it.transactionId) },
            repeats = dao.repeatsIn(ids).map {
                BackupRepeat(
                    id = it.id,
                    groupId = it.groupId,
                    kind = it.kind,
                    frequency = it.frequency,
                    startDate = it.startDate,
                    endKind = it.endKind,
                    endDate = it.endDate,
                    endTimes = it.endTimes,
                    remind = it.remind,
                    active = it.active,
                    lastAdded = it.lastAdded,
                    description = it.description,
                    categoryId = it.categoryId,
                    currency = it.currency,
                    amountMinor = it.amountMinor,
                    rateUnscaled = it.rateUnscaled,
                    rateScale = it.rateScale,
                    payerMemberId = it.payerMemberId,
                    splitMode = it.splitMode,
                    weights = it.weights,
                    createdAtEpochMillis = it.createdAtEpochMillis,
                )
            },
        )
    }

    private suspend fun restoreShared(dao: SharedDao, shared: BackupShared, knownCategories: Set<String>): Int = dao.restoreShared(
        people = shared.people.map { PersonEntity(id = it.id, name = it.name, archived = it.archived) },
        groups = shared.groups.map {
            SharedGroupEntity(
                id = it.id,
                name = it.name,
                type = it.type,
                icon = it.icon,
                simplifyDebts = it.simplifyDebts,
                createdAtEpochMillis = it.createdAtEpochMillis,
                meMemberId = it.meMemberId,
                currency = it.currency ?: HomeMoney.Baht.code,
            )
        },
        members = shared.members.map {
            GroupMemberEntity(
                id = it.id,
                groupId = it.groupId,
                name = it.name,
                personId = it.personId,
                archived = it.archived,
                createdAtEpochMillis = it.createdAtEpochMillis,
                promptPayId = it.promptPayId,
            )
        },
        versions = shared.versions.map { it.toEntity(knownCategories) },
        shares = shared.shares.map {
            ExpenseShareEntity(
                versionId = it.versionId,
                memberId = it.memberId,
                weight = it.weight,
                amountMinor = it.amountMinor,
                amountSatang = it.amountSatang,
            )
        },
        payments = shared.payments.map {
            SharedPaymentEntity(
                id = it.id,
                groupId = it.groupId,
                fromMemberId = it.fromMemberId,
                toMemberId = it.toMemberId,
                amountSatang = it.amountSatang,
                method = it.method,
                occurredAtEpochMillis = it.occurredAtEpochMillis,
                authorMemberId = it.authorMemberId,
                createdAtEpochMillis = it.createdAtEpochMillis,
            )
        },
        links = shared.links.map { ExpenseLinkEntity(expenseId = it.expenseId, transactionId = it.transactionId) },
        repeats = shared.repeats.map {
            RepeatRuleEntity(
                id = it.id,
                groupId = it.groupId,
                kind = it.kind,
                frequency = it.frequency,
                startDate = it.startDate,
                endKind = it.endKind,
                endDate = it.endDate,
                endTimes = it.endTimes,
                remind = it.remind,
                active = it.active,
                lastAdded = it.lastAdded,
                description = it.description,
                categoryId = it.categoryId,
                currency = it.currency,
                amountMinor = it.amountMinor,
                rateUnscaled = it.rateUnscaled,
                rateScale = it.rateScale,
                payerMemberId = it.payerMemberId,
                splitMode = it.splitMode,
                weights = it.weights,
                createdAtEpochMillis = it.createdAtEpochMillis,
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
    private fun BackupTransaction.toEntity(knownCategories: Set<String>, homeCode: String) = TransactionEntity(
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
        planned = planned,
        currency = currency ?: homeCode,
        originalMinor = originalMinor,
        rateUnscaled = rateUnscaled,
        rateScale = rateScale,
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
    planned = planned,
    // #46: only a row in other money names its currency; the file's home covers the rest.
    currency = currency.takeIf { originalMinor != null },
    originalMinor = originalMinor,
    rateUnscaled = rateUnscaled,
    rateScale = rateScale,
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
