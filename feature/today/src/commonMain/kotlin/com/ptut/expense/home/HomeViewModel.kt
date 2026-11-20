package com.ptut.expense.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ptut.expense.domain.Category
import com.ptut.expense.domain.Direction
import com.ptut.expense.domain.HomeTime
import com.ptut.expense.domain.ImportFix
import com.ptut.expense.domain.MetaParts
import com.ptut.expense.domain.PeriodSummary
import com.ptut.expense.domain.ReviewReason
import com.ptut.expense.domain.SUBSCRIPTION_STOP_MILLIS
import com.ptut.expense.domain.Separators
import com.ptut.expense.domain.SubCategory
import com.ptut.expense.domain.Transaction
import com.ptut.expense.domain.UsualDay
import com.ptut.expense.domain.WeeklyTarget
import com.ptut.expense.domain.colorName
import com.ptut.expense.domain.dateIn
import com.ptut.expense.domain.dayLabel
import com.ptut.expense.domain.folders.ObserveWatchedFoldersUseCase
import com.ptut.expense.domain.folders.WatchedFolder
import com.ptut.expense.domain.format
import com.ptut.expense.domain.formatCompact
import com.ptut.expense.domain.iconName
import com.ptut.expense.domain.label
import com.ptut.expense.domain.ledger.LedgerSpan
import com.ptut.expense.domain.ledger.ObserveImportRunsUseCase
import com.ptut.expense.domain.ledger.ObserveLastImportUseCase
import com.ptut.expense.domain.ledger.ObserveTransactionsUseCase
import com.ptut.expense.domain.rules.ShopNames
import com.ptut.expense.domain.slip.ObserveQueuedSlipsUseCase
import com.ptut.expense.domain.today.GetHomeSummaryUseCase
import com.ptut.expense.domain.today.GetHomeTodayUseCase
import com.ptut.expense.domain.today.GetImportHealthUseCase
import com.ptut.expense.domain.today.GetMoodRecapUseCase
import com.ptut.expense.domain.today.GetWeeklyCheckInUseCase
import com.ptut.expense.domain.today.HideWeeklyNoteUseCase
import com.ptut.expense.domain.today.HomePeriod
import com.ptut.expense.domain.today.MoodRecap
import com.ptut.expense.domain.today.ObserveScanningUseCase
import com.ptut.expense.domain.today.ReviewCounts
import com.ptut.expense.locale.AppText
import com.ptut.expense.locale.currencyCopy
import com.ptut.expense.ui.SpendWords
import com.ptut.expense.ui.displayName
import com.ptut.expense.ui.metaLine
import com.ptut.expense.ui.metaParts
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

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
        /** #68: blank rows at the top of the list while a scan runs, where its rows will land. */
        val placeholderRows: Int = 0,
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
        /** #20: Monday's look back at last week, on the Today tab only. */
        val weeklyCard: WeeklyCardUiModel? = null,
        /** #21: the one weekly target, on the Week tab only. */
        val target: TargetUiModel? = null,
    ) : HomeUiState
}

/** The way into the review queue, stated as a count and a reason rather than a bare badge. */
data class ReviewCardUiModel(val title: String, val detail: String)

/** "฿180 less than a usual Friday" and its trend glyph's name (#119). */
data class ComparedUiModel(val line: String, val glyph: String)

/** "Good morning" or "Your day, wrapped", its sentence, its glyph, and the day its receipt opens on (#120). */
data class MoodCardUiModel(val lead: String, val line: String, val glyph: String, val receiptDate: String)

