package com.ptut.expense.locale

/** The day receipt. */
data class ReceiptCopy(
    val city: String = "BANGKOK",
    val other: String = "Other",
    val nothingSpent: String = "Nothing was spent on this day.",
    val total: String = "TOTAL",
    val keptOnPhone: String = "Kept on this phone. Nothing was sent anywhere.",
)

val BurmeseReceipt = ReceiptCopy(
    city = "ဘန်ကောက်",
    other = "အခြား",
    nothingSpent = "ဒီရက်မှာ ဘာမှ မသုံးခဲ့ဘူး။",
    total = "စုစုပေါင်း",
    keptOnPhone = "ဒီဖုန်းထဲမှာပဲ သိမ်းထားတယ်။ ဘယ်ကိုမှ မပို့ထားဘူး။",
)
