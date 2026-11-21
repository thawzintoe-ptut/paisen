package com.ptut.expense.locale

/**
 * #82's words, one class per play area. Its own file, read through [playCopy], so play needs no
 * field on [Copy]. Thai reads the English until a Thai reader signs it off (D12).
 */
data class PlayCopy(
    val settings: PlaySettingsCopy = PlaySettingsCopy(),
    val run: PlayRunCopy = PlayRunCopy(),
    val stamps: PlayStampsCopy = PlayStampsCopy(),
    val question: PlayQuestionCopy = PlayQuestionCopy(),
    val guess: PlayGuessCopy = PlayGuessCopy(),
    val coach: PlayCoachCopy = PlayCoachCopy(),
    val reminder: PlayReminderCopy = PlayReminderCopy(),
    /** The month, goal and close screens (#90 to #99). */
    val extras: PlayExtrasCopy = PlayExtrasCopy(),
    /** Play inside shared groups (#93 to #97). */
    val group: PlayGroupCopy = PlayGroupCopy(),
    /** Just-in-time tips and their Settings rows. */
    val tips: PlayTipsCopy = PlayTipsCopy(),
    /** R8: every PaiSeN line says where it was worked out. */
    val local: String = "Worked out on this phone",
)

/** Just-in-time tips (V3 section 4): one per feature, keyed by the tip's name. */
data class PlayTipsCopy(
    val eyebrow: (number: Int, of: Int) -> String = { number, of -> "Tip · $number of $of" },
    val skip: String = "Skip all tips",
    val gotIt: String = "Got it",
    val label: (title: String) -> String = { title -> "Tip: $title" },
    val titles: Map<String, String> = mapOf(
        "Run" to "Your tray clear run",
        "Question" to "Today's 30 seconds",
        "Stamp" to "Turn over your stamp",
        "Guess" to "Guess the week",
        "Jar" to "Your goal jar",
        "Wheel" to "Fair wheel",
    ),
    val bodies: Map<String, String> = mapOf(
        "Run" to "Check every slip in a day to add a day. Miss one and a freeze you earned keeps the run going.",
        "Question" to "One question a day from your own week. Right or wrong, you keep your run.",
        "Stamp" to "Every cleared day gives one. Most are the day's stamp; some days give a rare one.",
        "Guess" to "Guess your week before you see it. PaiSeN guesses too. Closer than PaiSeN earns a stamp.",
        "Jar" to "It fills from income less spending. No money moves; your bank stays as it is.",
        "Wheel" to "Whoever paid fewer rounds lately gets a bigger slice. Turn it off for equal slices.",
    ),
    val group: String = "Help and tips",
    val show: String = "Show tips",
    val showLine: String = "One short tip the first time you meet a feature",
    val again: String = "See all tips again",
    val againLine: String = "Run, question, stamps, guess, jar, wheel",
)