class HomeViewModel(
    observeTransactions: ObserveTransactionsUseCase,
    observeLastImport: ObserveLastImportUseCase,
    observeImportRuns: ObserveImportRunsUseCase,
    /** #219: slips waiting out their Undo are already gone from Review, so not counted here either. */
    observeQueuedSlips: ObserveQueuedSlipsUseCase,
    /**
     * The fact, not the importer. A screen that only needs to know whether a scan is running has
     * no business being able to start one, and taking the fact keeps every test that is not about
     * the chip from having to build an importer.
     */
    observeScanning: ObserveScanningUseCase = ObserveScanningUseCase(),
    categories: List<Category> = Category.seed(),
    /** Read on every change for the part of the day, which the mood card turns on. */
    private val clock: Clock = Clock.System,
    /**
     * #115: the reader's categories as they change. A tab keeps its holder while the reader is
     * away, so a list read once would keep an old name, or drop a new category, until a restart.
     */
    categoryUpdates: Flow<List<Category>> = flowOf(categories),
    /** The sub-categories as they change, for the same reason (#112). */
    subCategoryUpdates: Flow<List<SubCategory>> = flowOf(emptyList()),
    /** Every folder Photo folders lists, so the empty state names what is watched (#124). */
    observeWatchedFolders: ObserveWatchedFoldersUseCase = ObserveWatchedFoldersUseCase(),
    /** Settings' "Mood by time of day" (#121). Off where nothing binds it, so a test sees no mood card. */
    moodByTimeOfDay: Flow<Boolean> = flowOf(false),
    /** Settings' "Weekly check-in" (#20). Off where nothing binds it, so a test sees no weekly card. */
    weeklyCheckIn: Flow<Boolean> = flowOf(false),
    /** #21: the one weekly target, or null with none set. */
    weeklyTarget: Flow<WeeklyTarget?> = flowOf(null),
    /** #22: the weekly note. Off where nothing binds it, so a test sees the #20 card unchanged. */
    weeklyNote: Flow<WeeklyNoteSettings> = flowOf(WeeklyNoteSettings()),
    /** #22: where "Just hide this week" is kept. */
    private val hideWeeklyNote: HideWeeklyNoteUseCase = HideWeeklyNoteUseCase(homeToday = GetHomeTodayUseCase(clock)),
    private val getImportHealth: GetImportHealthUseCase = GetImportHealthUseCase(),
    private val getHomeSummary: GetHomeSummaryUseCase = GetHomeSummaryUseCase(),
    private val getMoodRecap: GetMoodRecapUseCase = GetMoodRecapUseCase(),
    private val getWeeklyCheckIn: GetWeeklyCheckInUseCase = GetWeeklyCheckInUseCase(),
) : ViewModel() {

    private val period = MutableStateFlow(HomePeriod.Today)

    /** Bumped when the screen comes back, so a card that belongs to the morning goes at noon. */
    private val clockTicks = MutableStateFlow(0)

    /** #22: no note for this week; next Monday shows its own. */
    fun hideNoteWeek() = hideWeeklyNote()

    fun refreshClock() {
        clockTicks.value += 1
    }

    val uiState: StateFlow<HomeUiState> =
        combine(
            observeTransactions(LedgerSpan.Today),
            combine(observeLastImport(), observeImportRuns(), ::Pair),
            observeQueuedSlips(),
            combine(observeScanning(), observeWatchedFolders(), moodByTimeOfDay, combine(weeklyCheckIn, weeklyTarget, weeklyNote, ::Triple), clockTicks) { scanning, folders, mood, weekly, _ ->
                Extras(scanning, folders, mood, weekly.first, weekly.second, weekly.third)
            },
            // ShopNames too: naming a shop read as noise retitles its rows here without a restart.
            combine(observeTransactions(LedgerSpan.All), combine(categoryUpdates, subCategoryUpdates, ShopNames.names) { c, s, _ -> c to s }, period, ::Triple),
        ) { todays, (sinceLastImportMillis, runs), queued, extras, (ledger, filing, chosen) ->
            val (categories, subCategories) = filing
            val now = clock.now()
            val today = now.toLocalDateTime(HomeTime).date
            val home = getHomeSummary(todays, ledger, chosen, today, queued, categories, extras.target, SpendWords)
                ?: return@combine HomeUiState.Loading
            val health = getImportHealth(sinceLastImportMillis, runs, stopSentences())
            val transactions = home.transactions
            HomeUiState.Success(
                heroLabel = "${chosen.spent()}${Separators.DOT}${if (chosen == HomePeriod.Today) today.dayLabel() else home.range.label()}",
                summary = home.summary,
                transactions = transactions.map { it.asRow(categories, subCategories) },
                importStatus = health.asImportStatus(),
                watchLine = extras.folders.watchLine(sinceLastImportMillis),
                isScanning = extras.scanning,
                // An empty period keeps its empty state, which already shows the scan waiting.
                placeholderRows = if (extras.scanning && transactions.isNotEmpty()) PLACEHOLDER_ROWS else 0,
                importFix = health.fix,
                review = home.review?.asReviewCard(),
                savedToday = home.savedToday.take(SAVED_TODAY_SHOWN).map { it.asSavedRow(categories, subCategories) },
                savedTodayMore = (home.savedToday.size - SAVED_TODAY_SHOWN).coerceAtLeast(0),
                period = chosen,
                amountLabel = chosen.spent(),
                compared = home.usualDay?.asCompared(),
                moodCard = if (extras.mood && chosen == HomePeriod.Today) getMoodRecap(home.history, now)?.asMoodCard() else null,
                weeklyCard = if (extras.weekly && chosen == HomePeriod.Today) {
                    getWeeklyCheckIn(home.history, today, extras.target, extras.note.on, extras.note.hiddenWeek)?.asCard(categories, extras.note.tone)
                } else {
                    null
                },
                target = home.target?.targetLine(categories),
                topCategories = home.byCategory
                    .take(TOP_CATEGORIES)
                    .map { TopCategoryUiModel(name = it.category.name, icon = it.category.icon) },
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SUBSCRIPTION_STOP_MILLIS),
            initialValue = HomeUiState.Loading,
        )

    fun show(period: HomePeriod) {
        this.period.value = period
    }
}

