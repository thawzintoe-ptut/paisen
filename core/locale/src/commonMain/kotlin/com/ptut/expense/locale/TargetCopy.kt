package com.ptut.expense.locale

/**
 * #21's words: the one weekly spend-less target. Plain amounts, never "over budget" and never red.
 *
 * Its own file, read through [targetCopy], so the target needs no field on [Copy]. A language with
 * no entry here reads the English.
 */
data class TargetCopy(
    val fieldLabel: String = "Weekly target",
    val fieldHint: String = "Spend less than this in a week. Leave it empty for no target.",
    val usualWeek: (amount: String) -> String = { amount -> "A usual week here: $amount" },
    val replaces: (amount: String, category: String) -> String = { amount, category ->
        "Saving replaces the $amount target on $category. There is one target at a time."
    },
    val weekLine: (category: String, spent: String, target: String) -> String = { category, spent, target ->
        "$category: $spent of $target this week"
    },
    val stayedUnder: (category: String, target: String) -> String = { category, target ->
        "$category stayed under its $target target."
    },
    val wentPast: (category: String, over: String, target: String) -> String = { category, over, target ->
        "$category went $over past its $target target."
    },
    val ofTarget: (target: String) -> String = { target -> "of $target" },
)

val BurmeseTarget = TargetCopy(
    fieldLabel = "အပတ်စဉ် ပန်းတိုင်",
    fieldHint = "တစ်ပတ်မှာ ဒီထက် နည်းနည်း သုံးမယ်။ ပန်းတိုင် မထားချင်ရင် ဗလာထားပါ။",
    usualWeek = { amount -> "ဒီအမျိုးအစားရဲ့ ပုံမှန် တစ်ပတ်: $amount" },
    replaces = { amount, category -> "သိမ်းရင် $category ပေါ်က $amount ပန်းတိုင်ကို အစားထိုးမယ်။ ပန်းတိုင် တစ်ခုပဲ ထားလို့ရတယ်။" },
    weekLine = { category, spent, target -> "$category: ဒီအပတ် $target ထဲက $spent" },
    stayedUnder = { category, target -> "$category က $target ပန်းတိုင်အောက်မှာ ရှိခဲ့တယ်။" },
    wentPast = { category, over, target -> "$category က $target ပန်းတိုင်ထက် $over ကျော်သွားတယ်။" },
    ofTarget = { target -> "ပန်းတိုင် $target" },
)

val ThaiTarget = TargetCopy(
    fieldLabel = "เป้าหมายรายสัปดาห์",
    fieldHint = "ใช้จ่ายให้น้อยกว่านี้ในหนึ่งสัปดาห์ เว้นว่างไว้ถ้าไม่ต้องการเป้าหมาย",
    usualWeek = { amount -> "สัปดาห์ปกติของหมวดนี้: $amount" },
    replaces = { amount, category -> "การบันทึกจะแทนที่เป้าหมาย $amount ของ $category ตั้งเป้าหมายได้ครั้งละหนึ่งรายการ" },
    weekLine = { category, spent, target -> "$category: สัปดาห์นี้ $spent จาก $target" },
    stayedUnder = { category, target -> "$category อยู่ต่ำกว่าเป้าหมาย $target" },
    wentPast = { category, over, target -> "$category เกินเป้าหมาย $target ไป $over" },
    ofTarget = { target -> "จากเป้าหมาย $target" },
)

/** The target's words in the chosen language. */
fun targetCopy(): TargetCopy = when (AppText.language.value) {
    Language.Burmese -> BurmeseTarget
    Language.Thai -> ThaiTarget
    else -> TargetCopy()
}
