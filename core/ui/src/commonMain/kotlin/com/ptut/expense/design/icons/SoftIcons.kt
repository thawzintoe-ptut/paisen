package com.ptut.expense.design.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * PaiSeN's own icon family (#108, V2 board N06): 24 grid, 2 safe edge, 1.6 outline, round caps
 * and joins, soft corners on every shape so the set sits beside the rounded nav capsule.
 *
 * The first block is the path data the V2 screens draw, copied unedited. The second block has no
 * V2 drawing yet (mostly category-picker choices) and was drawn here to the same rules; replace a
 * path with the design's own when V2 adds it.
 *
 * Detail dots are drawn at [DOT_WIDTH], so a 0.01 line reads as a dot at 20dp.
 */
internal object SoftIcons {
    // Drawn in the V2 screens.
    val ChevronRight = soft("ChevronRight", "M9.5 6.5 15 12l-5.5 5.5")

    val Bowl = soft("Bowl", "M4 11.5h16c0 4.4-3.6 8-8 8s-8-3.6-8-8Z", "M9 4c-.8 1 .8 2 0 3.2", "M12.2 4c-.8 1 .8 2 0 3.2", "M15.4 4c-.8 1 .8 2 0 3.2")

    val Cart = soft("Cart", "M3 4.5h1.4c.8 0 1.4.5 1.5 1.3L7.1 14c.1.9.9 1.5 1.7 1.5h8.1c.8 0 1.5-.5 1.7-1.3l1.3-5.4c.2-.9-.4-1.8-1.4-1.8H6.3", "M7.7 19.4a1.3 1.3 0 1 0 2.6 0a1.3 1.3 0 1 0 -2.6 0Z", "M15.5 19.4a1.3 1.3 0 1 0 2.6 0a1.3 1.3 0 1 0 -2.6 0Z")

    val ArrowLeft = soft("ArrowLeft", "M10 6.5 4.5 12l5.5 5.5", "M5 12h14.5")

    val Car = soft("Car", "M4 16v-3.4c0-.6.2-1.2.6-1.7l2-2.7c.5-.6 1.2-1 2-1h6.8c.8 0 1.5.4 2 1l2 2.7c.4.5.6 1.1.6 1.7V16c0 1-.8 1.8-1.8 1.8H5.8C4.8 17.8 4 17 4 16Z", "M4.6 11.5h14.8", "M7.5 17.8v1.7M16.5 17.8v1.7", dots = "M7.6 14.5h.01M16.4 14.5h.01")

    val Dots = soft("Dots", dots = "M6 12h.01M12 12h.01M18 12h.01")

    val Pencil = soft("Pencil", "M11 4.5H8.5c-2.9 0-4 1.1-4 4v7c0 2.9 1.1 4 4 4h7c2.9 0 4-1.1 4-4V13", "M17.2 4.7a1.9 1.9 0 0 1 2.7 2.7l-6.5 6.5-3.5.8.8-3.5Z")

    val Cross = soft("Cross", "M7 7l10 10", "M17 7 7 17")

    val Plus = soft("Plus", "M12 5.5v13", "M5.5 12h13")

    val Tick = soft("Tick", "M5.5 12.5l4.2 4.2L18.5 7.5")

    val Sparkle = soft("Sparkle", "M11 3.5c.6 3.9 2.6 5.9 6.5 6.5-3.9.6-5.9 2.6-6.5 6.5-.6-3.9-2.6-5.9-6.5-6.5 3.9-.6 5.9-2.6 6.5-6.5Z", "M18 15.5c.2 1.4.9 2.1 2.3 2.3-1.4.2-2.1.9-2.3 2.3-.2-1.4-.9-2.1-2.3-2.3 1.4-.2 2.1-.9 2.3-2.3Z")

    val Banknote = soft("Banknote", "M6.5 6h11.0a3.5 3.5 0 0 1 3.5 3.5v5.0a3.5 3.5 0 0 1 -3.5 3.5h-11.0a3.5 3.5 0 0 1 -3.5 -3.5v-5.0a3.5 3.5 0 0 1 3.5 -3.5Z", "M9.6 12a2.4 2.4 0 1 0 4.8 0a2.4 2.4 0 1 0 -4.8 0Z", dots = "M6.6 12h.01M17.4 12h.01")

