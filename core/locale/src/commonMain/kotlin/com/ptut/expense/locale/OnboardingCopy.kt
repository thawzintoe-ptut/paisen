package com.ptut.expense.locale

/** First run. */
data class OnboardingCopy(
    val headline: String = "Spending,\nfinally\nsimple.",
    val promise: String = "Bank slips from your gallery become a tidy record of every baht. Your spending stays on this phone.",
    val settingTitle: String = "One setting to change.",
    val settingText: String = "Set K PLUS slip language to English so the reader can see the numbers.",
    val restore: String = "Restore",
    val allowPhotos: String = "Allow photos",
    val byHand: String = "I'll add expenses by hand",
    val restoreFailed: String = "That file could not be read. Nothing was changed.",
)

val BurmeseOnboarding = OnboardingCopy(
    headline = "ငွေသုံးစာရင်း\nလွယ်လွယ်လေး\nဖြစ်သွားပြီ။",
    promise = "ဖုန်းထဲက ဘဏ်စလစ်တွေကို ငွေသုံးစာရင်းအဖြစ် အလိုလို မှတ်ပေးတယ်။ ဒေတာတွေ ဒီဖုန်းထဲမှာပဲ ရှိနေမယ်။",
    settingTitle = "ပြောင်းစရာ ဆက်တင် တစ်ခု ရှိတယ်။",
    settingText = "ဂဏန်းတွေ ဖတ်လို့ရအောင် K PLUS ရဲ့ စလစ်ဘာသာကို အင်္ဂလိပ်လို ပြောင်းထားပါ။",
    restore = "ပြန်ယူမယ်",
    allowPhotos = "ဓာတ်ပုံ ခွင့်ပြုမယ်",
    byHand = "အသုံးတွေကို ကိုယ်တိုင် ထည့်မယ်",
    restoreFailed = "ဒီဖိုင်ကို ဖတ်လို့ မရဘူး။ ဘာမှ မပြောင်းလဲဘူး။",
)

val ThaiOnboarding = OnboardingCopy(
    headline = "รายจ่าย\nง่ายขึ้น\nเสียที",
    promise = "สลิปธนาคารในแกลเลอรีกลายเป็นบันทึกรายจ่ายที่เป็นระเบียบทุกบาท ข้อมูลการใช้จ่ายของคุณอยู่ในโทรศัพท์เครื่องนี้เท่านั้น",
    settingTitle = "มีการตั้งค่าเดียวที่ต้องเปลี่ยน",
    settingText = "ตั้งภาษาสลิปของ K PLUS เป็นภาษาอังกฤษ เพื่อให้แอปอ่านตัวเลขได้",
    restore = "กู้คืน",
    allowPhotos = "อนุญาตการเข้าถึงรูป",
    byHand = "ฉันจะเพิ่มรายจ่ายเอง",
    restoreFailed = "อ่านไฟล์นั้นไม่ได้ ไม่มีอะไรเปลี่ยนแปลง",
)
