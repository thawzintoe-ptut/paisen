package com.ptut.expense.locale

/** Categories and the category editor. [colourNames] is keyed by a tint's stored name; English keeps the tint's own label. */
data class CategoriesCopy(
    val title: String = "Categories",
    val newCategory: String = "New category",
    val intro: String = "Tap to edit. Sub-categories roll up into their category in every total.",
    val reorder: String = "Reorder",
    val noSubCategories: String = "No sub-categories",
    val moveUp: (String) -> String = { name -> "Move $name up" },
    val moveDown: (String) -> String = { name -> "Move $name down" },
    val subCount: (Int) -> String = { count ->
        when (count) {
            0 -> "No sub-categories yet"
            1 -> "1 sub-category"
            else -> "$count sub-categories"
        }
    },
    val meta: (String, String) -> String = { subs, spent -> "$subs · $spent this month" },
    val editTitle: String = "Edit category",
    val closeWithoutSaving: String = "Close without saving",
    val changeIcon: String = "Change the icon",
    val moneyOut: String = "Money out",
    val moneyIn: String = "Money in",
    val directionHint: String = "Decides which sheets offer this category. What is already filed here stays exactly as it was recorded.",
    val subCategories: String = "Sub-categories",
    val removingKeepsSpending: (String) -> String = { name ->
        "Removing a sub-category keeps its spending in $name. Nothing is deleted, it just stops being counted separately."
    },
    val deleteCategory: String = "Delete category",
    val categoryName: String = "Category name",
    val moreIcons: String = "More icons",
    val colour: String = "Colour",
    val renameSub: (String) -> String = { name -> "Rename $name" },
    val removeSub: (String) -> String = { name -> "Remove $name" },
    val addSubCategory: String = "Add sub-category",
    val nameTheSub: String = "Name the sub-category",
    val addThisSub: String = "Add this sub-category",
    val chooseColour: String = "Choose a colour",
    val keepThisOne: String = "Keep this one",
    val chooseIcon: String = "Choose an icon",
    val whereShouldTheseGo: String = "Where should these go?",
    val keepTheCategory: String = "Keep the category",
    val nothingMoves: String = "Nothing is filed here, so nothing moves.",
    val askWhereTheyGo: (Int) -> String = { count ->
        if (count == 1) {
            "You will be asked where its 1 transaction should go."
        } else {
            "You will be asked where its $count transactions should go."
        }
    },
    val askWhereRulesGo: (Int) -> String = { count ->
        if (count == 1) {
            "You will be asked where its 1 shop rule should go."
        } else {
            "You will be asked where its $count shop rules should go."
        }
    },
    val rulesGoToo: (Int) -> String = { count -> if (count == 1) "Its 1 shop rule goes there too." else "Its $count shop rules go there too." },
    val purchases: (Int) -> String = { count -> if (count == 1) "1 purchase" else "$count purchases" },
    val colourNames: Map<String, String> = emptyMap(),
    /** The installed categories' names, by id, while the reader has not renamed them. English keeps the stored names. */
    val seedNames: Map<String, String> = emptyMap(),
)

