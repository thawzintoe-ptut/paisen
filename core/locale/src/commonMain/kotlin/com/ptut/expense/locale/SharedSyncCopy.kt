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
    val endingElsewhere: (Int) -> String = { n -> "Signed in on another phone · $n ${if (n == 1) "change" else "changes"} still to send" },
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
    endingElsewhere = { n -> "တခြားဖုန်းမှာ အကောင့်ဝင်ထားတယ် · ပြောင်းထားတာ ${n.inMyanmarDigits()} ခု ပို့ဖို့ ကျန်သေးတယ်" },
)

val ThaiSharedSync = SharedSyncCopy(
    offline = "ไม่มีการเชื่อมต่อ ลองอีกครั้งเมื่อออนไลน์",
    refused = "กลุ่มไม่ยอมรับการเปลี่ยนแปลงนี้",
    sessionEnded = "ลงชื่อเข้าใช้อีกครั้งเพื่อแชร์ต่อ",
    inviteExpired = "คำเชิญนี้หมดอายุแล้ว ขอคำเชิญใหม่",
    signInToShare = "ลงชื่อเข้าใช้เพื่อแชร์กลุ่ม",
    aGroup = "กลุ่มหนึ่ง",
    noSuchCode = "ไม่มีกลุ่มที่ใช้โค้ดนี้ ตรวจสอบโค้ด หรือขอลิงก์อีกครั้ง",
    nameTaken = "มีคนเพิ่งเลือกชื่อนี้ไป เลือกชื่ออื่น",
    signedOutGroup = { n ->
        if (n == 0) "ออกจากระบบแล้ว ลงชื่อเข้าใช้เพื่อซิงก์กลุ่มนี้ต่อ" else "ออกจากระบบแล้ว · ยังไม่ได้ส่ง $n รายการ"
    },
    signedOutHome = "ออกจากระบบแล้ว ลงชื่อเข้าใช้เพื่อซิงก์กลุ่มของคุณต่อ",
    signInAgainReason = "ลงชื่อเข้าใช้ด้วยบัญชีเดิม แล้วกลุ่มของคุณจะซิงก์อีกครั้ง",
    endingElsewhere = { n -> "ลงชื่อเข้าใช้ในโทรศัพท์อีกเครื่องแล้ว · ยังต้องส่งอีก $n รายการ" },
)
