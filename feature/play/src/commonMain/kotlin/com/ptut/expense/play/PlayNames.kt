package com.ptut.expense.play

import com.ptut.expense.domain.Category
import com.ptut.expense.domain.play.QuestionSide
import com.ptut.expense.ui.inAppLanguage

/** A category's name and glyph in the app's language, or the id's own name once it was deleted. */
internal fun List<Category>.named(categoryId: String): Pair<String, String> =
    firstOrNull { it.id == categoryId }?.inAppLanguage()?.let { it.name to it.icon } ?: (categoryId to "label")

internal fun QuestionSide.named(categories: List<Category>): Pair<String, String> = when (this) {
    is QuestionSide.Category -> categories.named(categoryId)
    is QuestionSide.Shop -> name to "store"
}
