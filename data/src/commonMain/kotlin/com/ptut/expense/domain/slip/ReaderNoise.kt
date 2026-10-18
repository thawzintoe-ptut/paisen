package com.ptut.expense.domain.slip
import com.ptut.expense.domain.rules.ShopNames

/**
 * What a Latin-only reader makes of a Thai name (ML Kit ships no Thai model): `msus:quus:óiÜangönms`.
 * A colon inside a word, or two accented letters, which no English K PLUS name prints.
 */
fun String.isReaderNoise(): Boolean =
    COLON_IN_WORD.containsMatchIn(this) || count { it.isLetter() && it in ACCENTED_LATIN } >= ACCENTS_IN_NOISE

/** The slip-read [name] to show: the reader's own name for it, else as read, and [unknown] for noise. */
fun shownShop(name: String, unknown: String): String = ShopNames.of(name) ?: if (name.isReaderNoise()) unknown else name

private val COLON_IN_WORD = Regex("""\p{L}[:;]\p{L}""")

/** Latin-1 Supplement to Latin Extended-B: `À` to `ɏ`. `×` and `÷` sit inside it but are not letters. */
private val ACCENTED_LATIN = 'À'..'ɏ'

/** One accent is a real name (`Café Amazon`); noise brings several. */
private const val ACCENTS_IN_NOISE = 2