    val Tray = soft("Tray", "M4 13.5 5.9 7.4c.3-1.1 1.2-1.9 2.3-1.9h7.6c1.1 0 2 .8 2.3 1.9l1.9 6.1V16c0 2.3-1.2 3.5-3.5 3.5h-9C5.2 19.5 4 18.3 4 16Z", "M4 13.5h3.8c.6 0 1 .4 1.2.9.3.9 1.2 1.6 2.3 1.6h1.4c1.1 0 2-.7 2.3-1.6.2-.5.6-.9 1.2-.9H20")

    val Receipt = soft("Receipt", "M6 5.5c0-1.1.9-2 2-2h8c1.1 0 2 .9 2 2v15l-2.4-1.5-2.4 1.5-2.4-1.5-2.4 1.5L6 20.5Z", "M9.5 14.5l5-5", dots = "M9.7 9.7h.01M14.3 14.3h.01")

    val Bag = soft("Bag", "M5.2 9c.1-1.1 1-2 2.1-2h9.4c1.1 0 2 .9 2.1 2l.8 8.4c.1 1.7-1.2 3.1-2.9 3.1H7.3c-1.7 0-3-1.4-2.9-3.1Z", "M9 10V7a3 3 0 0 1 6 0v3")

    val Bin = soft("Bin", "M4.5 6.5h15", "M9.5 6.5V5c0-.8.7-1.5 1.5-1.5h2c.8 0 1.5.7 1.5 1.5v1.5", "M6 6.5l.8 11.3c.1 1.5 1.4 2.7 2.9 2.7h4.6c1.5 0 2.8-1.2 2.9-2.7L18 6.5", "M10.2 11v5M13.8 11v5")

    val House = soft("House", HOUSE, "M10 15.6h4")

    val TrendDown = soft("TrendDown", "M3.5 7.5l6 6 3.5-3.5 6.5 6.5", "M14.5 17h5.5v-5.5")

    val Warning = soft("Warning", "M10.56 6.31Q12.00 3.80 13.44 6.31L19.56 16.99Q21.00 19.50 18.12 19.50L5.88 19.50Q3.00 19.50 4.44 16.99Z", "M12 9.8v3.6", dots = "M12 16.4h.01")

    val Magnifier = soft("Magnifier", "M4.5 11a6.5 6.5 0 1 0 13.0 0a6.5 6.5 0 1 0 -13.0 0Z", "M16 16l3.8 3.8")

    val Sliders = soft("Sliders", "M4.5 8h6.5M15.5 8h4M4.5 16h3.5M12.5 16h7", "M11.1 8a2.1 2.1 0 1 0 4.2 0a2.1 2.1 0 1 0 -4.2 0Z", "M8.1 16a2.1 2.1 0 1 0 4.2 0a2.1 2.1 0 1 0 -4.2 0Z")

    val Folder = soft("Folder", "M4 8c0-1.9 1.1-3 3-3h2.6c.7 0 1.3.3 1.7.8l.9 1.2c.4.5 1 .8 1.7.8H17c1.9 0 3 1.1 3 3v5.2c0 1.9-1.1 3-3 3H7c-1.9 0-3-1.1-3-3Z")

    val Clock = soft("Clock", "M3.5 12a8.5 8.5 0 1 0 17.0 0a8.5 8.5 0 1 0 -17.0 0Z", "M12 7.5V12l3 2")

    val TickCircle = soft("TickCircle", "M3.5 12a8.5 8.5 0 1 0 17.0 0a8.5 8.5 0 1 0 -17.0 0Z", "M8.5 12.2l2.4 2.4 4.6-4.8")

    val Card = soft("Card", "M7.0 5.5h10.0a3.5 3.5 0 0 1 3.5 3.5v6.0a3.5 3.5 0 0 1 -3.5 3.5h-10.0a3.5 3.5 0 0 1 -3.5 -3.5v-6.0a3.5 3.5 0 0 1 3.5 -3.5Z", "M3.5 10h17", "M7 14.5h3")

    val Refresh = soft("Refresh", "M19.5 12a7.5 7.5 0 0 1-13.2 4.9", "M4.5 12a7.5 7.5 0 0 1 13.2-4.9", "M17.9 3.6v3.6h-3.6", "M6.1 20.4v-3.6h3.6")

