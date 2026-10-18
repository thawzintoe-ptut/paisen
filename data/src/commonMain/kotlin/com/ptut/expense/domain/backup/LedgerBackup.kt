package com.ptut.expense.domain.backup

import com.ptut.expense.domain.Direction
import com.ptut.expense.domain.TransactionSource
import com.ptut.expense.locale.AppText
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * FR-22. The whole ledger as one file, so it can leave the device and come back.
 *
 * [format] carries no default, so a JSON file that is not one of ours fails to parse at all
 * rather than passing as format 1 with an empty ledger. A file that *is* ours and newer parses,
 * and is then refused by version — a build that half-understands a file puts money in the wrong
 * place, and there is no later screen where that shows up as a mistake rather than as a total.
 */
@Serializable
data class LedgerBackup(
    val format: Int,
    val exportedAtEpochMillis: Long,
    val transactions: List<BackupTransaction>,
    /** Format 2 (#133): people and the groups that live only on this phone. Absent in format 1. */
    val shared: BackupShared? = null,
    /**
     * Format 3 (#215): the reader's own categories and the built-ins as they stand, so a row filed
     * under one it made comes back filed. Empty in formats 1 and 2.
     */
    val categories: List<BackupCategory> = emptyList(),
    val subCategories: List<BackupSubCategory> = emptyList(),
    val shopRules: List<BackupShopRule> = emptyList(),
) {
    companion object {
        const val FORMAT: Int = 3

        /** Formats 1 and 2 are format 3 with fewer lists, so all three still import. */
        val READABLE: IntRange = 1..FORMAT

        /**
         * Lenient about fields this build does not know so a file from a later format still
         * parses far enough to have its version read and be refused by name.
         */
        private val codec = Json {
            ignoreUnknownKeys = true
            prettyPrint = true
        }

        fun encode(backup: LedgerBackup): String = codec.encodeToString(backup)

        /** @throws BackupUnreadable when the text is not a ledger file this build can place. */
        fun decode(text: String): LedgerBackup {
            val backup = try {
                codec.decodeFromString<LedgerBackup>(text)
            } catch (failure: SerializationException) {
                throw BackupUnreadable(AppText.copy.phrases.backupNotReadable)
            }
            // Refused, not read as far as it goes. A newer format means fields this build does not
            // know about, and a total missing a field it never saw looks like a correct total.
            if (backup.format !in READABLE) {
                throw BackupUnreadable(AppText.copy.phrases.backupOtherVersion(backup.format))
            }
            // The whole file is refused over one unreadable direction rather than defaulted.
            // Guessing turns income into spend, and that shows only as a smaller month.
            backup.transactions.forEach { row ->
                row.direction.checked(Direction.entries.map(Direction::name), "direction")
                row.source.checked(TransactionSource.entries.map(TransactionSource::name), "source")
            }
            return backup
        }

        private fun String.checked(allowed: List<String>, field: String) {
            if (this !in allowed) throw BackupUnreadable(AppText.copy.phrases.backupUnknownValue(field, this))
        }
    }
}

/** Mirrors the `category` row: [direction] is the enum's name, [color] a tint's stored name. */
@Serializable
data class BackupCategory(
    val id: String,
    val name: String,
    val icon: String,
    val direction: String,
    val color: String,
    val sortOrder: Int,
)

@Serializable
data class BackupSubCategory(val id: String, val categoryId: String, val name: String)

/** FR-12's remembered shop. The counterparty is the key one phone holds a single answer for. */
@Serializable
data class BackupShopRule(
    val id: String,
    val counterparty: String,
    val categoryId: String,
    val createdAtEpochMillis: Long,
)

/**
 * #220: what a file holds, read before anything is merged, so the reader can say no.
 * [oldestEpochMillis] is null when the file holds no transactions.
 */
data class BackupPreview(
    val transactions: Int,
    val oldestEpochMillis: Long?,
    val groups: Int,
    val categories: Int,
    val subCategories: Int,
    val shopRules: Int,
)

fun LedgerBackup.preview(): BackupPreview = BackupPreview(
    transactions = transactions.size,
    oldestEpochMillis = transactions.minOfOrNull { it.occurredAtEpochMillis },
    groups = shared?.groups?.size ?: 0,
    categories = categories.size,
    subCategories = subCategories.size,
    shopRules = shopRules.size,
)