val BurmesePlayTips = PlayTipsCopy(
    eyebrow = { number, of -> "အကြံပြုချက် · ${of.inMyanmarDigits()} ခုထဲက ${number.inMyanmarDigits()}" },
    skip = "အကြံပြုချက် အားလုံး ကျော်မယ်",
    gotIt = "နားလည်ပြီ",
    label = { title -> "အကြံပြုချက်: $title" },
    titles = mapOf(
        "Run" to "စလစ် ရှင်းတဲ့ ရက်ဆက်",
        "Question" to "ဒီနေ့ စက္ကန့် ၃၀",
        "Stamp" to "တံဆိပ်ကို လှန်ကြည့်ပါ",
        "Guess" to "ဒီအပတ်ကို ခန့်မှန်းကြည့်",
        "Jar" to "ပန်းတိုင်ဘူး",
        "Wheel" to "တရားမျှတတဲ့ ဘီး",
    ),
    bodies = mapOf(
        "Run" to "တစ်ရက်ထဲက စလစ်တိုင်း စစ်ရင် တစ်ရက် တိုးမယ်။ တစ်ရက် လွတ်သွားရင် ရထားတဲ့ freeze က ရက်ဆက်ကို ဆက်ထားပေးမယ်။",
        "Question" to "ကိုယ့်အပတ်ထဲက တစ်နေ့ မေးခွန်း တစ်ခု။ မှန်မှန် မှားမှား ရက်ဆက် မပျက်ဘူး။",
        "Stamp" to "ရှင်းတဲ့ နေ့တိုင်း တံဆိပ် တစ်ခု ရမယ်။ အများစုက အဲဒီနေ့ တံဆိပ်၊ တချို့နေ့ ရှားပါးတာ ရမယ်။",
        "Guess" to "မကြည့်ခင် ကိုယ့်အပတ်ကို ခန့်မှန်းပါ။ PaiSeN လည်း ခန့်မှန်းတယ်။ PaiSeN ထက် နီးရင် တံဆိပ် ရမယ်။",
        "Jar" to "ဝင်ငွေထဲက သုံးစွဲမှု နုတ်ပြီး ပြည့်တာပါ။ ငွေ ဘာမှ မရွှေ့ဘူး။ ဘဏ်ထဲမှာ ဒီအတိုင်း ရှိနေမယ်။",
        "Wheel" to "မကြာသေးခင်က နည်းနည်း ပေးခဲ့သူ အပိုင်း ပိုကြီးမယ်။ ပိတ်ရင် အပိုင်းတွေ တူမယ်။",
    ),
    group = "အကူအညီနဲ့ အကြံပြုချက်",
    show = "အကြံပြုချက် ပြမယ်",
    showLine = "အသစ်တစ်ခု ပထမဆုံး တွေ့တဲ့အခါ အကြံပြုချက် တိုတို တစ်ခု",
    again = "အကြံပြုချက်တွေ ပြန်ကြည့်မယ်",
    againLine = "ရက်ဆက်၊ မေးခွန်း၊ တံဆိပ်၊ ခန့်မှန်း၊ ဘူး၊ ဘီး",
)

/** p22, word for word. */
data class PlaySettingsCopy(
    val title: String = "Play",
    val masterOn: String = "Stamps, guesses and challenges are on",
    val masterOff: String = "Off: PaiSeN shows money only",
    val onToday: String = "On Today",
    val run: String = "Tray clear run",
    val runLine: String = "Days you check every slip",
    val guess: String = "Guess the week",
    val guessLine: String = "Before the week report",
    val challenges: String = "Challenges",
    val challengesLine: String = "One a week, you pick",
    val inGroups: String = "In shared groups",
    val groupStamps: String = "Stamps and awards",
    val groupStampsLine: String = "Seen by everyone in the group",
    val sharing: String = "Sharing and reminders",
    val shareAmounts: String = "Amounts on share cards",
    val shareAmountsLine: String = "Off: cards show words and shapes only",
    val reminder: String = "Weekly play reminder",
    val reminderLine: String = "At most one a week",
    val footer: String = "Your own play is worked out on this phone and never leaves it.",
)

/** The Today run card (artboard Main). */
data class PlayRunCopy(
    val eyebrow: String = "Tray clear run",
    val days: (Int) -> String = { n -> if (n == 1) "1 day" else "$n days" },
    val freezes: (Int) -> String = { n -> if (n == 1) "1 freeze" else "$n freezes" },
    val next: (slips: Int, day: Int) -> String = { slips, day ->
        if (slips == 1) "Check 1 slip to make it $day and turn over a stamp." else "Check $slips slips to make it $day and turn over a stamp."
    },
    val clearToday: String = "Tray clear today. A stamp is waiting.",
    val keptToday: String = "Tray clear today. Come back tomorrow.",
    val startLine: String = "Clear the tray today to start a run.",
    val stamps: String = "Stamps",
    val turnStamp: String = "Turn over today's stamp",
    val dayCleared: (day: String) -> String = { day -> "$day, tray cleared" },
    val dayFrozen: (day: String) -> String = { day -> "$day, kept by a freeze" },
    val dayMissed: (day: String) -> String = { day -> "$day, not cleared" },
    val dayToday: (day: String, waiting: Int) -> String = { day, waiting ->
        if (waiting == 0) "$day, today" else "$day, today, $waiting waiting"
    },
    val dayAhead: (day: String) -> String = { day -> "$day, still to come" },
    /** The week's dots, Monday first. */
    val weekLetters: List<String> = listOf("M", "T", "W", "T", "F", "S", "S"),
    val freezeLine: (Int) -> String = { n -> if (n == 1) "1 freeze keeps a missed day in your run." else "$n freezes keep missed days in your run." },
)

