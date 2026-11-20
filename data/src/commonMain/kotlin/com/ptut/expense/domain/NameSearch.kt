package com.ptut.expense.domain

/** A search box's match: [query] trimmed, in either case, anywhere in this name. An empty query matches every name. */
fun String.matchesQuery(query: String): Boolean {
    val match = query.trim().lowercase()
    return match.isEmpty() || lowercase().contains(match)
}