    val Speech = soft("Speech", "M3.5 7.5c0-2.2 1.3-3.5 3.5-3.5h10c2.2 0 3.5 1.3 3.5 3.5v6c0 2.2-1.3 3.5-3.5 3.5h-4.5l-4 3.5V17H7c-2.2 0-3.5-1.3-3.5-3.5Z", "M9.6 13.5 12 7.5l2.4 6", "M10.4 11.6h3.2")

    val People = soft("People", "M6.3 8.2a3.2 3.2 0 1 0 6.4 0a3.2 3.2 0 1 0 -6.4 0Z", "M3.6 18.3c0-3 2.6-4.9 5.9-4.9s5.9 1.9 5.9 4.9c0 1-.7 1.7-1.7 1.7H5.3c-1 0-1.7-.7-1.7-1.7Z", "M14.4 6.6A2.5 2.5 0 1 1 15.45 10.55", "M17.6 13.6c1.9.4 3 1.8 3 3.7 0 .8-.5 1.4-1.3 1.6")

    val InfoCircle = soft("InfoCircle", "M3.5 12a8.5 8.5 0 1 0 17.0 0a8.5 8.5 0 1 0 -17.0 0Z", "M12 11v5", dots = "M12 8h.01")

    val Suitcase = soft("Suitcase", "M7.0 7.5h10.0a3.5 3.5 0 0 1 3.5 3.5v5.0a3.5 3.5 0 0 1 -3.5 3.5h-10.0a3.5 3.5 0 0 1 -3.5 -3.5v-5.0a3.5 3.5 0 0 1 3.5 -3.5Z", "M9 7.5V6c0-1.1.9-2 2-2h2c1.1 0 2 .9 2 2v1.5", "M3.5 12.5c5.6 2 11.4 2 17 0")

    val ChevronDown = soft("ChevronDown", "M6.5 9.5 12 15l5.5-5.5")

    val Calendar = soft("Calendar", "M7.5 5.5h9.0a3.5 3.5 0 0 1 3.5 3.5v7.5a3.5 3.5 0 0 1 -3.5 3.5h-9.0a3.5 3.5 0 0 1 -3.5 -3.5v-7.5a3.5 3.5 0 0 1 3.5 -3.5Z", "M4 10h16", "M8.5 3.5v3.5M15.5 3.5v3.5", dots = "M8.5 14.2h.01M12 14.2h.01M15.5 14.2h.01")

    val PersonAdd = soft("PersonAdd", "M6.8 8a3.2 3.2 0 1 0 6.4 0a3.2 3.2 0 1 0 -6.4 0Z", "M3.8 18.3c0-3 2.7-4.8 6.2-4.8s6.2 1.8 6.2 4.8c0 .9-.7 1.7-1.7 1.7H5.5c-1 0-1.7-.8-1.7-1.7Z", "M19 7.5v5M16.5 10h5")

    val Microphone = soft("Microphone", "M12 3.5h0a3 3 0 0 1 3 3v5a3 3 0 0 1 -3 3h-0a3 3 0 0 1 -3 -3v-5a3 3 0 0 1 3 -3Z", "M5.5 11.5a6.5 6.5 0 0 0 13 0", "M12 18v2.5")

    val ArrowDownLeft = soft("ArrowDownLeft", "M17.5 6.5 7 17", "M7 9.5V17h7.5")

    val Padlock = soft("Padlock", "M8 10.5h8a3 3 0 0 1 3 3v4a3 3 0 0 1 -3 3h-8a3 3 0 0 1 -3 -3v-4a3 3 0 0 1 3 -3Z", "M8 10.5V8a4 4 0 0 1 8 0v2.5", "M12 14.5v2")

    val Tag = soft("Tag", "M4 5.6V10c0 .7.3 1.3.8 1.8l7.4 7.4c.8.8 2 .8 2.8 0l4.2-4.2c.8-.8.8-2 0-2.8L11.8 4.8c-.5-.5-1.1-.8-1.8-.8H5.6C4.7 4 4 4.7 4 5.6Z", dots = "M8.2 8.2h.01")

    val DownloadTray = soft("DownloadTray", "M12 4v10.5", "M8 10.5l4 4 4-4", "M4.5 15.5v.5c0 2.2 1.3 3.5 3.5 3.5h8c2.2 0 3.5-1.3 3.5-3.5v-.5")

    val List = soft("List", "M9 7h10.5M9 12h10.5M9 17h10.5", dots = "M5 7h.01M5 12h.01M5 17h.01")

