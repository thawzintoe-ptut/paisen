package com.ptut.expense.locale

/** #22: how the weekly note sounds. Plain is the default; Playful is chosen in Settings. */
enum class NoteTone { Plain, Playful }

/**
 * #22's words: the weekly note in two tones, the Planned mark, and the "That's not right" sheet.
 * Its own file, read through [noteCopy], so it needs no field on [Copy]. A language with no entry
 * here reads the English. Playful never mocks the person and never comments on income.
 */
data class NoteCopy(
    val rose: (category: String, amount: String, shop: String?) -> String = { category, amount, shop ->
        "$category rose $amount" + (shop?.let { ", mostly at $it" } ?: "") + "."
    },
    val roseAfterPlanned: (category: String, amount: String, planned: String) -> String = { category, amount, planned ->
        "$category rose $amount after the planned $planned."
    },
    val under: (amount: String) -> String = { amount -> "$amount under a usual week." },
    val same: String = "Right on a usual week.",
    val planned: String = "Planned",
    val plannedHint: String = "Left out of weekly notes",
    val notRight: String = "That's not right",
    val whatsOff: String = "What's off?",
    val somePlanned: String = "Some of it was planned",
    val wrongCategory: String = "A category is wrong",
    val hideWeek: String = "Just hide this week",
    val sheetHint: String = "The first two open last week's spending in History.",
    val toneTitle: String = "Note tone",
    val tonePlain: String = "Plain",
    val tonePlayful: String = "Playful",
    val toneHint: String = "Written on this phone. Nothing is sent.",
)

private val EnglishPlayful = NoteCopy(
    rose = { category, amount, shop ->
        (shop?.let { "$it had a good week: " } ?: "") + "$category ran $amount above usual."
    },
    roseAfterPlanned = { category, amount, planned -> "$planned of $category was planned. Only $amount sneaked in." },
    under = { amount -> "$amount under a usual week. Your wallet noticed." },
    same = "Right on a usual week. Steady.",
)

private val BurmesePlain = NoteCopy(
    rose = { category, amount, shop -> "$category $amount တက်တယ်" + (shop?.let { "၊ အများစုက $it မှာ" } ?: "") + "။" },
    roseAfterPlanned = { category, amount, planned -> "စီစဉ်ထားတဲ့ $planned ကလွဲရင် $category $amount တက်တယ်။" },
    under = { amount -> "ပုံမှန် တစ်ပတ်ထက် $amount နည်းတယ်။" },
    same = "ပုံမှန် တစ်ပတ်အတိုင်းပဲ။",
    planned = "စီစဉ်ထားတာ",
    plannedHint = "အပတ်စဉ် မှတ်ချက်ထဲ မထည့်ဘူး",
    notRight = "ဒါ မမှန်ဘူး",
    whatsOff = "ဘာမှားနေလဲ",
    somePlanned = "တချို့က စီစဉ်ထားပြီးသား",
    wrongCategory = "အမျိုးအစား မှားနေတယ်",
    hideWeek = "ဒီအပတ် ဖျောက်ထားမယ်",
    sheetHint = "ပထမ နှစ်ခုက ပြီးခဲ့တဲ့ အပတ်ရဲ့ သုံးငွေကို မှတ်တမ်းထဲမှာ ဖွင့်ပြမယ်။",
    toneTitle = "မှတ်ချက် လေသံ",
    tonePlain = "ရိုးရိုး",
    tonePlayful = "ပေါ့ပေါ့ပါးပါး",
    toneHint = "ဒီဖုန်းထဲမှာပဲ ရေးတယ်။ ဘာမှ မပို့ဘူး။",
)

private val BurmesePlayful = BurmesePlain.copy(
    rose = { category, amount, shop ->
        (shop?.let { "$it အတွက် ကောင်းတဲ့ အပတ်ပဲ၊ " } ?: "") + "$category ပုံမှန်ထက် $amount ပိုသွားတယ်။"
    },
    roseAfterPlanned = { category, amount, planned -> "$category $planned က စီစဉ်ထားပြီးသား၊ $amount ပဲ ခိုးဝင်လာတယ်။" },
    under = { amount -> "ပုံမှန်ထက် $amount နည်းတယ်။ ပိုက်ဆံအိတ်လေး ပျော်နေပြီ။" },
    same = "ပုံမှန်အတိုင်း တည်တည်ငြိမ်ငြိမ်ပဲ။",
)

private val ThaiPlain = NoteCopy(
    rose = { category, amount, shop -> "${category}เพิ่มขึ้น $amount" + (shop?.let { " ส่วนใหญ่ที่ $it" } ?: "") },
    roseAfterPlanned = { category, amount, planned -> "${category}เพิ่ม $amount ไม่นับ $planned ที่วางแผนไว้" },
    under = { amount -> "ต่ำกว่าสัปดาห์ปกติ $amount" },
    same = "เท่ากับสัปดาห์ปกติ",
    planned = "วางแผนไว้",
    plannedHint = "ไม่นับในบันทึกรายสัปดาห์",
    notRight = "ไม่ถูกต้อง",
    whatsOff = "อะไรไม่ถูก",
    somePlanned = "บางส่วนวางแผนไว้แล้ว",
    wrongCategory = "หมวดหมู่ไม่ถูก",
    hideWeek = "ซ่อนสัปดาห์นี้",
    sheetHint = "สองข้อแรกจะเปิดรายจ่ายสัปดาห์ที่แล้วในประวัติ",
    toneTitle = "น้ำเสียงของบันทึก",
    tonePlain = "เรียบ ๆ",
    tonePlayful = "สนุก ๆ",
    toneHint = "เขียนบนโทรศัพท์เครื่องนี้ ไม่ส่งออกไปไหน",
)

private val ThaiPlayful = ThaiPlain.copy(
    rose = { category, amount, shop -> (shop?.let { "สัปดาห์นี้ $it ยิ้มเลย " } ?: "") + "${category}เกินปกติไป $amount" },
    roseAfterPlanned = { _, amount, planned -> "$planned วางแผนไว้แล้ว แอบเพิ่มมาแค่ $amount" },
    under = { amount -> "ต่ำกว่าปกติ $amount กระเป๋าตังค์ยิ้มแล้ว" },
    same = "เท่าเดิมเป๊ะ สม่ำเสมอดี",
)

/** The words in the chosen language and tone. */
fun noteCopy(tone: NoteTone = NoteTone.Plain): NoteCopy = when (AppText.language.value) {
    Language.Burmese -> if (tone == NoteTone.Playful) BurmesePlayful else BurmesePlain
    Language.Thai -> if (tone == NoteTone.Playful) ThaiPlayful else ThaiPlain
    else -> if (tone == NoteTone.Playful) EnglishPlayful else NoteCopy()
}
