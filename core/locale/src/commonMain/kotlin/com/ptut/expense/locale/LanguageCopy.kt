package com.ptut.expense.locale

/** The Language row and its screen. */
data class LanguageCopy(
    val title: String = "Language",
    val intro: String = "The language PaiSeN is written in. Your own category names and notes stay as you typed them.",
    val groupTitle: String = "Show PaiSeN in",
)

val BurmeseLanguage = LanguageCopy(
    title = "ဘာသာစကား",
    intro = "PaiSeN ကို ပြမယ့် ဘာသာစကား။ ကိုယ်တိုင်ပေးထားတဲ့ အမျိုးအစားနာမည်နဲ့ မှတ်ချက်တွေက ရေးထားတဲ့အတိုင်း ကျန်မယ်။",
    groupTitle = "PaiSeN ကို ဒီဘာသာနဲ့ ပြမယ်",
)