    val StopSquare = soft("StopSquare", "M9 5h6a4 4 0 0 1 4 4v6a4 4 0 0 1 -4 4h-6a4 4 0 0 1 -4 -4v-6a4 4 0 0 1 4 -4Z")

    val Heart = soft("Heart", "M12 19.5s-7.5-4.3-7.5-9.6C4.5 7.4 6.3 5.5 8.6 5.5c1.5 0 2.7.8 3.4 2 .7-1.2 1.9-2 3.4-2 2.3 0 4.1 1.9 4.1 4.4 0 5.3-7.5 9.6-7.5 9.6Z")

    val Chart = soft("Chart", CHART_FRAME, "M8.6 15.4v-2.2M12 15.4V8.8M15.4 15.4v-4")

    val Bolt = soft("Bolt", "M13.2 3.5 5.6 13.2c-.3.4 0 .9.5.9h5.4l-1 6.4 7.7-9.8c.3-.4 0-.9-.5-.9h-5.4Z")

    val Bell = soft("Bell", "M6.2 15.5V11a5.8 5.8 0 0 1 11.6 0v4.5l1.3 1.6c.4.5 0 1.4-.7 1.4H5.6c-.7 0-1.1-.9-.7-1.4Z", "M10 20.5c.4.6 1.1 1 2 1s1.6-.4 2-1")

    val Sun = soft("Sun", "M8.4 12a3.6 3.6 0 1 0 7.2 0a3.6 3.6 0 1 0 -7.2 0Z", "M18.00 12.00L20.00 12.00M16.24 16.24L17.66 17.66M12.00 18.00L12.00 20.00M7.76 16.24L6.34 17.66M6.00 12.00L4.00 12.00M7.76 7.76L6.34 6.34M12.00 6.00L12.00 4.00M16.24 7.76L17.66 6.34")

    val UploadTray = soft("UploadTray", "M12 15V4.5", "M8 8.5l4-4 4 4", "M4.5 15.5v.5c0 2.2 1.3 3.5 3.5 3.5h8c2.2 0 3.5-1.3 3.5-3.5v-.5")

    val QuestionCircle = soft("QuestionCircle", "M3.5 12a8.5 8.5 0 1 0 17.0 0a8.5 8.5 0 1 0 -17.0 0Z", "M9.8 9.6a2.3 2.3 0 0 1 4.4.9c0 1.5-2.2 2-2.2 3.3", dots = "M12 16.6h.01")

    val Book = soft("Book", "M12 7c-1.8-1.6-4.2-2.5-7-2.5h-.5V18H5c2.8 0 5.2.9 7 2.5 1.8-1.6 4.2-2.5 7-2.5h.5V4.5H19c-2.8 0-5.2.9-7 2.5Zm0 0v13")

    val Globe = soft("Globe", "M3.5 12a8.5 8.5 0 1 0 17.0 0a8.5 8.5 0 1 0 -17.0 0Z", "M3.5 12h17", "M12 3.5c2.3 2.4 3.4 5.2 3.4 8.5s-1.1 6.1-3.4 8.5c-2.3-2.4-3.4-5.2-3.4-8.5S9.7 5.9 12 3.5Z")

    val Hexagon = soft("Hexagon", HEXAGON, "M9.4 12a2.6 2.6 0 1 0 5.2 0a2.6 2.6 0 1 0-5.2 0Z")

    // Not in V2 yet: drawn to N06's rules.
    val ChevronUp = soft("ChevronUp", "M6.5 14.5 12 9l5.5 5.5")

    val TrendUp = soft("TrendUp", "M3.5 16.5l6-6 3.5 3.5 6.5-6.5", "M14.5 7h5.5v5.5")

    val TrendFlat = soft("TrendFlat", "M4 12h15.5", "M16 8.5l3.5 3.5-3.5 3.5")

    val TickDouble = soft("TickDouble", "M3.5 12.5l4 4L16 8", "M12 16l.5.5L21 8")

    val Exclaim = soft("Exclaim", "M3.5 12a8.5 8.5 0 1 0 17 0a8.5 8.5 0 1 0-17 0Z", "M12 7.8v4.8", dots = "M12 16h.01")

