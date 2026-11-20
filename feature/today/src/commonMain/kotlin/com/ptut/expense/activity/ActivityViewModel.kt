package com.ptut.expense.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ptut.expense.domain.Home
import com.ptut.expense.domain.HomeTime
import com.ptut.expense.domain.ImportOutcome
import com.ptut.expense.domain.ImportRun
import com.ptut.expense.domain.QueuedSlip
import com.ptut.expense.domain.ReviewReason
import com.ptut.expense.domain.SUBSCRIPTION_STOP_MILLIS
import com.ptut.expense.domain.Separators
import com.ptut.expense.domain.Transaction
import com.ptut.expense.domain.dayAndMonth
import com.ptut.expense.domain.formatCompact
import com.ptut.expense.domain.ledger.LedgerSnapshot
import com.ptut.expense.domain.ledger.LedgerSpan
import com.ptut.expense.domain.ledger.ObserveImportRunsUseCase
import com.ptut.expense.domain.ledger.ObserveTransactionsUseCase
import com.ptut.expense.domain.shared.Currencies
import com.ptut.expense.domain.shared.formatMinor
import com.ptut.expense.domain.slip.ObserveQueuedSlipsUseCase
import com.ptut.expense.domain.slip.shownShop
import com.ptut.expense.domain.today.FindRunOnlyShopUseCase
import com.ptut.expense.domain.today.GetHomeTodayUseCase
import com.ptut.expense.domain.today.GetWeeklyWrapsUseCase
import com.ptut.expense.domain.today.WeekWrap
import com.ptut.expense.domain.weekdayAndDay
import com.ptut.expense.locale.AppText
import com.ptut.expense.locale.SlipsCopy
import com.ptut.expense.locale.currencyCopy
import com.ptut.expense.ui.displayName
import com.ptut.expense.ui.phrase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * What an entry is: the importer succeeding, the importer reporting in, or the importer blocked.
 *
 * It drives the fill. Only [NeedsYou] takes `warnBg`, so a blocked import is visible as work at a
 * glance rather than after reading four similar cards — and the design keeps that fill narrow on
 * purpose. A new shop is not on it here or on the review queue: it was asked once and it is
 * waiting, which is a different thing from a slip the reader has to unstick.
 */
enum class ActivityTone { Imported, Reported, NeedsYou }

/** #217: where a row leads. A row about work left for the reader opens the place to do it. */
sealed interface ActivityTarget {
    data object None : ActivityTarget
    data object Review : ActivityTarget
    data class Slip(val id: String) : ActivityTarget
    data object PhotoFolders : ActivityTarget
}

data class ActivityEntryUiModel(
    val id: String,
    val title: String,
    val body: String,
    val time: String,
    val icon: String,
    val tone: ActivityTone,
    val target: ActivityTarget = ActivityTarget.None,
)

data class ActivityUiState(
    val entries: List<ActivityEntryUiModel>,
    /** True until the ledger answers, so an empty list is not claimed before it is known (#217). */
    val loading: Boolean = false,
)

/**
 * FR-5 and US-8: what the importer did and what it could not do, with the failures explained.
 * This is where a silently broken import becomes visible before it distorts a month.
 *
 * Three sources: the `import_run` journal #12 keeps, the review queue, and the ledger, which
 * names a one-slip run's shop and backs each finished week's wrap (#127).
 */
