package com.ptut.expense.ui

import com.ptut.expense.domain.Category
import com.ptut.expense.locale.AppText
import com.ptut.expense.domain.inAppLanguage as inAppLanguageBy
import com.ptut.expense.domain.inTableLanguage as inTableLanguageBy

/** An installed category the reader never renamed, named in the app's current language. */
fun Category.inAppLanguage(): Category = inAppLanguageBy(AppText.copy.categories.seedNames)

/** A translated installed name back to the English one the table keeps. */
fun Category.inTableLanguage(): Category = inTableLanguageBy(AppText.copy.categories.seedNames)
