package com.ptut.expense.locale

/** The domain's own phrases: row names, meta lines, day labels, elapsed times and retention. */
data class PhrasesCopy(
    val unknownShop: String = "Unknown shop",
    val uncategorised: String = "Uncategorised",
    val unknownCategory: String = "Unknown category",
    val unsorted: String = "Unsorted",
    val auto: String = "auto",
    val addedByHand: String = "added by hand",
    val dateAndTime: (date: String, time: String) -> String = { date, time -> "$date, $time" },
    val listComma: String = ", ",
    val listAnd: String = " and ",
    val underAMinute: String = "under a minute",
    val minutes: (Long) -> String = { n -> "$n min" },
    val hours: (Long) -> String = { n -> if (n == 1L) "1 hour" else "$n hours" },
    val days: (Long) -> String = { n -> if (n == 1L) "1 day" else "$n days" },
    val lessThanUsual: (amount: String, whose: String, weekday: String) -> String =
        { amount, whose, weekday -> "$amount less than $whose usual $weekday" },
    val moreThanUsual: (amount: String, whose: String, weekday: String) -> String =
        { amount, whose, weekday -> "$amount more than $whose usual $weekday" },
    val sameAsUsual: (whose: String, weekday: String) -> String = { whose, weekday -> "the same as $whose usual $weekday" },
    val percentUnderUsual: (percent: Int, weekday: String) -> String = { percent, weekday -> "$percent% under a usual $weekday" },
    val percentOverUsual: (percent: Int, weekday: String) -> String = { percent, weekday -> "$percent% over a usual $weekday" },
    val sameAsBaseline: (baseline: String) -> String = { baseline -> "the same as $baseline" },
    val percentMoreThan: (percent: Int, baseline: String) -> String = { percent, baseline -> "$percent% more than $baseline" },
    val percentLessThan: (percent: Int, baseline: String) -> String = { percent, baseline -> "$percent% less than $baseline" },
    val spokenBaht: (digits: String) -> String = { digits -> "$digits baht" },
    val restoreEmpty: String = "That file held no transactions",
    val restoreNothingNew: (Int) -> String = { held -> "Nothing new — your history already held all $held" },
    val restoreAdded: (Int) -> String = { added -> "Added $added ${if (added == 1) "transaction" else "transactions"}" },
    val restoreAlreadyHere: (Int) -> String = { held -> "$held already here" },
    val restoreGroups: (Int) -> String = { groups -> "$groups shared ${if (groups == 1) "group" else "groups"}" },
    val restoreAddedOnly: (String) -> String = { what -> "Added $what" },
    val restoreLostCategory: (Int) -> String = { lost -> if (lost == 1) "1 lost its category" else "$lost lost their category" },
    val backupNotReadable: String = "This is not a file this app can read",
    val backupOtherVersion: (Int) -> String = { format -> "This file was written by a different version of the app (format $format)" },
    val backupUnknownValue: (field: String, value: String) -> String = { field, value -> "The file holds an unknown $field: $value" },
    val everyWeekday: (weekday: String) -> String = { weekday -> "Every $weekday" },
    val everyOtherWeekday: (weekday: String) -> String = { weekday -> "Every other $weekday" },
    val monthlyOn: (ordinal: String) -> String = { ordinal -> "The $ordinal of each month" },
    val yearlyOn: (day: Int, month: String) -> String = { day, month -> "$day $month each year" },
    val ordinal: (Int) -> String = { n ->
        val suffix = if (n % 100 in 11..13) {
            "th"
        } else {
            when (n % 10) {
                1 -> "st"
                2 -> "nd"
                3 -> "rd"
                else -> "th"
            }
        }
        "$n$suffix"
    },
)

val BurmesePhrases = PhrasesCopy(
    unknownShop = "မသိတဲ့ဆိုင်",
    uncategorised = "အမျိုးအစား မရှိ",
    unknownCategory = "မသိတဲ့ အမျိုးအစား",
    unsorted = "အမျိုးအစားခွဲ မရှိ",
    auto = "အလိုလို",
    addedByHand = "ကိုယ်တိုင်ထည့်ထား",
    dateAndTime = { date, time -> "$date၊ $time" },
    listComma = "၊ ",
    listAnd = "နဲ့ ",
    underAMinute = "တစ်မိနစ်",
    minutes = { n -> "${n.inMyanmarDigits()} မိနစ်" },
    hours = { n -> "${n.inMyanmarDigits()} နာရီ" },
    days = { n -> "${n.inMyanmarDigits()} ရက်" },
    lessThanUsual = { amount, _, weekday -> "ပုံမှန်${weekday}ထက် $amount နည်းတယ်" },
    moreThanUsual = { amount, _, weekday -> "ပုံမှန်${weekday}ထက် $amount များတယ်" },
    sameAsUsual = { _, weekday -> "ပုံမှန်${weekday}နဲ့ အတူတူပဲ" },
    percentUnderUsual = { percent, weekday -> "ပုံမှန်${weekday}ထက် $percent% နည်းတယ်" },
    percentOverUsual = { percent, weekday -> "ပုံမှန်${weekday}ထက် $percent% များတယ်" },
    sameAsBaseline = { baseline -> "${baseline}နဲ့ အတူတူပဲ" },
    percentMoreThan = { percent, baseline -> "${baseline}ထက် $percent% များတယ်" },
    percentLessThan = { percent, baseline -> "${baseline}ထက် $percent% နည်းတယ်" },
    spokenBaht = { digits -> "$digits ဘတ်" },
    restoreEmpty = "ဒီဖိုင်ထဲမှာ ငွေစာရင်း တစ်ခုမှ မပါဘူး",
    restoreNothingNew = { held -> "အသစ် မရှိဘူး — မှတ်တမ်းထဲမှာ ${held.inMyanmarDigits()} ခုလုံး ရှိပြီးသား" },
    restoreAdded = { added -> "ငွေစာရင်း ${added.inMyanmarDigits()} ခု ထည့်ပြီးပြီ" },
    restoreAlreadyHere = { held -> "${held.inMyanmarDigits()} ခု ရှိပြီးသား" },
    restoreGroups = { groups -> "Shared group ${groups.inMyanmarDigits()} ဖွဲ့" },
    restoreAddedOnly = { what -> "$what ထည့်ပြီးပြီ" },
    restoreLostCategory = { lost -> "${lost.inMyanmarDigits()} ခုက အမျိုးအစား ပျောက်သွားတယ်" },
    backupNotReadable = "ဒီဖိုင်ကို ဒီအက်ပ်က ဖတ်လို့ မရဘူး",
    backupOtherVersion = { format -> "ဒီဖိုင်ကို တခြား ဗားရှင်းက ရေးထားတာ (format $format)" },
    backupUnknownValue = { field, value -> "ဖိုင်ထဲမှာ မသိတဲ့ $field ပါတယ်: $value" },
    everyWeekday = { weekday -> "${weekday}တိုင်း" },
    everyOtherWeekday = { weekday -> "တစ်ပတ်ခြား $weekday" },
    monthlyOn = { ordinal -> "လတိုင်း $ordinal" },
    yearlyOn = { day, month -> "နှစ်တိုင်း $day $month" },
    ordinal = { n -> "$n ရက်" },
)
