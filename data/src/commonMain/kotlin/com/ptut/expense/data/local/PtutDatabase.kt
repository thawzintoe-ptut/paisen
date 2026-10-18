package com.ptut.expense.data.local

import androidx.room.AutoMigration
import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import com.ptut.expense.data.local.dao.CatalogBackupDao
import com.ptut.expense.data.local.dao.CategoryDao
import com.ptut.expense.data.local.dao.ImportRunDao
import com.ptut.expense.data.local.dao.ImportedSlipDao
import com.ptut.expense.data.local.dao.LedgerMergeDao
import com.ptut.expense.data.local.dao.QueuedSlipDao
import com.ptut.expense.data.local.dao.RuleDao
import com.ptut.expense.data.local.dao.SharedDao
import com.ptut.expense.data.local.dao.SlipConfirmationDao
import com.ptut.expense.data.local.dao.SubCategoryDao
import com.ptut.expense.data.local.dao.TransactionDao
import com.ptut.expense.data.local.dao.WatchedFolderDao
import com.ptut.expense.data.local.entity.CategoryEntity
import com.ptut.expense.data.local.entity.CurrencyRateEntity
import com.ptut.expense.data.local.entity.DeletedBuiltInEntity
import com.ptut.expense.data.local.entity.ExpenseLinkEntity
import com.ptut.expense.data.local.entity.ExpenseShareEntity
import com.ptut.expense.data.local.entity.ExpenseVersionEntity
import com.ptut.expense.data.local.entity.GroupMemberEntity
import com.ptut.expense.data.local.entity.ImportRunEntity
import com.ptut.expense.data.local.entity.ImportedSlipEntity
import com.ptut.expense.data.local.entity.PaymentRemovalEntity
import com.ptut.expense.data.local.entity.PersonEntity
import com.ptut.expense.data.local.entity.QueuedSlipEntity
import com.ptut.expense.data.local.entity.RepeatRuleEntity
import com.ptut.expense.data.local.entity.RuleEntity
import com.ptut.expense.data.local.entity.SharedGroupEntity
import com.ptut.expense.data.local.entity.SharedPaymentEntity
import com.ptut.expense.data.local.entity.SubCategoryEntity
import com.ptut.expense.data.local.entity.SyncOutboxEntity
import com.ptut.expense.data.local.entity.TransactionEntity
import com.ptut.expense.data.local.entity.WatchedFolderEntity

/**
 * SQLite is the single source of truth and the UI observes it. The importer is the only writer;
 * screens are readers, which is why no DAO exposes a refresh.
 *
 * `exportSchema` stays on: the committed JSON under `composeApp/schemas/` is the artefact that
 * proves no money column is REAL.
 */
@Database(
    entities = [
        TransactionEntity::class,
        CategoryEntity::class,
        RuleEntity::class,
        QueuedSlipEntity::class,
        ImportedSlipEntity::class,
        ImportRunEntity::class,
        WatchedFolderEntity::class,
        SubCategoryEntity::class,
        SharedGroupEntity::class,
        GroupMemberEntity::class,
        PersonEntity::class,
        ExpenseVersionEntity::class,
        ExpenseShareEntity::class,
        SharedPaymentEntity::class,
        ExpenseLinkEntity::class,
        CurrencyRateEntity::class,
        SyncOutboxEntity::class,
        RepeatRuleEntity::class,
        DeletedBuiltInEntity::class,
        PaymentRemovalEntity::class,
    ],
    version = 18,
    // Schema 2 adds the nullable transactions.editedAt, and schema 4 the nullable memo and
    // slipAmountMinor beside it. Schema 5 adds the watched_folder table whole. A nullable column
    // with no default, and a new table, are the shapes Room migrates on its own. Schema 6 is
    // both at once: the sub_category table, and the nullable transactions.subCategoryId.
    // Schema 7 adds category.direction, which carries a default and so migrates the same way —
    // with one row the default gets wrong, which the spec repairs. Schema 8 adds category.color
    // and category.sortOrder, both with defaults that are right for every row already there: the
    // theme primary is what they all wore, and 0 leaves them sorting by name as before. Schema 9
    // adds the nullable transactions.filedAt, the editedAt shape again. Schema 12 adds v2's shared
    // cost tables whole (#133), the watched_folder shape eight times over. Schema 13 adds sync
    // state to groups and members, all nullable or defaulted, and the sync_outbox table (#189).
    // Schema 16 adds the deleted_built_in table whole (#219), and schema 17 the payment_removal table (#218).
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5),
        AutoMigration(from = 5, to = 6),
        AutoMigration(from = 6, to = 7, spec = IncomeKeepsItsDirection::class),
        AutoMigration(from = 7, to = 8),
        AutoMigration(from = 8, to = 9),
        AutoMigration(from = 9, to = 10),
        AutoMigration(from = 10, to = 11),
        AutoMigration(from = 11, to = 12),
        AutoMigration(from = 12, to = 13),
        AutoMigration(from = 13, to = 14),
        AutoMigration(from = 14, to = 15),
        AutoMigration(from = 15, to = 16),
        AutoMigration(from = 16, to = 17),
        AutoMigration(from = 17, to = 18),
    ],
    exportSchema = true,
)
@ConstructedBy(PtutDatabaseConstructor::class)
abstract class PtutDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao

    abstract fun watchedFolderDao(): WatchedFolderDao

    abstract fun categoryDao(): CategoryDao

    abstract fun subCategoryDao(): SubCategoryDao

    abstract fun ruleDao(): RuleDao

    abstract fun queuedSlipDao(): QueuedSlipDao

    abstract fun importedSlipDao(): ImportedSlipDao

    abstract fun importRunDao(): ImportRunDao

    abstract fun slipConfirmationDao(): SlipConfirmationDao

    abstract fun ledgerMergeDao(): LedgerMergeDao

    abstract fun sharedDao(): SharedDao

    abstract fun catalogBackupDao(): CatalogBackupDao
}

/** KSP writes the actual for each target; there is nothing to implement by hand. */
@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object PtutDatabaseConstructor : RoomDatabaseConstructor<PtutDatabase> {
    override fun initialize(): PtutDatabase
}
