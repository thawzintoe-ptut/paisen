package com.ptut.expense.locale

/**
 * #93–#97's words in shared groups. Praise only: nothing here ranks people by what they owe (R7).
 * Read through [playGroupCopy]; Thai reads the English (D12).
 */
data class PlayGroupCopy(
    // #93, p14
    val planHeadline: (Int) -> String = { n -> if (n == 1) "1 payment settles everyone" else "$n payments settle everyone" },
    val planSaves: (Int) -> String = { n -> "Instead of $n back-and-forth payments" },
    val planFewest: String = "Already the fewest payments",
    val yourPart: String = "Your part",
    val settleMyPart: String = "Settle my part",
    // #94, p11
    val wheelTitle: String = "Who pays this round?",
    val wheelFair: String = "Fair wheel",
    val wheelFairLine: String = "Whoever paid fewer rounds lately gets a bigger slice",
    val wheelEqualLine: String = "Every slice is the same size",
    val wheelSpin: String = "Spin",
    val wheelSpinAgain: String = "Spin again",
    val wheelLanded: (name: String) -> String = { name -> "$name pays this round" },
    val wheelSlice: (name: String, rounds: Int) -> String = { name, rounds ->
        when (rounds) {
            0 -> "$name has paid no rounds lately"
            1 -> "$name paid 1 round lately"
            else -> "$name paid $rounds rounds lately"
        }
    },
    val wheelUsual: (amount: String) -> String = { amount -> "A usual round here is about $amount" },
    val wheelAdd: (name: String) -> String = { name -> "Add the round, $name paid" },
    val wheelOpen: String = "Who pays this round?",
    val wheelNoOne: String = "Add at least two people to spin the wheel.",
    // #95, p10
    val allSquare: String = "All square",
    val allSquareLine: String = "Everyone is settled. A stamp is in your book.",
    val react: String = "React",
    val reactions: Map<String, String> = mapOf("Thanks" to "Thanks", "WellPlayed" to "Well played", "Cheers" to "Cheers"),
    val reactedBy: (kind: String, name: String) -> String = { kind, name -> "$kind from $name" },
    val takeBack: String = "Take back",
    // #96, p12
    val awardsTitle: (month: String) -> String = { month -> "Awards for $month" },
    val awardsOpen: String = "Monthly awards",
    val awardNames: Map<String, String> = mapOf(
        "FastestToSettle" to "Fastest to settle",
        "KeptTheBooks" to "Kept the books",
        "SteadyPayer" to "Steady payer",
    ),
    val fastestLine: (days: Int) -> String = { days -> if (days == 1) "Paid back within 1 day of a share" else "Paid back within $days days of a share" },
    val booksLine: (count: Int) -> String = { count -> if (count == 1) "Added 1 entry" else "Added $count entries" },
    val steadyLine: (weeks: Int) -> String = { weeks -> if (weeks == 1) "Paid back in 1 week of the month" else "Paid back in $weeks weeks of the month" },
    val awardsNone: String = "Not enough this month for awards yet.",
    val awardsBehind: String = "Awards show once this phone is up to date.",
    // #97, p13
    val tripEnded: String = "Trip ended",
    val tripEndedLine: String = "Mark the trip over to see its recap",
    val tripOpenRecap: String = "Trip recap",
    val recapTitle: String = "Trip recap",
    val perPersonPerDay: String = "per person per day",
    val recapLine: (days: Int, people: Int) -> String = { days, people ->
        "${if (days == 1) "1 day" else "$days days"}, ${if (people == 1) "1 person" else "$people people"}"
    },
    val recapTotal: (amount: String) -> String = { amount -> "Total $amount" },
    val nextTrip: (estimate: String, perWeek: String, weeks: Int) -> String = { estimate, perWeek, weeks ->
        "A trip like this is about $estimate. Put $perWeek aside a week for $weeks weeks."
    },
    val startJar: String = "Start a jar",
    val nextTripTitle: String = "Next trip like this",
    val recapNone: String = "Nothing was spent on this trip.",
    val share: String = "Share",
    val dayBar: (day: String, amount: String) -> String = { day, amount -> "$day: $amount" },
)

