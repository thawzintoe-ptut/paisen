package com.ptut.expense.locale

/** History: search, filters and the list. */
data class HistoryCopy(
    val title: String = "History",
    val search: String = "Search purchases",
    val filters: String = "Filters",
    val show: String = "Show",
    val filterAll: String = "All",
    val filterSpending: String = "Spending",
    val filterAuto: String = "Auto-filed",
    val filterByHand: String = "By hand",
    val filterEdited: String = "Edited",
    val moreFilters: String = "More filters",
    val emptyHeadline: String = "Nothing recorded yet",
    val emptyBody: String = "Slips file themselves as they arrive, and anything you add by hand lands here too.",
    val emptyStatus: String = "Your history fills itself as slips arrive",
    val noMatchHeadline: String = "No transactions match",
    val noMatchBody: String = "Nothing here matches what you are looking for. Clear the filters, or search for less.",
    val hiddenByFilter: (Int) -> String = { n -> "$n transactions outside this filter" },
    val clearFilters: String = "Clear filters",
    val clearSearch: String = "Clear search",
    /** #70: the top line while the newest month is at the top, and the month index's spoken name. */
    val monthsTotal: (Int, Int) -> String = { n, months -> "${transactions(n)} over $months months" },
    val monthsAbove: (Int, Int) -> String = { n, months ->
        "${transactions(n, "newer ")} above, from $months ${if (months == 1) "month" else "months"}"
    },
    val goToMonth: (String) -> String = { month -> "Go to $month" },
)

private fun transactions(n: Int, kind: String = ""): String = "$n $kind${if (n == 1) "transaction" else "transactions"}"

val BurmeseHistory = HistoryCopy(
    title = "မှတ်တမ်း",
    search = "သုံးထားတာ ရှာမယ်",
    filters = "စစ်ထုတ်မှု",
    show = "ဘာတွေ ပြမလဲ",
    filterAll = "အားလုံး",
    filterSpending = "အသုံး",
    filterAuto = "အလိုလို ထည့်ထား",
    filterByHand = "ကိုယ်တိုင်ထည့်ထား",
    filterEdited = "ပြင်ထား",
    moreFilters = "စစ်ထုတ်မှု ထပ်ကြည့်မယ်",
    emptyHeadline = "ဘာမှ မမှတ်ရသေးဘူး",
    emptyBody = "စလစ်တွေ ဝင်လာတာနဲ့ အလိုလို ထည့်မယ်။ ကိုယ်တိုင်ထည့်တာတွေလည်း ဒီမှာ ပေါ်မယ်။",
    emptyStatus = "စလစ်တွေ ဝင်လာတာနဲ့ မှတ်တမ်း အလိုလို ပြည့်လာမယ်",
    noMatchHeadline = "ကိုက်တာ မရှိဘူး",
    noMatchBody = "ရှာတာနဲ့ ကိုက်တာ ဒီမှာ မရှိဘူး။ စစ်ထုတ်တာတွေ ဖြုတ်ပါ၊ ဒါမှမဟုတ် နည်းနည်းပဲ ရှာကြည့်ပါ။",
    hiddenByFilter = { n -> "ဒီစစ်ထုတ်မှု အပြင်မှာ ${n.inMyanmarDigits()} ခု ရှိတယ်" },
    clearFilters = "စစ်ထုတ်တာ ဖြုတ်မယ်",
    clearSearch = "ရှာထားတာ ဖျက်မယ်",
    monthsTotal = { n, months -> "${months.inMyanmarDigits()} လအတွင်း ${n.inMyanmarDigits()} ခု" },
    monthsAbove = { n, months -> "အပေါ်မှာ ပိုသစ်တဲ့ ${n.inMyanmarDigits()} ခု၊ ${months.inMyanmarDigits()} လစာ ရှိတယ်" },
    goToMonth = { month -> "$month ကို သွားမယ်" },
)

val ThaiHistory = HistoryCopy(
    title = "ประวัติ",
    search = "ค้นหารายการ",
    filters = "ตัวกรอง",
    show = "แสดง",
    filterAll = "ทั้งหมด",
    filterSpending = "รายจ่าย",
    filterAuto = "บันทึกอัตโนมัติ",
    filterByHand = "เพิ่มเอง",
    filterEdited = "แก้ไขแล้ว",
    moreFilters = "ตัวกรองเพิ่มเติม",
    emptyHeadline = "ยังไม่มีรายการ",
    emptyBody = "สลิปจะบันทึกเองเมื่อเข้ามา และรายการที่คุณเพิ่มเองก็จะแสดงที่นี่ด้วย",
    emptyStatus = "ประวัติจะเพิ่มขึ้นเองเมื่อมีสลิปเข้ามา",
    noMatchHeadline = "ไม่มีรายการที่ตรงกัน",
    noMatchBody = "ไม่มีรายการที่ตรงกับสิ่งที่คุณค้นหา ล้างตัวกรอง หรือค้นหาให้กว้างขึ้น",
    hiddenByFilter = { n -> "อีก $n รายการอยู่นอกตัวกรองนี้" },
    clearFilters = "ล้างตัวกรอง",
    clearSearch = "ล้างคำค้นหา",
    monthsTotal = { n, months -> "$n รายการใน $months เดือน" },
    monthsAbove = { n, months -> "รายการใหม่กว่า $n รายการด้านบน จาก $months เดือน" },
    goToMonth = { month -> "ไปที่ $month" },
)
