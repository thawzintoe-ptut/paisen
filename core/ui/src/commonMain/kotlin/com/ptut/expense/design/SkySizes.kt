package com.ptut.expense.design

import androidx.compose.ui.unit.dp

/**
 * Sky's control sizes (#151), measured off the 390pt crops at 1pt = 1.646px. [Sizes] holds the floors
 * and the sizes no artboard sets. A control painted under 44 pads its tap area to the floor.
 */
object SkySizes {
    /** SAVE, CONFIRM, NOT MINE, DELETE: 52 on the add sheet (p10), confirm (p13) and folders (p22). */
    val button = 52.dp

    /** The ink edge on NOT MINE and KEEP IT, and the red one on an outlined DELETE. */
    val buttonBorder = 1.dp

    /** A category chip on confirm and edit (p13, p18). The add sheet draws it a step shorter (p10). */
    val chip = 38.dp
    val sheetChip = 36.dp
    val chipBorder = 1.dp

    /** A sub-category chip on the grey box under the categories (p18), and its chosen tick. */
    val subChip = 32.dp
    val subChipTick = 16.dp

    /** Expense / Income (p10): a 36 track with the chosen segment 3 inside it. */
    val segmentedTrack = 36.dp
    val segmentInset = 3.dp

    /** Home's Today / Week / Month (p05), the ink line under the chosen one and the rule under all. */
    val tab = 48.dp
    val tabUnderline = 2.dp
    val hairline = 1.dp

    /** The Settings switch (p21): a 44 by 26 track and a 20 knob, 3 in from its edge. */
    val switchWidth = 44.dp
    val switchHeight = 26.dp
    val switchKnob = 20.dp
    val switchInset = 3.dp

    /** The rounded checkbox on edit and photo folders (p18, p22), and its tick. */
    val checkbox = 24.dp
    val checkboxCorner = 7.dp
    val checkboxTick = 16.dp

    /** History's search pill (p15) and its magnifier. */
    val search = 44.dp
    val searchGlyph = 20.dp

    /** "+ NEW CATEGORY" (p19): its height, plus glyph and dash. */
    val addRow = 54.dp
    val addGlyph = 20.dp
    val dash = 3.dp
    val dashGap = 2.dp

    /** A transaction row (p05, p15): 56 a row, the category circle 40 with its glyph at 20. */
    val transactionRow = 56.dp
    val rowCircle = 40.dp
    val rowGlyph = 20.dp

    /** The arrow beside money that came in, so income differs from spending by more than colour. */
    val moneyInArrow = 14.dp

    /** The ruby dot on a group row another member added since the last visit. */
    val newDot = 8.dp

    /** A label and its value on confirm and detail (p13, p16), hairline included. */
    val keyValueRow = 47.dp

    /** The glyph on a notice card, a banner, a stat card and beside a section title. */
    val cardGlyph = 20.dp

    /** The red Fix pill on the stale banner (p06), painted inside a 44 tap area. */
    val bannerAction = 34.dp

    /** A sheet's corner: p02 puts it in the 31–34 band, and the crops of p05 and p17 measure 32. */
    val sheetCorner = 32.dp

    /** The grey handle at the top of a sheet (p10, p17). */
    val sheetHandleWidth = 36.dp
    val sheetHandleHeight = 4.dp

    /** NOT COUNTED on a review card (p08), and the confidence badge on confirm (p13) with its tick. */
    val statusPill = 22.dp
    val confidenceBadge = 26.dp
    val badgeTick = 14.dp

    /** Room over the tallest bar for the selected figure, so it never runs off the top of the band. */
    val chartHeadroom = 20.dp

    /** How far the average's label sits over its line. */
    val chartLabelLift = 18.dp

    /** The app's mark on first run (p23). */
    val brandMark = 30.dp

    /** The empty state's illustration slot (p24): 342 by 220 on a 390 screen, kept as a ratio. */
    const val illustrationRatio = 342f / 220f

    /** The delete sheet's slot (p17): 322 by 130, wider and shorter than the empty state's. */
    const val deleteIllustrationRatio = 322f / 130f

    /** First run's slot (p23): 334 by 190. */
    const val firstRunIllustrationRatio = 334f / 190f

    /** p27's floating ink add button on a group, 46 of the artboard's 0.75 scale. */
    val groupFab = 60.dp

    /** p28's amount, wide enough for ₫1,850,000 at hero size. */
    val amountField = 240.dp

    /** The rate under it (#181), and a member's field on p29's split. */
    val rateField = 120.dp
    val weightField = 132.dp

    /** #200: the progress ring beside "Making a link…" and the like, and its stroke. */
    val progress = 20.dp
    val progressStroke = 2.dp

    /** #190's invite QR, large enough for a camera across a table. */
    val inviteQr = 200.dp

    /** p31's payer and payee, the largest initials v2 draws. */
    val payAvatar = 64.dp

    /** The waiting arc over the slot (p33), drawn at the size that page shows it. */
    val waitingArc = 64.dp

    /** The app's mark in an empty slot with no art yet: small enough to sit inside [waitingArc]. */
    val emptyStateMark = 28.dp

    /** The top bar (p13, p16, p18, p19): 56 tall, its glyphs at 24. */
    val topBar = 56.dp
    val barGlyph = 24.dp

    /**
     * The nav pill (p05): five 44 circles, 6 apart and 6 in from the pill's edge, so the pill is 56
     * tall. It floats 12 above the system's own bottom inset.
     */
    val pillSlot = 44.dp
    val pillGap = 6.dp
    val pillGlyph = 24.dp
    val pillMargin = 12.dp
    val pillShadow = 12.dp

    /** How far glass blurs what scrolls under it (#123). Sky gives no value; iOS glass blurs this hard. */
    val glassBlur = 28.dp

    /**
     * What the end of a scroll leaves free under the pill: its 12 of margin, its 56 and 20 more, so
     * the last row comes to rest clear of it rather than under it.
     */
    val pillClearance = 88.dp

    /**
     * Reports' chart on the sky field (p14): bars in a 220 band, an empty bucket as a 6 stub (p15),
     * the selected bar's ink edge and the gap between its hatch lines.
     */
    val chart = 220.dp
    val chartStub = 6.dp
    val chartBarCorner = 6.dp
    val chartEdge = 1.dp
    val chartHatch = 6.dp

    /** W / M / 3M / 6M / Y (p14): a 44 track, the chosen period a white pill 4 inside it. */
    val periodTrack = 44.dp
    val periodInset = 4.dp

    /** A keypad key (p10, p13): grey, 48 tall, the twelve of them 8 apart. */
    val key = 48.dp

    /** The proportion bar over Reports' top categories (p14), and the gap between its parts. */
    val shareBar = 8.dp
    val shareGap = 4.dp
}