/** p08's reveal and p04's book. Each kind's [names] entry is keyed by its enum name. */
data class PlayStampsCopy(
    val names: Map<String, String> = mapOf(
        "Monday" to "Monday",
        "Tuesday" to "Tuesday",
        "Wednesday" to "Wednesday",
        "Thursday" to "Thursday",
        "Friday" to "Friday",
        "Saturday" to "Saturday",
        "Sunday" to "Sunday",
        "NightOwl" to "Night owl",
        "EarlyBird" to "Early bird",
        "BigSweep" to "Big sweep",
        "BeatPaiSeN" to "Beat PaiSeN",
        "ChallengeMet" to "Challenge met",
        "MonthClosed" to "Month closed",
        "AllSquare" to "All square",
    ),
    /** The short word pressed into the ring. */
    val faces: Map<String, String> = mapOf(
        "Monday" to "Mon",
        "Tuesday" to "Tue",
        "Wednesday" to "Wed",
        "Thursday" to "Thu",
        "Friday" to "Fri",
        "Saturday" to "Sat",
        "Sunday" to "Sun",
        "NightOwl" to "Owl",
        "EarlyBird" to "Early",
        "BigSweep" to "Sweep",
        "BeatPaiSeN" to "Beat",
        "ChallengeMet" to "Met",
        "MonthClosed" to "Sealed",
        "AllSquare" to "Square",
    ),
    val captionTray: String = "Tray clear",
    val captionRare: String = "Rare",
    val captionWin: String = "Win",
    /** D3: a rare stamp says the pattern behind it, never odds. */
    val rareLines: Map<String, String> = mapOf(
        "NightOwl" to "Cleared at 22:00 or later",
        "EarlyBird" to "Cleared before 08:00",
        "BigSweep" to "10 or more slips in one day",
    ),
    val sets: Map<String, String> = mapOf("Everyday" to "Every day", "Night" to "Night and day", "Wins" to "Wins"),
    val stampLabel: (name: String, day: String) -> String = { name, day -> "$name stamp, $day" },
    val emptyLabel: (name: String) -> String = { name -> "$name, not earned yet" },
    val revealTitle: String = "Today's stamp",
    val faceDownHint: String = "Tap the card to turn it over",
    /** The book shows a stamp not yet turned as a card still face down. */
    val faceDownCaption: String = "Face down",
    val turnedHint: String = "Pressed into your book",
    val cardLabel: String = "Face-down stamp card. Tap to turn it over.",
    val setLine: (set: String, have: Int, of: Int) -> String = { set, have, of -> "$set: $have of $of" },
    val setCount: (have: Int, of: Int) -> String = { have, of -> "$have of $of" },
    val setLeft: (Int) -> String = { n -> if (n == 1) "1 more finishes the set" else "$n more finish the set" },
    val setDone: String = "Set complete",
    val putInBook: String = "Put it in my book",
    val missing: String = "This stamp is no longer here.",
    val bookTitle: String = "Stamps",
    val noSpendTitle: String = "No-spend days",
    val noSpendCount: (Int) -> String = { n -> if (n == 1) "1 day with nothing spent this month" else "$n days with nothing spent this month" },
    val noSpendLabel: (day: String) -> String = { day -> "$day, nothing spent" },
    val nextSet: (set: String) -> String = { set -> "Next set: $set" },
    val allSets: String = "Every set complete",
)

/** #87, p07 and the ink Play card on Today. */
data class PlayQuestionCopy(
    val cardEyebrow: String = "Today's 30 seconds",
    val cardTitle: String = "Which cost more in the last 7 days?",
    val cardAction: String = "Play",
    val title: String = "Today's question",
    val ask: String = "Which cost more in the last 7 days?",
    val rows: (Int) -> String = { n -> if (n == 1) "1 payment" else "$n payments" },
    val right: String = "You got it",
    val wrong: String = "Not this time",
    val cameTo: (a: String, aAmount: String, b: String, bAmount: String) -> String = { a, aAmount, b, bAmount -> "$a came to $aAmount and $b to $bAmount." },
    val freezeProgress: (have: Int, of: Int) -> String = { have, of -> "$have of $of answers toward a freeze" },
    val freezeFilled: (Int) -> String = { n -> if (n == 1) "A freeze is filled. You hold 1." else "A freeze is filled. You hold $n." },
    val freezesFull: String = "You hold the most freezes there are.",
    val openStamp: String = "Open today's stamp",
    val end: String = "That's today's question. A new one comes tomorrow.",
    val none: String = "No fair pair in the last 7 days. Come back tomorrow.",
    val pickLabel: (name: String) -> String = { name -> "$name cost more" },
    val paisenPicked: String = "PaiSeN picked two costs from your week that came out close.",
)

