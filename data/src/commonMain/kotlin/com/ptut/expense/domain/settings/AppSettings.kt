package com.ptut.expense.domain.settings

import com.ptut.expense.domain.EvidenceRetention
import com.ptut.expense.domain.HomeMoney
import com.ptut.expense.domain.WeeklyTarget
import com.ptut.expense.domain.slip.SlipOwner
import com.ptut.expense.locale.NoteTone
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

/**
 * Every app-wide choice the reader may move (import, mood, app lock, currency and more), as one record.
 *
 * They travel together because one store holds them all, written whole.
 * The defaults are the shipped behaviour: the importer runs, and NFR-4's seven days is what a
 * failed slip's picture gets — a fresh install behaves as it did before this screen existed.
 */
data class AppChoices(
    val backgroundImport: Boolean = true,
    val retention: Duration = EvidenceRetention,
    /** Morning recap and evening receipt by the clock (#121, #122). On, as p21 ships it. */
    val moodByTimeOfDay: Boolean = true,
    /** The nav pill frosted over the list (#123). On, as p21 ships it; off draws it solid. */
    val transparency: Boolean = true,
    /** Fingerprint, face or phone PIN before the ledger shows (#25). Off until switched on. */
    val appLock: Boolean = false,
    /** Monday's card on Today about last week (#20). On, so the check-in is there to find. */
    val weeklyCheckIn: Boolean = true,
    /** #21: the one spend-less target, or null with none set. */
    val weeklyTarget: WeeklyTarget? = null,
    /** #26: your name as slips print it, learned from your own slips or set by hand. */
    val slipOwner: SlipOwner = SlipOwner(),
    /** #22: how the weekly note sounds. */
    val noteTone: NoteTone = NoteTone.Plain,
    /** #22: the Monday (ISO date) whose note the reader hid; that week shows none. */
    val hiddenNoteWeek: String? = null,
    /**
     * #41: a copy of the ledger's home currency and zone, read at launch before the database is
     * open. Null on every phone set up before #41, which is baht and Bangkok.
     */
    val homeCurrency: String? = null,
    val homeZone: String? = null,
)

/**
 * Where [AppChoices] survives a process death. `SharedPreferences` on Android,
 * `NSUserDefaults` on iOS — the whole record either way, so a half-written pair is not a state.
 *
 * Read synchronously for the reason [com.ptut.expense.onboarding.OnboardingFlag] is: the importer
 * asks whether it may run before any screen exists to await an answer.
 */
interface AppSettingsStore {

    fun read(): AppChoices

    fun write(choices: AppChoices)
}

/**
 * What "Keep failed slips" offers, shortest first.
 *
 * There is no "keep for ever". A slip picture carries the account number NFR-1 promises is never
 * stored, so the promise is what bounds this list — thirty days is a long retry window, and
 * indefinite is a different promise.
 */
val RetentionChoices: List<Duration> = listOf(3.days, EvidenceRetention, 14.days, 30.days)

/**
 * The live copy of [AppChoices], shared by everything that reads one.
 *
 * A `single` holding a flow rather than a getter, for the reason [com.ptut.expense.onboarding.Onboarding]
 * is one: several callers need the same answer and two of them write it. The sweep asks how long a
 * picture lives, the schedule asks whether it may run, and two screens change both.
 */
class AppSettings(private val store: AppSettingsStore) {

    private val current = MutableStateFlow(store.read())

    val choices: StateFlow<AppChoices> = current.asStateFlow()

    fun setBackgroundImport(running: Boolean) = update { it.copy(backgroundImport = running) }

    fun setRetention(retention: Duration) = update { it.copy(retention = retention) }

    fun setMoodByTimeOfDay(on: Boolean) = update { it.copy(moodByTimeOfDay = on) }

    fun setTransparency(on: Boolean) = update { it.copy(transparency = on) }

    fun setAppLock(on: Boolean) = update { it.copy(appLock = on) }

    fun setWeeklyCheckIn(on: Boolean) = update { it.copy(weeklyCheckIn = on) }

    /** Null clears it. A target on another category replaces the old one: there is only ever one. */
    fun setWeeklyTarget(target: WeeklyTarget?) = update { it.copy(weeklyTarget = target) }

    /** #26: a sender read off a slip the bank app saved, so it is yours. */
    fun learnSlipOwner(sender: String) = update { it.copy(slipOwner = it.slipOwner.learnFrom(sender)) }

    /** #26: set by hand, or cleared to start learning again. */

    /** #22 */
    fun setNoteTone(tone: NoteTone) = update { it.copy(noteTone = tone) }

    /** #22: hides the note for the week starting [monday]; a new week shows its own. */
    fun hideNoteWeek(monday: String) = update { it.copy(hiddenNoteWeek = monday) }

    /**
     * #41: the weekly target was typed in the old home's money, so a new home clears it, unless
     * #47 [converted] it along with the rows. One write, so a death cannot split the two.
     */
    fun setHome(currency: String, zone: String, converted: WeeklyTarget? = null) = update {
        // Null is a phone set up before #41, which was baht.
        val moved = (it.homeCurrency ?: HomeMoney.Baht.code) != currency
        it.copy(homeCurrency = currency, homeZone = zone, weeklyTarget = if (moved) converted else it.weeklyTarget)
    }

    fun setSlipOwner(name: String?) = update { it.copy(slipOwner = SlipOwner(name = name?.trim()?.takeIf(String::isNotEmpty))) }

    /**
     * Written before it is announced, like the first-run flag. If the process dies between the two
     * the reader finds the old answer, which is the harmless way round — a screen showing a choice
     * the sweep never got told about is not.
     */
    private fun update(change: (AppChoices) -> AppChoices) {
        val next = change(current.value)
        if (next == current.value) return
        store.write(next)
        current.value = next
    }
}
