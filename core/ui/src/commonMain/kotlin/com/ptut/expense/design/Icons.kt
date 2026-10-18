package com.ptut.expense.design

import androidx.compose.ui.graphics.vector.ImageVector
import com.ptut.expense.design.icons.Heroicons
import com.ptut.expense.locale.AppText

/**
 * Every glyph the app draws, named the way the design names it.
 *
 * The entries keep the Material Symbols names the designs were authored with, because the markup
 * still carries them and the design project's own `heroicons.js` resolves them the same way. The
 * artwork underneath is Heroicons v2 — one set on both platforms, so a glyph cannot change shape
 * between Android and iOS.
 *
 * There is no fallback, and that is the point of this type. The map it replaced ended
 * `else -> MoreHoriz`, which drew the wrong picture with no build error and no failing test —
 * three screens shipped that bug into their own issue text before anyone looked at the screen.
 *
 * Six names extend the design's table, which has no entry for them: [Backspace] (the keypad's
 * delete key), [TrendingUp] and [TrendingFlat] (the design maps only `trending_down`), and [Mic] and
 * [Stop] for Quick add's voice (#212), and [CallReceived], the arrow beside money that came in.
 * Each takes the obvious Heroicon of the pair it belongs to.
 */
enum class PtutIcon(val designName: String, val image: ImageVector) {
    AccountBalance("account_balance", Heroicons.BuildingLibrary),
    AccountBalanceWallet("account_balance_wallet", Heroicons.Wallet),
    Add("add", Heroicons.Plus),
    Apartment("apartment", Heroicons.BuildingOffice),
    ArrowBack("arrow_back", Heroicons.ArrowLeft),
    AutoAwesome("auto_awesome", Heroicons.Sparkles),
    AutoDelete("auto_delete", Heroicons.Trash),
    Backspace("backspace", Heroicons.Backspace),
    BarChart("bar_chart", Heroicons.ChartBar),
    Bolt("bolt", Heroicons.Bolt),
    Brush("brush", Heroicons.PaintBrush),
    CalendarMonth("calendar_month", Heroicons.CalendarDays),
    CallReceived("call_received", Heroicons.ArrowDownLeft),
    CardGiftcard("card_giftcard", Heroicons.Gift),
    Cash("cash", Heroicons.Banknotes),
    Check("check", Heroicons.Check),
    CheckCircle("check_circle", Heroicons.CheckCircle),
    ChevronDown("chevron_down", Heroicons.ChevronDown),
    ChevronRight("chevron_right", Heroicons.ChevronRight),
    ChevronUp("chevron_up", Heroicons.ChevronUp),
    Close("close", Heroicons.XMark),
    Computer("computer", Heroicons.ComputerDesktop),
    ContentCut("content_cut", Heroicons.Scissors),
    Delete("delete", Heroicons.Trash),
    DirectionsBus("directions_bus", Heroicons.Truck),
    DoneAll("done_all", Heroicons.CheckBadge),
    Download("download", Heroicons.ArrowDownTray),
    Edit("edit", Heroicons.PencilSquare),
    EmojiEvents("emoji_events", Heroicons.Trophy),
    Error("error", Heroicons.ExclamationTriangle),
    Extension("extension", Heroicons.PuzzlePiece),
    FilterAltOff("filter_alt_off", Heroicons.Funnel),
    Folder("folder", Heroicons.Folder),
    Group("group", Heroicons.Users),
    GroupAdd("group_add", Heroicons.UserPlus),
    Handyman("handyman", Heroicons.WrenchScrewdriver),
    Help("help", Heroicons.QuestionMarkCircle),
    History("history", Heroicons.Clock),
    Home("home", Heroicons.Home),
    Inbox("inbox", Heroicons.Inbox),
    Info("info", Heroicons.InformationCircle),
    Insights("insights", Heroicons.ChartBar),
    Key("key", Heroicons.Key),
    Label("label", Heroicons.Tag),
    Lightbulb("lightbulb", Heroicons.LightBulb),
    ListAlt("list_alt", Heroicons.ListBullet),
    LocalActivity("local_activity", Heroicons.Ticket),
    LocalFireDepartment("local_fire_department", Heroicons.Fire),
    LocalGroceryStore("local_grocery_store", Heroicons.ShoppingCart),
    Lock("lock", Heroicons.LockClosed),
    MenuBook("menu_book", Heroicons.BookOpen),
    Mic("mic", Heroicons.Microphone),
    MonitorHeart("monitor_heart", Heroicons.Heart),
    MoreHoriz("more_horiz", Heroicons.EllipsisHorizontal),
    Movie("movie", Heroicons.Film),
    MusicNote("music_note", Heroicons.MusicalNote),
    Notifications("notifications", Heroicons.Bell),
    NotificationsPaused("notifications_paused", Heroicons.BellSnooze),
    Payments("payments", Heroicons.CreditCard),
    PhotoLibrary("photo_library", Heroicons.Photo),
    Place("place", Heroicons.MapPin),
    PriorityHigh("priority_high", Heroicons.ExclamationCircle),
    Public("public", Heroicons.GlobeAlt),
    ReceiptLong("receipt_long", Heroicons.ReceiptPercent),
    Restaurant("restaurant", Heroicons.Cake),
    Rule("rule", Heroicons.ClipboardDocumentCheck),
    RuleSettings("rule_settings", Heroicons.AdjustmentsHorizontal),
    Schedule("schedule", Heroicons.Clock),
    School("school", Heroicons.AcademicCap),
    Search("search", Heroicons.MagnifyingGlass),
    Sell("sell", Heroicons.Tag),
    Settings("settings", Heroicons.Cog6Tooth),
    ShoppingBag("shopping_bag", Heroicons.ShoppingBag),
    Smartphone("smartphone", Heroicons.DevicePhoneMobile),
    Stop("stop", Heroicons.Stop),
    Store("store", Heroicons.BuildingStorefront),
    Sync("sync", Heroicons.ArrowPath),
    TaskAlt("task_alt", Heroicons.CheckCircle),
    ToggleOn("toggle_on", Heroicons.CheckCircle),
    Translate("translate", Heroicons.Language),
    TrendingDown("trending_down", Heroicons.ArrowTrendingDown),
    TrendingFlat("trending_flat", Heroicons.MinusSmall),
    TrendingUp("trending_up", Heroicons.ArrowTrendingUp),
    Tune("tune", Heroicons.AdjustmentsHorizontal),
    Upload("upload", Heroicons.ArrowUpTray),
    VerifiedUser("verified_user", Heroicons.ShieldCheck),
    WbTwilight("wb_twilight", Heroicons.Sun),
    Wifi("wifi", Heroicons.Wifi),
    WifiOff("wifi_off", Heroicons.SignalSlash),
    Work("work", Heroicons.Briefcase),
    ;

