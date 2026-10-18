# Burmese review: shared words, the shell and the Language screen

These Burmese strings are not in the PaiSen Sky Screens MY design. A native reader should check them. Paths are under `composeApp/src/commonMain/kotlin/com/ptut/expense/locale/`.

| English | Burmese | Where |
|---|---|---|
| Done | ပြီးပြီ | CommonCopy.kt `done` |
| Cancel | မလုပ်တော့ဘူး | CommonCopy.kt `cancel` |
| Back | နောက်သို့ | CommonCopy.kt `back` |
| Close | ပိတ်မယ် | CommonCopy.kt `close` |
| Try again | ထပ်ကြိုးစားမယ် | CommonCopy.kt `tryAgain` |
| Yesterday | မနေ့က | CommonCopy.kt `yesterday` |
| Reports (tab) | အစီရင်ခံစာ | ShellCopy.kt `tabReports` |
| Add | ထည့်မယ် | ShellCopy.kt `add` |
| Add an expense by hand | အသုံးကို ကိုယ်တိုင် ထည့်မယ် | ShellCopy.kt `addByHand` |
| What this was for | ဘာအတွက်လဲ | ShellCopy.kt `notePlaceholder` |
| Keep this note | ဒီမှတ်ချက် သိမ်းမယ် | ShellCopy.kt `keepNote` |
| Date & time | ရက်စွဲနဲ့ အချိန် | ShellCopy.kt `dateAndTime` |
| Now | အခု | ShellCopy.kt `now` |
| Next | ရှေ့ဆက်မယ် | ShellCopy.kt `next` |
| Picture gone | ပုံ မရှိတော့ဘူး | ShellCopy.kt `pictureGone` |
| Open the slip picture | စလစ်ပုံ ဖွင့်မယ် | ShellCopy.kt `openSlipPicture` |
| Slip picture | စလစ်ပုံ | ShellCopy.kt `slipPicture` |
| Close the slip picture | စလစ်ပုံ ပိတ်မယ် | ShellCopy.kt `closeSlipPicture` |
| Delete the last digit | နောက်ဆုံးဂဏန်း ဖျက်မယ် | ShellCopy.kt `deleteLastDigit` |
| X is not available offline on this phone. Type it instead. | ဒီဖုန်းမှာ X ကို အင်တာနက်မပါဘဲ နားမထောင်နိုင်ဘူး။ စာရိုက်ပြီး ထည့်ပါ။ | ShellCopy.kt `voiceUnavailable` |
| The phone's speech service is busy. Try again. | ဖုန်းရဲ့ အသံဖတ်စနစ် အလုပ်များနေတယ်။ ထပ်ကြိုးစားပါ။ | ShellCopy.kt `speechBusy` |
| Could not hear that. Type it instead. | မကြားရဘူး။ စာရိုက်ပြီး ထည့်ပါ။ | ShellCopy.kt `couldNotHear` |
| This phone has no microphone it can use right now. Type it instead. | ဒီဖုန်းမှာ အခု သုံးလို့ရတဲ့ မိုက်ခရိုဖုန်း မရှိဘူး။ စာရိုက်ပြီး ထည့်ပါ။ | ShellCopy.kt `noMicrophone` |
| Category icon names (40, spoken by screen readers) | see the map | ShellCopy.kt `iconNames` |
| Language | ဘာသာစကား | LanguageCopy.kt `title` |
| The language PaiSeN is written in. Your own category names and notes stay as you typed them. | PaiSeN ကို ပြမယ့် ဘာသာစကား။ ကိုယ်တိုင်ပေးထားတဲ့ အမျိုးအစားနာမည်နဲ့ မှတ်ချက်တွေက ရေးထားတဲ့အတိုင်း ကျန်မယ်။ | LanguageCopy.kt `intro` |
| Show PaiSeN in | PaiSeN ကို ဒီဘာသာနဲ့ ပြမယ် | LanguageCopy.kt `groupTitle` |
| Weekday and month names | standard names, no short forms | CalendarCopy.kt |

The design's own words used here: သိမ်းမယ်, ဖျက်မယ်, မဖျက်တော့ဘူး, ပြန်ယူမယ်, ပြင်မယ်, အသုံး, ဝင်ငွေ, ပမာဏ, အမျိုးအစား, အမျိုးအစားခွဲ, မှတ်ချက်, အချိန်, စုစုပေါင်း, ဒီနေ့, ဒီအပတ်, ဒီလ, သင်, မျှသုံး, ဆက်တင်, မှတ်ချက် ရေးမယ်.
- Seed category names (7) | see CategoriesCopy.kt seedNames | five from the design (စားစရာ, ကုန်စုံ, ကားခ, ဘေလ်, ဈေးဝယ်); Other → တခြား and Income → ဝင်ငွေ are mine
| That file held no transactions | ဒီဖိုင်ထဲမှာ ငွေစာရင်း တစ်ခုမှ မပါဘူး | PhrasesCopy.kt `restoreEmpty` |
| Nothing new — your history already held all N | အသစ် မရှိဘူး — မှတ်တမ်းထဲမှာ N ခုလုံး ရှိပြီးသား | PhrasesCopy.kt `restoreNothingNew` |
| Added N transactions | ငွေစာရင်း N ခု ထည့်ပြီးပြီ | PhrasesCopy.kt `restoreAdded` |
| N already here | N ခု ရှိပြီးသား | PhrasesCopy.kt `restoreAlreadyHere` |
| N shared groups | Shared group N ဖွဲ့ (corrected by a native reader, 15 Oct) | PhrasesCopy.kt `restoreGroups` |
| Added X | X ထည့်ပြီးပြီ | PhrasesCopy.kt `restoreAddedOnly` |
| This is not a file this app can read | ဒီဖိုင်ကို ဒီအက်ပ်က ဖတ်လို့ မရဘူး | PhrasesCopy.kt `backupNotReadable` |
| This file was written by a different version of the app (format N) | ဒီဖိုင်ကို တခြား ဗားရှင်းက ရေးထားတာ (format N) | PhrasesCopy.kt `backupOtherVersion` |
| The file holds an unknown FIELD: VALUE | ဖိုင်ထဲမှာ မသိတဲ့ FIELD ပါတယ်: VALUE | PhrasesCopy.kt `backupUnknownValue` |