/** #86, p01 and p02. */
data class PlayGuessCopy(
    val eyebrow: String = "Guess the week",
    val title: String = "What did last week come to?",
    val week: (from: String, to: String) -> String = { from, to -> "$from to $to" },
    val less: String = "Less",
    val more: String = "More",
    val guess: String = "Guess",
    val skip: String = "Skip guessing",
    val record: (best: Int, weeks: Int) -> String = { best, weeks -> if (weeks == 1) "Your best: $best% · 1 week played" else "Your best: $best% · $weeks weeks played" },
    val firstTime: String = "Your first guess. PaiSeN has guessed already.",
    val revealTitle: String = "Last week came to",
    val you: String = "You",
    val paisen: String = "PaiSeN",
    val off: (amount: String) -> String = { amount -> "off by $amount" },
    val spotOn: String = "spot on",
    val closer: String = "Closer",
    val youWon: String = "You beat PaiSeN. A stamp is in your book.",
    val paisenWon: String = "PaiSeN was closer this time. Nothing is lost.",
    val how: String = "How PaiSeN guessed",
    val howWeek: (week: String, amount: String) -> String = { week, amount -> "$week: $amount" },
    val howTrend: (amount: String) -> String = { amount -> "Trend added: $amount" },
    val seeWeek: String = "See the week",
    val missing: String = "PaiSeN needs two earlier weeks to guess. Your week is ready to look at.",
    val sliderLabel: String = "Your guess",
    val paisenToo: String = "PaiSeN has guessed too. Its number shows after yours.",
)

/** #89: the one PaiSeN line under Today's totals. Never praise for spending, never shame (FR-89.3). */
data class PlayCoachCopy(
    val more: (category: String, amount: String, weekday: String) -> String = { category, amount, weekday -> "$category: $amount more than a usual $weekday" },
    val less: (category: String, amount: String, weekday: String) -> String = { category, amount, weekday -> "$category: $amount less than a usual $weekday" },
    val disc: String = "P",
    val label: (line: String) -> String = { line -> "PaiSeN: $line. Opens the rows behind it." },
)

/** #83 (D15): at most one a week, never about a run (R7). */
data class PlayReminderCopy(
    val channel: String = "Weekly play",
    val title: String = "PaiSeN",
    val body: String = "Your week is ready. Guess first?",
)

val BurmesePlaySettings = PlaySettingsCopy(
    title = "ကစားစရာ",
    masterOn = "တံဆိပ်၊ ခန့်မှန်းချက်နဲ့ စိန်ခေါ်မှုတွေ ဖွင့်ထားတယ်",
    masterOff = "ပိတ်ထားတယ်: PaiSeN က ငွေစာရင်းပဲ ပြမယ်",
    onToday = "ဒီနေ့ စာမျက်နှာမှာ",
    run = "စလစ် ရှင်းတဲ့ ရက်ဆက်",
    runLine = "စလစ်တိုင်း စစ်ပြီးတဲ့ ရက်တွေ",
    guess = "ဒီအပတ်ကို ခန့်မှန်းကြည့်",
    guessLine = "အပတ်စဉ် အနှစ်ချုပ် မတိုင်ခင်",
    challenges = "စိန်ခေါ်မှုတွေ",
    challengesLine = "တစ်ပတ် တစ်ခု၊ ကိုယ်တိုင် ရွေးမယ်",
    inGroups = "Shared group တွေထဲမှာ",
    groupStamps = "တံဆိပ်နဲ့ ဆုတွေ",
    groupStampsLine = "အဖွဲ့ထဲက လူတိုင်း မြင်ရတယ်",
    sharing = "မျှဝေခြင်းနဲ့ သတိပေးချက်",
    shareAmounts = "မျှဝေကတ်ပေါ်မှာ ငွေပမာဏ",
    shareAmountsLine = "ပိတ်ထားရင် စာနဲ့ ပုံပဲ ပြမယ်",
    reminder = "အပတ်စဉ် ကစားဖို့ သတိပေးချက်",
    reminderLine = "တစ်ပတ်ကို အများဆုံး တစ်ကြိမ်",
    footer = "ကိုယ်ပိုင် ကစားမှုတွေကို ဒီဖုန်းထဲမှာပဲ တွက်တယ်။ ဒီဖုန်းထဲမှာ လုံခြုံပါတယ်။",
)

