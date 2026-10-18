package com.ptut.expense.locale

/** The lines a sync failure shows, and the words the sync code hands to a screen. */
data class SharedSyncCopy(
    val offline: String = "No connection. Try again when you're online.",
    val refused: String = "The group didn't accept this.",
    val sessionEnded: String = "Sign in again to keep sharing.",
    val inviteExpired: String = "This invite has expired. Ask for a new one.",
    val signInToShare: String = "Sign in to share groups",
    val aGroup: String = "A group",
    val noSuchCode: String = "No group has this code. Check it, or ask for the link again.",
    val nameTaken: String = "Someone just picked this name. Choose another one.",
    val signedOutGroup: (Int) -> String = { n ->
        if (n == 0) "Signed out. Sign in to keep this group in sync." else "Signed out · $n ${if (n == 1) "change" else "changes"} not sent"
    },
    val signedOutHome: String = "Signed out. Sign in to keep your shared groups in sync.",
    val signInAgainReason: String = "Sign in with the same account, and your shared groups sync again.",
)

val BurmeseSharedSync = SharedSyncCopy(
    offline = "အင်တာနက် မရှိဘူး။ အွန်လိုင်း ဖြစ်မှ ထပ်ကြိုးစားပါ။",
    refused = "အဖွဲ့က ဒါကို လက်မခံဘူး။",
    sessionEnded = "ဆက်မျှသုံးဖို့ အကောင့် ပြန်ဝင်ပါ။",
    inviteExpired = "ဒီဖိတ်စာ သက်တမ်းကုန်ပြီ။ အသစ်တစ်ခု တောင်းပါ။",
    signInToShare = "အဖွဲ့တွေ မျှသုံးဖို့ အကောင့်ဝင်ပါ",
    aGroup = "အဖွဲ့တစ်ခု",
    noSuchCode = "ဒီကုဒ်နဲ့ အဖွဲ့ မရှိဘူး။ ကုဒ်ကို ပြန်စစ်ပါ၊ ဒါမှမဟုတ် လင့်ခ် ပြန်တောင်းပါ။",
    nameTaken = "ဒီနာမည်ကို တခြားသူ ခုလေးတင် ရွေးသွားပြီ။ တခြားနာမည် ရွေးပါ။",
    signedOutGroup = { n ->
        if (n == 0) "အကောင့်ထွက်နေတယ်။ ဒီအဖွဲ့ ဆက်ချိတ်ထားဖို့ အကောင့် ပြန်ဝင်ပါ။" else "အကောင့်ထွက်နေတယ် · ပြောင်းထားတာ ${n.inMyanmarDigits()} ခု မပို့ရသေးဘူး"
    },
    signedOutHome = "အကောင့်ထွက်နေတယ်။ Shared group တွေ ဆက်ချိတ်ထားဖို့ အကောင့် ပြန်ဝင်ပါ။",
    signInAgainReason = "အရင်အကောင့်နဲ့ ပြန်ဝင်ရင် Shared group တွေ ပြန်ချိတ်မယ်။",
)
