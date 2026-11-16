package com.ptut.expense.locale

/**
 * Words for money in another currency: a kyat slip that asks for a rate (#33), and the Add sheet's
 * currency and rate. Its own file, read through [currencyCopy], so it needs no field on [Copy]. A
 * language with no entry here reads the English. [unit] is "1,000 K" or "$", what one rate buys.
 */
data class CurrencyCopy(
    val rateNeeded: String = "A kyat slip needs a rate",
    val rateNeededBody: (shop: String, amount: String) -> String = { shop, amount -> "$shop · $amount · add the rate in To check" },
    val reasonRate: String = "Kyat rate needed",
    val rateLabel: String = "Rate",
    val perUnit: (unit: String) -> String = { unit -> "฿ per $unit" },
    val todaysRate: String = "See today's rate",
    val askRate: String = "Type the rate first",
    val inBaht: (baht: String) -> String = { baht -> "Saved as $baht" },
    val rateNote: String = "Later entries in this currency use this rate until you change it.",
    val paidLabel: String = "Paid",
    val slipsNeedRate: (Int) -> String = { n -> if (n == 1) "one kyat slip" else "$n kyat slips" },
    val atRate: (amount: String, rate: String, unit: String) -> String = { amount, rate, unit -> "$amount at ฿$rate per $unit" },
    val currencyTitle: String = "Currency",
    val startsIn: String = "Add starts in",
    val startsInIntro: String = "The Add sheet opens on this currency. Totals still count in baht.",
    val fromPhone: (code: String) -> String = { code -> "$code · from this phone" },
    val followPhone: (code: String) -> String = { code -> "Follow this phone ($code)" },
    val rateFor: (code: String) -> String = { code -> "Rate for $code" },
    val setRate: (code: String) -> String = { code -> "Set the rate for $code" },
    val converted: (baht: String, rate: String, unit: String) -> String = { baht, rate, unit -> "$baht · ฿$rate per $unit" },
    val rateOnly: (rate: String, unit: String) -> String = { rate, unit -> "฿$rate per $unit" },
)

private val Burmese = CurrencyCopy(
    rateNeeded = "ကျပ်စလစ်အတွက် ငွေလဲနှုန်း လိုတယ်",
    rateNeededBody = { shop, amount -> "$shop · $amount · စစ်ဖို့ထဲမှာ နှုန်းထည့်ပါ" },
    reasonRate = "ကျပ်နှုန်း လိုတယ်",
    rateLabel = "နှုန်း",
    perUnit = { unit -> "$unit ကို ฿" },
    todaysRate = "ဒီနေ့ နှုန်း ကြည့်မယ်",
    askRate = "နှုန်းအရင် ထည့်ပါ",
    inBaht = { baht -> "$baht အဖြစ် သိမ်းမယ်" },
    rateNote = "ဒီငွေနဲ့ နောက်ထည့်တာတွေက ဒီနှုန်းကို သုံးမယ်။ ပြောင်းချင်ရင် ပြောင်းလို့ရတယ်။",
    paidLabel = "ပေးခဲ့တာ",
    slipsNeedRate = { n -> "ကျပ်စလစ် ${n.inMyanmarDigits()} ခု" },
    atRate = { amount, rate, unit -> "$amount၊ $unit ကို ฿$rate နှုန်း" },
    currencyTitle = "ငွေကြေး",
    startsIn = "ထည့်ရင် စမယ့်ငွေ",
    startsInIntro = "ထည့်တဲ့ စာမျက်နှာက ဒီငွေနဲ့ စမယ်။ စုစုပေါင်းကတော့ ဘတ်နဲ့ပဲ တွက်တယ်။",
    fromPhone = { code -> "$code · ဒီဖုန်းအတိုင်း" },
    followPhone = { code -> "ဒီဖုန်းအတိုင်း ($code)" },
    rateFor = { code -> "$code နှုန်း" },
    setRate = { code -> "$code နှုန်း ထည့်ပါ" },
    converted = { baht, rate, unit -> "$baht · $unit ကို ฿$rate" },
    rateOnly = { rate, unit -> "$unit ကို ฿$rate" },
)

private val Thai = CurrencyCopy(
    rateNeeded = "สลิปจ๊าดต้องใส่อัตราแลกเปลี่ยน",
    rateNeededBody = { shop, amount -> "$shop · $amount · ใส่อัตราในรอตรวจ" },
    reasonRate = "ต้องใส่อัตราจ๊าด",
    rateLabel = "อัตรา",
    perUnit = { unit -> "฿ ต่อ $unit" },
    todaysRate = "ดูอัตราวันนี้",
    askRate = "ใส่อัตราก่อน",
    inBaht = { baht -> "บันทึกเป็น $baht" },
    rateNote = "รายการสกุลนี้ครั้งต่อไปใช้อัตรานี้จนกว่าคุณจะเปลี่ยน",
    paidLabel = "จ่าย",
    slipsNeedRate = { n -> "สลิปจ๊าด $n ใบ" },
    atRate = { amount, rate, unit -> "$amount ที่ ฿$rate ต่อ $unit" },
    currencyTitle = "สกุลเงิน",
    startsIn = "เพิ่มรายการเริ่มที่",
    startsInIntro = "หน้าเพิ่มรายการเปิดที่สกุลนี้ ยอดรวมยังนับเป็นบาท",
    fromPhone = { code -> "$code · ตามเครื่องนี้" },
    followPhone = { code -> "ตามเครื่องนี้ ($code)" },
    rateFor = { code -> "อัตรา $code" },
    setRate = { code -> "ใส่อัตรา $code" },
    converted = { baht, rate, unit -> "$baht · ฿$rate ต่อ $unit" },
    rateOnly = { rate, unit -> "฿$rate ต่อ $unit" },
)

fun currencyCopy(): CurrencyCopy = when (AppText.language.value) {
    Language.Burmese -> Burmese
    Language.Thai -> Thai
    else -> CurrencyCopy()
}