/**
 * v2's records, mirroring their tables. A group shared through Firestore is left out: it comes
 * back when you sign in, and a second copy from a file could only disagree with it (#189). The
 * links to your own transactions are kept, since they never leave this phone otherwise.
 */
@Serializable
data class BackupShared(
    val people: List<BackupPerson> = emptyList(),
    val groups: List<BackupGroup> = emptyList(),
    val members: List<BackupMember> = emptyList(),
    val versions: List<BackupExpenseVersion> = emptyList(),
    val shares: List<BackupShare> = emptyList(),
    val payments: List<BackupPayment> = emptyList(),
    val links: List<BackupLink> = emptyList(),
    /** #182's repeats, as their table holds them. */
    val repeats: List<BackupRepeat> = emptyList(),
)

@Serializable
data class BackupPerson(val id: String, val name: String, val archived: Boolean)

@Serializable
data class BackupGroup(
    val id: String,
    val name: String,
    val type: String,
    val icon: String,
    val simplifyDebts: Boolean,
    val createdAtEpochMillis: Long,
    val meMemberId: String? = null,
)

@Serializable
data class BackupMember(
    val id: String,
    val groupId: String,
    val name: String,
    val personId: String? = null,
    val archived: Boolean,
    val createdAtEpochMillis: Long,
    val promptPayId: String? = null,
)

/** Amounts are integer minor units and the rate an exact decimal, as the columns hold them (p36). */
@Serializable
data class BackupExpenseVersion(
    val versionId: String,
    val expenseId: String,
    val groupId: String,
    val version: Int,
    val description: String,
    val occurredAtEpochMillis: Long,
    val categoryId: String? = null,
    val subCategoryId: String? = null,
    val note: String? = null,
    val currency: String,
    val amountMinor: Long,
    val rateUnscaled: Long? = null,
    val rateScale: Int? = null,
    val amountSatang: Long,
    val payerMemberId: String,
    val splitMode: String,
    val authorMemberId: String? = null,
    val createdAtEpochMillis: Long,
    val removed: Boolean,
    val cause: String? = null,
)

@Serializable
data class BackupShare(val versionId: String, val memberId: String, val weight: Long, val amountMinor: Long, val amountSatang: Long)

@Serializable
data class BackupPayment(
    val id: String,
    val groupId: String,
    val fromMemberId: String,
    val toMemberId: String,
    val amountSatang: Long,
    val method: String,
    val occurredAtEpochMillis: Long,
    val authorMemberId: String? = null,
    val createdAtEpochMillis: Long,
)

@Serializable
data class BackupLink(val expenseId: String, val transactionId: String)

@Serializable
data class BackupRepeat(
    val id: String,
    val groupId: String,
    val kind: String,
    val frequency: String,
    val startDate: String,
    val endKind: String,
    val endDate: String? = null,
    val endTimes: Int? = null,
    val remind: Boolean,
    val active: Boolean,
    val lastAdded: String? = null,
    val description: String,
    val categoryId: String? = null,
    val currency: String,
    val amountMinor: Long,
    val rateUnscaled: Long? = null,
    val rateScale: Int? = null,
    val payerMemberId: String,
    val splitMode: String,
    val weights: String,
    val createdAtEpochMillis: Long,
)

/**
 * FR-9 across the round trip: [amountMinor] is the integer count of satang, exactly as the column
 * holds it. A baht `Double` here would lose the satang the reader typed, and the grep guard from
 * \#4 reads Kotlin source rather than a file format, so nothing else would catch it.
 *
 * [reference] is the slip's own number and half of the dedupe key an import merges on, which is
 * why this mirrors the stored row rather than the domain model — the domain [
 * com.ptut.expense.domain.Transaction] does not carry it.
 */
@Serializable
data class BackupTransaction(
    val id: String,
    val occurredAtEpochMillis: Long,
    val amountMinor: Long,
    val direction: String,
    val categoryId: String? = null,
    val counterparty: String? = null,
    val source: String,
    val reference: String? = null,
    val editedAt: Long? = null,
    /** Added with schema 10. Optional, so a backup written before it still imports. */
    val sourceFolder: String? = null,
    /** Added with #110. Optional for the same reason; one this install does not hold is dropped. */
    val subCategoryId: String? = null,
    /** Format 3 (#215): the reader's note, which #210 also uses to name the row. */
    val memo: String? = null,
    /** Format 3: the shop rule that filed the row, so the rule's count comes back with it. */
    val ruleId: String? = null,
)
