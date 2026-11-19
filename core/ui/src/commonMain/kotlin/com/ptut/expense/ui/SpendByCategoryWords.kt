package com.ptut.expense.ui

import com.ptut.expense.domain.Category
import com.ptut.expense.domain.CategorySpend
import com.ptut.expense.domain.SubCategory
import com.ptut.expense.domain.Transaction
import com.ptut.expense.locale.AppText
import com.ptut.expense.domain.spendByCategory as spendByCategoryNamed

/** Where spend goes when its category is not in the list a screen holds. */
val UnknownCategory: Category
    get() = Category(id = "unknown", name = AppText.copy.phrases.unknownCategory, icon = "more_horiz")

/** [spendByCategoryNamed] with the unknown and unsorted rows named in the reader's language. */
fun List<Transaction>.spendByCategory(
    categories: List<Category>,
    subCategories: List<SubCategory> = emptyList(),
): List<CategorySpend> = spendByCategoryNamed(categories, UnknownCategory, AppText.copy.phrases.unsorted, subCategories)
