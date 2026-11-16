package com.ptut.expense.locale

/**
 * #26's words: money in from a slip someone sent you, and your own name as slips print it. Its own
 * file, read through [incomeSlipCopy], so it needs no field on [Copy].
 */
data class IncomeSlipCopy(
    val moneyInFrom: String = "Money in from",
    val ownerRow: String = "Your name on slips",
    val ownerUnknown: String = "Not learned yet",
    val ownerTitle: String = "Your name on slips",
    val ownerHint: String =
        "As K PLUS prints you as the sender, like MR. THAW Z. A slip that names you as the recipient is filed as money in. " +
            "Leave it empty and PaiSeN learns it again from your own slips.",
    val save: String = "Save",
    val cancel: String = "Cancel",
)

val BurmeseIncomeSlip = IncomeSlipCopy(
    moneyInFrom = "ဝင်ငွေ ပေးသူ",
    ownerRow = "စလစ်ပေါ်က သင့်နာမည်",
    ownerUnknown = "မသိရသေးဘူး",
    ownerTitle = "စလစ်ပေါ်က သင့်နာမည်",
    ownerHint = "K PLUS က ပေးပို့သူအဖြစ် ထုတ်တဲ့အတိုင်း၊ ဥပမာ MR. THAW Z။ သင့်ကို လက်ခံသူအဖြစ် ဖော်ပြထားတဲ့ စလစ်ကို ဝင်ငွေအဖြစ် သိမ်းမယ်။ " +
        "ဗလာထားရင် သင့်ကိုယ်ပိုင် စလစ်တွေကနေ PaiSeN ပြန်သင်ယူမယ်။",
    save = "သိမ်းမယ်",
    cancel = "မလုပ်တော့ဘူး",
)

val ThaiIncomeSlip = IncomeSlipCopy(
    moneyInFrom = "รับเงินจาก",
    ownerRow = "ชื่อของคุณบนสลิป",
    ownerUnknown = "ยังไม่รู้จัก",
    ownerTitle = "ชื่อของคุณบนสลิป",
    ownerHint = "ตามที่ K PLUS พิมพ์ชื่อคุณเป็นผู้โอน เช่น MR. THAW Z สลิปที่ระบุคุณเป็นผู้รับจะบันทึกเป็นเงินเข้า " +
        "เว้นว่างไว้ PaiSeN จะเรียนรู้ชื่อใหม่จากสลิปของคุณเอง",
    save = "บันทึก",
    cancel = "ยกเลิก",
)

/** The words in the chosen language. */
fun incomeSlipCopy(): IncomeSlipCopy = when (AppText.language.value) {
    Language.Burmese -> BurmeseIncomeSlip
    Language.Thai -> ThaiIncomeSlip
    else -> IncomeSlipCopy()
}