    val FunnelOff = soft("FunnelOff", "M5.6 5.5h12.8c.9 0 1.3 1 .8 1.7l-4.7 5.6v4.6c0 .4-.2.7-.5.9l-2.4 1.3c-.6.3-1.3-.1-1.3-.8v-6l-4.7-5.6c-.6-.7-.1-1.7.8-1.7Z", "M4 4l16 16")

    val Photo = soft("Photo", "M7.5 4.5h9a3 3 0 0 1 3 3v9a3 3 0 0 1-3 3h-9a3 3 0 0 1-3-3v-9a3 3 0 0 1 3-3Z", "M4.8 16 8.6 12.2c.6-.6 1.5-.6 2.1 0l3.3 3.3", "M13.2 14.5l1.3-1.3c.6-.6 1.5-.6 2.1 0l2.7 2.7", dots = "M15.3 8.7h.01")

    val Toggle = soft("Toggle", "M8 7h8a5 5 0 0 1 0 10H8a5 5 0 0 1 0-10Z", "M13.5 12a2.5 2.5 0 1 0 5 0a2.5 2.5 0 1 0-5 0Z")

    val BellZ = soft("BellZ", "M6.2 15.5V11a5.8 5.8 0 0 1 11.6 0v4.5l1.3 1.6c.4.5 0 1.4-.7 1.4H5.6c-.7 0-1.1-.9-.7-1.4Z", "M10 20.5c.4.6 1.1 1 2 1s1.6-.4 2-1", "M10.4 9h3.2l-3.2 4h3.2")

    val Bank = soft("Bank", "M4.6 8.9 11 5c.6-.4 1.4-.4 2 0l6.4 3.9c.5.3.3 1.1-.3 1.1H4.9c-.6 0-.8-.8-.3-1.1Z", "M6.5 10v7M10.2 10v7M13.8 10v7M17.5 10v7", "M4.5 19.5h15")

    val Wallet = soft("Wallet", "M7 7.5h10a3 3 0 0 1 3 3v6a3 3 0 0 1-3 3H7a3 3 0 0 1-3-3v-6a3 3 0 0 1 3-3Z", "M5 8.2l9.6-3.5c1-.4 2 .3 2 1.4v1.4", "M20 11.5h-3a2 2 0 0 0 0 4h3", dots = "M17 13.5h.01")

    val Office = soft("Office", "M6.5 20V6.5c0-1.7.8-2.5 2.5-2.5h6c1.7 0 2.5.8 2.5 2.5V20", "M4.5 20h15", "M10 20v-2.5c0-.6.4-1 1-1h2c.6 0 1 .4 1 1V20", dots = "M10 8h.01M14 8h.01M10 11.5h.01M14 11.5h.01")

    val Backspace = soft("Backspace", "M9.3 6h8.2a3 3 0 0 1 3 3v6a3 3 0 0 1-3 3H9.3c-.6 0-1.1-.3-1.5-.7L4 12.6c-.3-.4-.3-.8 0-1.2l3.8-4.7c.4-.4.9-.7 1.5-.7Z", "M11.8 9.8l4.4 4.4M16.2 9.8l-4.4 4.4")

    val Brush = soft("Brush", "M19.6 4.4c.6.6.6 1.5 0 2.1l-6.4 6.4-2.1-2.1 6.4-6.4c.6-.6 1.5-.6 2.1 0Z", "M11.1 10.8c-1.7-.4-3.3.6-3.6 2.3-.3 1.6-.5 3.5-3 4.4 2.6 1.5 6.2 1.2 7.8-.6.9-1 1.1-2.4.9-3.7")

    val Gift = soft("Gift", "M5.5 11h13v6.5c0 1.6-.9 2.5-2.5 2.5H8c-1.6 0-2.5-.9-2.5-2.5Z", "M5.5 7.5h13A1.5 1.5 0 0 1 20 9v.5a1.5 1.5 0 0 1-1.5 1.5h-13A1.5 1.5 0 0 1 4 9.5V9a1.5 1.5 0 0 1 1.5-1.5Z", "M12 7.5V20", "M12 7.5C10.8 5 8.2 4 7.6 5.6c-.5 1.4 1.8 1.9 4.4 1.9Zm0 0c1.2-2.5 3.8-3.5 4.4-1.9.5 1.4-1.8 1.9-4.4 1.9Z")

