package com.ptut.expense.domain.play

import kotlinx.datetime.YearMonth

internal fun String.toYearMonthOrNull(): YearMonth? = runCatching { YearMonth.parse(this) }.getOrNull()
