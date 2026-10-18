package com.ptut.expense.locale

/** The sign-in sheet and its privacy notice. */
data class SharedSignInCopy(
    val title: String = "Sign in to share",
    val leavesTitle: String = "What leaves this phone",
    val leavesBody: (String) -> String = { region ->
        "The group's name, the members' names, the shared expenses and payments with who added them, " +
            "a PromptPay ID if you add one, and a push token for this phone, so it can be told when someone " +
            "changes a group. They are kept with Google Firebase in $region. Your own spending, slips and reports " +
            "never leave this phone. Delete your account in Settings to remove what is stored."
    },
    val signingIn: String = "Signing in…",
    val google: String = "Continue with Google",
    val didNotFinish: String = "Sign-in did not finish. Try again.",
)

val BurmeseSharedSignIn = SharedSignInCopy(
    title = "မျှသုံးဖို့ အကောင့်ဝင်မယ်",
    leavesTitle = "ဒီဖုန်းကနေ ဘာတွေ ထွက်သွားမလဲ",
    leavesBody = { region ->
        "အဖွဲ့နာမည်၊ အဖွဲ့ဝင်တွေရဲ့ နာမည်၊ ဘယ်သူ ထည့်ခဲ့လဲဆိုတာနဲ့အတူ မျှသုံးတဲ့ စရိတ်နဲ့ ပေးချေမှုတွေ၊ ထည့်ထားရင် PromptPay ID၊ " +
            "တစ်ယောက်ယောက်က အဖွဲ့ကို ပြောင်းရင် ဒီဖုန်းကို အသိပေးနိုင်ဖို့ push token။ " +
            "ဒါတွေကို $region က Google Firebase မှာ သိမ်းထားတယ်။ သင့်ကိုယ်ပိုင် အသုံးစရိတ်၊ စလစ်နဲ့ အစီရင်ခံစာတွေက " +
            "ဒီဖုန်းထဲမှာ လုံခြုံပါတယ်။ သိမ်းထားတာတွေ ဖယ်ချင်ရင် ဆက်တင်မှာ အကောင့် ဖျက်ပါ။"
    },
    signingIn = "အကောင့်ဝင်နေတယ်…",
    google = "Google နဲ့ ဆက်မယ်",
    didNotFinish = "အကောင့်ဝင်တာ မပြီးဘူး။ ထပ်ကြိုးစားပါ။",
)

/** The invite sheet: the link, its QR and code, and the message shared to LINE. */
data class SharedInviteCopy(
    val makeLinkBody: (String, String) -> String = { group, region ->
        "Make a link so people can join $group. The group's name, its members and its expenses then sync " +
            "through Google Firebase in $region. Your own spending stays on this phone."
    },
    val makeLink: String = "Make an invite link",
    val notNow: String = "Not now",
    val making: String = "Making a link…",
    val switchedOff: String = "This link is switched off. Nobody new can join with it.",
    val newLink: String = "Make a new link",
    val howToJoin: (String) -> String = { group -> "Each person opens the link, signs in and picks their own name in $group." },
    val qr: String = "QR code for the invite link",
    val copyLink: String = "Copy",
    val shareLink: String = "Share link",
    val message: (String, String) -> String = { group, link -> "Join $group on PaiSen: $link" },
    val switchOff: String = "Switch this link off",
)

val BurmeseSharedInvite = SharedInviteCopy(
    makeLinkBody = { group, region ->
        "လူတွေ $group ထဲ ဝင်လို့ရအောင် လင့်ခ် လုပ်ပါ။ ပြီးရင် အဖွဲ့နာမည်၊ အဖွဲ့ဝင်တွေနဲ့ စရိတ်တွေကို " +
            "$region က Google Firebase ကနေ ချိတ်ဆက်မယ်။ သင့်ကိုယ်ပိုင် အသုံးစရိတ်တွေက ဒီဖုန်းထဲမှာပဲ ရှိနေမယ်။"
    },
    makeLink = "ဖိတ်လင့်ခ် လုပ်မယ်",
    notNow = "နောက်မှ",
    making = "လင့်ခ် လုပ်နေတယ်…",
    switchedOff = "ဒီလင့်ခ်ကို ပိတ်ထားတယ်။ ဒီလင့်ခ်နဲ့ ဘယ်သူမှ အသစ် ဝင်လို့ မရဘူး။",
    newLink = "လင့်ခ်အသစ် လုပ်မယ်",
    howToJoin = { group -> "လူတိုင်း လင့်ခ်ကို ဖွင့်၊ အကောင့်ဝင်ပြီး $group ထဲက ကိုယ့်နာမည်ကို ရွေးရမယ်။" },
    qr = "ဖိတ်လင့်ခ်ရဲ့ QR ကုဒ်",
    copyLink = "ကူးမယ်",
    shareLink = "လင့်ခ် မျှဝေမယ်",
    message = { group, link -> "PaiSen မှာ $group ထဲ ဝင်ပါ: $link" },
    switchOff = "ဒီလင့်ခ် ပိတ်မယ်",
)

