package com.ptut.expense.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ptut.expense.domain.BangkokTime
import com.ptut.expense.domain.Category
import com.ptut.expense.domain.DateRange
import com.ptut.expense.domain.DayPart
import com.ptut.expense.domain.Direction
import com.ptut.expense.domain.ImportFix
import com.ptut.expense.domain.ImportFreshness
import com.ptut.expense.domain.ImportOutcome
import com.ptut.expense.domain.ImportRun
import com.ptut.expense.domain.MetaParts
import com.ptut.expense.domain.PeriodSummary
import com.ptut.expense.domain.QueuedSlip
import com.ptut.expense.domain.ReportPeriod
import com.ptut.expense.domain.ReviewReason
import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.SubCategory
import com.ptut.expense.domain.Transaction
import com.ptut.expense.domain.UsualDay
import com.ptut.expense.domain.colorName
import com.ptut.expense.domain.dateIn
import com.ptut.expense.domain.dayLabel
import com.ptut.expense.domain.dayPartAt
import com.ptut.expense.domain.differencePhrase
import com.ptut.expense.domain.displayName
import com.ptut.expense.domain.elapsedPhrase
import com.ptut.expense.domain.folders.WatchedFolder
import com.ptut.expense.domain.folders.watched
import com.ptut.expense.domain.format
import com.ptut.expense.domain.formatCompact
import com.ptut.expense.domain.iconName
import com.ptut.expense.domain.importFix
import com.ptut.expense.domain.importFreshness
import com.ptut.expense.domain.isAutomatic
import com.ptut.expense.domain.label
import com.ptut.expense.domain.ledger.LedgerRepository
import com.ptut.expense.domain.ledger.LedgerSnapshot
import com.ptut.expense.domain.metaLine
import com.ptut.expense.domain.metaParts
import com.ptut.expense.domain.percentPhrase
import com.ptut.expense.domain.rangeContaining
import com.ptut.expense.domain.rules.ShopNames
import com.ptut.expense.domain.savedOn
import com.ptut.expense.domain.spendByCategory
import com.ptut.expense.domain.summarise
import com.ptut.expense.domain.usualDay
import com.ptut.expense.locale.AppText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * What the freshness line says. The span lives inside the states that have one, so no screen can
 * print an elapsed time for an import that never ran — the case a fresh install always hits.
 */
sealed interface ImportStatusUiModel {
    /** No import has ever finished successfully. Distinct from [Broken]: not stopped, not started. */
    data object NeverRun : ImportStatusUiModel

    data class Fresh(val since: String) : ImportStatusUiModel

    data class Stale(val since: String) : ImportStatusUiModel

    data class Broken(val since: String) : ImportStatusUiModel

    /** The newest scan failed. A success an hour ago says nothing about the scans since (US-8). */
    data object Failing : ImportStatusUiModel
}

/** Home's three spans (#157, p05). The ranges are Reports' own, on Asia/Bangkok. */
enum class HomePeriod { Today, Week, Month }

/** One of the period's three biggest categories (p05): its name and glyph, in rank order. */
data class TopCategoryUiModel(val name: String, val icon: String)

/** One row of the day's list, with every string already resolved. */
data class TransactionRowUiModel(
    val id: String,
    val merchant: String,
    val meta: String,
    /** [meta]'s parts, for a Sky row that draws the time under the amount (#156). */
    val parts: MetaParts,
    val amount: String,
    val direction: Direction,
    /** Material Symbols name; the UI layer turns it into a vector. */
    val icon: String,
    /** The category's tint, so one row is the same colour here and in the Ledger. */
    val color: String,
)

sealed interface HomeUiState {
    data object Loading : HomeUiState

