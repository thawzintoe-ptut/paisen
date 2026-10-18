package com.ptut.expense.sync.push

import com.ptut.expense.locale.Language

/**
 * One change in a shared group, worded for the other members' lock screens: the group's name as
 * the title and one sentence in each language the app speaks. The phone that made the change
 * builds both, so the Worker that relays it holds no copy.
 */
data class PushNotice(val title: String, val en: String, val my: String) {
    fun body(language: Language): String = when (language) {
        Language.English -> en
        Language.Burmese -> my
    }
}
