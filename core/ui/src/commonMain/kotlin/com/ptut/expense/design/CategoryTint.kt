package com.ptut.expense.design

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/**
 * The colours a category may wear.
 *
 * Neither alert red nor sky is on offer, and that absence is the design. [Palette] reserves red
 * for delete and stale data and spends sky as its one accent, so a category wearing either would
 * spend a signal the rest of the app depends on. Six hues that carry no meaning elsewhere leave
 * both intact.
 *
 * [Default] is the theme's own primary, which is what every category wore before there was a
 * choice. Picking a colour is opt-in, so a reader who never opens the picker sees the app the
 * design drew.
 */
enum class CategoryTint(val storedName: String, val spoken: String) {
    Default("default", "Theme colour"),
    Teal("teal", "Teal"),
    Blue("blue", "Blue"),
    Violet("violet", "Violet"),
    Rose("rose", "Rose"),
    Moss("moss", "Moss"),
    Slate("slate", "Slate"),
    ;

    companion object {
        /**
         * A name this build does not know is a row written by a later one, so it reads as
         * [Default] rather than throwing. The tile has to draw either way.
         */
        fun named(storedName: String): CategoryTint =
            entries.firstOrNull { it.storedName == storedName } ?: Default
    }
}

/** One tint as a tile draws it: the fill, and the ink the glyph on it is tinted with. */
@Immutable
data class CategoryTintColors(val fill: Color, val on: Color)

/**
 * The six tints for one theme.
 *
 * Every fill clears 9.4:1 against its own theme's surface and 8:1 against its ink, which is what
 * makes them read as one family rather than six unrelated swatches. `CategoryTintContrastTest`
 * holds them there.
 */
@Immutable
data class CategoryTints(
    val teal: CategoryTintColors,
    val blue: CategoryTintColors,
    val violet: CategoryTintColors,
    val rose: CategoryTintColors,
    val moss: CategoryTintColors,
    val slate: CategoryTintColors,
) {
    /** [CategoryTint.Default] is the theme's primary and so is not held here — see [categoryTint]. */
    fun of(tint: CategoryTint): CategoryTintColors? = when (tint) {
        CategoryTint.Default -> null
        CategoryTint.Teal -> teal
        CategoryTint.Blue -> blue
        CategoryTint.Violet -> violet
        CategoryTint.Rose -> rose
        CategoryTint.Moss -> moss
        CategoryTint.Slate -> slate
    }

    val all: List<Pair<CategoryTint, CategoryTintColors>>
        get() = CategoryTint.entries.mapNotNull { tint -> of(tint)?.let { tint to it } }
}

/**
 * Deep fills on the light theme, white ink. The same inversion [Palette] makes for primary, which
 * goes from ink on [Sky] to near-white on [SkyNight]: a tile has to sit against its surface, and
 * one fixed hex cannot do that on both.
 */
val SkyCategoryTints = CategoryTints(
    teal = CategoryTintColors(Color(0xFF0C4A45), Color(0xFFFFFFFF)),
    blue = CategoryTintColors(Color(0xFF21407E), Color(0xFFFFFFFF)),
    violet = CategoryTintColors(Color(0xFF5E2C76), Color(0xFFFFFFFF)),
    rose = CategoryTintColors(Color(0xFF79203E), Color(0xFFFFFFFF)),
    moss = CategoryTintColors(Color(0xFF194C14), Color(0xFFFFFFFF)),
    slate = CategoryTintColors(Color(0xFF394352), Color(0xFFFFFFFF)),
)

/** Bright fills on the dark theme, each with its own hue taken down for the glyph. */
val SkyNightCategoryTints = CategoryTints(
    teal = CategoryTintColors(Color(0xFF2EBFB2), Color(0xFF041817)),
    blue = CategoryTintColors(Color(0xFF94ACDB), Color(0xFF0B1428)),
    violet = CategoryTintColors(Color(0xFFC39ED5), Color(0xFF1E0E26)),
    rose = CategoryTintColors(Color(0xFFDC99AF), Color(0xFF280B15)),
    moss = CategoryTintColors(Color(0xFF54C24A), Color(0xFF081807)),
    slate = CategoryTintColors(Color(0xFFA2ACBB), Color(0xFF111419)),
)

/** What a tile of [tint] is filled and inked with under the theme that is on. */
@Composable
@ReadOnlyComposable
fun categoryTint(tint: CategoryTint): CategoryTintColors =
    MaterialTheme.ptut.categoryTints.of(tint) ?: CategoryTintColors(
        fill = MaterialTheme.colorScheme.primary,
        on = MaterialTheme.colorScheme.onPrimary,
    )

/** The same, from the name a category stores. */
@Composable
@ReadOnlyComposable
fun categoryTint(storedName: String): CategoryTintColors =
    categoryTint(CategoryTint.named(storedName))