val BurmesePlayRun = PlayRunCopy(
    eyebrow = "စလစ် ရှင်းတဲ့ ရက်ဆက်",
    days = { n -> "${n.inMyanmarDigits()} ရက်" },
    freezes = { n -> "ခွင့် ${n.inMyanmarDigits()} ရက်" },
    next = { slips, day -> "စလစ် ${slips.inMyanmarDigits()} ခု စစ်လိုက်ရင် ${day.inMyanmarDigits()} ရက် ပြည့်ပြီး တံဆိပ်တစ်ခု လှန်ရမယ်။" },
    clearToday = "ဒီနေ့ စလစ် ရှင်းပြီ။ တံဆိပ်တစ်ခု စောင့်နေတယ်။",
    keptToday = "ဒီနေ့ စလစ် ရှင်းပြီ။ မနက်ဖြန် ပြန်လာခဲ့။",
    startLine = "ရက်ဆက် စဖို့ ဒီနေ့ စလစ်တွေ ရှင်းလိုက်ပါ။",
    stamps = "တံဆိပ်တွေ",
    turnStamp = "ဒီနေ့ တံဆိပ်ကို လှန်ကြည့်မယ်",
    dayCleared = { day -> "$day၊ စလစ် ရှင်းပြီ" },
    dayFrozen = { day -> "$day၊ ခွင့်ရက်နဲ့ ထိန်းထားတယ်" },
    dayMissed = { day -> "$day၊ မရှင်းခဲ့ဘူး" },
    dayToday = { day, waiting -> if (waiting == 0) "$day၊ ဒီနေ့" else "$day၊ ဒီနေ့၊ ${waiting.inMyanmarDigits()} ခု စောင့်နေတယ်" },
    dayAhead = { day -> "$day၊ မရောက်သေးဘူး" },
    weekLetters = listOf("တ", "အ", "ဗ", "က", "သ", "စ", "နွေ"),
    freezeLine = { n -> "ခွင့် ${n.inMyanmarDigits()} ရက်က လွတ်သွားတဲ့ ရက်ကို ရက်ဆက်ထဲမှာ ထိန်းထားပေးတယ်။" },
)

