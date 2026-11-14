# Burmese review: the tester page

The Burmese on `firebase/hosting/index.html` (the `MY` table in its script). A native reader should check it before the page goes live. Counts use ၀–၉ and stay on one line with their unit; amounts (฿) and dates keep 0–9, as in the app. Tab names follow `ShellCopy.kt`.

| English | Burmese | Key |
|---|---|---|
| Test PaiSeN | PaiSeN စမ်းသုံးပါ | `title` |
| Try PaiSeN for 14 days | PaiSeN ကို ၁၄ ရက် စမ်းသုံးပေးပါ | `heroTitle` |
| PaiSeN reads the payment slips in your photos and keeps a record of what you spent. Slips are read on your phone, and nothing is uploaded. Before it goes on Google Play, I need 20 friends to test it. | PaiSeN က ဓာတ်ပုံထဲက ငွေလွှဲစလစ်တွေကို ဖတ်ပြီး သင် ဘာတွေ သုံးခဲ့လဲ မှတ်ပေးတယ်။ စလစ်တွေကို ဖုန်းထဲမှာပဲ ဖတ်တာမို့ ဘယ်ကိုမှ မပို့ပါဘူး။ Google Play ပေါ် မတင်ခင် စမ်းသုံးပေးမယ့် သူငယ်ချင်း ၂၀ ယောက် လိုပါတယ်။ | `heroBody` |
| Join on Android | Android နဲ့ ဝင်မယ် | `ctaAndroid` |
| Install on iPhone | iPhone မှာ ထည့်မယ် (checked by a native reader, 13 Nov) | `ctaIphone` |
| Free. Your own spending needs no account. For now, slip reading works with K PLUS set to English. | အခမဲ့ပါ။ ကိုယ်ပိုင် အသုံးစရိတ်အတွက် အကောင့် မလိုပါဘူး။ စလစ်ဖတ်တာက အခုတော့ English ထားတဲ့ K PLUS နဲ့ပဲ ရသေးတယ်။ (second sentence new 14 Nov, not yet checked) | `heroFine` |
| Transfer complete | ငွေလွှဲပြီးပါပြီ | `slipDone` |
| To | လက်ခံသူ | `slipTo` |
| Date | ရက်စွဲ | `slipDate` |
| Ref | Ref (corrected by a native reader, 13 Nov) | `slipRef` |
| Food & drink · read from a slip | အစားအသောက် · စလစ်ကနေ ဖတ်ထားတာ | `rowSub` |
| A slip in your photos becomes a row in PaiSeN. | ဓာတ်ပုံထဲက စလစ်တစ်ခုက PaiSeN ထဲမှာ စာရင်းတစ်ကြောင်း ဖြစ်လာမယ်။ (corrected by a native reader, 13 Nov) | `demoCaption` |
| How to join | ဘယ်လို ဝင်ရမလဲ | `joinTitle` |
| Join the Google Group | Google Group ထဲ ဝင်ပါ | `a1t` |
| Use the Google account that's signed in to Play Store on your phone. | ဖုန်းထဲက Play Store မှာ သုံးနေတဲ့ Google အကောင့်နဲ့ ဝင်ပါ။ | `a1b` |
| Join paisen-testers | paisen-testers ထဲ ဝင်မယ် | `a1c` |
| Become a tester | Tester အဖြစ် စာရင်းသွင်းပါ (corrected by a native reader, 13 Nov) | `a2t` |
| Tap **Become a tester** on the page that opens. If it says the app isn't available, wait a few minutes after joining the group and open it again. | ပွင့်လာတဲ့ စာမျက်နှာမှာ **Become a tester** ကို နှိပ်ပါ။ app မရသေးဘူးလို့ ပြရင် group ထဲ ဝင်ပြီး မိနစ်အနည်းငယ် စောင့်ပြီး ပြန်ဖွင့်ပါ။ | `a2b` |
| Open the tester page | Tester စာမျက်နှာ ဖွင့်မယ် | `a2c` |
| Install from Google Play | Google Play ကနေ ထည့်ပါ | `a3t` |
| Then keep PaiSeN installed for 14 days and open it now and then. | ပြီးရင် ၁၄ ရက်လုံး မဖျက်ဘဲ ထားပြီး ရံဖန်ရံခါ ဖွင့်သုံးပေးပါ။ | `a3b` |
| Open in Google Play | Google Play မှာ ဖွင့်မယ် | `a3c` |
| Use the same Google account for all three steps. | အဆင့် ၃ ဆင့်လုံးမှာ Google အကောင့် တစ်ခုတည်းကို သုံးပါ။ | `aNote` |
| There's no App Store test yet, so on iPhone you install PaiSeN with SideStore and your own Apple ID. | App Store မှာ စမ်းသုံးလို့ မရသေးလို့ iPhone မှာ SideStore နဲ့ ကိုယ့် Apple ID ကို သုံးပြီး ထည့်ရပါမယ်။ | `iNote` |
| Set up SideStore | SideStore ထည့်ပါ | `i1t` |
| Follow SideStore's own guide. You need a computer once. | SideStore ရဲ့ လမ်းညွှန်အတိုင်း လုပ်ပါ။ ကွန်ပျူတာ တစ်ခါ လိုပါမယ်။ | `i1b` |
| Open SideStore's guide | SideStore လမ်းညွှန် ဖွင့်မယ် | `i1c` |
| Download PaiSeN | PaiSeN ကို ဒေါင်းလုဒ်လုပ်ပါ | `i2t` |
| On the iPhone, open the newest release and download **PaiSeN-<version>.ipa**. | iPhone ပေါ်မှာ အသစ်ဆုံး release ကို ဖွင့်ပြီး **PaiSeN-<version>.ipa** ကို ဒေါင်းလုဒ်လုပ်ပါ။ | `i2b` |
| Open releases on GitHub | GitHub release တွေ ဖွင့်မယ် | `i2c` |
| Add it in SideStore | SideStore ထဲ ထည့်ပါ | `i3t` |
| Open **My Apps**, tap **+** and pick the file. | **My Apps** ကို ဖွင့်၊ **+** ကို နှိပ်ပြီး ဖိုင်ကို ရွေးပါ။ | `i3b` |
| Refresh once a week | တစ်ပတ် တစ်ခါ Refresh လုပ်ပါ | `i4t` |
| Open SideStore and tap **Refresh All**. A free Apple ID signs an app for 7 days, and PaiSeN won't open after that until you refresh. | SideStore ကို ဖွင့်ပြီး **Refresh All** ကို နှိပ်ပါ။ တစ်ပတ်တစ်ခါ refresh ပြန်လုပ်မယ်။ (corrected by a native reader, 13 Nov) | `i4b` |
| On iPhone, Sign in with Apple and notifications don't work yet. For shared groups, sign in with Google. | iPhone မှာ Sign in with Apple နဲ့ notification တွေ မရသေးပါဘူး။ Shared group အတွက် Google နဲ့ ဝင်ပါ။ | `iLimits` |
| What you'll test | ဘာတွေ စမ်းရမလဲ | `screensTitle` |
| Today | ဒီနေ့ | `s1t` |
| What you spent this month and your latest purchases. | ဒီလ သုံးငွေနဲ့ နောက်ဆုံး ဝယ်ထားတာတွေ။ | `s1b` |
| Reports | အစီရင်ခံစာ | `s2t` |
| Spending by week, month, 3 months, 6 months or a year. | အပတ်၊ လ၊ ၃ လ၊ ၆ လ ဒါမှမဟုတ် တစ်နှစ်စာ သုံးငွေ။ | `s2b` |
| Quick add | အမြန်ထည့် | `s3t` |
| Type or say “coffee 60” in English, Thai or Burmese. | “ကော်ဖီ 60” လို့ အင်္ဂလိပ်၊ ထိုင်း ဒါမှမဟုတ် မြန်မာလို ရိုက်လည်းရ၊ ပြောလည်းရတယ်။ | `s3b` |
| Shared | မျှသုံး | `s4t` |
| What you're owed and what you owe, across groups. | Shared group တွေထဲမှာ သင်က ရမယ့်ငွေနဲ့ ပေးရမယ့်ငွေ။ | `s4b` |
| A shared group | Shared group | `s5t` |
| Split a trip or a bill, and settle up with a PromptPay QR. | ခရီးစရိတ်၊ ထမင်းဖိုး ခွဲပြီး PromptPay QR နဲ့ ရှင်းလို့ရတယ်။ | `s5b` |
| Settings | ဆက်တင် | `s6t` |
| Photo folders, background import and your categories. | ဓာတ်ပုံ folder၊ နောက်ကွယ်က import နဲ့ ကိုယ့် အမျိုးအစားတွေ။ | `s6b` |
| What stays on your phone | ဖုန်းထဲမှာပဲ ရှိနေမယ့်အရာတွေ | `promTitle` |
| Slips are read on the phone | စလစ်တွေကို ဖုန်းထဲမှာပဲ ဖတ်တယ် | `p1t` |
| ML Kit on Android and Apple Vision on iPhone read them. Nothing is uploaded. | Android မှာ ML Kit၊ iPhone မှာ Apple Vision က ဖတ်ပေးတယ်။ ဘယ်ကိုမှ မပို့ပါဘူး။ | `p1b` |
| No account, no ads, no analytics | အကောင့်၊ ကြော်ငြာ၊ analytics မရှိဘူး | `p2t` |
| Your own spending needs no sign-in. | ကိုယ်ပိုင် အသုံးစရိတ်အတွက် sign in ဝင်စရာ မလိုပါဘူး။ | `p2b` |
| Shared groups only when you share | Shared group ကို သင်ဝေမျှမှသာသုံးလို့ရမယ် (corrected by a native reader, 13 Nov) | `p3t` |
| Only a group you share is sent, and only after you sign in. | မျှသုံးတဲ့ group ကိုပဲ ပို့ပြီး sign in ဝင်ပြီးမှ ပို့တာပါ။ | `p3b` |
| Read the privacy policy | Privacy policy ဖတ်မယ် | `privacyLink` |
| What testing asks of you | စမ်းသုံးသူတွေဆီက လိုချင်တာ | `askTitle` |
| PaiSeN reads K PLUS slips for now, and K PLUS has to be set to English. With another bank, add expenses by hand or with Quick add, and tell me which bank you use. | အခုတော့ PaiSeN က K PLUS စလစ်တွေကိုပဲ ဖတ်နိုင်တယ်၊ K PLUS ကိုလည်း English ထားပေးရမယ်။ တခြားဘဏ် သုံးရင် အသုံးစရိတ်ကို ကိုယ်တိုင် ဒါမှမဟုတ် Quick add နဲ့ ထည့်ပြီး ဘယ်ဘဏ် သုံးလဲ ပြောပြပါ။ (new 14 Nov, not yet checked) | `askSlips` |
| Keep PaiSeN installed for 14 days. | PaiSeN ကို ၁၄ ရက် မဖျက်ဘဲ ထားပါ။ | `ask1` |
| Add a few expenses, or let it read your slips. | အသုံးစရိတ် နည်းနည်း ထည့်ကြည့်ပါ၊ ဒါမှမဟုတ် စလစ်တွေကို ဖတ်ခိုင်းကြည့်ပါ။ | `ask2` |
| When something breaks or confuses you, take a screenshot and send it to the friend who shared this page. | တစ်ခုခု မှားနေရင် ဒါမှမဟုတ် နားမလည်ရင် screenshot ရိုက်ပြီး ဒီ link ပို့ပေးတဲ့ သူငယ်ချင်းဆီ ပို့ပေးပါ။ | `ask3` |
| Use GitHub? You can also open an issue. | GitHub သုံးရင် issue ဖွင့်ပေးလို့လည်း ရပါတယ်။ | `ask4` |
| PaiSeN is free software under GPL-3.0. | PaiSeN က GPL-3.0 အောက်က free software ပါ။ | `foot` |
| Source code | Source code | `footCode` |
| Privacy | Privacy | `footPrivacy` |
