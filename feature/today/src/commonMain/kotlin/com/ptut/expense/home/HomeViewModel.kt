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
import com.ptut.expense.domain.Satang
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
import com.ptut.expense.domain.formatNet
import com.ptut.expense.domain.iconName
import com.ptut.expense.domain.isAutomatic
import com.ptut.expense.domain.label
import com.ptut.expense.domain.ledger.LedgerSnapshot
import com.ptut.expense.domain.ledger.LedgerSpan
import com.ptut.expense.domain.ledger.ObserveImportRunsUseCase
import com.ptut.expense.domain.ledger.ObserveLastImportUseCase
import com.ptut.expense.domain.ledger.ObserveTransactionsUseCase
import com.ptut.expense.domain.play.AnswerGoalDayUseCase
import com.ptut.expense.domain.play.ObserveTodayPlayUseCase
import com.ptut.expense.domain.play.TodayPlay
import com.ptut.expense.domain.rules.ShopNames
import com.ptut.expense.domain.runSuspendCatching
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
import com.ptut.expense.ui.ToastAction
import com.ptut.expense.ui.ToastMessage
import com.ptut.expense.ui.ToastMessages
import com.ptut.expense.ui.displayName
import com.ptut.expense.ui.metaLine
import com.ptut.expense.ui.metaParts
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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
    /** #103: the slip an importer just filed, drawn with a tick on its tile until its toast goes. */
    val caught: Boolean = false,
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
        /** "Income ฿0.00 · Net −฿655.00", or null while the ledger holds no income at all. */
        val incomeLine: IncomeLineUiModel? = null,
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
        /** #85: the run card, on the Today tab only, while Play and its run are on. */
        val play: PlayStripUiModel? = null,
        /** #87: the ink Play card while today's question waits. */
        val question: PlayQuestionCardUiModel? = null,
        /** #89: PaiSeN's one line; while it shows, [compared] is null (D9). */
        val coach: CoachLineUiModel? = null,
        /** #91: the week's challenge, once picked. */
        val challenge: ChallengeRowUiModel? = null,
        /** The ways into the extras whose parts are on. */
        val shelf: List<ShelfItemUiModel> = emptyList(),
        /** #104: the goal jar and today's Count it card, on the Today tab only. */
        val jar: JarCardUiModel? = null,
        /** #102: "Games and stamps join on day 8" through Today's first week, or null. */
        val firstWeekLine: String? = null,
        /** #103: a slip was just caught, so the total's change is its roll. */
        val caught: Boolean = false,
        /** #103: the stamp press and the rolling total; false with Play off, and the number just changes. */
        val playMotion: Boolean = false,
    ) : HomeUiState
}

/** The way into the review queue, stated as a count and a reason rather than a bare badge. */
data class ReviewCardUiModel(val title: String, val detail: String, val action: String)

