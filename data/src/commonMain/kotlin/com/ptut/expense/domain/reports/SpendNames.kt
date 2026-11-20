package com.ptut.expense.domain.reports

import com.ptut.expense.domain.Category

/**
 * What spend outside a screen's own categories is called. The screens' copy writes it, so the
 * domain holds no word; it is read on every use, so a language switch reaches an open screen.
 */
interface SpendNames {
    /** Where spend goes when its category is not in the list a screen holds. */
    val unknown: Category

    /** A parent's spend that no sub-category holds. */
    val unsorted: String
}
