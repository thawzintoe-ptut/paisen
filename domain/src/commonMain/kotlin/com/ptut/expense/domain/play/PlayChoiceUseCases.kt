package com.ptut.expense.domain.play

import com.ptut.expense.domain.settings.AppSettings
import com.ptut.expense.domain.settings.ObserveAppChoicesUseCase
import com.ptut.expense.domain.settings.PlayChoices
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** #83: p22's switches as they change. */
class ObservePlayChoicesUseCase(private val choices: ObserveAppChoicesUseCase) {
    operator fun invoke(): Flow<PlayChoices> = choices().map { it.play }.distinctUntilChanged()

    /** The switches as they stand, for a screen's first frame. */
    fun now(): PlayChoices = choices().value.play
}

/** #83: one switch changed against the stored set, which is written whole. */
class SetPlayChoicesUseCase(private val settings: AppSettings) {
    operator fun invoke(change: (PlayChoices) -> PlayChoices) = settings.setPlay(change(settings.choices.value.play))
}

/**
 * #83: whether [PlayPart] shows now: the build offers it and the reader's switches let it. The
 * master switch changes at runtime, so this is a flow rather than a value read once.
 */
class ObservePlayOnUseCase(
    private val choices: ObserveAppChoicesUseCase,
    private val gate: PlayGate = PlayGate.Off,
) {
    operator fun invoke(part: PlayPart): Flow<Boolean> =
        choices().map { gate.offers(part) && it.play.allows(part) }.distinctUntilChanged()

    /** For a screen built once: the answer as it stands. */
    fun now(part: PlayPart): Boolean = gate.offers(part) && choices().value.play.allows(part)

    /** Whether this build offers [part] at all, before the reader's switches. */
    fun offered(part: PlayPart): Boolean = gate.offers(part)

    /** Whether [tip] shows now: the build and switches allow it and the reader has not seen it. */
    fun tip(tip: PlayTip): Flow<Boolean> = choices().map { it.play.shows(tip, gate) }.distinctUntilChanged()

    /** Whether this build shows tips at all (the `tips_on` flag). */
    fun tipsOffered(): Boolean = gate.tipsOn && gate.flagOn
}
