package com.ptut.expense.locale

/** The Add sheet, Quick add and the mic. */
data class AddCopy(
    val pickCategoryHint: String = "Pick a category to save this",
    val typeAmountHint: String = "Type an amount to save this",
    val askPlaceholder: String = "Type it: coffee 60",
    val listening: String = "Listening…",
    val sayIt: String = "Say it",
    val stopListening: String = "Stop listening",
    /** The pill beside the mic and the sheet it opens. */
    val voiceLanguageTitle: String = "Voice language",
    val voiceLanguageLabel: (language: String) -> String = { language -> "Voice language: $language" },
    val voiceOnPhone: String = "On this phone. Works offline.",
    /** Asked once per language, before its first online listen. The body is [voiceOnline]. */
    val onlineAskTitle: (language: String) -> String = { language -> "$language is heard online" },
    val listenOnline: String = "Listen",
    val typeInstead: String = "Type instead",
    val saveAmount: (amount: String) -> String = { amount -> "Save $amount" },
    val voiceUnavailable: (language: String) -> String = { language ->
        "$language voice isn't available on this phone. Type it instead."
    },
    /** Said whenever the voice will leave the phone, before the person speaks. */
    val voiceOnline: (language: String) -> String = { language ->
        "$language is recognised online by Google, so your voice is sent to Google."
    },
    /** A pack the phone can fetch. Android downloads it, and the words still stay on the phone. */
    val voiceDownloadable: (language: String) -> String = { language ->
        "$language voice isn't on this phone yet. Download it once and it works offline."
    },
    val downloadVoice: (language: String) -> String = { language -> "Download $language" },
    val voiceDownloading: (language: String) -> String = { language ->
        "Downloading $language. The mic appears when it's ready."
    },
    val voiceDownloadFailed: (language: String) -> String = { language ->
        "The phone couldn't download $language. Try again on Wi-Fi, or type it."
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
    typeAmountHint = "သိမ်းဖို့ ပမာဏ ရိုက်ထည့်ပါ",
    askPlaceholder = "ရိုက်ထည့်ပါ: ကော်ဖီ ၆၀",
    listening = "နားထောင်နေတယ်…",
    sayIt = "ပြောမယ်",
    stopListening = "နားထောင်တာ ရပ်မယ်",
    voiceLanguageTitle = "အသံ ဘာသာစကား",
    voiceLanguageLabel = { language -> "အသံ ဘာသာစကား: $language" },
    voiceOnPhone = "ဖုန်းထဲမှာ ရှိတယ်။ အင်တာနက် မလိုဘူး။",
    onlineAskTitle = { language -> "$language ကို အွန်လိုင်းကနေ နားထောင်တယ်" },
    listenOnline = "နားထောင်မယ်",
    typeInstead = "ရိုက်ထည့်မယ်",
    saveAmount = { amount -> "$amount သိမ်းမယ်" },
    voiceUnavailable = { language -> "ဒီဖုန်းမှာ $language အသံနဲ့ ထည့်လို့ မရသေးဘူး။ ရိုက်ထည့်ပါ။" },
    voiceOnline = { language -> "$language ကို Google က အွန်လိုင်းကနေ နားထောင်ပေးတာမို့ သင့်အသံကို Google ဆီ ပို့ရတယ်။" },
    voiceDownloadable = { language -> "ဒီဖုန်းမှာ $language အသံ မရှိသေးဘူး။ တစ်ခါ ဒေါင်းလုဒ်လုပ်ထားရင် အင်တာနက်မပါဘဲ ပြောလို့ရမယ်။" },
    downloadVoice = { language -> "$language ဒေါင်းလုဒ်လုပ်မယ်" },
    voiceDownloading = { language -> "$language ဒေါင်းလုဒ်လုပ်နေတယ်။ ပြီးရင် မိုက် ပေါ်လာမယ်။" },
    voiceDownloadFailed = { language -> "ဖုန်းက $language ကို ဒေါင်းလုဒ် မလုပ်နိုင်ဘူး။ Wi-Fi နဲ့ ထပ်ကြိုးစားပါ၊ ဒါမှမဟုတ် ရိုက်ထည့်ပါ။" },
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

/** [language] arrives as the language's own name ("English", "ไทย"), so it keeps a space on each side. */
val ThaiAdd = AddCopy(
    pickCategoryHint = "เลือกหมวดหมู่เพื่อบันทึก",
    typeAmountHint = "ใส่จำนวนเงินเพื่อบันทึก",
    askPlaceholder = "พิมพ์เลย: กาแฟ 60",
    listening = "กำลังฟัง…",
    sayIt = "พูด",
    stopListening = "หยุดฟัง",
    voiceLanguageTitle = "ภาษาที่พูด",
    voiceLanguageLabel = { language -> "ภาษาที่พูด: $language" },
    voiceOnPhone = "อยู่ในโทรศัพท์ ใช้ได้แม้ไม่มีอินเทอร์เน็ต",
    onlineAskTitle = { language -> "เสียงพูด $language ฟังผ่านออนไลน์" },
    listenOnline = "ฟัง",
    typeInstead = "พิมพ์แทน",
    saveAmount = { amount -> "บันทึก $amount" },
    voiceUnavailable = { language -> "โทรศัพท์เครื่องนี้ใช้เสียงพูด $language ไม่ได้ พิมพ์แทนได้เลย" },
    voiceOnline = { language -> "Google ฟังเสียงพูด $language ทางออนไลน์ เสียงของคุณจึงถูกส่งไปที่ Google" },
    voiceDownloadable = { language ->
        "โทรศัพท์เครื่องนี้ยังไม่มีเสียงพูด $language ดาวน์โหลดครั้งเดียว แล้วใช้ได้แม้ไม่มีอินเทอร์เน็ต"
    },
    downloadVoice = { language -> "ดาวน์โหลด $language" },
    voiceDownloading = { language -> "กำลังดาวน์โหลด $language ไมค์จะแสดงเมื่อพร้อม" },
    voiceDownloadFailed = { language -> "โทรศัพท์ดาวน์โหลด $language ไม่ได้ ลองอีกครั้งผ่าน Wi-Fi หรือพิมพ์แทน" },
    couldNotRead = "อ่านไม่ออก กรอกด้านล่างได้เลย",
    notRead = { amount, category ->
        when {
            amount && category -> "อ่านจำนวนเงินและหมวดหมู่ไม่ออก กรอกด้านล่างได้เลย"
            amount -> "อ่านจำนวนเงินไม่ออก พิมพ์ด้านล่างได้เลย"
            else -> "อ่านหมวดหมู่ไม่ออก เลือกด้านล่างได้เลย"
        }
    },
    didNotCatch = "ฟังไม่ทัน ลองอีกครั้งหรือพิมพ์แทน",
    send = "ส่ง",
    allowMicrophone = "อนุญาตไมโครโฟนในการตั้งค่าเพื่อพูด ยังพิมพ์ได้ตามปกติ",
    saved = { amount, category -> "บันทึก $amount แล้ว · $category" },
    spentToday = { amount -> "วันนี้ใช้ไป $amount" },
    spentThisWeek = { amount -> "สัปดาห์นี้ใช้ไป $amount" },
    spentThisMonth = { amount -> "เดือนนี้ใช้ไป $amount" },
    rowsMatch = { n -> "ตรงกัน $n รายการ" },
)
