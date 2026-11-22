package com.ptut.expense.design

import androidx.compose.ui.graphics.vector.ImageVector
import com.ptut.expense.design.icons.SoftIcons
import com.ptut.expense.locale.AppText

/**
 * Every glyph the app draws, named the way the design names it.
 *
 * The entries keep the Material Symbols names the designs were authored with, because categories,
 * rules and slips store them as strings. The artwork underneath is PaiSeN's own soft family
 * ([SoftIcons], #108): one set on both platforms, so a glyph cannot change shape between them.
 *
 * There is no fallback, and that is the point of this type. The map it replaced ended
 * `else -> MoreHoriz`, which drew the wrong picture with no build error and no failing test.
 */
enum class PtutIcon(val designName: String, val image: ImageVector) {
    AccountBalance("account_balance", SoftIcons.Bank),
    AccountBalanceWallet("account_balance_wallet", SoftIcons.Wallet),
    Add("add", SoftIcons.Plus),
    Apartment("apartment", SoftIcons.Office),
    ArrowBack("arrow_back", SoftIcons.ArrowLeft),
    AutoAwesome("auto_awesome", SoftIcons.Sparkle),
    AutoDelete("auto_delete", SoftIcons.Bin),
    Backspace("backspace", SoftIcons.Backspace),
    BarChart("bar_chart", SoftIcons.Chart),
    Bolt("bolt", SoftIcons.Bolt),
    Brush("brush", SoftIcons.Brush),
    CalendarMonth("calendar_month", SoftIcons.Calendar),
    CallReceived("call_received", SoftIcons.ArrowDownLeft),
    CardGiftcard("card_giftcard", SoftIcons.Gift),
    Cash("cash", SoftIcons.Banknote),
    Check("check", SoftIcons.Tick),
    CheckCircle("check_circle", SoftIcons.TickCircle),
    ChevronDown("chevron_down", SoftIcons.ChevronDown),
    ChevronRight("chevron_right", SoftIcons.ChevronRight),
    ChevronUp("chevron_up", SoftIcons.ChevronUp),
    Close("close", SoftIcons.Cross),
    Computer("computer", SoftIcons.Screen),
    ContentCut("content_cut", SoftIcons.Scissors),
    Delete("delete", SoftIcons.Bin),
    DirectionsBus("directions_bus", SoftIcons.Car),
    DoneAll("done_all", SoftIcons.TickDouble),
    Download("download", SoftIcons.DownloadTray),
    Edit("edit", SoftIcons.Pencil),
    EmojiEvents("emoji_events", SoftIcons.Trophy),
    Error("error", SoftIcons.Warning),
    Extension("extension", SoftIcons.Puzzle),
    FilterAltOff("filter_alt_off", SoftIcons.FunnelOff),
    Folder("folder", SoftIcons.Folder),
    Group("group", SoftIcons.People),
    GroupAdd("group_add", SoftIcons.PersonAdd),
    Handyman("handyman", SoftIcons.Wrench),
    Help("help", SoftIcons.QuestionCircle),
    History("history", SoftIcons.Clock),
    Home("home", SoftIcons.House),
    Inbox("inbox", SoftIcons.Tray),
    Info("info", SoftIcons.InfoCircle),
    Insights("insights", SoftIcons.Chart),
    Key("key", SoftIcons.Key),
    Label("label", SoftIcons.Tag),
    Lightbulb("lightbulb", SoftIcons.Bulb),
    ListAlt("list_alt", SoftIcons.List),
    LocalActivity("local_activity", SoftIcons.Ticket),
    LocalFireDepartment("local_fire_department", SoftIcons.Flame),
    LocalGroceryStore("local_grocery_store", SoftIcons.Cart),
    Lock("lock", SoftIcons.Padlock),
    MenuBook("menu_book", SoftIcons.Book),
    Mic("mic", SoftIcons.Microphone),
    MonitorHeart("monitor_heart", SoftIcons.Heart),
    MoreHoriz("more_horiz", SoftIcons.Dots),
    Movie("movie", SoftIcons.Film),
    MusicNote("music_note", SoftIcons.Note),
    Notifications("notifications", SoftIcons.Bell),
    NotificationsPaused("notifications_paused", SoftIcons.BellZ),
    Payments("payments", SoftIcons.Card),
    PhotoLibrary("photo_library", SoftIcons.Photo),
    Place("place", SoftIcons.Pin),
    PriorityHigh("priority_high", SoftIcons.Exclaim),
    Public("public", SoftIcons.Globe),
    ReceiptLong("receipt_long", SoftIcons.Receipt),
    Restaurant("restaurant", SoftIcons.Bowl),
    Rule("rule", SoftIcons.Clipboard),
    RuleSettings("rule_settings", SoftIcons.Sliders),
    Schedule("schedule", SoftIcons.Clock),
    School("school", SoftIcons.Cap),
    Search("search", SoftIcons.Magnifier),
    Sell("sell", SoftIcons.Tag),
    Settings("settings", SoftIcons.Hexagon),
    ShoppingBag("shopping_bag", SoftIcons.Bag),
    Smartphone("smartphone", SoftIcons.Phone),
    Stop("stop", SoftIcons.StopSquare),
    Store("store", SoftIcons.Shop),
    Sync("sync", SoftIcons.Refresh),
    TaskAlt("task_alt", SoftIcons.TickCircle),
    ToggleOn("toggle_on", SoftIcons.Toggle),
    Translate("translate", SoftIcons.Speech),
    TrendingDown("trending_down", SoftIcons.TrendDown),
    TrendingFlat("trending_flat", SoftIcons.TrendFlat),
    TrendingUp("trending_up", SoftIcons.TrendUp),
    Tune("tune", SoftIcons.Sliders),
    Upload("upload", SoftIcons.UploadTray),
    VerifiedUser("verified_user", SoftIcons.Shield),
    WbTwilight("wb_twilight", SoftIcons.Sun),
    Wifi("wifi", SoftIcons.Wifi),
    WifiOff("wifi_off", SoftIcons.WifiOff),
    Work("work", SoftIcons.Suitcase),
    ;

    companion object {
        private val byDesignName = entries.associateBy(PtutIcon::designName)

        /**
         * Throws on a name nobody has drawn. A category, a rule or a slip carries its icon as a
         * string, so this is the one door artwork can be asked for by name — and a wrong name has
         * to stop here rather than travel on as a picture of something else.
         */
        fun named(designName: String): PtutIcon = byDesignName[designName]
            ?: error("No icon named \"$designName\". Add it to PtutIcon, with a SoftIcons glyph to draw.")

        /**
         * For a name another phone or an older build wrote, such as a shared group's icon: null
         * rather than a throw, so one glyph this build never drew cannot close the screen.
         */
        fun find(designName: String): PtutIcon? = byDesignName[designName]

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