val BurmesePlayGroup = PlayGroupCopy(
    planHeadline = { n -> "ပေးချေမှု ${n.inMyanmarDigits()} ခုနဲ့ အားလုံး ကျေမယ်" },
    planSaves = { n -> "အပြန်အလှန် ${n.inMyanmarDigits()} ခါ ပေးမယ့်အစား" },
    planFewest = "အနည်းဆုံး ပေးချေမှု ဖြစ်ပြီးသား",
    yourPart = "သင့်အပိုင်း",
    settleMyPart = "ကိုယ့်အပိုင်း ပေးချေမယ်",
    wheelTitle = "ဒီတစ်ခါ ဘယ်သူ ပေးမလဲ",
    wheelFair = "မျှတတဲ့ ဘီး",
    wheelFairLine = "မကြာခင်က နည်းနည်း ပေးခဲ့သူက အကွက် ပိုကြီးရမယ်",
    wheelEqualLine = "အကွက်တိုင်း အရွယ်တူတယ်",
    wheelSpin = "လှည့်မယ်",
    wheelSpinAgain = "ထပ်လှည့်မယ်",
    wheelLanded = { name -> "ဒီတစ်ခါ $name ပေးမယ်" },
    wheelSlice = { name, rounds -> "$name က မကြာခင်က ${rounds.inMyanmarDigits()} ခါ ပေးခဲ့တယ်" },
    wheelUsual = { amount -> "ဒီအဖွဲ့ရဲ့ ပုံမှန် တစ်ခါစာ $amount လောက်" },
    wheelAdd = { name -> "$name ပေးတဲ့အဖြစ် ထည့်မယ်" },
    wheelOpen = "ဒီတစ်ခါ ဘယ်သူ ပေးမလဲ",
    wheelNoOne = "ဘီးလှည့်ဖို့ လူ ၂ ယောက် အနည်းဆုံး ထည့်ပါ။",
    allSquare = "အကြွေး ကျေပြီ",
    allSquareLine = "အားလုံး ကျေပြီ။ တံဆိပ်တစ်ခု စာအုပ်ထဲ ရောက်ပြီ။",
    react = "တုံ့ပြန်မယ်",
    reactions = mapOf("Thanks" to "ကျေးဇူး", "WellPlayed" to "တော်တယ်", "Cheers" to "ချီးယားစ်"),
    reactedBy = { kind, name -> "$name ဆီက $kind" },
    takeBack = "ပြန်ရုပ်မယ်",
    awardsTitle = { month -> "$month ဆုတွေ" },
    awardsOpen = "လစဉ် ဆုတွေ",
    awardNames = mapOf("FastestToSettle" to "အမြန်ဆုံး ပေးချေသူ", "KeptTheBooks" to "စာရင်း ထိန်းသူ", "SteadyPayer" to "ပုံမှန် ပေးချေသူ"),
    fastestLine = { days -> "ခွဲပြီး ${days.inMyanmarDigits()} ရက်အတွင်း ပြန်ပေးတယ်" },
    booksLine = { count -> "စာရင်း ${count.inMyanmarDigits()} ခု ထည့်ခဲ့တယ်" },
    steadyLine = { weeks -> "လထဲက ${weeks.inMyanmarDigits()} ပတ်မှာ ပြန်ပေးခဲ့တယ်" },
    awardsNone = "ဒီလ ဆုပေးဖို့ မလုံလောက်သေးဘူး။",
    awardsBehind = "ဒီဖုန်း နောက်ဆုံးအခြေအနေ ရောက်ရင် ဆုတွေ ပြမယ်။",
    tripEnded = "ခရီး ပြီးပြီ",
    tripEndedLine = "ခရီး အနှစ်ချုပ် ကြည့်ဖို့ ပြီးကြောင်း မှတ်ပါ",
    tripOpenRecap = "ခရီး အနှစ်ချုပ်",
    recapTitle = "ခရီး အနှစ်ချုပ်",
    perPersonPerDay = "တစ်ယောက် တစ်ရက်",
    recapLine = { days, people -> "${days.inMyanmarDigits()} ရက်၊ ${people.inMyanmarDigits()} ယောက်" },
    recapTotal = { amount -> "စုစုပေါင်း $amount" },
    nextTrip = { estimate, perWeek, weeks -> "ဒီလို ခရီးတစ်ခု $estimate လောက် ကုန်မယ်။ ${weeks.inMyanmarDigits()} ပတ် တစ်ပတ်ကို $perWeek စုထားပါ။" },
    startJar = "ပန်းတိုင်ဘူး စမယ်",
    nextTripTitle = "ဒီလို နောက်ခရီး",
    recapNone = "ဒီခရီးမှာ ဘာမှ မသုံးခဲ့ဘူး။",
    share = "မျှဝေမယ်",
    dayBar = { day, amount -> "$day: $amount" },
)