val BurmesePlayStamps = PlayStampsCopy(
    names = mapOf(
        "Monday" to "တနင်္လာ",
        "Tuesday" to "အင်္ဂါ",
        "Wednesday" to "ဗုဒ္ဓဟူး",
        "Thursday" to "ကြာသပတေး",
        "Friday" to "သောကြာ",
        "Saturday" to "စနေ",
        "Sunday" to "တနင်္ဂနွေ",
        "NightOwl" to "ညဇီးကွက်",
        "EarlyBird" to "စောစောထ",
        "BigSweep" to "အကုန်ရှင်း",
        "BeatPaiSeN" to "PaiSeN ကို နိုင်တယ်",
        "ChallengeMet" to "စိန်ခေါ်မှု အောင်တယ်",
        "MonthClosed" to "လ ပိတ်ပြီ",
        "AllSquare" to "အကြွေး ကျေပြီ",
    ),
    faces = mapOf(
        "Monday" to "တနင်္လာ",
        "Tuesday" to "အင်္ဂါ",
        "Wednesday" to "ဗုဒ္ဓ",
        "Thursday" to "ကြာ",
        "Friday" to "သော",
        "Saturday" to "စနေ",
        "Sunday" to "နွေ",
        "NightOwl" to "ည",
        "EarlyBird" to "မနက်",
        "BigSweep" to "ရှင်း",
        "BeatPaiSeN" to "နိုင်",
        "ChallengeMet" to "အောင်",
        "MonthClosed" to "ပိတ်",
        "AllSquare" to "ကျေ",
    ),
    captionTray = "စလစ် ရှင်း",
    captionRare = "ရှားပါး",
    captionWin = "အောင်မြင်",
    rareLines = mapOf(
        "NightOwl" to "ည ၁၀ နာရီနောက်ပိုင်း ရှင်းခဲ့တယ်",
        "EarlyBird" to "မနက် ၈ နာရီ မတိုင်ခင် ရှင်းခဲ့တယ်",
        "BigSweep" to "တစ်ရက်ထဲ စလစ် ၁၀ ခု နဲ့ အထက်",
    ),
    sets = mapOf("Everyday" to "နေ့စဉ်", "Night" to "နေ့နဲ့ည", "Wins" to "အောင်မြင်မှုတွေ"),
    stampLabel = { name, day -> "$name တံဆိပ်၊ $day" },
    emptyLabel = { name -> "$name၊ မရသေးဘူး" },
    revealTitle = "ဒီနေ့ တံဆိပ်",
    faceDownHint = "ကတ်ကို နှိပ်ပြီး လှန်ကြည့်ပါ",
    faceDownCaption = "မှောက်ထား",
    turnedHint = "စာအုပ်ထဲ ရိုက်ထည့်ပြီးပြီ",
    cardLabel = "မှောက်ထားတဲ့ တံဆိပ်ကတ်။ နှိပ်ပြီး လှန်ပါ။",
    setLine = { set, have, of -> "$set: ${of.inMyanmarDigits()} ခုထဲက ${have.inMyanmarDigits()} ခု" },
    setCount = { have, of -> "${of.inMyanmarDigits()} ခုထဲက ${have.inMyanmarDigits()} ခု" },
    setLeft = { n -> "နောက်ထပ် ${n.inMyanmarDigits()} ခု ရရင် အစုံ ပြည့်မယ်" },
    setDone = "အစုံ ပြည့်ပြီ",
    putInBook = "စာအုပ်ထဲ ထည့်မယ်",
    missing = "ဒီတံဆိပ် မရှိတော့ဘူး။",
    bookTitle = "တံဆိပ်တွေ",
    noSpendTitle = "မသုံးခဲ့တဲ့ ရက်တွေ",
    noSpendCount = { n -> "ဒီလ ဘာမှ မသုံးခဲ့တဲ့ ရက် ${n.inMyanmarDigits()} ရက်" },
    noSpendLabel = { day -> "$day၊ ဘာမှ မသုံးခဲ့ဘူး" },
    nextSet = { set -> "နောက်အစုံ: $set" },
    allSets = "အစုံတိုင်း ပြည့်ပြီ",
)

val BurmesePlayQuestion = PlayQuestionCopy(
    cardEyebrow = "ဒီနေ့ စက္ကန့် ၃၀",
    cardTitle = "လွန်ခဲ့တဲ့ ၇ ရက်မှာ ဘယ်ဟာ ပိုကုန်လဲ",
    cardAction = "ကစားမယ်",
    title = "ဒီနေ့ မေးခွန်း",
    ask = "လွန်ခဲ့တဲ့ ၇ ရက်မှာ ဘယ်ဟာ ပိုကုန်လဲ",
    rows = { n -> "${n.inMyanmarDigits()} ကြိမ် ပေးခဲ့တယ်" },
    right = "မှန်တယ်",
    wrong = "ဒီတစ်ခါ မမှန်ဘူး",
    cameTo = { a, aAmount, b, bAmount -> "$a က $aAmount၊ $b က $bAmount ကုန်ခဲ့တယ်။" },
    freezeProgress = { have, of -> "ခွင့်ရက် တစ်ရက်အတွက် အဖြေ ${of.inMyanmarDigits()} ခုထဲက ${have.inMyanmarDigits()} ခု" },
    freezeFilled = { n -> "ခွင့်ရက် တစ်ရက် ပြည့်ပြီ။ အခု ${n.inMyanmarDigits()} ရက် ရှိတယ်။" },
    freezesFull = "ခွင့်ရက် အများဆုံး ရထားပြီ။",
    openStamp = "ဒီနေ့ တံဆိပ်ကို ဖွင့်မယ်",
    end = "ဒီနေ့ မေးခွန်း ဒါပါပဲ။ မနက်ဖြန် အသစ် လာမယ်။",
    none = "လွန်ခဲ့တဲ့ ၇ ရက်မှာ နှိုင်းယှဉ်စရာ မရှိဘူး။ မနက်ဖြန် ပြန်လာခဲ့ပါ။",
    pickLabel = { name -> "$name က ပိုကုန်တယ်" },
    paisenPicked = "PaiSeN က မင်းရဲ့ တစ်ပတ်ထဲက နီးနီးချင်း ကုန်ကျစရိတ် နှစ်ခု ရွေးထားတယ်။",
)

