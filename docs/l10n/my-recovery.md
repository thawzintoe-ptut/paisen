# Burmese review: a ledger that will not open, and the crash-report prompt

Written 2026-11-21 without a native reader. Every line below needs a native read. Path: `core/locale/src/commonMain/kotlin/com/ptut/expense/locale/RecoveryCopy.kt`.

| English | Burmese | Where |
|---|---|---|
| Your ledger could not open | စာရင်းကို ဖွင့်လို့ မရဘူး | RecoveryCopy.kt `title` |
| Nothing was deleted. A copy of the file is kept on this phone. Pick what to do next. | ဘာမှ မဖျက်ရသေးဘူး။ ဖိုင်ရဲ့ မိတ္တူကို ဒီဖုန်းထဲမှာ သိမ်းထားတယ်။ ဆက်လုပ်မယ့်ဟာကို ရွေးပါ။ | RecoveryCopy.kt `body` |
| Export the file | ဖိုင်ကို ထုတ်မယ် | RecoveryCopy.kt `export` |
| Start fresh (your old file is kept) | အသစ်က စမယ် (ဖိုင်အဟောင်းကို သိမ်းထားမယ်) | RecoveryCopy.kt `startFresh` |
| Restore from a backup | Backup ကနေ ပြန်ယူမယ် | RecoveryCopy.kt `restore` |
| PaiSeN closed unexpectedly last time. Share a report? | PaiSeN က အရင်တစ်ခေါက် ရုတ်တရက် ပိတ်သွားတယ်။ Report ပို့မလား။ | RecoveryCopy.kt `crashTitle` |
| The report has the app version, the phone model and where the app stopped. No amounts, names or notes. | Report ထဲမှာ app version၊ ဖုန်းအမျိုးအစားနဲ့ app ရပ်သွားတဲ့နေရာပဲ ပါတယ်။ ငွေပမာဏ၊ နာမည်၊ မှတ်စု မပါဘူး။ | RecoveryCopy.kt `crashBody` |
| Share | ပို့မယ် | RecoveryCopy.kt `share` |
| Not now | နောက်မှ | RecoveryCopy.kt `notNow` |
