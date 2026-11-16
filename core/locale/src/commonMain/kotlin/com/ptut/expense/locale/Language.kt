package com.ptut.expense.locale

/**
 * The languages PaiSeN is written in. [label] is each one's own name, as the switch shows it.
 * A new language passes `released = false` until its words are signed off, so only debug builds offer it.
 */
enum class Language(val tag: String, val label: String, private val released: Boolean = true) {
    English("en", "English"),
    Burmese("my", "မြန်မာ"),
    Thai("th", "ไทย"),
    ;

    val copy: Copy get() = when (this) {
        English -> EnglishCopy
        Burmese -> BurmeseCopy
        Thai -> ThaiCopy
    }

    private fun offeredIn(debug: Boolean): Boolean = released || debug

    companion object {
        /** The language a tag such as `my`, `my-MM` or `en_US` names, or null for one the app is not written in. */
        fun of(tag: String?): Language? {
            val language = tag?.substringBefore('-')?.substringBefore('_')?.lowercase() ?: return null
            return entries.firstOrNull { it.tag == language }
        }

        /** What the Language screen lists in this build. */
        fun offered(debug: Boolean): List<Language> = entries.filter { it.offeredIn(debug) }

        /**
         * At launch: the saved choice, else the phone's language, else English. A language this build
         * does not offer counts as neither, so a build that hides Thai again never opens in it (F1.4).
         */
        fun atLaunch(saved: Language?, phoneTag: String?, debug: Boolean): Language =
            listOfNotNull(saved, of(phoneTag)).firstOrNull { it.offeredIn(debug) } ?: English
    }
}
