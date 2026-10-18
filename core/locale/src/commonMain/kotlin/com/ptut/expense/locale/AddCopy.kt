package com.ptut.expense.locale

/** The Add sheet, Quick add and the mic. */
data class AddCopy(
    val pickCategoryHint: String = "Pick a category to save this",
    val incomeHint: String = "Income · lands in today, week and month",
    val byHandHint: String = "Added by hand · no slip for this",
    val askPlaceholder: String = "Type it: coffee 60 · กาแฟ 60 · ကော်ဖီ ၆၀",
    val listening: String = "Listening…",
    val sayIt: String = "Say it",
    val stopListening: String = "Stop listening",
    val voiceUnavailable: (language: String) -> String = { language ->
        "$language is not available offline on this phone. Type it instead."
    },
    val couldNotRead: String = "Could not read that. Fill it in below.",
    /** #216: the part of a sentence Quick add could not read. Only one is ever missing today. */
    val notRead: (amount: Boolean, category: Boolean) -> String = { amount, category ->
        when {
            amount && category -> "Could not read the amount or the category. Fill them in below."
            amount -> "Could not read the amount. Type it below."
            else -> "Could not read the category. Pick one below."
        }
    },
    val didNotCatch: String = "Didn't catch that. Try again or type it.",
    val send: String = "Send",
    val allowMicrophone: String = "Allow the microphone in Settings to say it. Typing still works.",
    val saved: (amount: String, category: String) -> String = { amount, category -> "Saved $amount · $category" },
    val spentToday: (amount: String) -> String = { amount -> "Spent $amount today" },
    val spentThisWeek: (amount: String) -> String = { amount -> "Spent $amount this week" },
    val spentThisMonth: (amount: String) -> String = { amount -> "Spent $amount this month" },
    val rowsMatch: (Int) -> String = { n -> if (n == 1) "1 row matches" else "$n rows match" },
)

val BurmeseAdd = AddCopy(
    pickCategoryHint = "သိမ်းဖို့ အမျိုးအစား ရွေးပါ",
    incomeHint = "ဝင်ငွေ · ဒီနေ့၊ ဒီအပတ်နဲ့ ဒီလ စုစုပေါင်းထဲ ဝင်မယ်",
    byHandHint = "ကိုယ်တိုင် ထည့်တာ · စလစ် မရှိဘူး",
    askPlaceholder = "ရိုက်ထည့်ပါ: coffee 60 · กาแฟ 60 · ကော်ဖီ ၆၀",
    listening = "နားထောင်နေတယ်…",
    sayIt = "ပြောမယ်",
    stopListening = "နားထောင်တာ ရပ်မယ်",
    voiceUnavailable = { language -> "ဒီဖုန်းမှာ $language ကို အင်တာနက်မပါဘဲ နားမထောင်နိုင်ဘူး။ ရိုက်ထည့်ပါ။" },
    couldNotRead = "ဒါကို ဖတ်လို့ မရဘူး။ အောက်မှာ ဖြည့်ပါ။",
    notRead = { amount, category ->
        when {
            amount && category -> "ပမာဏနဲ့ အမျိုးအစားကို ဖတ်လို့ မရဘူး။ အောက်မှာ ဖြည့်ပါ။"
            amount -> "ပမာဏကို ဖတ်လို့ မရဘူး။ အောက်မှာ ရိုက်ထည့်ပါ။"
            else -> "အမျိုးအစားကို ဖတ်လို့ မရဘူး။ အောက်မှာ ရွေးပါ။"
        }
    },
    didNotCatch = "ဘာပြောလဲ မကြားလိုက်ဘူး။ ထပ်ပြောပါ၊ ဒါမှမဟုတ် ရိုက်ထည့်ပါ။",
    send = "ပို့မယ်",
    allowMicrophone = "ပြောပြီး ထည့်ဖို့ ဆက်တင်မှာ မိုက်ခရိုဖုန်း ခွင့်ပြုပါ။ ရိုက်ထည့်တာကတော့ ရသေးတယ်။",
    saved = { amount, category -> "$amount သိမ်းပြီးပြီ · $category" },
    spentToday = { amount -> "ဒီနေ့ $amount သုံးထားတယ်" },
    spentThisWeek = { amount -> "ဒီအပတ် $amount သုံးထားတယ်" },
    spentThisMonth = { amount -> "ဒီလ $amount သုံးထားတယ်" },
    rowsMatch = { n -> "ကိုက်တာ ${n.inMyanmarDigits()} ခု ရှိတယ်" },
)