    /**
     * An empty [transactions] list is a real, expected state, not an error: FR-20 wants the
     * screen to say plainly that nothing was recorded rather than show a zero that reads
     * like a measurement.
     */
    data class Success(
        /** "Spent today · Fri 14 Aug" — the hero number is useless without the span it covers. */
        val heroLabel: String,
        /** The chosen period's money: the amount, and Income and Net beside it (#146, K1). */
        val summary: PeriodSummary,
        /** The chosen period's rows, newest first. */
        val transactions: List<TransactionRowUiModel>,
        val importStatus: ImportStatusUiModel,
        /** p24's status line on an empty period: who is watched, and when the importer last looked (#124). */
        val watchLine: String = "",
        /**
         * Whether the importer is scanning as the reader looks at the screen. It drives the pulse
         * in the freshness chip, and page 05 is why it is a field rather than something the chip
         * assumes: "a still dot and a scanning dot must not look the same".
         */
        val isScanning: Boolean,
        /** Null while the importer is healthy enough that offering an action would overstate it. */
        val importFix: ImportFix?,
        /** Null when the queue is empty — an empty queue has nothing to offer the reader. */
        val review: ReviewCardUiModel?,
        /**
         * Rows saved today that carry another day's date, newest first and at most three. Shown and
         * never counted: their money belongs to the day printed on the slip.
         */
        val savedToday: List<TransactionRowUiModel>,
        /** How many more were saved today than [savedToday] shows. History holds them by day. */
        val savedTodayMore: Int,
        val period: HomePeriod = HomePeriod.Today,
        /** "Spent today", "Spent this week", "Spent this month" (p05). */
        val amountLabel: String = AppText.copy.home.spentToday,
        /** At most three, biggest first; fewer when fewer categories carry spending. */
        val topCategories: List<TopCategoryUiModel> = emptyList(),
        /** p05's Compared card, on the Today tab only; null with too little history (#119). */
        val compared: ComparedUiModel? = null,
        /** p07 and p08's card under the tabs: yesterday before noon, today after 18:00 (#121, #122). */
        val moodCard: MoodCardUiModel? = null,
    ) : HomeUiState
}

/** The way into the review queue, stated as a count and a reason rather than a bare badge. */
data class ReviewCardUiModel(val title: String, val detail: String)

/** "฿180 less than a usual Friday" and its trend glyph's name (#119). */
data class ComparedUiModel(val line: String, val glyph: String)

/** "Good morning" or "Your day, wrapped", its sentence, its glyph, and the day its receipt opens on (#120). */
data class MoodCardUiModel(val lead: String, val line: String, val glyph: String, val receiptDate: String)

