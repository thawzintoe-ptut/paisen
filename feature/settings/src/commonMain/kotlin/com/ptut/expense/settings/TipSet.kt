package com.ptut.expense.settings

/** The seen tips are kept as one comma-joined string, the same on both phones. */
internal const val TIP_SEPARATOR = ","

internal fun String?.toTipSet(): Set<String> = this?.split(TIP_SEPARATOR)?.filter(String::isNotEmpty)?.toSet().orEmpty()
