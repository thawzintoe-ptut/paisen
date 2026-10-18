package com.ptut.expense

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isSelectable

/**
 * A destination in the nav pill. The pill draws glyphs alone, so a slot is found by its spoken
 * name, and by being selectable, which tells it apart from a header icon with the same name.
 */
internal fun pillSlot(label: String): SemanticsMatcher = hasContentDescription(label) and isSelectable()
