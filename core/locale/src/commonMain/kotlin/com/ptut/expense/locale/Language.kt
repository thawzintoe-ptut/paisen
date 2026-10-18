package com.ptut.expense.locale

/** The languages PaiSeN is written in. [label] is each one's own name, as the switch shows it. */
enum class Language(val tag: String, val label: String) {
    English("en", "English"),
    Burmese("my", "မြန်မာ"),
    ;

    val copy: Copy get() = when (this) {
        English -> EnglishCopy
        Burmese -> BurmeseCopy
    }

    companion object {
        /** The language a tag such as `my`, `my-MM` or `en_US` names, or null for one the app is not written in. */
        fun of(tag: String?): Language? {
            val language = tag?.substringBefore('-')?.substringBefore('_')?.lowercase() ?: return null
            return entries.firstOrNull { it.tag == language }
        }
    }
}