val BurmesePlayGuess = PlayGuessCopy(
    eyebrow = "အပတ်ကို ခန့်မှန်းကြည့်",
    title = "ပြီးခဲ့တဲ့ အပတ် ဘယ်လောက် ကုန်ခဲ့လဲ",
    week = { from, to -> "$from မှ $to" },
    less = "လျှော့",
    more = "တိုး",
    guess = "ခန့်မှန်းမယ်",
    skip = "မခန့်မှန်းဘဲ ကြည့်မယ်",
    record = { best, weeks -> "အကောင်းဆုံး: $best% · ${weeks.inMyanmarDigits()} ပတ် ကစားပြီး" },
    firstTime = "ပထမဆုံး ခန့်မှန်းချက်ပါ။ PaiSeN က ခန့်မှန်းပြီးပြီ။",
    revealTitle = "ပြီးခဲ့တဲ့ အပတ် ကုန်ခဲ့တာ",
    you = "သင်",
    paisen = "PaiSeN",
    off = { amount -> "$amount ကွာတယ်" },
    spotOn = "အတိအကျ မှန်တယ်",
    closer = "ပိုနီးတယ်",
    youWon = "PaiSeN ကို နိုင်သွားပြီ။ တံဆိပ်တစ်ခု စာအုပ်ထဲ ရောက်ပြီ။",
    paisenWon = "ဒီတစ်ခါ PaiSeN က ပိုနီးတယ်။ ဘာမှ မဆုံးရှုံးပါဘူး။",
    how = "PaiSeN ဘယ်လို ခန့်မှန်းလဲ",
    howWeek = { week, amount -> "$week: $amount" },
    howTrend = { amount -> "လမ်းကြောင်း ထပ်ပေါင်း: $amount" },
    seeWeek = "အပတ်ကို ကြည့်မယ်",
    missing = "PaiSeN ခန့်မှန်းဖို့ အရင် အပတ် ၂ ပတ် လိုတယ်။ အပတ်ကို ကြည့်လို့ ရပါပြီ။",
    sliderLabel = "သင့် ခန့်မှန်းချက်",
    paisenToo = "PaiSeN လည်း ခန့်မှန်းထားပြီ။ သူ့ဂဏန်းက မင်းဟာ ပြီးမှ ပြမယ်။",
)

val BurmesePlayCoach = PlayCoachCopy(
    more = { category, amount, weekday -> "$category: ပုံမှန် $weekday ထက် $amount ပိုကုန်တယ်" },
    less = { category, amount, weekday -> "$category: ပုံမှန် $weekday ထက် $amount လျော့ကုန်တယ်" },
    label = { line -> "PaiSeN: $line။ နောက်ကွယ်က စာရင်းတွေကို ဖွင့်မယ်။" },
)

val BurmesePlay = PlayCopy(
    settings = BurmesePlaySettings,
    run = BurmesePlayRun,
    stamps = BurmesePlayStamps,
    question = BurmesePlayQuestion,
    guess = BurmesePlayGuess,
    coach = BurmesePlayCoach,
    reminder = PlayReminderCopy(channel = "အပတ်စဉ် ကစားစရာ", body = "သင့် အပတ် အဆင်သင့်ဖြစ်ပြီ။ အရင် ခန့်မှန်းကြည့်မလား။"),
    extras = BurmesePlayExtras,
    group = BurmesePlayGroup,
    tips = BurmesePlayTips,
    local = "ဒီဖုန်းထဲမှာပဲ တွက်ထားတယ်",
)
