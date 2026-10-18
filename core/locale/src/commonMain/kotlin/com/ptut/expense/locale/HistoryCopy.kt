package com.ptut.expense.locale

/** History: search, filters and the list. */
data class HistoryCopy(
    val title: String = "History",
    val search: String = "Search purchases",
    val filters: String = "Filters",
    val show: String = "Show",
    val filterAll: String = "All",
    val filterAuto: String = "Auto-filed",
    val filterByHand: String = "By hand",
    val filterEdited: String = "Edited",
    val emptyHeadline: String = "Nothing recorded yet",
    val emptyBody: String = "Slips file themselves as they arrive, and anything you add by hand lands here too.",
    val emptyStatus: String = "Your history fills itself as slips arrive",
    val noMatchHeadline: String = "No transactions match",
    val noMatchBody: String = "Nothing here matches what you are looking for. Clear the filters, or search for less.",
    val hiddenByFilter: (Int) -> String = { n -> "$n transactions outside this filter" },
    val clearFilters: String = "Clear filters",
    val clearSearch: String = "Clear search",
)

val BurmeseHistory = HistoryCopy(
    title = "မှတ်တမ်း",
    search = "သုံးထားတာ ရှာမယ်",
    filters = "စစ်ထုတ်မှု",
    show = "ဘာတွေ ပြမလဲ",
    filterAll = "အားလုံး",
    filterAuto = "အလိုလို ထည့်ထား",
    filterByHand = "ကိုယ်တိုင်ထည့်ထား",
    filterEdited = "ပြင်ထား",
    emptyHeadline = "ဘာမှ မမှတ်ရသေးဘူး",
    emptyBody = "စလစ်တွေ ဝင်လာတာနဲ့ အလိုလို ထည့်မယ်။ ကိုယ်တိုင်ထည့်တာတွေလည်း ဒီမှာ ပေါ်မယ်။",
    emptyStatus = "စလစ်တွေ ဝင်လာတာနဲ့ မှတ်တမ်း အလိုလို ပြည့်လာမယ်",
    noMatchHeadline = "ကိုက်တာ မရှိဘူး",
    noMatchBody = "ရှာတာနဲ့ ကိုက်တာ ဒီမှာ မရှိဘူး။ စစ်ထုတ်တာတွေ ဖြုတ်ပါ၊ ဒါမှမဟုတ် နည်းနည်းပဲ ရှာကြည့်ပါ။",
    hiddenByFilter = { n -> "ဒီစစ်ထုတ်မှု အပြင်မှာ ${n.inMyanmarDigits()} ခု ရှိတယ်" },
    clearFilters = "စစ်ထုတ်တာ ဖြုတ်မယ်",
    clearSearch = "ရှာထားတာ ဖျက်မယ်",
)
