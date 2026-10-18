package com.ptut.expense.design

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * One theme's complete token set. [Sky]'s values are measured from `App Sky Design.pdf` (#143)
 * and [SkyNight] is derived from them (#145).
 *
 * Material 3 has roles for roughly half of these; the rest reach screens through
 * [PtutColors]. Nothing here is regenerated from a seed.
 */
@Immutable
data class Palette(
    val surface: Color,
    val container: Color,
    val containerHigh: Color,
    val onSurface: Color,
    val onVar: Color,
    val primary: Color,
    val onPrimary: Color,
    val accent: Color,
    val accentOn: Color,
    /** Ruby beside ink and white: money going out, slips waiting, Add. A fill; [signatureInk] is its text. */
    val signature: Color,
    val onSignature: Color,
    val signatureInk: Color,
    val heroBg: Color,
    val heroOn: Color,
    val heroInner: Color,
    val navBg: Color,
    val pillShadow: Color,
    /** The light catching the frosted nav pill's top edge and rim (#123), iOS glass style. */
    val glassHighlight: Color,
    val outline: Color,
    val barTrack: Color,
    val stripe: Color,
    val slipCap: Color,
    val warn: Color,
    val warnInk: Color,
    val warnOn: Color,
    val warnBg: Color,
    val ok: Color,
    val sheetBg: Color,
    val toastBg: Color,
    val toastOn: Color,
    /** The pulse dot: the importer is running, and only that. */
    val live: Color,
    /** The check that draws in the toast, apart from the toast's words. */
    val toastCheck: Color,
    /** Something to know before acting. Never a warning. */
    val infoBg: Color,
    val infoInk: Color,
    /** The six a category may wear. Held per theme, because a fill has to sit against a surface. */
    val categoryTints: CategoryTints,
)

/**
 * Sky (#143), from `App Sky Design.pdf`: ink carries the weight, sky is the soft accent, ruby marks
 * money going out, and alert red is only ever a warning. Light values are measured from the
 * artboards; ruby is not in them.
 *
 * The page is grey and the cards white. The fields name the role the views read, so
 * [Palette.surface] holds p02's `container` grey and [Palette.container] its white.
 */
val Sky = Palette(
    surface = Color(0xFFF4F6F8),
    container = Color(0xFFFFFFFF),
    containerHigh = Color(0xFFE4E7EA),
    onSurface = Color(0xFF0B0B0C),
    // The artboard grey #6B7178 is 3.97:1 on containerHigh and 4.15:1 on warnBg, both under body
    // text. The same grey taken down until it clears 4.5 on every fill.
    onVar = Color(0xFF63686F),
    primary = Color(0xFF0B0B0C),
    onPrimary = Color(0xFFFFFFFF),
    accent = Color(0xFFA9DCF7),
    accentOn = Color(0xFF0B0B0C),
    // A deep crimson, 15° of hue away from alert red so the two never read as one warning: 8.1:1 on white.
    signature = Color(0xFF9B1B33),
    onSignature = Color(0xFFFFFFFF),
    signatureInk = Color(0xFF9B1B33),
    heroBg = Color(0xFFA9DCF7),
    heroOn = Color(0xFF0B0B0C),
    heroInner = Color(0x59FFFFFF),
    navBg = Color(0xFFFFFFFF),
    pillShadow = Color(0x290B0B0C),
    glassHighlight = Color(0xFFFFFFFF),
    outline = Color(0x1F63686F),
    barTrack = Color(0xFFD8EFFB),
    stripe = Color(0x290B0B0C),
    slipCap = Color(0xEBF4F6F8),
    warn = Color(0xFFC8372D),
    // Alert red is 4.37:1 on its own tint, under the body floor. The same red taken just far
    // enough down to carry a sentence there: 4.52 on warnBg, 5.37 on white.
    warnInk = Color(0xFFC4362C),
    warnOn = Color(0xFFFFFFFF),
    warnBg = Color(0xFFFBE7E5),
    ok = Color(0xFF1F7A55),
    sheetBg = Color(0xFFF4F6F8),
    // Ink, not p33's #0D1615, which has a green tinge.
    toastBg = Color(0xFF0B0B0C),
    toastOn = Color(0xFFFFFFFF),
    live = Color(0xFF15704F),
    toastCheck = Color(0xFFA9DCF7),
    infoBg = Color(0xFFEEF7FD),
    infoInk = Color(0xFF0B0B0C),
    categoryTints = SkyCategoryTints,
)

/**
 * [Sky] after 18:00 (p08, #122): only the five neutrals warm. Ink, sky and alert red never change,
 * so a colour means the same thing at any hour. Sky gives no values; these keep every text pair
 * at or above the day palette's contrast (onVar on containerHigh 4.56:1, day 4.52:1).
 */
val SkyEvening = Sky.copy(
    surface = Color(0xFFF7F4EF),
    container = Color(0xFFFFFDFA),
    containerHigh = Color(0xFFEBE7E1),
    sheetBg = Color(0xFFF7F4EF),
    navBg = Color(0xFFFFFDFA),
)

/**
 * Dark pair for [Sky], derived because Sky has no dark artboards (#145). The ground is darkest and
 * cards lighter. Ink's text and fills turn light with ink on them; sky and the red fill stay; red
 * and green foregrounds lift until they read on a dark ground.
 * `SkyKeepsItsMeaningAtNightTest` holds the parts of that rule an edit could break on one side.
 */
val SkyNight = Palette(
    surface = Color(0xFF0B0B0C),
    container = Color(0xFF17191C),
    containerHigh = Color(0xFF25282D),
    onSurface = Color(0xFFF4F6F8),
    onVar = Color(0xFF9AA0A6),
    primary = Color(0xFFF4F6F8),
    onPrimary = Color(0xFF0B0B0C),
    accent = Color(0xFFA9DCF7),
    accentOn = Color(0xFF0B0B0C),
    // Day ruby is 2.2:1 on the night bar; lifted until Add stands off it at 3.2:1, white still 5.5:1 on it.
    signature = Color(0xFFC2304E),
    onSignature = Color(0xFFFFFFFF),
    // The fill is 3.2:1 on a night card, under the body floor for an amount; this rose reads 6.6:1.
    signatureInk = Color(0xFFE3828F),
    // Sky's hue at a depth that does not light up a dark room.
    heroBg = Color(0xFF153647),
    heroOn = Color(0xFFF4F6F8),
    heroInner = Color(0x26A9DCF7),
    navBg = Color(0xFF17191C),
    pillShadow = Color(0x8C000000),
    glassHighlight = Color(0xFFFFFFFF),
    outline = Color(0x1FF4F6F8),
    barTrack = Color(0x40A9DCF7),
    stripe = Color(0x38F4F6F8),
    slipCap = Color(0xEB0B0B0C),
    warn = Color(0xFFC8372D),
    warnInk = Color(0xFFDA635A),
    warnOn = Color(0xFFFFFFFF),
    warnBg = Color(0xFF3A1714),
    ok = Color(0xFF2FB981),
    sheetBg = Color(0xFF0B0B0C),
    // A light toast would hide the sky check, so this one stays dark.
    toastBg = Color(0xFF2E3238),
    toastOn = Color(0xFFF4F6F8),
    live = Color(0xFF2FB981),
    toastCheck = Color(0xFFA9DCF7),
    infoBg = Color(0xFF142934),
    infoInk = Color(0xFFF4F6F8),
    categoryTints = SkyNightCategoryTints,
)

/**
 * A QR code is printed matter, the same in every theme: black modules on white, or a scanner
 * cannot read it (#190, #199). On a night surface the code would vanish.
 */
val QrPaper = Color(0xFFFFFFFF)
