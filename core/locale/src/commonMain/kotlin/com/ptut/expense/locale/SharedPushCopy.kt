package com.ptut.expense.locale

/**
 * The one sentence a push shows under the group's name (spec 2026-10-15). Names are the members'
 * own: a push goes only to the others, so it never says "You".
 */
data class SharedPushCopy(
    val anExpense: String = "an expense",
    val added: (String, String, String) -> String = { actor, what, amount -> "$actor added $what · $amount" },
    val edited: (String, String) -> String = { actor, what -> "$actor changed $what" },
    val removed: (String, String) -> String = { actor, what -> "$actor removed $what" },
    val putBack: (String, String) -> String = { actor, what -> "$actor put back $what" },
    val repeatAdded: (String, String) -> String = { what, amount -> "$what was added · $amount" },
    val paid: (String, String, String) -> String = { from, to, amount -> "$from paid $to $amount" },
    val paymentRemoved: (String) -> String = { actor -> "$actor removed a payment" },
    val paymentPutBack: (String) -> String = { actor -> "$actor put back a payment" },
    val joined: (String) -> String = { name -> "$name joined" },
    val left: (String) -> String = { name -> "$name left" },
    val addedPerson: (String, String) -> String = { actor, name -> "$actor added $name" },
    val removedPerson: (String, String) -> String = { actor, name -> "$actor removed $name" },
    val changedName: (String) -> String = { actor -> "$actor changed a name" },
    val renamed: (String, String) -> String = { actor, name -> "$actor renamed the group to $name" },
    val deleted: (String) -> String = { actor -> "$actor deleted the group" },
    val repeatSetUp: (String, String) -> String = { actor, what -> "$actor set up a repeat: $what" },
    val repeatChanged: (String, String) -> String = { actor, what -> "$actor changed the repeat $what" },
    val repeatStopped: (String, String) -> String = { actor, what -> "$actor stopped the repeat $what" },
)

val BurmeseSharedPush = SharedPushCopy(
    anExpense = "စရိတ်တစ်ခု",
    added = { actor, what, amount -> "$actor က “$what” ထည့်ခဲ့တယ် · $amount" },
    edited = { actor, what -> "$actor က “$what” ကို ပြင်ခဲ့တယ်" },
    removed = { actor, what -> "$actor က “$what” ကို ဖယ်ခဲ့တယ်" },
    putBack = { actor, what -> "$actor က “$what” ကို ပြန်ထည့်ခဲ့တယ်" },
    repeatAdded = { what, amount -> "“$what” ကို အလိုအလျောက် ထည့်ပြီးပြီ · $amount" },
    paid = { from, to, amount -> "$from က $to ကို $amount ပေးခဲ့တယ်" },
    paymentRemoved = { actor -> "$actor က ပေးချေမှုကို ဖယ်ခဲ့တယ်" },
    paymentPutBack = { actor -> "$actor က ပေးချေမှုကို ပြန်ထည့်ခဲ့တယ်" },
    joined = { name -> "$name အဖွဲ့ထဲ ဝင်လာတယ်" },
    left = { name -> "$name အဖွဲ့ကနေ ထွက်သွားတယ်" },
    addedPerson = { actor, name -> "$actor က $name ကို ထည့်ခဲ့တယ်" },
    removedPerson = { actor, name -> "$actor က $name ကို ဖယ်ခဲ့တယ်" },
    changedName = { actor -> "$actor က နာမည်တစ်ခု ပြောင်းခဲ့တယ်" },
    renamed = { actor, name -> "$actor က အဖွဲ့နာမည်ကို $name လို့ ပြောင်းခဲ့တယ်" },
    deleted = { actor -> "$actor က အဖွဲ့ကို ဖျက်ခဲ့တယ်" },
    repeatSetUp = { actor, what -> "$actor က ထပ်ခါထပ်ခါ စရိတ် “$what” သတ်မှတ်ခဲ့တယ်" },
    repeatChanged = { actor, what -> "$actor က ထပ်ခါထပ်ခါ စရိတ် “$what” ကို ပြင်ခဲ့တယ်" },
    repeatStopped = { actor, what -> "$actor က ထပ်ခါထပ်ခါ စရိတ် “$what” ကို ရပ်ခဲ့တယ်" },
)
