package com.ptut.expense.sync.flags

/** The switches Firebase Remote Config can turn off in a release build, each with its key and default. */
enum class Flag(val key: String, val default: Boolean) {
    Currencies("currencies_on", default = true),
    Assistant("assistant_on", default = true),
    NoteTone("note_tone_on", default = true),
    Planned("planned_on", default = true),
    SlipGroupDebt("slip_group_debt_on", default = true),
    ThaiLanguage("thai_language_on", default = true),

    /** #82: every play part at once. Each part is also held to debug until checked on a phone (D17). */
    Play("play_on", default = true),

    /** Just-in-time play tips, off for everyone without a release. */
    Tips("tips_on", default = true),

    /** #116: the Should column (voice add, categories, import control, more splitting, year view, thanks). */
    V2("v2_on", default = false),

    /** #116: the Nice column, which is every play screen but the goal jar. */
    Later("later_on", default = false),
}

/**
 * The values this launch runs with, read once from the cache. A fetch writes the cache for the
 * next launch, so a switch never changes under an open screen.
 */
class FeatureFlags(private val entries: Map<String, String>) {

    /** A debug build sees everything; a release build takes the cached value, else the default. */
    fun isOn(flag: Flag, debug: Boolean): Boolean = debug || entries[flag.key]?.toBooleanStrictOrNull() ?: flag.default

    companion object {
        val Defaults = FeatureFlags(emptyMap())
    }
}
