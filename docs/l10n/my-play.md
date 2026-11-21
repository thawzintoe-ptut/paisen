Burmese for play (#82), for a native reader to check. None of it is native-corrected yet. Paths are under `core/locale/src/commonMain/kotlin/com/ptut/expense/`; `{name}` marks a value the app fills in. The lists by name (stamp names and faces, rare-stamp lines, sets, reactions, award titles, challenge levels, score parts) sit in the same files under each `Burmese…` value, in the English order.

| English | Burmese | Where |
| --- | --- | --- |
| Play | ကစားစရာ | locale/PlayCopy.kt `PlaySettingsCopy.title` |
| Stamps, guesses and challenges are on | တံဆိပ်၊ ခန့်မှန်းချက်နဲ့ စိန်ခေါ်မှုတွေ ဖွင့်ထားတယ် | locale/PlayCopy.kt `PlaySettingsCopy.masterOn` |
| Off: PaiSeN shows money only | ပိတ်ထားတယ်: PaiSeN က ငွေစာရင်းပဲ ပြမယ် | locale/PlayCopy.kt `PlaySettingsCopy.masterOff` |
| On Today | ဒီနေ့ စာမျက်နှာမှာ | locale/PlayCopy.kt `PlaySettingsCopy.onToday` |
| Tray clear run | စလစ် ရှင်းတဲ့ ရက်ဆက် | locale/PlayCopy.kt `PlaySettingsCopy.run` |
| Days you check every slip | စလစ်တိုင်း စစ်ပြီးတဲ့ ရက်တွေ | locale/PlayCopy.kt `PlaySettingsCopy.runLine` |
| Guess the week | ဒီအပတ်ကို ခန့်မှန်းကြည့် | locale/PlayCopy.kt `PlaySettingsCopy.guess` |
| Before the week report | အပတ်စဉ် အနှစ်ချုပ် မတိုင်ခင် | locale/PlayCopy.kt `PlaySettingsCopy.guessLine` |
| Challenges | စိန်ခေါ်မှုတွေ | locale/PlayCopy.kt `PlaySettingsCopy.challenges` |
| One a week, you pick | တစ်ပတ် တစ်ခု၊ ကိုယ်တိုင် ရွေးမယ် | locale/PlayCopy.kt `PlaySettingsCopy.challengesLine` |
| In shared groups | Shared group တွေထဲမှာ | locale/PlayCopy.kt `PlaySettingsCopy.inGroups` |
| Stamps and awards | တံဆိပ်နဲ့ ဆုတွေ | locale/PlayCopy.kt `PlaySettingsCopy.groupStamps` |
| Seen by everyone in the group | အဖွဲ့ထဲက လူတိုင်း မြင်ရတယ် | locale/PlayCopy.kt `PlaySettingsCopy.groupStampsLine` |
| Sharing and reminders | မျှဝေခြင်းနဲ့ သတိပေးချက် | locale/PlayCopy.kt `PlaySettingsCopy.sharing` |
| Amounts on share cards | မျှဝေကတ်ပေါ်မှာ ငွေပမာဏ | locale/PlayCopy.kt `PlaySettingsCopy.shareAmounts` |
| Off: cards show words and shapes only | ပိတ်ထားရင် စာနဲ့ ပုံပဲ ပြမယ် | locale/PlayCopy.kt `PlaySettingsCopy.shareAmountsLine` |
| Weekly play reminder | အပတ်စဉ် ကစားဖို့ သတိပေးချက် | locale/PlayCopy.kt `PlaySettingsCopy.reminder` |
| At most one a week | တစ်ပတ်ကို အများဆုံး တစ်ကြိမ် | locale/PlayCopy.kt `PlaySettingsCopy.reminderLine` |
| Your own play is worked out on this phone and never leaves it. | ကိုယ်ပိုင် ကစားမှုတွေကို ဒီဖုန်းထဲမှာပဲ တွက်တယ်။ ဒီဖုန်းထဲမှာ လုံခြုံပါတယ်။ | locale/PlayCopy.kt `PlaySettingsCopy.footer` |
| Tray clear run | စလစ် ရှင်းတဲ့ ရက်ဆက် | locale/PlayCopy.kt `PlayRunCopy.eyebrow` |
| 1 day / {n} days | {n} ရက် | locale/PlayCopy.kt `PlayRunCopy.days` |
| 1 freeze / {n} freezes | ခွင့် {n} ရက် | locale/PlayCopy.kt `PlayRunCopy.freezes` |
| Tray clear today. A stamp is waiting. | ဒီနေ့ စလစ် ရှင်းပြီ။ တံဆိပ်တစ်ခု စောင့်နေတယ်။ | locale/PlayCopy.kt `PlayRunCopy.clearToday` |
| Tray clear today. Come back tomorrow. | ဒီနေ့ စလစ် ရှင်းပြီ။ မနက်ဖြန် ပြန်လာခဲ့။ | locale/PlayCopy.kt `PlayRunCopy.keptToday` |
| Clear the tray today to start a run. | ရက်ဆက် စဖို့ ဒီနေ့ စလစ်တွေ ရှင်းလိုက်ပါ။ | locale/PlayCopy.kt `PlayRunCopy.startLine` |
| Stamps | တံဆိပ်တွေ | locale/PlayCopy.kt `PlayRunCopy.stamps` |
| Turn over today's stamp | ဒီနေ့ တံဆိပ်ကို လှန်ကြည့်မယ် | locale/PlayCopy.kt `PlayRunCopy.turnStamp` |
| {day}, tray cleared | {day}၊ စလစ် ရှင်းပြီ | locale/PlayCopy.kt `PlayRunCopy.dayCleared` |
| {day}, kept by a freeze | {day}၊ ခွင့်ရက်နဲ့ ထိန်းထားတယ် | locale/PlayCopy.kt `PlayRunCopy.dayFrozen` |
| {day}, not cleared | {day}၊ မရှင်းခဲ့ဘူး | locale/PlayCopy.kt `PlayRunCopy.dayMissed` |
| {day}, still to come | {day}၊ မရောက်သေးဘူး | locale/PlayCopy.kt `PlayRunCopy.dayAhead` |
| M / T / W / F / S | တ / အ / ဗ / က / သ / စ / နွေ | locale/PlayCopy.kt `PlayRunCopy.weekLetters` |
| 1 freeze keeps a missed day in your run. / {n} freezes keep missed days in your run. | ခွင့် {n} ရက်က လွတ်သွားတဲ့ ရက်ကို ရက်ဆက်ထဲမှာ ထိန်းထားပေးတယ်။ | locale/PlayCopy.kt `PlayRunCopy.freezeLine` |
| Tray clear | စလစ် ရှင်း | locale/PlayCopy.kt `PlayStampsCopy.captionTray` |
| Rare | ရှားပါး | locale/PlayCopy.kt `PlayStampsCopy.captionRare` |
| Win | အောင်မြင် | locale/PlayCopy.kt `PlayStampsCopy.captionWin` |
| Everyday / Every day / Night / Night and day / Wins | Everyday / နေ့စဉ် / Night / နေ့နဲ့ည / Wins / အောင်မြင်မှုတွေ | locale/PlayCopy.kt `PlayStampsCopy.sets` |
| {name} stamp, {day} | {name} တံဆိပ်၊ {day} | locale/PlayCopy.kt `PlayStampsCopy.stampLabel` |
| {name}, not earned yet | {name}၊ မရသေးဘူး | locale/PlayCopy.kt `PlayStampsCopy.emptyLabel` |
| Today's stamp | ဒီနေ့ တံဆိပ် | locale/PlayCopy.kt `PlayStampsCopy.revealTitle` |
| Tap the card to turn it over | ကတ်ကို နှိပ်ပြီး လှန်ကြည့်ပါ | locale/PlayCopy.kt `PlayStampsCopy.faceDownHint` |
| Pressed into your book | စာအုပ်ထဲ ရိုက်ထည့်ပြီးပြီ | locale/PlayCopy.kt `PlayStampsCopy.turnedHint` |
| Face-down stamp card. Tap to turn it over. | မှောက်ထားတဲ့ တံဆိပ်ကတ်။ နှိပ်ပြီး လှန်ပါ။ | locale/PlayCopy.kt `PlayStampsCopy.cardLabel` |
| {set}: {have} of {of} | {set}: {of} ခုထဲက {have} ခု | locale/PlayCopy.kt `PlayStampsCopy.setLine` |
| 1 more finishes the set / {n} more finish the set | နောက်ထပ် {n} ခု ရရင် အစုံ ပြည့်မယ် | locale/PlayCopy.kt `PlayStampsCopy.setLeft` |
| Set complete | အစုံ ပြည့်ပြီ | locale/PlayCopy.kt `PlayStampsCopy.setDone` |
| Put it in my book | စာအုပ်ထဲ ထည့်မယ် | locale/PlayCopy.kt `PlayStampsCopy.putInBook` |
| This stamp is no longer here. | ဒီတံဆိပ် မရှိတော့ဘူး။ | locale/PlayCopy.kt `PlayStampsCopy.missing` |
| Stamps | တံဆိပ်တွေ | locale/PlayCopy.kt `PlayStampsCopy.bookTitle` |
| No-spend days | မသုံးခဲ့တဲ့ ရက်တွေ | locale/PlayCopy.kt `PlayStampsCopy.noSpendTitle` |
| 1 day with nothing spent this month / {n} days with nothing spent this month | ဒီလ ဘာမှ မသုံးခဲ့တဲ့ ရက် {n} ရက် | locale/PlayCopy.kt `PlayStampsCopy.noSpendCount` |
| {day}, nothing spent | {day}၊ ဘာမှ မသုံးခဲ့ဘူး | locale/PlayCopy.kt `PlayStampsCopy.noSpendLabel` |
| Next set: {set} | နောက်အစုံ: {set} | locale/PlayCopy.kt `PlayStampsCopy.nextSet` |
| Every set complete | အစုံတိုင်း ပြည့်ပြီ | locale/PlayCopy.kt `PlayStampsCopy.allSets` |
| Today's 30 seconds | ဒီနေ့ စက္ကန့် ၃၀ | locale/PlayCopy.kt `PlayQuestionCopy.cardEyebrow` |
| Which cost more in the last 7 days? | လွန်ခဲ့တဲ့ ၇ ရက်မှာ ဘယ်ဟာ ပိုကုန်လဲ | locale/PlayCopy.kt `PlayQuestionCopy.cardTitle` |
| Play | ကစားမယ် | locale/PlayCopy.kt `PlayQuestionCopy.cardAction` |
| Today's question | ဒီနေ့ မေးခွန်း | locale/PlayCopy.kt `PlayQuestionCopy.title` |
| Which cost more in the last 7 days? | လွန်ခဲ့တဲ့ ၇ ရက်မှာ ဘယ်ဟာ ပိုကုန်လဲ | locale/PlayCopy.kt `PlayQuestionCopy.ask` |
| 1 payment / {n} payments | {n} ကြိမ် ပေးခဲ့တယ် | locale/PlayCopy.kt `PlayQuestionCopy.rows` |
| You got it | မှန်တယ် | locale/PlayCopy.kt `PlayQuestionCopy.right` |
| Not this time | ဒီတစ်ခါ မမှန်ဘူး | locale/PlayCopy.kt `PlayQuestionCopy.wrong` |
| {a} came to {aAmount} and {b} to {bAmount}. | {a} က {aAmount}၊ {b} က {bAmount} ကုန်ခဲ့တယ်။ | locale/PlayCopy.kt `PlayQuestionCopy.cameTo` |
| {have} of {of} answers toward a freeze | ခွင့်ရက် တစ်ရက်အတွက် အဖြေ {of} ခုထဲက {have} ခု | locale/PlayCopy.kt `PlayQuestionCopy.freezeProgress` |
| A freeze is filled. You hold 1. / A freeze is filled. You hold {n}. | ခွင့်ရက် တစ်ရက် ပြည့်ပြီ။ အခု {n} ရက် ရှိတယ်။ | locale/PlayCopy.kt `PlayQuestionCopy.freezeFilled` |
| You hold the most freezes there are. | ခွင့်ရက် အများဆုံး ရထားပြီ။ | locale/PlayCopy.kt `PlayQuestionCopy.freezesFull` |
| Open today's stamp | ဒီနေ့ တံဆိပ်ကို ဖွင့်မယ် | locale/PlayCopy.kt `PlayQuestionCopy.openStamp` |
| That's today's question. A new one comes tomorrow. | ဒီနေ့ မေးခွန်း ဒါပါပဲ။ မနက်ဖြန် အသစ် လာမယ်။ | locale/PlayCopy.kt `PlayQuestionCopy.end` |
| No fair pair in the last 7 days. Come back tomorrow. | လွန်ခဲ့တဲ့ ၇ ရက်မှာ နှိုင်းယှဉ်စရာ မရှိဘူး။ မနက်ဖြန် ပြန်လာခဲ့ပါ။ | locale/PlayCopy.kt `PlayQuestionCopy.none` |
| {name} cost more | {name} က ပိုကုန်တယ် | locale/PlayCopy.kt `PlayQuestionCopy.pickLabel` |
| Guess the week | အပတ်ကို ခန့်မှန်းကြည့် | locale/PlayCopy.kt `PlayGuessCopy.eyebrow` |
| What did last week come to? | ပြီးခဲ့တဲ့ အပတ် ဘယ်လောက် ကုန်ခဲ့လဲ | locale/PlayCopy.kt `PlayGuessCopy.title` |
| {from} to {to} | {from} မှ {to} | locale/PlayCopy.kt `PlayGuessCopy.week` |
| Less | လျှော့ | locale/PlayCopy.kt `PlayGuessCopy.less` |
| More | တိုး | locale/PlayCopy.kt `PlayGuessCopy.more` |
| Guess | ခန့်မှန်းမယ် | locale/PlayCopy.kt `PlayGuessCopy.guess` |
| Skip guessing | မခန့်မှန်းဘဲ ကြည့်မယ် | locale/PlayCopy.kt `PlayGuessCopy.skip` |
| Your best: {best}% · 1 week played / Your best: {best}% · {weeks} weeks played | အကောင်းဆုံး: {best}% · {weeks} ပတ် ကစားပြီး | locale/PlayCopy.kt `PlayGuessCopy.record` |
| Your first guess. PaiSeN has guessed already. | ပထမဆုံး ခန့်မှန်းချက်ပါ။ PaiSeN က ခန့်မှန်းပြီးပြီ။ | locale/PlayCopy.kt `PlayGuessCopy.firstTime` |
| Last week came to | ပြီးခဲ့တဲ့ အပတ် ကုန်ခဲ့တာ | locale/PlayCopy.kt `PlayGuessCopy.revealTitle` |
| You | သင် | locale/PlayCopy.kt `PlayGuessCopy.you` |
| PaiSeN | PaiSeN | locale/PlayCopy.kt `PlayGuessCopy.paisen` |
| off by {amount} | {amount} ကွာတယ် | locale/PlayCopy.kt `PlayGuessCopy.off` |
| spot on | အတိအကျ မှန်တယ် | locale/PlayCopy.kt `PlayGuessCopy.spotOn` |
| Closer | ပိုနီးတယ် | locale/PlayCopy.kt `PlayGuessCopy.closer` |
| You beat PaiSeN. A stamp is in your book. | PaiSeN ကို နိုင်သွားပြီ။ တံဆိပ်တစ်ခု စာအုပ်ထဲ ရောက်ပြီ။ | locale/PlayCopy.kt `PlayGuessCopy.youWon` |
| PaiSeN was closer this time. Nothing is lost. | ဒီတစ်ခါ PaiSeN က ပိုနီးတယ်။ ဘာမှ မဆုံးရှုံးပါဘူး။ | locale/PlayCopy.kt `PlayGuessCopy.paisenWon` |
| How PaiSeN guessed | PaiSeN ဘယ်လို ခန့်မှန်းလဲ | locale/PlayCopy.kt `PlayGuessCopy.how` |
| {week}: {amount} | {week}: {amount} | locale/PlayCopy.kt `PlayGuessCopy.howWeek` |
| Trend added: {amount} | လမ်းကြောင်း ထပ်ပေါင်း: {amount} | locale/PlayCopy.kt `PlayGuessCopy.howTrend` |
| See the week | အပတ်ကို ကြည့်မယ် | locale/PlayCopy.kt `PlayGuessCopy.seeWeek` |
| PaiSeN needs two earlier weeks to guess. Your week is ready to look at. | PaiSeN ခန့်မှန်းဖို့ အရင် အပတ် ၂ ပတ် လိုတယ်။ အပတ်ကို ကြည့်လို့ ရပါပြီ။ | locale/PlayCopy.kt `PlayGuessCopy.missing` |
| Your guess | သင့် ခန့်မှန်းချက် | locale/PlayCopy.kt `PlayGuessCopy.sliderLabel` |
| {category}: {amount} more than a usual {weekday} | {category}: ပုံမှန် {weekday} ထက် {amount} ပိုကုန်တယ် | locale/PlayCopy.kt `PlayCoachCopy.more` |
| {category}: {amount} less than a usual {weekday} | {category}: ပုံမှန် {weekday} ထက် {amount} လျော့ကုန်တယ် | locale/PlayCopy.kt `PlayCoachCopy.less` |
| PaiSeN: {line}. Opens the rows behind it. | PaiSeN: {line}။ နောက်ကွယ်က စာရင်းတွေကို ဖွင့်မယ်။ | locale/PlayCopy.kt `PlayCoachCopy.label` |
| Worked out on this phone | ဒီဖုန်းထဲမှာပဲ တွက်ထားတယ် | locale/PlayCopy.kt `PlayCopy.local` |
| 1 payment settles everyone / {n} payments settle everyone | ပေးချေမှု {n} ခုနဲ့ အားလုံး ကျေမယ် | locale/PlayGroupCopy.kt `PlayGroupCopy.planHeadline` |
| Instead of {n} back-and-forth payments | အပြန်အလှန် {n} ခါ ပေးမယ့်အစား | locale/PlayGroupCopy.kt `PlayGroupCopy.planSaves` |
| Already the fewest payments | အနည်းဆုံး ပေးချေမှု ဖြစ်ပြီးသား | locale/PlayGroupCopy.kt `PlayGroupCopy.planFewest` |
| Your part | သင့်အပိုင်း | locale/PlayGroupCopy.kt `PlayGroupCopy.yourPart` |
| Settle my part | ကိုယ့်အပိုင်း ပေးချေမယ် | locale/PlayGroupCopy.kt `PlayGroupCopy.settleMyPart` |
| Who pays this round? | ဒီတစ်ခါ ဘယ်သူ ပေးမလဲ | locale/PlayGroupCopy.kt `PlayGroupCopy.wheelTitle` |
| Fair wheel | မျှတတဲ့ ဘီး | locale/PlayGroupCopy.kt `PlayGroupCopy.wheelFair` |
| Whoever paid fewer rounds lately gets a bigger slice | မကြာခင်က နည်းနည်း ပေးခဲ့သူက အကွက် ပိုကြီးရမယ် | locale/PlayGroupCopy.kt `PlayGroupCopy.wheelFairLine` |
| Every slice is the same size | အကွက်တိုင်း အရွယ်တူတယ် | locale/PlayGroupCopy.kt `PlayGroupCopy.wheelEqualLine` |
| Spin | လှည့်မယ် | locale/PlayGroupCopy.kt `PlayGroupCopy.wheelSpin` |
| Spin again | ထပ်လှည့်မယ် | locale/PlayGroupCopy.kt `PlayGroupCopy.wheelSpinAgain` |
| {name} pays this round | ဒီတစ်ခါ {name} ပေးမယ် | locale/PlayGroupCopy.kt `PlayGroupCopy.wheelLanded` |
| A usual round here is about {amount} | ဒီအဖွဲ့ရဲ့ ပုံမှန် တစ်ခါစာ {amount} လောက် | locale/PlayGroupCopy.kt `PlayGroupCopy.wheelUsual` |
| Add the round, {name} paid | {name} ပေးတဲ့အဖြစ် ထည့်မယ် | locale/PlayGroupCopy.kt `PlayGroupCopy.wheelAdd` |
| Who pays this round? | ဒီတစ်ခါ ဘယ်သူ ပေးမလဲ | locale/PlayGroupCopy.kt `PlayGroupCopy.wheelOpen` |
| Add at least two people to spin the wheel. | ဘီးလှည့်ဖို့ လူ ၂ ယောက် အနည်းဆုံး ထည့်ပါ။ | locale/PlayGroupCopy.kt `PlayGroupCopy.wheelNoOne` |
| All square | အကြွေး ကျေပြီ | locale/PlayGroupCopy.kt `PlayGroupCopy.allSquare` |
| Everyone is settled. A stamp is in your book. | အားလုံး ကျေပြီ။ တံဆိပ်တစ်ခု စာအုပ်ထဲ ရောက်ပြီ။ | locale/PlayGroupCopy.kt `PlayGroupCopy.allSquareLine` |
| React | တုံ့ပြန်မယ် | locale/PlayGroupCopy.kt `PlayGroupCopy.react` |
| Thanks / WellPlayed / Well played / Cheers | Thanks / ကျေးဇူး / WellPlayed / တော်တယ် / Cheers / ချီးယားစ် | locale/PlayGroupCopy.kt `PlayGroupCopy.reactions` |
| {kind} from {name} | {name} ဆီက {kind} | locale/PlayGroupCopy.kt `PlayGroupCopy.reactedBy` |
| Take back | ပြန်ရုပ်မယ် | locale/PlayGroupCopy.kt `PlayGroupCopy.takeBack` |
| Awards for {month} | {month} ဆုတွေ | locale/PlayGroupCopy.kt `PlayGroupCopy.awardsTitle` |
| Monthly awards | လစဉ် ဆုတွေ | locale/PlayGroupCopy.kt `PlayGroupCopy.awardsOpen` |
| Paid back within 1 day of a share / Paid back within {days} days of a share | ခွဲပြီး {days} ရက်အတွင်း ပြန်ပေးတယ် | locale/PlayGroupCopy.kt `PlayGroupCopy.fastestLine` |
| Added 1 entry / Added {count} entries | စာရင်း {count} ခု ထည့်ခဲ့တယ် | locale/PlayGroupCopy.kt `PlayGroupCopy.booksLine` |
| Paid back in 1 week of the month / Paid back in {weeks} weeks of the month | လထဲက {weeks} ပတ်မှာ ပြန်ပေးခဲ့တယ် | locale/PlayGroupCopy.kt `PlayGroupCopy.steadyLine` |
| Not enough this month for awards yet. | ဒီလ ဆုပေးဖို့ မလုံလောက်သေးဘူး။ | locale/PlayGroupCopy.kt `PlayGroupCopy.awardsNone` |
| Awards show once this phone is up to date. | ဒီဖုန်း နောက်ဆုံးအခြေအနေ ရောက်ရင် ဆုတွေ ပြမယ်။ | locale/PlayGroupCopy.kt `PlayGroupCopy.awardsBehind` |
| Trip ended | ခရီး ပြီးပြီ | locale/PlayGroupCopy.kt `PlayGroupCopy.tripEnded` |
| Mark the trip over to see its recap | ခရီး အနှစ်ချုပ် ကြည့်ဖို့ ပြီးကြောင်း မှတ်ပါ | locale/PlayGroupCopy.kt `PlayGroupCopy.tripEndedLine` |
| Trip recap | ခရီး အနှစ်ချုပ် | locale/PlayGroupCopy.kt `PlayGroupCopy.tripOpenRecap` |
| Trip recap | ခရီး အနှစ်ချုပ် | locale/PlayGroupCopy.kt `PlayGroupCopy.recapTitle` |
| per person per day | တစ်ယောက် တစ်ရက် | locale/PlayGroupCopy.kt `PlayGroupCopy.perPersonPerDay` |
| Total {amount} | စုစုပေါင်း {amount} | locale/PlayGroupCopy.kt `PlayGroupCopy.recapTotal` |
| Start a jar | ပန်းတိုင်ဘူး စမယ် | locale/PlayGroupCopy.kt `PlayGroupCopy.startJar` |
| Next trip like this | ဒီလို နောက်ခရီး | locale/PlayGroupCopy.kt `PlayGroupCopy.nextTripTitle` |
| Nothing was spent on this trip. | ဒီခရီးမှာ ဘာမှ မသုံးခဲ့ဘူး။ | locale/PlayGroupCopy.kt `PlayGroupCopy.recapNone` |
| Share | မျှဝေမယ် | locale/PlayGroupCopy.kt `PlayGroupCopy.share` |
| {day}: {amount} | {day}: {amount} | locale/PlayGroupCopy.kt `PlayGroupCopy.dayBar` |
| More to play | နောက်ထပ် ကစားစရာ | locale/PlayExtrasCopy.kt `PlayExtrasCopy.shelfTitle` |
| Challenge | စိန်ခေါ်မှု | locale/PlayExtrasCopy.kt `PlayExtrasCopy.shelfChallenge` |
| Goal jar | ပန်းတိုင်ဘူး | locale/PlayExtrasCopy.kt `PlayExtrasCopy.shelfJar` |
| Month story | လ ပုံပြင် | locale/PlayExtrasCopy.kt `PlayExtrasCopy.shelfStory` |
| Close the month | လ ပိတ်မယ် | locale/PlayExtrasCopy.kt `PlayExtrasCopy.shelfClose` |
| Money score | ငွေ ရမှတ် | locale/PlayExtrasCopy.kt `PlayExtrasCopy.shelfScore` |
| This week's challenge | ဒီအပတ် စိန်ခေါ်မှု | locale/PlayExtrasCopy.kt `PlayExtrasCopy.challengeTitle` |
| Pick one for this week | ဒီအပတ်အတွက် တစ်ခု ရွေးပါ | locale/PlayExtrasCopy.kt `PlayExtrasCopy.challengePick` |
| Easy / Medium / Hard | Easy / လွယ် / Medium / အလယ်အလတ် / Hard / ခက် | locale/PlayExtrasCopy.kt `PlayExtrasCopy.challengeLevels` |
| {category} at most {limit} | {category} {limit} အထိ | locale/PlayExtrasCopy.kt `PlayExtrasCopy.challengeOption` |
| {category} at most {limit} this week | ဒီအပတ် {category} {limit} အထိ | locale/PlayExtrasCopy.kt `PlayExtrasCopy.challengeRow` |
| Over this time. Next week starts fresh. | ဒီတစ်ခါ ကျော်သွားတယ်။ နောက်အပတ် အသစ် စမယ်။ | locale/PlayExtrasCopy.kt `PlayExtrasCopy.challengeOver` |
| Two weeks of spending in a category are needed to set a challenge. | စိန်ခေါ်မှု ထားဖို့ အမျိုးအစားတစ်ခုမှာ ၂ ပတ်စာ သုံးစွဲမှု လိုတယ်။ | locale/PlayExtrasCopy.kt `PlayExtrasCopy.challengeNone` |
| Missing it loses nothing. Making it earns a stamp. | မအောင်လည်း ဘာမှ မဆုံးရှုံးဘူး။ အောင်ရင် တံဆိပ် ရမယ်။ | locale/PlayExtrasCopy.kt `PlayExtrasCopy.challengeNote` |
| Picked | ရွေးပြီး | locale/PlayExtrasCopy.kt `PlayExtrasCopy.challengePicked` |
| Goal jar | ပန်းတိုင်ဘူး | locale/PlayExtrasCopy.kt `PlayExtrasCopy.jarTitle` |
| What is it for? | ဘာအတွက်လဲ | locale/PlayExtrasCopy.kt `PlayExtrasCopy.jarName` |
| How much? | ဘယ်လောက်လဲ | locale/PlayExtrasCopy.kt `PlayExtrasCopy.jarTarget` |
| Save the jar | ဘူး သိမ်းမယ် | locale/PlayExtrasCopy.kt `PlayExtrasCopy.jarSave` |
| of {target} · {percent}% | {target} ထဲက {percent}% | locale/PlayExtrasCopy.kt `PlayExtrasCopy.jarOf` |
| Kept each week | တစ်ပတ်ကို စုမိတာ | locale/PlayExtrasCopy.kt `PlayExtrasCopy.jarKept` |
| Income less spending, last 4 weeks | လွန်ခဲ့တဲ့ ၄ ပတ်က ဝင်ငွေထဲက သုံးစွဲမှု နုတ်ထားတာ | locale/PlayExtrasCopy.kt `PlayExtrasCopy.jarKeptLine` |
| At this pace | ဒီနှုန်းနဲ့ဆို | locale/PlayExtrasCopy.kt `PlayExtrasCopy.jarPaceTitle` |
| Full by {date} | {date} မှာ ပြည့်မယ် | locale/PlayExtrasCopy.kt `PlayExtrasCopy.jarPace` |
| Keep a little more than you spend and a date shows here. | သုံးတာထက် နည်းနည်း ပိုစုရင် ရက်စွဲ ပြမယ်။ | locale/PlayExtrasCopy.kt `PlayExtrasCopy.jarNoPace` |
| The jar fills from income less spending. Add your income to see it fill. | ဘူးက ဝင်ငွေထဲက သုံးစွဲမှု နုတ်ပြီး ပြည့်တာပါ။ ပြည့်လာတာ မြင်ဖို့ ဝင်ငွေ ထည့်ပါ။ | locale/PlayExtrasCopy.kt `PlayExtrasCopy.jarNeedsIncome` |
| The jar is full. | ဘူး ပြည့်ပြီ။ | locale/PlayExtrasCopy.kt `PlayExtrasCopy.jarFull` |
| No money moves. Your bank stays as it is. | ငွေ ဘာမှ မရွှေ့ဘူး။ ဘဏ်ထဲမှာ ဒီအတိုင်း ရှိနေမယ်။ | locale/PlayExtrasCopy.kt `PlayExtrasCopy.jarNoMoney` |
| Skip {category} | {category} မသုံးရင် | locale/PlayExtrasCopy.kt `PlayExtrasCopy.jarWhatIf` |
| Without {category}: full by {date} | {category} မပါရင်: {date} မှာ ပြည့်မယ် | locale/PlayExtrasCopy.kt `PlayExtrasCopy.jarWhatIfDate` |
| Without {category}: still no date | {category} မပါရင်လည်း ရက်စွဲ မရှိသေးဘူး | locale/PlayExtrasCopy.kt `PlayExtrasCopy.jarWhatIfNone` |
| Put the jar away | ဘူးကို သိမ်းထားမယ် | locale/PlayExtrasCopy.kt `PlayExtrasCopy.jarArchive` |
| Goal jar, {percent} percent full | ပန်းတိုင်ဘူး၊ {percent} ရာခိုင်နှုန်း ပြည့်ပြီ | locale/PlayExtrasCopy.kt `PlayExtrasCopy.jarLabel` |
| {month} in five cards | {month} ကတ် ငါးချပ် | locale/PlayExtrasCopy.kt `PlayExtrasCopy.storyTitle` |
| You spent {amount} | {amount} သုံးခဲ့တယ် | locale/PlayExtrasCopy.kt `PlayExtrasCopy.storyTotal` |
| {amount} more than a usual month | ပုံမှန်လထက် {amount} ပိုတယ် | locale/PlayExtrasCopy.kt `PlayExtrasCopy.storyTotalMore` |
| {amount} less than a usual month | ပုံမှန်လထက် {amount} လျော့တယ် | locale/PlayExtrasCopy.kt `PlayExtrasCopy.storyTotalLess` |
| Your first month here | ဒီမှာ ပထမဆုံး လ | locale/PlayExtrasCopy.kt `PlayExtrasCopy.storyTotalFirst` |
| {category} moved most | {category} အပြောင်းအလဲ အများဆုံး | locale/PlayExtrasCopy.kt `PlayExtrasCopy.storyShift` |
| {amount} more than usual | ပုံမှန်ထက် {amount} ပိုတယ် | locale/PlayExtrasCopy.kt `PlayExtrasCopy.storyShiftMore` |
| {amount} less than usual | ပုံမှန်ထက် {amount} လျော့တယ် | locale/PlayExtrasCopy.kt `PlayExtrasCopy.storyShiftLess` |
| New this month: {shop} | ဒီလ အသစ်: {shop} | locale/PlayExtrasCopy.kt `PlayExtrasCopy.storyNewShop` |
| Quietest week: {week} | အငြိမ်ဆုံး အပတ်: {week} | locale/PlayExtrasCopy.kt `PlayExtrasCopy.storyQuiet` |
| 1 day with nothing spent / {n} days with nothing spent | ဘာမှ မသုံးခဲ့တဲ့ ရက် {n} ရက် | locale/PlayExtrasCopy.kt `PlayExtrasCopy.storyNoSpend` |
| Next | ရှေ့ဆက် | locale/PlayExtrasCopy.kt `PlayExtrasCopy.storyNext` |
| Done | ပြီးပြီ | locale/PlayExtrasCopy.kt `PlayExtrasCopy.storyDone` |
| Share | မျှဝေမယ် | locale/PlayExtrasCopy.kt `PlayExtrasCopy.storyShare` |
| Amount hidden | ငွေပမာဏ ဖျောက်ထားတယ် | locale/PlayExtrasCopy.kt `PlayExtrasCopy.storyHidden` |
| Nothing was spent this month, so there is no story yet. | ဒီလ ဘာမှ မသုံးခဲ့လို့ ပုံပြင် မရှိသေးဘူး။ | locale/PlayExtrasCopy.kt `PlayExtrasCopy.storyNone` |
| Close {month} | {month} ပိတ်မယ် | locale/PlayExtrasCopy.kt `PlayExtrasCopy.closeTitle` |
| Step {step} of {of} | {of} ဆင့်ထဲက {step} | locale/PlayExtrasCopy.kt `PlayExtrasCopy.closeStep` |
| Check the last slips | နောက်ဆုံး စလစ်တွေ စစ်မယ် | locale/PlayExtrasCopy.kt `PlayExtrasCopy.closeSlips` |
| 1 slip is waiting / {n} slips are waiting | စလစ် {n} ခု စောင့်နေတယ် | locale/PlayExtrasCopy.kt `PlayExtrasCopy.closeSlipsWaiting` |
| No slips waiting | စောင့်နေတဲ့ စလစ် မရှိဘူး | locale/PlayExtrasCopy.kt `PlayExtrasCopy.closeSlipsDone` |
| Open slips to check | စစ်ဖို့ စလစ်တွေ ဖွင့်မယ် | locale/PlayExtrasCopy.kt `PlayExtrasCopy.closeSlipsAction` |
| Look at the big costs | ကြီးတဲ့ ကုန်ကျစရိတ်တွေ ကြည့်မယ် | locale/PlayExtrasCopy.kt `PlayExtrasCopy.closeBig` |
| Maybe twice: {shop}, {amount} | နှစ်ခါ ဖြစ်နိုင်: {shop}၊ {amount} | locale/PlayExtrasCopy.kt `PlayExtrasCopy.closeDuplicate` |
| {shop} · {amount} · {why} | {shop} · {amount} · {why} | locale/PlayExtrasCopy.kt `PlayExtrasCopy.closeBigCost` |
| Nothing looks odd | ထူးဆန်းတာ မရှိဘူး | locale/PlayExtrasCopy.kt `PlayExtrasCopy.closeNothingOdd` |
| Compare with last month | ပြီးခဲ့တဲ့လနဲ့ နှိုင်းယှဉ်မယ် | locale/PlayExtrasCopy.kt `PlayExtrasCopy.closeCompare` |
| {amount} more than last month | ပြီးခဲ့တဲ့လထက် {amount} ပိုတယ် | locale/PlayExtrasCopy.kt `PlayExtrasCopy.closeMore` |
| {amount} less than last month | ပြီးခဲ့တဲ့လထက် {amount} လျော့တယ် | locale/PlayExtrasCopy.kt `PlayExtrasCopy.closeLess` |
| The same as last month | ပြီးခဲ့တဲ့လနဲ့ အတူတူပဲ | locale/PlayExtrasCopy.kt `PlayExtrasCopy.closeSame` |
| No month before this one yet | ဒီမတိုင်ခင် လ မရှိသေးဘူး | locale/PlayExtrasCopy.kt `PlayExtrasCopy.closeFirst` |
| Next | ရှေ့ဆက် | locale/PlayExtrasCopy.kt `PlayExtrasCopy.closeNext` |
| Seal the month | လ ပိတ်မယ် | locale/PlayExtrasCopy.kt `PlayExtrasCopy.closeSeal` |
| Sealed. A stamp is in your book. You can still edit any row. | ပိတ်ပြီ။ တံဆိပ်တစ်ခု စာအုပ်ထဲ ရောက်ပြီ။ စာရင်းတွေကို ပြင်လို့ ရသေးတယ်။ | locale/PlayExtrasCopy.kt `PlayExtrasCopy.closeSealed` |
| {month}, sealed | {month}၊ ပိတ်ပြီး | locale/PlayExtrasCopy.kt `PlayExtrasCopy.sealedMonth` |
| Money score | ငွေ ရမှတ် | locale/PlayExtrasCopy.kt `PlayExtrasCopy.scoreTitle` |
| out of 100 | ၁၀၀ ထဲက | locale/PlayExtrasCopy.kt `PlayExtrasCopy.scoreOutOf` |
| {points} of {of} | {of} ထဲက {points} | locale/PlayExtrasCopy.kt `PlayExtrasCopy.scorePart` |
| Full / Up to +{n} | ပြည့်ပြီ / +{n} အထိ | locale/PlayExtrasCopy.kt `PlayExtrasCopy.scoreGain` |
| A score shows once there are slips checked or income recorded. | စလစ် စစ်ပြီး ဒါမှမဟုတ် ဝင်ငွေ ထည့်ပြီးရင် ရမှတ် ပြမယ်။ | locale/PlayExtrasCopy.kt `PlayExtrasCopy.scoreNone` |
| Only your own numbers. Not advice about money. | ကိုယ့် ကိန်းဂဏန်းတွေပဲ။ ငွေကြေး အကြံဉာဏ် မဟုတ်ပါ။ | locale/PlayExtrasCopy.kt `PlayExtrasCopy.scoreNote` |
| PaiSeN has guessed too. Its number shows after yours. | PaiSeN လည်း ခန့်မှန်းထားပြီ။ သူ့ဂဏန်းက မင်းဟာ ပြီးမှ ပြမယ်။ | locale/PlayCopy.kt `PlayGuessCopy.paisenToo` |
| PaiSeN picked two costs from your week that came out close. | PaiSeN က မင်းရဲ့ တစ်ပတ်ထဲက နီးနီးချင်း ကုန်ကျစရိတ် နှစ်ခု ရွေးထားတယ်။ | locale/PlayCopy.kt `PlayQuestionCopy.paisenPicked` |
| Show amounts | ငွေပမာဏ ပြမယ် | locale/PlayExtrasCopy.kt `PlayExtrasCopy.storyShowAmounts` |
| On this card and the picture you share | ဒီကတ်နဲ့ မျှဝေမယ့် ပုံပေါ်မှာ | locale/PlayExtrasCopy.kt `PlayExtrasCopy.storyShowAmountsHint` |
| closed (stamp) | ပိတ်ပြီ | locale/PlayExtrasCopy.kt `PlayExtrasCopy.closeStampCaption` |
| Strong / Steady / Building / Starting | ခိုင်မာတယ် / တည်ငြိမ်တယ် / တည်ဆောက်နေတယ် / စနေပြီ | locale/PlayExtrasCopy.kt `PlayExtrasCopy.scoreWord` |
| If you make it | အောင်ရင် | locale/PlayExtrasCopy.kt `PlayExtrasCopy.challengeIfMade` |
| challenge (stamp) | စိန်ခေါ်မှု | locale/PlayExtrasCopy.kt `PlayExtrasCopy.challengeStampCaption` |

### Close the month: needs a native read

New 2026-11-21, written without a native reader. Check before release.

| English | Burmese | Where |
|---|---|---|
| more than last month | ပြီးခဲ့တဲ့လထက် ပိုတယ် | locale/PlayExtrasCopy.kt `PlayExtrasCopy.closeWhyRisen` |
| large | ကြီးတယ် | locale/PlayExtrasCopy.kt `PlayExtrasCopy.closeWhyLarge` |
| Check the {n} {month} slips first | {month} စလစ် {n} ခု အရင် စစ်ပါ | locale/PlayExtrasCopy.kt `PlayExtrasCopy.closeSealSlipsFirst` |
| Go through the three steps first | အဆင့် သုံးဆင့် အရင် ကြည့်ပါ | locale/PlayExtrasCopy.kt `PlayExtrasCopy.closeSealStepsFirst` |