/** Home shows a few. History holds the rest, each under its own day. */
private const val SAVED_TODAY_SHOWN = 3

/** #68: enough to show where rows land without promising how many a scan will find. */
private const val PLACEHOLDER_ROWS = 2

/** p05 draws three tiles. */
private const val TOP_CATEGORIES = 3

private fun HomePeriod.spent(): String = AppText.copy.home.let { text ->
    when (this) {
        HomePeriod.Today -> text.spentToday
        HomePeriod.Week -> text.spentThisWeek
        HomePeriod.Month -> text.spentThisMonth
    }
}

private fun ReviewCounts.asReviewCard(): ReviewCardUiModel {
    val text = AppText.copy.home
    val reasons = listOfNotNull(
        of(ReviewReason.NewMerchant).takeIf { it > 0 }?.let(text.newShops),
        of(ReviewReason.AmountUnclear).takeIf { it > 0 }?.let(text.unreadableAmounts),
        of(ReviewReason.Unreadable).takeIf { it > 0 }?.let(text.unreadSlips),
        of(ReviewReason.RateNeeded).takeIf { it > 0 }?.let(currencyCopy().slipsNeedRate),
    )
    // One title for every reason (#157, C3): "need a category" is wrong for an unread amount.
    return ReviewCardUiModel(
        title = text.slipsToCheck(total),
        detail = reasons.joinToString(AppText.copy.phrases.listComma).replaceFirstChar { it.uppercase() },
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
        row.copy(meta = listOfNotNull(occurredAtEpochMillis.dateIn(HomeTime).dayLabel(), row.parts.category).joinToString(Separators.DOT))
    }

private data class Extras(
    val scanning: Boolean,
    val folders: List<WatchedFolder>,
    val mood: Boolean,
    val weekly: Boolean,
    val target: WeeklyTarget?,
    val note: WeeklyNoteSettings,
)

private fun UsualDay.asCompared() = ComparedUiModel(line = differencePhrase(), glyph = difference.trendGlyph())

private fun MoodRecap.asMoodCard(): MoodCardUiModel = when (this) {
    is MoodRecap.Morning -> MoodCardUiModel(
        lead = AppText.copy.home.goodMorning,
        line = AppText.copy.home.yesterdayCameTo(spent.formatCompact(), usual?.let { " — ${it.percentPhrase()}" }.orEmpty()),
        glyph = "wb_twilight",
        receiptDate = day.toString(),
    )

    is MoodRecap.Evening -> MoodCardUiModel(
        lead = AppText.copy.home.dayWrapped,
        line = AppText.copy.home.wrapLine(purchases, spent.format()),
        glyph = "receipt_long",
        receiptDate = day.toString(),
    )
}
