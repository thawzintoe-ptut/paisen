package com.ptut.expense.domain

/**
 * How long a screen's `WhileSubscribed` state keeps its upstream alive after the last collector
 * leaves, so a rotation or a quick back-and-forth does not restart the query.
 */
const val SUBSCRIPTION_STOP_MILLIS: Long = 5_000L
