package com.ptut.expense.domain.record

import com.ptut.expense.domain.HomeTime
import com.ptut.expense.domain.LedgerPeriod
import com.ptut.expense.domain.Transaction
import com.ptut.expense.domain.dateIn
import com.ptut.expense.domain.ledger.LedgerRepository
import com.ptut.expense.domain.periodsMovedByDeleting
import com.ptut.expense.domain.rules.RememberedMerchant
import com.ptut.expense.domain.rules.RuleRepository
import com.ptut.expense.domain.rules.forShop
import com.ptut.expense.domain.shared.SharedGroup
import com.ptut.expense.domain.shared.SharedRepository
import com.ptut.expense.domain.shared.groupHolding
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.datetime.TimeZone
import kotlin.time.Clock

/** #22: the reader says a payment was planned, or takes it back. Not a change to the money. */
class SetPlannedUseCase(private val ledger: LedgerRepository) {
    suspend operator fun invoke(transactionId: String, planned: Boolean) = ledger.setPlanned(transactionId, planned)
}

/**
 * FR-21: the totals a delete moves, on the row's own home day. A row from last week moves the
 * month and not the week, so it depends on the day the delete happens.
 */
class GetDeleteImpactUseCase(
    private val clock: Clock = Clock.System,
    private val zone: TimeZone = HomeTime,
) {
    operator fun invoke(occurredAtEpochMillis: Long): List<LedgerPeriod> =
        periodsMovedByDeleting(occurredAt = occurredAtEpochMillis.dateIn(zone), today = clock.now().dateIn(zone))
}

/** #186: the groups a row could be shared to, and the one that holds it now. */
data class ShareOptions(val groups: List<SharedGroup>, val holding: SharedGroup?)

/** [ShareOptions] for one row as the groups change. Null in a build without groups, which then draws as v1 does. */
class ObserveShareOptionsUseCase(private val shared: SharedRepository? = null) {
    operator fun invoke(transactionId: String): Flow<ShareOptions?> =
        shared?.snapshot?.map { ShareOptions(it.groups, it.groupHolding(transactionId)) } ?: flowOf(null)
}

/** The shop rules as one record reads them. */
class FilingRules(private val held: List<RememberedMerchant>) {
    /**
     * p16: the rule that filed [row], while the row still sits in that rule's category (#117). A
     * hand entry, a confirmed slip or an edited row has a different story.
     */
    fun filedBy(row: Transaction): RememberedMerchant? {
        if (row.counterparty == null) return null
        return held.firstOrNull { it.id == row.ruleId }?.takeIf { it.categoryId == row.categoryId }
    }

    /** FR-12: the rule [row]'s shop follows now, which files its next slip. Null for a row with no shop. */
    fun nextFor(row: Transaction): RememberedMerchant? = row.counterparty?.let { held.forShop(it) }
}

/**
 * The shop rules as they change, so a rule changed on another screen redraws the record (#117).
 * None where no rules table is bound, and the record then draws as it does for a hand entry.
 */
class ObserveFilingRulesUseCase(private val rules: RuleRepository? = null) {
    operator fun invoke(): Flow<FilingRules> = rules?.rules?.map(::FilingRules) ?: flowOf(FilingRules(emptyList()))
}
