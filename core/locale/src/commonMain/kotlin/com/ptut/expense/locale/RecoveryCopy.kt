package com.ptut.expense.locale

/**
 * The words for a ledger that would not open and for the crash-report prompt. Read through
 * [recoveryCopy], so it needs no field on [Copy]. Thai reads the English until a Thai reader signs it off.
 */
data class RecoveryCopy(
    val title: String = "Your ledger could not open",
    val body: String = "Nothing was deleted. A copy of the file is kept on this phone. Pick what to do next.",
    val export: String = "Export the file",
    val startFresh: String = "Start fresh (your old file is kept)",
    val restore: String = "Restore from a backup",
    val crashTitle: String = "PaiSeN closed unexpectedly last time. Share a report?",
    val crashBody: String = "The report has the app version, the phone model and where the app stopped. No amounts, names or notes.",
    val share: String = "Share",
    val notNow: String = "Not now",
)

val BurmeseRecovery = RecoveryCopy(
    title = "စာရင်းကို ဖွင့်လို့ မရဘူး",
    body = "ဘာမှ မဖျက်ရသေးဘူး။ ဖိုင်ရဲ့ မိတ္တူကို ဒီဖုန်းထဲမှာ သိမ်းထားတယ်။ ဆက်လုပ်မယ့်ဟာကို ရွေးပါ။",
    export = "ဖိုင်ကို ထုတ်မယ်",
    startFresh = "အသစ်က စမယ် (ဖိုင်အဟောင်းကို သိမ်းထားမယ်)",
    restore = "Backup ကနေ ပြန်ယူမယ်",
    crashTitle = "PaiSeN က အရင်တစ်ခေါက် ရုတ်တရက် ပိတ်သွားတယ်။ Report ပို့မလား။",
    crashBody = "Report ထဲမှာ app version၊ ဖုန်းအမျိုးအစားနဲ့ app ရပ်သွားတဲ့နေရာပဲ ပါတယ်။ ငွေပမာဏ၊ နာမည်၊ မှတ်စု မပါဘူး။",
    share = "ပို့မယ်",
    notNow = "နောက်မှ",
)

fun recoveryCopy(): RecoveryCopy = when (AppText.language.value) {
    Language.Burmese -> BurmeseRecovery
    else -> RecoveryCopy()
}