    companion object {
        private val byDesignName = entries.associateBy(PtutIcon::designName)

        /**
         * Throws on a name nobody has drawn. A category, a rule or a slip carries its icon as a
         * string, so this is the one door artwork can be asked for by name — and a wrong name has
         * to stop here rather than travel on as a picture of something else.
         */
        fun named(designName: String): PtutIcon = byDesignName[designName]
            ?: error("No icon named \"$designName\". Add it to PtutIcon, with a Heroicon to draw.")

        /**
         * What a category may wear, in the order the picker draws them. A subset of [entries] and
         * not the whole table: `arrow_back` and `backspace` are chrome, and a reader naming what
         * they buy should be offered things they buy.
         *
         * Every icon [com.ptut.expense.domain.Category.seed] installs is in here, and so is the
         * one a new category starts with. A stored icon missing from this list would open the
         * picker with nothing selected, which reads as "no icon yet" rather than "this one".
         */
        val categoryChoices: List<CategoryIcon> = listOf(
            // What money goes out on, roughly in the order a week spends it.
            CategoryIcon(Restaurant, "Food"),
            CategoryIcon(LocalGroceryStore, "Groceries"),
            CategoryIcon(ShoppingBag, "Shopping"),
            CategoryIcon(Store, "A shop"),
            CategoryIcon(DirectionsBus, "Transport"),
            CategoryIcon(Place, "Somewhere to go"),
            CategoryIcon(Public, "Travel"),
            CategoryIcon(LocalActivity, "Tickets"),
            // Home and the bills it brings.
            CategoryIcon(Home, "Home"),
            CategoryIcon(Key, "Rent"),
            CategoryIcon(ReceiptLong, "Bills"),
            CategoryIcon(Bolt, "Utilities"),
            CategoryIcon(Lightbulb, "Electricity"),
            CategoryIcon(LocalFireDepartment, "Gas"),
            CategoryIcon(Wifi, "Internet"),
            CategoryIcon(Smartphone, "Phone"),
            CategoryIcon(Handyman, "Repairs"),
            // The things a person does rather than owns.
            CategoryIcon(MonitorHeart, "Health"),
            CategoryIcon(EmojiEvents, "Sport"),
            CategoryIcon(Extension, "A hobby"),
            CategoryIcon(MusicNote, "Music"),
            CategoryIcon(Movie, "Films"),
            CategoryIcon(MenuBook, "Books"),
            CategoryIcon(School, "Learning"),
            CategoryIcon(Brush, "Making things"),
            CategoryIcon(Computer, "Computing"),
            CategoryIcon(ContentCut, "Grooming"),
            CategoryIcon(AutoAwesome, "A treat"),
            CategoryIcon(CardGiftcard, "Gifts"),
            CategoryIcon(Group, "Family"),
            // Work, and the money that arrives rather than leaves.
            CategoryIcon(Work, "Work"),
            CategoryIcon(Apartment, "The office"),
            CategoryIcon(Payments, "Money in"),
            CategoryIcon(Cash, "Cash"),
            CategoryIcon(AccountBalanceWallet, "A wallet"),
            CategoryIcon(AccountBalance, "The bank"),
            CategoryIcon(VerifiedUser, "Insurance"),
            CategoryIcon(CalendarMonth, "Something monthly"),
            // The two that mean "no picture in particular".
            CategoryIcon(Label, "A plain label"),
            CategoryIcon(MoreHoriz, "Anything else"),
        )
    }
}

/**
 * One glyph a category may be given, with the words a screen reader says for it.
 *
 * The label lives beside the icon rather than being derived from [PtutIcon.designName], because
 * the picker draws pictures and nothing else — there is no row text to carry the meaning, and
 * "more horiz" read aloud tells the listener nothing about what they are choosing.
 */
data class CategoryIcon(val icon: PtutIcon, private val english: String) {
    val spoken: String get() = AppText.copy.shell.iconNames[icon.designName] ?: english
}

/** [PtutIcon.named] for the callers that only want something to draw. */
fun ptutIcon(designName: String): ImageVector = PtutIcon.named(designName).image
