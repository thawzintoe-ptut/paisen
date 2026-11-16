package com.ptut.expense.locale

/**
 * #33's words: a kyat slip asks for a rate before it becomes baht. Its own file, read through
 * [kyatCopy], so it needs no field on [Copy]. A language with no entry here reads the English.
 */
data class KyatCopy(
    val rateNeeded: String = "A kyat slip needs a rate",
    val rateNeededBody: (shop: String, amount: String) -> String = { shop, amount -> "$shop · $amount · add the rate in To check" },
    val reasonRate: String = "Kyat rate needed",
    val rateLabel: String = "Rate",
    val rateUnit: String = "฿ per 1,000 K",
    val todaysRate: String = "See today's rate",
    val askRate: String = "Type the rate first",
    val inBaht: (baht: String) -> String = { baht -> "Saved as $baht" },
    val rateNote: String = "Later kyat slips use this rate until you change it.",
    val paidLabel: String = "Paid",
    val slipsNeedRate: (Int) -> String = { n -> if (n == 1) "one kyat slip" else "$n kyat slips" },
    val atRate: (amount: String, rate: String) -> String = { amount, rate -> "$amount at ฿$rate per 1,000 K" },
)

private val Burmese = KyatCopy(
    rateNeeded = "ကျပ်စလစ်အတွက် ငွေလဲနှုန်း လိုတယ်",
    rateNeededBody = { shop, amount -> "$shop · $amount · စစ်ဖို့ထဲမှာ နှုန်းထည့်ပါ" },
    reasonRate = "ကျပ်နှုန်း လိုတယ်",
    rateLabel = "နှုန်း",
    rateUnit = "1,000 K ကို ฿",
    todaysRate = "ဒီနေ့ နှုန်း ကြည့်မယ်",
    askRate = "နှုန်းအရင် ထည့်ပါ",
    inBaht = { baht -> "$baht အဖြစ် သိမ်းမယ်" },
    rateNote = "နောက် ကျပ်စလစ်တွေက ဒီနှုန်းကို သုံးမယ်။ ပြောင်းချင်ရင် ပြောင်းလို့ရတယ်။",
    paidLabel = "ပေးခဲ့တာ",
    slipsNeedRate = { n -> "ကျပ်စလစ် ${n.inMyanmarDigits()} ခု" },
    atRate = { amount, rate -> "$amount၊ 1,000 K ကို ฿$rate နှုန်း" },
)

private val Thai = KyatCopy(
    rateNeeded = "สลิปจ๊าดต้องใส่อัตราแลกเปลี่ยน",
    rateNeededBody = { shop, amount -> "$shop · $amount · ใส่อัตราในรอตรวจ" },
    reasonRate = "ต้องใส่อัตราจ๊าด",
    rateLabel = "อัตรา",
    rateUnit = "฿ ต่อ 1,000 K",
    todaysRate = "ดูอัตราวันนี้",
    askRate = "ใส่อัตราก่อน",
    inBaht = { baht -> "บันทึกเป็น $baht" },
    rateNote = "สลิปจ๊าดใบต่อไปใช้อัตรานี้จนกว่าคุณจะเปลี่ยน",
    paidLabel = "จ่าย",
    slipsNeedRate = { n -> "สลิปจ๊าด $n ใบ" },
    atRate = { amount, rate -> "$amount ที่ ฿$rate ต่อ 1,000 K" },
)

fun kyatCopy(): KyatCopy = when (AppText.language.value) {
    Language.Burmese -> Burmese
    Language.Thai -> Thai
    else -> KyatCopy()
}