/** The period's income and its signed net; [netBelowZero] is the one case that is money out. */
data class IncomeLineUiModel(val income: String, val net: String, val netBelowZero: Boolean)

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
    /** #82: what Today shows of play. None where nothing binds it, so a test sees Today as before. */
    observeTodayPlay: ObserveTodayPlayUseCase? = null,
    /** #104: Count it and Not today. None where nothing binds it, so the card's buttons do nothing. */
    private val answerGoalDay: AnswerGoalDayUseCase? = null,
    /** #103: Play's switch for the caught slip's press and roll. Off where nothing binds it. */
    playMotion: Flow<Boolean> = flowOf(false),
    /** #103: the app's one toast, where "Caught a K PLUS slip" goes. None where nothing binds it. */
    private val toasts: ToastMessages? = null,
) : ViewModel() {

    private val period = MutableStateFlow(HomePeriod.Today)

    /** Bumped when the screen comes back, so a card that belongs to the morning goes at noon. */
    private val clockTicks = MutableStateFlow(0)

    /** #103: the newest slip an importer filed for today after the screen first read the day, until its toast goes. */
    private val caughtId = MutableStateFlow<String?>(null)

    private val edits = MutableStateFlow<String?>(null)

    /** #104: Count it landed on this screen, so the jar's stamp presses once. */
    private val countedNow = MutableStateFlow(false)

    private var answering = false

    /** #103: the row the toast's Change asked to edit; the screen opens Edit and calls [editOpened]. */
    val editRequests: StateFlow<String?> = edits

    init {
        // The first read only learns what is there, so a cold start or a new holder never replays a toast.
        viewModelScope.launch {
            var known: Set<String>? = null
            observeTransactions(LedgerSpan.Today).collect { snapshot ->
                val rows = (snapshot as? LedgerSnapshot.Rows)?.transactions ?: return@collect
                val seen = known
                known = rows.mapTo(HashSet()) { it.id }
                if (seen != null) rows.firstOrNull { it.id !in seen && it.source.isAutomatic }?.let { caught(it, categoryUpdates.first(), subCategoryUpdates.first()) }
            }
        }
    }

    private suspend fun caught(row: Transaction, categories: List<Category>, subCategories: List<SubCategory>) {
        caughtId.value = row.id
        val text = AppText.copy.home
        val category = row.metaParts(categories, subCategories = subCategories).category
        toasts?.show(
            ToastMessage(
                text = text.caught(row.sourceFolder, row.amount.formatCompact(), category),
                action = ToastAction(text.caughtChange) {
                    caughtId.value = null
                    edits.value = row.id
                },
                // Another slip's toast may have taken over; only this slip's tick goes with this toast.
                onGone = { caughtId.compareAndSet(row.id, null) },
            ),
        )
    }

    /** #104: Count it, or Not today when [count] is false. A second tap while the first writes does nothing. */
    fun answerDay(goalId: String, count: Boolean) {
        val answer = answerGoalDay ?: return
        if (answering) return
        answering = true
        viewModelScope.launch {
            runSuspendCatching { answer(goalId, count) }.onSuccess { counted -> if (counted) countedNow.value = true }
            answering = false
        }
    }

    fun editOpened() {
        edits.value = null
    }

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
            combine(
                observeScanning(),
                observeWatchedFolders(),
                moodByTimeOfDay,
                combine(weeklyCheckIn, weeklyTarget, weeklyNote, observeTodayPlay?.invoke() ?: flowOf(TodayPlay.Off), ::Weekly),
                combine(clockTicks, caughtId, playMotion, countedNow) { _, caught, motion, counted -> Triple(caught, motion, counted) },
            ) { scanning, folders, mood, weekly, (caught, motion, counted) ->
                Extras(scanning, folders, mood, weekly.on, weekly.target, weekly.note, weekly.play, caught, motion, counted)
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
            val onToday = chosen == HomePeriod.Today
            val weeklyCard = if (extras.weekly && onToday) {
                getWeeklyCheckIn(home.history, today, extras.target, extras.note.on, extras.note.hiddenWeek)?.asCard(categories, extras.note.tone)
                    ?.copy(guessMonday = extras.play.guessWeek?.toString())
            } else {
                null
            }
            // Today keeps one insight line, in screen order: weekly note, then coach, then Compared.
            val coach = if (onToday && weeklyCard == null) extras.play.coachCard(today, categories) else null
            HomeUiState.Success(
                heroLabel = "${chosen.spent()}${Separators.DOT}${if (chosen == HomePeriod.Today) today.dayLabel() else home.range.label()}",
                summary = home.summary,
                incomeLine = home.summary.takeIf { home.history.any { it.direction == Direction.Income } }?.let {
                    IncomeLineUiModel(income = it.income.format(), net = it.net.formatNet(), netBelowZero = it.net < Satang.Zero)
                },
                transactions = transactions.map { it.asRow(categories, subCategories).copy(caught = it.id == extras.caughtId) },
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
                compared = home.usualDay?.asCompared().takeIf { coach == null && weeklyCard == null },
                moodCard = if (extras.mood && chosen == HomePeriod.Today) getMoodRecap(home.history, now)?.asMoodCard() else null,
                weeklyCard = weeklyCard,
                target = home.target?.targetLine(categories),
                play = if (onToday) extras.play.strip(queued.size) else null,
                question = if (onToday) extras.play.questionCard() else null,
                coach = coach,
                challenge = if (onToday) extras.play.challengeRow(categories) else null,
                shelf = if (onToday) extras.play.shelfItems() else emptyList(),
                jar = if (onToday) extras.play.jarCard(pressed = extras.countedNow && extras.playMotion) else null,
                firstWeekLine = AppText.copy.play.extras.firstWeek.takeIf { onToday && extras.play.firstWeek },
                caught = extras.caughtId != null && transactions.any { it.id == extras.caughtId },
                playMotion = extras.playMotion,
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
        action = text.checkSlips(total),
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

private data class Weekly(val on: Boolean, val target: WeeklyTarget?, val note: WeeklyNoteSettings, val play: TodayPlay)

private data class Extras(
    val scanning: Boolean,
    val folders: List<WatchedFolder>,
    val mood: Boolean,
    val weekly: Boolean,
    val target: WeeklyTarget?,
    val note: WeeklyNoteSettings,
    val play: TodayPlay,
    val caughtId: String?,
    val playMotion: Boolean,
    val countedNow: Boolean,
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