/** Settings' shared groups account card: sign out and delete account. */
data class SharedAccountCopy(
    val title: String = "Shared groups account",
    val signedInAs: (String) -> String = { who -> "Signed in as $who" },
    val signOut: String = "Sign out",
    val deleteAccount: String = "Delete account",
    val notSignedIn: String = "Not signed in. You sign in when you share a group or open an invite.",
    val freshSignIn: String = "Sign in once more to delete your account. It keeps someone else from deleting it.",
    val signInReason: String = "So the people in your groups see the same expenses in their own app.",
    val deleteAsk: String = "Delete your account?",
    val deleteBody: String = "You leave every shared group, and your name in them is free for someone else to pick. " +
        "What you added stays in each group's history. Shared groups leave this phone; your own spending stays.",
    val notSentYet: (Int) -> String = { n ->
        "$n ${if (n == 1) "change has" else "changes have"} not reached your groups yet. Connect to the internet, then try again."
    },
    val signOutAsk: String = "Sign out?",
    val signOutBody: String = "Your shared groups leave this phone and come back when you sign in again. Your own spending stays.",
    val endedTitle: String = "Signed out on this phone",
    val endedBody: String = "Your account signed in on another phone. Sign in again in Settings to bring your shared groups back here. " +
        "Your own spending is still on this phone.",
)

val BurmeseSharedAccount = SharedAccountCopy(
    title = "Shared group အကောင့်",
    signedInAs = { who -> "$who နဲ့ ဝင်ထားတယ်" },
    signOut = "အကောင့်ထွက်မယ်",
    deleteAccount = "အကောင့် ဖျက်မယ်",
    notSignedIn = "အကောင့် မဝင်ရသေးဘူး။ အဖွဲ့ မျှဝေတဲ့အခါ ဒါမှမဟုတ် ဖိတ်စာ ဖွင့်တဲ့အခါ အကောင့်ဝင်ရမယ်။",
    freshSignIn = "အကောင့် ဖျက်ဖို့ နောက်တစ်ခါ ဝင်ပါ။ တခြားသူ ဝင်ဖျက်လို့ မရအောင် ကာကွယ်ထားတာ။",
    signInReason = "သင့်အဖွဲ့ထဲက လူတွေ သူတို့ အက်ပ်မှာ တူညီတဲ့ စရိတ်တွေ မြင်ရအောင်။",
    deleteAsk = "အကောင့်ကို ဖျက်မှာ သေချာလား။",
    deleteBody = "Shared group အားလုံးကနေ ထွက်မယ်၊ အဲဒီထဲက သင့်နာမည်ကို တခြားသူ ရွေးလို့ ရမယ်။ " +
        "သင် ထည့်ခဲ့တာတွေက အဖွဲ့တစ်ခုချင်းရဲ့ မှတ်တမ်းထဲမှာ ကျန်မယ်။ Shared group တွေ ဒီဖုန်းကနေ ပျောက်သွားမယ်၊ သင့်ကိုယ်ပိုင် အသုံးစရိတ်တွေက ကျန်မယ်။",
    notSentYet = { n -> "ပြောင်းထားတာ ${n.inMyanmarDigits()} ခု အဖွဲ့ဆီ မရောက်သေးဘူး။ အင်တာနက် ချိတ်ပြီးမှ ထပ်ကြိုးစားပါ။" },
    signOutAsk = "အကောင့်ထွက်မှာ သေချာလား။",
    signOutBody = "Shared group တွေ ဒီဖုန်းကနေ ပျောက်သွားပြီး အကောင့် ပြန်ဝင်ရင် ပြန်ရောက်လာမယ်။ သင့်ကိုယ်ပိုင် အသုံးစရိတ်တွေက ကျန်မယ်။",
    endedTitle = "ဒီဖုန်းမှာ အကောင့်ထွက်သွားပြီ",
    endedBody = "သင့်အကောင့်ကို တခြားဖုန်းမှာ ဝင်လိုက်လို့ပါ။ Shared group တွေ ဒီမှာ ပြန်ရဖို့ ဆက်တင်မှာ အကောင့် ပြန်ဝင်ပါ။ " +
        "သင့်ကိုယ်ပိုင် အသုံးစရိတ်တွေက ဒီဖုန်းထဲမှာ ရှိနေတယ်။",
)