    val Screen = soft("Screen", "M6.5 4.5h11a3 3 0 0 1 3 3v6a3 3 0 0 1-3 3h-11a3 3 0 0 1-3-3v-6a3 3 0 0 1 3-3Z", "M12 16.5v3M8.5 19.5h7")

    val Scissors = soft("Scissors", "M4.5 7a2.5 2.5 0 1 0 5 0a2.5 2.5 0 1 0-5 0Z", "M4.5 17a2.5 2.5 0 1 0 5 0a2.5 2.5 0 1 0-5 0Z", "M9 8.5l10.5 9M9 15.5l10.5-9")

    val Trophy = soft("Trophy", "M7.5 4.5h9V9a4.5 4.5 0 0 1-9 0Z", "M7.5 6.5H5.8c-.7 0-1.3.6-1.2 1.3.3 1.9 1.4 3.2 3.2 3.5", "M16.5 6.5h1.7c.7 0 1.3.6 1.2 1.3-.3 1.9-1.4 3.2-3.2 3.5", "M12 13.5v3.5", "M8.5 19.5h7c0-1.4-.9-2.5-2.3-2.5h-2.4c-1.4 0-2.3 1.1-2.3 2.5Z")

    val Puzzle = soft("Puzzle", "M6.5 7.5h3c-.4-2.2 1-3.5 2.5-3.5s2.9 1.3 2.5 3.5h3c.6 0 1 .4 1 1v3c2.2-.4 3.5 1 3.5 2.5s-1.3 2.9-3.5 2.5v2c0 .8-.7 1.5-1.5 1.5H7c-1.4 0-2.5-1.1-2.5-2.5V9.5c0-1.1.9-2 2-2Z")

    val Wrench = soft("Wrench", "M14.5 4.2a4.5 4.5 0 0 0-4.8 5.9l-5.2 5.2a2 2 0 0 0 2.8 2.8l5.2-5.2a4.5 4.5 0 0 0 5.9-4.8l-2.6 2.6-2.4-.5-.5-2.4Z")

    val Key = soft("Key", "M12.5 9.5a4 4 0 1 0 8 0a4 4 0 1 0-8 0Z", "M13.7 12.3 5.5 20.5", "M8.3 17.7l1.8 1.8M10.6 15.4l1.4 1.4", dots = "M17.6 8.4h.01")

    val Bulb = soft("Bulb", "M9 16.5c0-1.6-.6-2.4-1.6-3.6a5.5 5.5 0 1 1 9.2 0c-1 1.2-1.6 2-1.6 3.6Z", "M9.5 19h5M10.5 21h3")

    val Ticket = soft("Ticket", "M5 6h14c.8 0 1.5.7 1.5 1.5v2a2.5 2.5 0 0 0 0 5v2c0 .8-.7 1.5-1.5 1.5H5c-.8 0-1.5-.7-1.5-1.5v-2a2.5 2.5 0 0 0 0-5v-2C3.5 6.7 4.2 6 5 6Z", "M14.5 8.5v1.2M14.5 11.4v1.2M14.5 14.3v1.2")

    val Flame = soft("Flame", "M12 20.5c-3.6 0-6-2.4-6-5.6 0-3.5 2.8-5.3 3.6-9.4 2.4 1.4 3.6 3.4 3.4 5.8 1-.4 1.7-1.2 2.1-2.3 1.9 1.6 2.9 3.6 2.9 5.9 0 3.2-2.4 5.6-6 5.6Z", "M12 20.5c-1.5 0-2.5-1-2.5-2.4 0-1.6 1.2-2.4 2.5-3.8 1.3 1.4 2.5 2.2 2.5 3.8 0 1.4-1 2.4-2.5 2.4Z")

    val Film = soft("Film", "M6.5 4.5h11a3 3 0 0 1 3 3v9a3 3 0 0 1-3 3h-11a3 3 0 0 1-3-3v-9a3 3 0 0 1 3-3Z", "M10.5 9.3v5.4c0 .5.5.8.9.5l4-2.7c.4-.3.4-.8 0-1l-4-2.7c-.4-.3-.9 0-.9.5Z")

    val Note = soft("Note", "M9 17.5V6.6c0-.7.4-1.2 1.1-1.4l7.5-1.9c.7-.2 1.4.3 1.4 1v11.2", "M9 9.5l10-2.5", "M4.5 17.5a2.25 2.25 0 1 0 4.5 0a2.25 2.25 0 1 0-4.5 0Z", "M14.5 16.5a2.25 2.25 0 1 0 4.5 0a2.25 2.25 0 1 0-4.5 0Z")