val BurmeseCategories = CategoriesCopy(
    title = "အမျိုးအစားများ",
    newCategory = "အမျိုးအစားအသစ်",
    intro = "ပြင်ချင်ရင် နှိပ်ပါ။ အမျိုးအစားခွဲတွေက စုစုပေါင်းတိုင်းမှာ သူ့အမျိုးအစားထဲ ပေါင်းဝင်တယ်။",
    reorder = "အစီအစဉ် ပြင်မယ်",
    noSubCategories = "အမျိုးအစားခွဲ မရှိဘူး",
    moveUp = { name -> "$name ကို အပေါ်ရွှေ့မယ်" },
    moveDown = { name -> "$name ကို အောက်ရွှေ့မယ်" },
    subCount = { count -> if (count == 0) "အမျိုးအစားခွဲ မရှိသေးဘူး" else "အမျိုးအစားခွဲ ${count.inMyanmarDigits()}" },
    meta = { subs, spent -> "$subs · ဒီလ $spent" },
    editTitle = "အမျိုးအစား ပြင်မယ်",
    closeWithoutSaving = "မသိမ်းဘဲ ပိတ်မယ်",
    changeIcon = "အိုင်ကွန် ပြောင်းမယ်",
    moneyOut = "ထွက်ငွေ",
    moneyIn = "ဝင်ငွေ",
    directionHint = "ဘယ်စာမျက်နှာတွေမှာ ဒီအမျိုးအစားကို ပြမလဲ ဆုံးဖြတ်တယ်။ ဒီထဲမှာ ရှိပြီးသားတွေက မှတ်ထားတဲ့အတိုင်း ကျန်မယ်။",
    subCategories = "အမျိုးအစားခွဲများ",
    removingKeepsSpending = { name ->
        "အမျိုးအစားခွဲကို ဖယ်ရင် သူ့အသုံးတွေ $name ထဲမှာ ကျန်မယ်။ ဘာမှ မဖျက်ဘူး၊ သီးသန့် မတွက်တော့တာပဲ။"
    },
    deleteCategory = "အမျိုးအစား ဖျက်မယ်",
    categoryName = "အမျိုးအစား နာမည်",
    moreIcons = "အိုင်ကွန် ထပ်ကြည့်မယ်",
    colour = "အရောင်",
    renameSub = { name -> "$name ကို နာမည်ပြောင်းမယ်" },
    removeSub = { name -> "$name ကို ဖယ်မယ်" },
    addSubCategory = "အမျိုးအစားခွဲ ထည့်မယ်",
    nameTheSub = "အမျိုးအစားခွဲ နာမည်",
    addThisSub = "ဒီအမျိုးအစားခွဲ ထည့်မယ်",
    chooseColour = "အရောင် ရွေးမယ်",
    keepThisOne = "ဒါပဲ ထားမယ်",
    chooseIcon = "အိုင်ကွန် ရွေးမယ်",
    whereShouldTheseGo = "ဒါတွေကို ဘယ်ရွှေ့မလဲ။",
    keepTheCategory = "အမျိုးအစား မဖျက်တော့ဘူး",
    nothingMoves = "ဒီထဲမှာ ဘာမှ မရှိဘူး၊ ဒါကြောင့် ဘာမှ မရွှေ့ရဘူး။",
    askWhereTheyGo = { count -> "ဒီထဲက ${count.inMyanmarDigits()} ကြိမ်ကို ဘယ်ရွှေ့မလဲ ရွေးရမယ်။" },
    askWhereRulesGo = { count -> "ဒီထဲက ဆိုင်စည်းမျဉ်း ${count.inMyanmarDigits()} ခုကို ဘယ်ရွှေ့မလဲ ရွေးရမယ်။" },
    rulesGoToo = { count -> "ဆိုင်စည်းမျဉ်း ${count.inMyanmarDigits()} ခုလည်း အဲဒီကို ပါသွားမယ်။" },
    purchases = { count -> "${count.inMyanmarDigits()} ကြိမ်" },
    colourNames = mapOf(
        "default" to "မူလအရောင်",
        "teal" to "စိမ်းပြာ",
        "blue" to "အပြာ",
        "violet" to "ခရမ်း",
        "rose" to "ပန်းရောင်",
        "moss" to "ရေညှိစိမ်း",
        "slate" to "မီးခိုးပြာ",
    ),
    seedNames = mapOf(
        "food" to "စားစရာ",
        "groceries" to "ကုန်စုံ",
        "transport" to "ကားခ",
        "bills" to "ဘေလ်",
        "shopping" to "ဈေးဝယ်",
        "other" to "တခြား",
        "income" to "ဝင်ငွေ",
    ),
)
