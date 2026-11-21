package com.ptut.expense.play

import com.ptut.expense.domain.dayLabel
import com.ptut.expense.domain.play.SetProgress
import com.ptut.expense.domain.play.Stamp
import com.ptut.expense.domain.play.StampKind
import com.ptut.expense.domain.play.StampSet
import com.ptut.expense.locale.AppText
import com.ptut.expense.locale.PlayStampsCopy
import com.ptut.expense.ui.play.StampTone

/** A stamp as every play screen draws it: its face, its small caption, its tone and what a screen reader says. */
data class StampUiModel(
    val id: String,
    val face: String,
    val caption: String,
    val tone: StampTone,
    val label: String,
    /** D3: the pattern behind a rare stamp, or null for a common one. */
    val rareLine: String?,
)

data class SetProgressUiModel(val name: String, val line: String, val count: String, val note: String, val fraction: Float)

internal fun Stamp.asUiModel(text: PlayStampsCopy = AppText.copy.play.stamps): StampUiModel = StampUiModel(
    id = id,
    face = text.faces[kind.name].orEmpty(),
    caption = kind.caption(text),
    tone = if (kind.rare) StampTone.Sky else StampTone.Ink,
    label = text.stampLabel(text.names[kind.name].orEmpty(), earnedOn.dayLabel()),
    rareLine = text.rareLines[kind.name],
)

/** The book shows a stamp not yet turned as a card still face down, so it gives nothing away before the reveal. */
internal fun Stamp.asBookUiModel(text: PlayStampsCopy = AppText.copy.play.stamps): StampUiModel =
    if (revealedAtEpochMillis != null) {
        asUiModel(text)
    } else {
        StampUiModel(id = id, face = FACE_DOWN, caption = text.faceDownCaption, tone = StampTone.Ink, label = text.cardLabel, rareLine = null)
    }

private const val FACE_DOWN = "?"

/** The outline of a kind still to earn. */
internal fun StampKind.asEmptySlot(text: PlayStampsCopy = AppText.copy.play.stamps): StampUiModel = StampUiModel(
    id = name,
    face = text.faces[name].orEmpty(),
    caption = caption(text),
    tone = StampTone.Empty,
    label = text.emptyLabel(text.names[name].orEmpty()),
    rareLine = null,
)

internal fun SetProgress.asUiModel(text: PlayStampsCopy = AppText.copy.play.stamps): SetProgressUiModel {
    val name = set.label(text)
    return SetProgressUiModel(
        name = name,
        line = text.setLine(name, have, of),
        count = text.setCount(have, of),
        note = if (complete) text.setDone else text.setLeft(of - have),
        fraction = have.ratio(of),
    )
}

internal fun StampSet.label(text: PlayStampsCopy = AppText.copy.play.stamps): String = text.sets[name].orEmpty()

private fun StampKind.caption(text: PlayStampsCopy): String = when {
    set == StampSet.Wins -> text.captionWin
    rare -> text.captionRare
    else -> text.captionTray
}

/** A share of whole counts, for a meter. Counts are not money, so the money rule's ratio does not apply. */
internal fun Int.ratio(of: Int): Float = if (of <= 0) 0f else this / (of * 1f)