    val Pin = soft("Pin", "M12 20.5s-6.5-5.3-6.5-10.6a6.5 6.5 0 0 1 13 0c0 5.3-6.5 10.6-6.5 10.6Z", "M9.6 9.9a2.4 2.4 0 1 0 4.8 0a2.4 2.4 0 1 0-4.8 0Z")

    val Cap = soft("Cap", "M4.2 8.8 11.1 5.2c.6-.3 1.2-.3 1.8 0l6.9 3.6c.4.2.4.8 0 1l-6.9 3.6c-.6.3-1.2.3-1.8 0L4.2 9.8c-.4-.2-.4-.8 0-1Z", "M7 11.8v3.7c0 1.6 2.2 3 5 3s5-1.4 5-3v-3.7", "M20.2 9.3v4.7")

    val Phone = soft("Phone", "M8.5 3.5h7A2.5 2.5 0 0 1 18 6v12a2.5 2.5 0 0 1-2.5 2.5h-7A2.5 2.5 0 0 1 6 18V6a2.5 2.5 0 0 1 2.5-2.5Z", "M10.5 17.5h3")

    val Shop = soft("Shop", "M4.5 9.5l1.1-3.9c.2-.7.8-1.1 1.5-1.1h9.8c.7 0 1.3.4 1.5 1.1l1.1 3.9", "M4.5 9.5c0 1.4 1.1 2.5 2.5 2.5s2.5-1.1 2.5-2.5c0 1.4 1.1 2.5 2.5 2.5s2.5-1.1 2.5-2.5c0 1.4 1.1 2.5 2.5 2.5s2.5-1.1 2.5-2.5", "M5.5 12v5c0 1.7.8 2.5 2.5 2.5h8c1.7 0 2.5-.8 2.5-2.5v-5", "M10 19.5v-2.5c0-.6.4-1 1-1h2c.6 0 1 .4 1 1v2.5")

    val Shield = soft("Shield", "M11.3 4 6.6 5.8c-.7.3-1.1.9-1.1 1.6v4.1c0 4.1 2.7 7.4 6.5 8.7 3.8-1.3 6.5-4.6 6.5-8.7V7.4c0-.7-.4-1.3-1.1-1.6L12.7 4c-.5-.2-.9-.2-1.4 0Z", "M9 12l2.1 2.1 4-4.2")

    val Wifi = soft("Wifi", "M4 9.5a11.5 11.5 0 0 1 16 0", "M6.8 12.6a7.5 7.5 0 0 1 10.4 0", "M9.6 15.6a3.5 3.5 0 0 1 4.8 0", dots = "M12 18.8h.01")

    val WifiOff = soft("WifiOff", "M4 9.5a11.5 11.5 0 0 1 16 0", "M6.8 12.6a7.5 7.5 0 0 1 10.4 0", "M9.6 15.6a3.5 3.5 0 0 1 4.8 0", "M4 4l16 16", dots = "M12 18.8h.01")

    val Clipboard = soft("Clipboard", "M8.5 5.5h-1A2.5 2.5 0 0 0 5 8v10a2.5 2.5 0 0 0 2.5 2.5h9A2.5 2.5 0 0 0 19 18V8a2.5 2.5 0 0 0-2.5-2.5h-1", "M9.5 4h5c.6 0 1 .4 1 1v1c0 .6-.4 1-1 1h-5c-.6 0-1-.4-1-1V5c0-.6.4-1 1-1Z", "M9 13.5l2 2 4-4")

    // The nav capsule (#106): outline when not selected, solid with the detail cut out when it is.
    val NavAdd = glyph("NavAdd") { line("M12 5.5v13M5.5 12h13", width = ADD_WIDTH) }

    val TodaySolid = cut("TodaySolid", HOUSE, "M10 14.65h4a.95.95 0 0 1 0 1.9h-4a.95.95 0 0 1 0-1.9Z")

    val ReportsSolid = cut(
        "ReportsSolid",
        CHART_FRAME,
        "M7.65 13.2a.95.95 0 0 1 1.9 0v2.2a.95.95 0 0 1-1.9 0Z" +
            "M11.05 8.8a.95.95 0 0 1 1.9 0v6.6a.95.95 0 0 1-1.9 0Z" +
            "M14.45 11.4a.95.95 0 0 1 1.9 0v4a.95.95 0 0 1-1.9 0Z",
    )

