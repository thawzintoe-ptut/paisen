package com.ptut.expense.locale

/** 0–9 as ၀–၉, for a count inside a Burmese sentence. Amounts and dates keep 0–9, as the design does. */
fun Long.inMyanmarDigits(): String = toString().map { if (it in '0'..'9') '၀' + (it - '0') else it }.joinToString("")

fun Int.inMyanmarDigits(): String = toLong().inMyanmarDigits()
