package com.ptut.expense

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isSelectable

/**
 * A tab in the nav capsule. A tab is found by its spoken name, and by being selectable, which
 * tells it apart from a header icon or a title with the same name.
 */
internal fun pillSlot(label: String): SemanticsMatcher = hasContentDescription(label) and isSelectable()
