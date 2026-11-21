package com.ptut.expense.design

import androidx.compose.ui.unit.dp

/** Play's sizes (#84), from Design V3's `play.css`, as [SkySizes] holds Sky's. */
object PlaySizes {
    /** A stamp in a grid or on a card, and the big one a turned card shows (p08). */
    val stamp = 72.dp
    val stampBig = 132.dp

    /** The stamp's outer ring, and its thin inner ring and where that sits in from the edge. */
    val stampRing = 2.dp
    val stampInnerRing = 1.dp
    val stampInnerInset = 6.dp
    val stampEmptyRing = 1.5.dp

    /** The stamp's text, sized to the stamp (art, not body text): its face and the caption under it. */
    val stampFace = 20.dp
    val stampFaceBig = 34.dp
    val stampCaption = 8.dp
    val stampCaptionBig = 11.dp

    /** A day on the run card's week (Main). */
    val dayDot = 32.dp
    val dayRing = 2.dp

    /** The face-down card (p08, p02). */
    val flipWidth = 240.dp
    val flipHeight = 320.dp
    val flipEdge = 1.dp

    /** A meter's track. */
    val meter = 10.dp

    /** PaiSeN's disc beside its guess, a freeze token, and the badges that say who is closer. */
    val aiDisc = 28.dp
    val token = 28.dp

    /** The day's question cards (p07), a challenge option (p05), a medal (p12). */
    val pick = 132.dp
    val option = 72.dp
    val medal = 48.dp

    /** The − / + steps beside the guess, and the shortest a play row or button may be. */
    val step = 44.dp

    /** A versus row on the reveal (p02), and a plan row on Who pays whom (p14). */
    val versusRow = 52.dp
    val planRow = 56.dp

    /** The month story's bars (p03). */
    val bar = 3.dp

    /** The who-pays wheel (p11) and the goal jar (p06). */
    val wheel = 260.dp
    val wheelPointer = 18.dp
    val jarWidth = 180.dp
    val jarHeight = 225.dp

    /** The avatar on a reaction pill. */
    val reactAvatar = 20.dp

    /** The wheel's centre hub (p11). */
    val wheelHub = 14.dp

    /** The money score's ring (p21) and its stroke. */
    val scoreRing = 132.dp
    val scoreStroke = 12.dp

    /** A challenge option's radio ring, and its ring once picked (`.pl-radio`). */
    val radio = 22.dp
    val radioPicked = 7.dp

    /** A just-in-time tip: its arrow, how far the arrow sits in from the card's edge, and its slide. */
    val tipArrow = 14.dp
    val tipArrowInset = 32.dp
    val tipSlide = 8.dp
}
