package com.ptut.expense.locale

/**
 * The languages PaiSeN is written in. [label] is each one's own name, as the switch shows it.
 * A language with `released = false` is offered only where its Remote Config switch or a debug build says so.
 */
enum class Language(val tag: String, val label: String, private val released: Boolean = true) {
    English("en", "English"),
    Burmese("my", "မြန်မာ"),
    Thai("th", "ไทย", released = false),
    ;

    val copy: Copy get() = when (this) {
        English -> EnglishCopy
        Burmese -> BurmeseCopy
        Thai -> ThaiCopy
    }

    private fun offeredIn(offersUnreleased: Boolean): Boolean = released || offersUnreleased

    companion object {
        /** The language a tag such as `my`, `my-MM` or `en_US` names, or null for one the app is not written in. */
        fun of(tag: String?): Language? {
            val language = tag?.substringBefore('-')?.substringBefore('_')?.lowercase() ?: return null
            return entries.firstOrNull { it.tag == language }
        }

        /** What the Language screen lists in this build. */
        fun offered(offersUnreleased: Boolean): List<Language> = entries.filter { it.offeredIn(offersUnreleased) }

        /**
         * At launch: the saved choice, else the phone's language, else English. A language this build
         * does not offer counts as neither, so a build that hides Thai again never opens in it (F1.4).
         */
        fun atLaunch(saved: Language?, phoneTag: String?, offersUnreleased: Boolean): Language =
            listOfNotNull(saved, of(phoneTag)).firstOrNull { it.offeredIn(offersUnreleased) } ?: English
    }
}
