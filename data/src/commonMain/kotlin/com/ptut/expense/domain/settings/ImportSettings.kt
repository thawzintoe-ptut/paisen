package com.ptut.expense.domain.settings

import com.ptut.expense.domain.EvidenceRetention
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

/**
 * The two import levers the reader may move, as one record.
 *
 * They travel together because one store holds both and both are read on the same screen's parent.
 * The defaults are the shipped behaviour: the importer runs, and NFR-4's seven days is what a
 * failed slip's picture gets — a fresh install behaves as it did before this screen existed.
 */
data class ImportChoices(
    val backgroundImport: Boolean = true,
    val retention: Duration = EvidenceRetention,
    /** Morning recap and evening receipt by the clock (#121, #122). On, as p21 ships it. */
    val moodByTimeOfDay: Boolean = true,
    /** The nav pill frosted over the list (#123). On, as p21 ships it; off draws it solid. */
    val transparency: Boolean = true,
)

/**
 * Where [ImportChoices] survives a process death. `SharedPreferences` on Android,
 * `NSUserDefaults` on iOS — the whole record either way, so a half-written pair is not a state.
 *
 * Read synchronously for the reason [com.ptut.expense.onboarding.OnboardingFlag] is: the importer
 * asks whether it may run before any screen exists to await an answer.
 */
interface ImportSettingsStore {

    fun read(): ImportChoices

    fun write(choices: ImportChoices)
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
 * The live copy of [ImportChoices], shared by everything that reads one.
 *
 * A `single` holding a flow rather than a getter, for the reason [com.ptut.expense.onboarding.Onboarding]
 * is one: several callers need the same answer and two of them write it. The sweep asks how long a
 * picture lives, the schedule asks whether it may run, and two screens change both.
 */
class ImportSettings(private val store: ImportSettingsStore) {

    private val current = MutableStateFlow(store.read())

    val choices: StateFlow<ImportChoices> = current.asStateFlow()

    fun setBackgroundImport(running: Boolean) = update { it.copy(backgroundImport = running) }

    fun setRetention(retention: Duration) = update { it.copy(retention = retention) }

    fun setMoodByTimeOfDay(on: Boolean) = update { it.copy(moodByTimeOfDay = on) }

    fun setTransparency(on: Boolean) = update { it.copy(transparency = on) }

    /**
     * Written before it is announced, like the first-run flag. If the process dies between the two
     * the reader finds the old answer, which is the harmless way round — a screen showing a choice
     * the sweep never got told about is not.
     */
    private fun update(change: (ImportChoices) -> ImportChoices) {
        val next = change(current.value)
        if (next == current.value) return
        store.write(next)
        current.value = next
    }
}