class HomeViewModel(
    ledger: LedgerRepository,
    /**
     * The fact, not the importer. A screen that only needs to know whether a scan is running has
     * no business being able to start one, and taking the flow keeps every test that is not about
     * the chip from having to build an importer.
     */
    isScanning: Flow<Boolean> = flowOf(false),
    categories: List<Category> = Category.seed(),
    private val clock: Clock = Clock.System,
    /**
     * #115: the reader's categories as they change. A tab keeps its holder while the reader is
     * away, so a list read once would keep an old name, or drop a new category, until a restart.
     */
    categoryUpdates: Flow<List<Category>> = flowOf(categories),
    /** The sub-categories as they change, for the same reason (#112). */
    subCategoryUpdates: Flow<List<SubCategory>> = flowOf(emptyList()),
    /** Every folder Photo folders lists, so the empty state names what is watched (#124). */
    watchedFolders: Flow<List<WatchedFolder>> = flowOf(emptyList()),
    /** Settings' "Mood by time of day" (#121). Off where nothing binds it, so a test sees no mood card. */
    moodByTimeOfDay: Flow<Boolean> = flowOf(false),
    /** #219: slips waiting out their Undo, already gone from Review, so not counted here either. */
    pendingDiscards: Flow<Set<String>> = flowOf(emptySet()),
) : ViewModel() {

    private val period = MutableStateFlow(HomePeriod.Today)

    /** Bumped when the screen comes back, so a card that belongs to the morning goes at noon. */
    private val clockTicks = MutableStateFlow(0)

    fun refreshClock() {
        clockTicks.value += 1
    }

    val uiState: StateFlow<HomeUiState> =
        combine(
            ledger.todaysTransactions,
            combine(ledger.sinceLastImportMillis, ledger.importRuns) { since, runs -> since to runs.latestRunFailed() },
            combine(ledger.queuedSlips, pendingDiscards) { slips, pending -> slips.filterNot { it.id in pending } },
            combine(isScanning, watchedFolders, moodByTimeOfDay, clockTicks) { scanning, folders, mood, _ ->
                Extras(scanning, folders, mood)
            },
            // ShopNames too: naming a shop read as noise retitles its rows here without a restart.
            combine(ledger.allTransactions, combine(categoryUpdates, subCategoryUpdates, ShopNames.names) { c, s, _ -> c to s }, period, ::Triple),
        ) { snapshot, (sinceLastImportMillis, failed), queued, (scanning, folders, mood), (ledgerRows, filing, chosen) ->
            val (categories, subCategories) = filing
            val todays = when (snapshot) {
                LedgerSnapshot.Unanswered -> return@combine HomeUiState.Loading
                is LedgerSnapshot.Rows -> snapshot.transactions
            }
            val now = clock.now()
            val today = now.toLocalDateTime(BangkokTime).date
            val history = ledgerRows.rowsOrNone()
            val saved = history.savedOn(today)
            // Week and month come off the whole ledger, which can answer after today's query.
            val range = chosen.rangeContaining(today)
            val transactions = if (chosen == HomePeriod.Today) {
                todays
            } else {
                ledgerRows.rowsOrNone()
                    .filter { it.occurredAtEpochMillis.dateIn(BangkokTime) in range }
                    .sortedByDescending { it.occurredAtEpochMillis }
            }
            HomeUiState.Success(
                heroLabel = "${chosen.spent()} · ${if (chosen == HomePeriod.Today) today.dayLabel() else range.label()}",
                summary = transactions.summarise(),
                transactions = transactions.map { it.asRow(categories, subCategories) },
                importStatus = if (failed) ImportStatusUiModel.Failing else sinceLastImportMillis.asImportStatus(),
                watchLine = folders.watchLine(sinceLastImportMillis),
                isScanning = scanning,
                importFix = if (failed) ImportFix.AllowAllPhotos else importFix(sinceLastImportMillis),
                review = queued.asReviewCard(),
                savedToday = saved.take(SAVED_TODAY_SHOWN).map { it.asSavedRow(categories, subCategories) },
                savedTodayMore = (saved.size - SAVED_TODAY_SHOWN).coerceAtLeast(0),
                period = chosen,
                amountLabel = chosen.spent(),
                compared = history.usualDay(today)?.asCompared()?.takeIf { chosen == HomePeriod.Today },
                moodCard = history.moodCard(dayPartAt(now), today).takeIf { mood && chosen == HomePeriod.Today },
                topCategories = transactions.spendByCategory(categories)
                    .take(TOP_CATEGORIES)
                    .map { TopCategoryUiModel(name = it.category.name, icon = it.category.icon) },
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HomeUiState.Loading,
        )

    fun show(period: HomePeriod) {
        this.period.value = period
    }

    /**
     * The whole-ledger query can answer after today's does. Home is worth drawing before it does,
     * and nothing saved is what an unanswered query supports until its first emission.
     */
    private fun LedgerSnapshot.rowsOrNone(): List<Transaction> = when (this) {
        LedgerSnapshot.Unanswered -> emptyList()
        is LedgerSnapshot.Rows -> transactions
    }
}

/** Home shows a few. History holds the rest, each under its own day. */
private const val SAVED_TODAY_SHOWN = 3

/** p05 draws three tiles. */
private const val TOP_CATEGORIES = 3

private fun HomePeriod.spent(): String = AppText.copy.home.let { text ->
    when (this) {
        HomePeriod.Today -> text.spentToday
        HomePeriod.Week -> text.spentThisWeek
        HomePeriod.Month -> text.spentThisMonth
    }
}

private fun HomePeriod.rangeContaining(today: LocalDate): DateRange = when (this) {
    HomePeriod.Today -> DateRange(today, today)
    HomePeriod.Week -> ReportPeriod.Week.rangeContaining(today)
    HomePeriod.Month -> ReportPeriod.Month.rangeContaining(today)
}

/** "Watching K PLUS · checked 4 min ago", or a count once more than one folder is watched. */
private fun List<WatchedFolder>.watchLine(sinceLastImportMillis: Long?): String {
    val text = AppText.copy.home
    val on = watched()
    val checked = sinceLastImportMillis?.let { text.checkedAgo(elapsedPhrase(it)) } ?: text.noRunYet
    return when (on.size) {
        0 -> text.noFolderWatched
        1 -> text.watchingOne(on.single().name, checked)
        else -> text.watchingMany(on.size, checked)
    }
}

/** Null means no import has ever succeeded, which is the one state carrying no span. */
private fun Long?.asImportStatus(): ImportStatusUiModel {
    val millis = this ?: return ImportStatusUiModel.NeverRun
    val since = elapsedPhrase(millis)
    return when (importFreshness(millis)) {
        ImportFreshness.Fresh -> ImportStatusUiModel.Fresh(since)
        ImportFreshness.Stale -> ImportStatusUiModel.Stale(since)
        ImportFreshness.Broken -> ImportStatusUiModel.Broken(since)
    }
}

/** Newest first, as the journal lists them. A run still going has not answered yet. */
private fun List<ImportRun>.latestRunFailed(): Boolean =
    firstOrNull { it.outcome != ImportOutcome.Running }?.outcome == ImportOutcome.Failure

private fun List<QueuedSlip>.asReviewCard(): ReviewCardUiModel? {
    if (isEmpty()) return null
    val text = AppText.copy.home
    val newMerchants = count { it.reason == ReviewReason.NewMerchant }
    val unclear = count { it.reason == ReviewReason.AmountUnclear }
    val unread = count { it.reason == ReviewReason.Unreadable }
    val reasons = listOfNotNull(
        newMerchants.takeIf { it > 0 }?.let(text.newShops),
        unclear.takeIf { it > 0 }?.let(text.unreadableAmounts),
        unread.takeIf { it > 0 }?.let(text.unreadSlips),
    )
    // One title for every reason (#157, C3): "need a category" is wrong for an unread amount.
    return ReviewCardUiModel(
        title = text.slipsToCheck(size),
        detail = reasons.joinToString(text.reasonSeparator).replaceFirstChar { it.uppercase() },
    )
}

private fun Transaction.asRow(categories: List<Category>, subCategories: List<SubCategory>) = TransactionRowUiModel(
    id = id,
    merchant = displayName(categories, subCategories),
    meta = metaLine(categories, subCategories = subCategories),
    parts = metaParts(categories, subCategories = subCategories),
    amount = amount.formatCompact(),
    direction = direction,
    icon = iconName(categories),
    color = colorName(categories),
)

/** The day leads, because the day is what keeps this row out of today's total. The category follows unless it is the name. */
private fun Transaction.asSavedRow(categories: List<Category>, subCategories: List<SubCategory>) =
    asRow(categories, subCategories).let { row ->
        row.copy(meta = listOfNotNull(occurredAtEpochMillis.dateIn(BangkokTime).dayLabel(), row.parts.category).joinToString(" · "))
    }

private data class Extras(val scanning: Boolean, val folders: List<WatchedFolder>, val mood: Boolean)

private fun UsualDay.asCompared() = ComparedUiModel(
    line = differencePhrase(),
    glyph = when {
        difference < Satang.Zero -> "trending_down"
        difference > Satang.Zero -> "trending_up"
        else -> "trending_flat"
    },
)

/** Morning recaps yesterday, evening wraps today, the day between has no card; none for a day with no spending. */
private fun List<Transaction>.moodCard(part: DayPart, today: LocalDate): MoodCardUiModel? = when (part) {
    DayPart.Morning -> {
        val yesterday = today.minus(1, DateTimeUnit.DAY)
        val spent = spendingOn(yesterday).fold(Satang.Zero) { sum, row -> sum + row.amount }
        val versus = usualDay(yesterday)?.let { " — ${it.percentPhrase()}" }.orEmpty()
        MoodCardUiModel(
            lead = AppText.copy.home.goodMorning,
            line = AppText.copy.home.yesterdayCameTo(spent.formatCompact(), versus),
            glyph = "wb_twilight",
            receiptDate = yesterday.toString(),
        ).takeIf { spent > Satang.Zero }
    }

    DayPart.Evening -> {
        val rows = spendingOn(today)
        val total = rows.fold(Satang.Zero) { sum, row -> sum + row.amount }
        MoodCardUiModel(
            lead = AppText.copy.home.dayWrapped,
            line = AppText.copy.home.wrapLine(rows.size, total.format()),
            glyph = "receipt_long",
            receiptDate = today.toString(),
        ).takeIf { rows.isNotEmpty() }
    }

    DayPart.Day -> null
}

private fun List<Transaction>.spendingOn(day: LocalDate): List<Transaction> =
    filter { it.direction == Direction.Expense && it.occurredAtEpochMillis.dateIn(BangkokTime) == day }