    val SharedSolid = glyph("SharedSolid") {
        solid("M5.7 8.2a3.8 3.8 0 1 0 7.6 0a3.8 3.8 0 1 0-7.6 0Z")
        solid(PERSON_BODY)
        line(PERSON_BODY, width = EDGE_WIDTH)
        line("M14.4 6.6A2.5 2.5 0 1 1 15.45 10.55", width = SOLID_DETAIL_WIDTH)
        line("M17.6 13.6c1.9.4 3 1.8 3 3.7 0 .8-.5 1.4-1.3 1.6", width = SOLID_DETAIL_WIDTH)
    }

    val SettingsSolid = cut("SettingsSolid", HEXAGON, "M9.4 12a2.6 2.6 0 1 0 5.2 0a2.6 2.6 0 1 0-5.2 0Z")
}

private const val HOUSE = "M4 11.1c0-1.1.5-2.1 1.4-2.8l4.4-3.6a3.5 3.5 0 0 1 4.4 0l4.4 3.6c.9.7 1.4 1.7 1.4 2.8v5.4c0 2.3-1.2 3.5-3.5 3.5h-9C5.2 20 4 18.8 4 16.5Z"

private const val CHART_FRAME = "M4 9.2C4 5.4 5.4 4 9.2 4h5.6C18.6 4 20 5.4 20 9.2v5.6c0 3.8-1.4 5.2-5.2 5.2H9.2C5.4 20 4 18.6 4 14.8Z"

private const val HEXAGON = "M9.96 4.78Q12.00 3.60 14.04 4.78L17.24 6.62Q19.27 7.80 19.27 10.15L19.27 13.85Q19.27 16.20 17.24 17.38L14.04 19.22Q12.00 20.40 9.96 19.22L6.76 17.38Q4.73 16.20 4.73 13.85L4.73 10.15Q4.73 7.80 6.76 6.62Z"

private const val PERSON_BODY = "M3.6 18.3c0-3 2.6-4.9 5.9-4.9s5.9 1.9 5.9 4.9c0 1-.7 1.7-1.7 1.7H5.3c-1 0-1.7-.7-1.7-1.7Z"

/** 24dp square, matching the viewport, so an icon drawn without a size modifier is the design's. */
private val IconSize = 24.dp

/** N06's outline weight, for the whole family. */
internal const val LINE_WIDTH = 1.6f

/** "Detail dots are drawn heavier so they read at 20dp" (#108). */
internal const val DOT_WIDTH = 2.4f

/** The nav Add's plus (N06): heavier than the outline, so it reads on ruby at 26dp. */
private const val ADD_WIDTH = 2.2f

/** The rim a solid nav glyph keeps, so it is the same size as its outline. */
private const val EDGE_WIDTH = 1.2f

/** Shared's back figure when solid: an arc, so it is drawn heavier instead of filled. */
private const val SOLID_DETAIL_WIDTH = 2.2f

/**
 * Outline artwork carries no fill and no colour of its own. The stroke is black only so there is
 * something to recolour: `Icon` tints the whole vector.
 */
private fun soft(name: String, vararg lines: String, dots: String? = null): ImageVector = glyph(name) {
    lines.forEach { line(it) }
    dots?.let { line(it, width = DOT_WIDTH) }
}

/** A solid nav glyph: [shape] filled with [holes] cut out, and rimmed so it matches the outline's size. */
private fun cut(name: String, shape: String, holes: String): ImageVector = glyph(name) {
    solid(shape + holes, evenOdd = true)
    line(shape, width = EDGE_WIDTH)
}

private fun glyph(name: String, draw: ImageVector.Builder.() -> Unit): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = IconSize,
        defaultHeight = IconSize,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply(draw).build()

private fun ImageVector.Builder.line(data: String, width: Float = LINE_WIDTH) {
    addPath(
        pathData = addPathNodes(data),
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = width,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    )
}

private fun ImageVector.Builder.solid(data: String, evenOdd: Boolean = false) {
    addPath(
        pathData = addPathNodes(data),
        pathFillType = if (evenOdd) PathFillType.EvenOdd else PathFillType.NonZero,
        fill = SolidColor(Color.Black),
    )
}