class ActivityViewModel(
    observeImportRuns: ObserveImportRunsUseCase,
    /** #219: slips waiting out their Undo, already gone from Review, so not listed here either. */
    observeQueuedSlips: ObserveQueuedSlipsUseCase,
    observeTransactions: ObserveTransactionsUseCase,
    /** For p23's right-hand times, which count back from now. */
    private val clock: Clock = Clock.System,
    private val getWeeklyWraps: GetWeeklyWrapsUseCase = GetWeeklyWrapsUseCase(GetHomeTodayUseCase(clock)),
    private val findRunOnlyShop: FindRunOnlyShopUseCase = FindRunOnlyShopUseCase(),
) : ViewModel() {

    private val queuedSlips = observeQueuedSlips()

    val uiState: StateFlow<ActivityUiState> =
        combine(observeImportRuns(), queuedSlips, observeTransactions(LedgerSpan.All)) { runs, queued, snapshot ->
            val rows = (snapshot as? LedgerSnapshot.Rows)?.transactions.orEmpty()
            ActivityUiState(
                entries = (queued.map { it.asEntry() } + runs.map { it.asEntry(rows) } + getWeeklyWraps(rows).map { it.asEntry() })
                    .sortedByDescending { it.sortKey }
                    .map { it.model },
                loading = snapshot == LedgerSnapshot.Unanswered,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SUBSCRIPTION_STOP_MILLIS),
            initialValue = ActivityUiState(emptyList(), loading = true),
        )

    private class Dated(val sortKey: Long, val model: ActivityEntryUiModel)

    private fun ImportRun.asEntry(rows: List<Transaction>): Dated {
        val at = finishedAtEpochMillis ?: startedAtEpochMillis
        val text = AppText.copy.slips
        val model = when (outcome) {
            // US-8: scanning and finding nothing is a success. Saying so is the whole point of
            // this screen — a run that found no slips still proves the pipe is alive.
            ImportOutcome.Success -> successEntry(at, rows)

            ImportOutcome.Failure -> ActivityEntryUiModel(
                id = id,
                title = text.folderUnread,
                // The reason, not a generic apology: a failure nobody can act on is the same
                // as a silent one, which is the failure this screen exists to prevent.
                body = failureReason ?: text.importerStopped,
                time = at.stamp(),
                icon = "error",
                tone = ActivityTone.NeedsYou,
                // Photo access and the watched folders are both fixed there.
                target = ActivityTarget.PhotoFolders,
            )

            ImportOutcome.Running -> ActivityEntryUiModel(
                id = id,
                title = text.importRunning,
                body = text.importRunningBody,
                time = at.stamp(),
                icon = "sync",
                tone = ActivityTone.Reported,
            )
        }
        return Dated(at, model)
    }

    /**
     * What a finished run wrote (p23): "Imported 3 slips", then the folders they came from (#170)
     * and whether any wait in To check. A run that only met slips it had read before wrote nothing.
     */
    private fun ImportRun.successEntry(at: Long, rows: List<Transaction>): ActivityEntryUiModel {
        val text = AppText.copy.slips
        val count = filed + queued
        if (count == 0) {
            return ActivityEntryUiModel(
                id = id,
                title = text.nothingNew,
                body = text.nothingNewBody,
                time = at.stamp(),
                icon = "monitor_heart",
                tone = ActivityTone.Reported,
            )
        }
        // A run with no folder recorded read only shared slips (#124).
        val from = folders.joinToString(", ").ifEmpty { text.sharedSlips }
        return ActivityEntryUiModel(
            id = id,
            title = text.imported(count),
            body = if (queued == 0) "$from${Separators.DOT}${onlyShop(rows) ?: text.allSorted}" else "$from${Separators.DOT}${text.toCheck(queued)}",
            time = at.stamp(),
            icon = "check_circle",
            tone = ActivityTone.Imported,
            target = if (queued == 0) ActivityTarget.None else ActivityTarget.Review,
        )
    }

    /** p23 names the shop when a run filed one slip (#127). */
    private fun ImportRun.onlyShop(rows: List<Transaction>): String? =
        // No categories: an imported row is named by its shop, its note or "Unknown", never its category.
        findRunOnlyShop(this, rows)?.displayName(categories = emptyList(), subCategories = emptyList())

    /** A finished week's wrap (#127), placed after the week it sums up. */
    private fun WeekWrap.asEntry(): Dated {
        val text = AppText.copy.slips
        return Dated(
            sortKey = endsAtEpochMillis,
            model = ActivityEntryUiModel(
                id = "week-${week.start}",
                title = text.weekTitle(week.start.dayAndMonth(), week.endInclusive.dayAndMonth()),
                body = text.weekBody(spent.formatCompact(), trend.phrase(text.lastWeek)),
                time = endsAtEpochMillis.stamp(),
                icon = "insights",
                tone = ActivityTone.Reported,
            ),
        )
    }

    /** #33: a kyat slip shows its kyat, "K29,500"; it has no baht until it has a rate. */
    private fun QueuedSlip.shownAmount(): String? =
        amount?.let { if (currency == Home.money.code) it.formatCompact() else formatMinor(it.toMinorUnits(), Currencies.of(currency)) }

    private fun QueuedSlip.asEntry(text: SlipsCopy = AppText.copy.slips) = Dated(
        sortKey = capturedAtEpochMillis,
        model = ActivityEntryUiModel(
            id = "queued-$id",
            title = when (reason) {
                ReviewReason.NewMerchant -> text.needsCategory
                ReviewReason.AmountUnclear -> text.needsAmount
                ReviewReason.Unreadable -> text.imageUnread
                ReviewReason.RateNeeded -> currencyCopy().rateNeeded(currency)
            },
            body = when (reason) {
                ReviewReason.NewMerchant -> text.addedToCheck(shownShop(merchant, text.unknownShop), shownAmount() ?: text.notCounted)
                ReviewReason.AmountUnclear -> text.amountNotConfident(shownShop(merchant, text.unknownShop))
                ReviewReason.Unreadable -> text.fillByHand
                ReviewReason.RateNeeded -> currencyCopy().rateNeededBody(shownShop(merchant, text.unknownShop), shownAmount() ?: text.notCounted)
            },
            time = capturedAtEpochMillis.stamp(),
            // To check's own glyph (p23); an image that read nothing takes p23's warning instead.
            icon = if (reason == ReviewReason.Unreadable) "error" else "inbox",
            tone = when (reason) {
                ReviewReason.NewMerchant -> ActivityTone.Reported
                ReviewReason.AmountUnclear, ReviewReason.Unreadable, ReviewReason.RateNeeded -> ActivityTone.NeedsYou
            },
            target = ActivityTarget.Slip(id),
        ),
    )

    /**
     * p23's right-hand times: minutes for the last hour, the clock for earlier today, the weekday
     * for this week, and the date beyond it. FR-19: the Bangkok day, never the device's.
     */
    private fun Long.stamp(): String {
        val now = clock.now()
        val minutes = (now.toEpochMilliseconds() - this) / MILLIS_PER_MINUTE
        val at = Instant.fromEpochMilliseconds(this).toLocalDateTime(HomeTime)
        val today = now.toLocalDateTime(HomeTime).date
        return when {
            minutes < 1 -> AppText.copy.slips.justNow
            minutes < MINUTES_PER_HOUR && at.date == today -> AppText.copy.slips.minutesAgo(minutes)
            at.date == today -> "${at.hour.pad()}:${at.minute.pad()}"
            at.date > today.minus(DAYS_SHOWN_AS_WEEKDAY, DateTimeUnit.DAY) -> at.date.weekdayAndDay().substringBefore(' ')
            else -> at.date.dayAndMonth()
        }
    }

    private fun Int.pad() = toString().padStart(2, '0')
}

private const val MILLIS_PER_MINUTE = 60_000L
private const val MINUTES_PER_HOUR = 60L

/** A day this week reads by its name; a week back, the name is ambiguous, so the date takes over. */
private const val DAYS_SHOWN_AS_WEEKDAY = 6
