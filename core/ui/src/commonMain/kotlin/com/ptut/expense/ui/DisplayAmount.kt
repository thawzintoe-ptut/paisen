package com.ptut.expense.ui

import com.ptut.expense.domain.Satang

/**
 * An amount that may not exist yet.
 *
 * FR-7: the reader could not make the number out, so none is written. The rule used to sit at the
 * call site as `amount?.format() ?: "not counted"`, which meant the next person rendering a
 * possibly-unread amount had to remember it. In the type, they cannot forget: there is no way to
 * hold [NotCounted] and accidentally print a zero.
 */
sealed interface DisplayAmount {

    data class Exact(val amount: Satang) : DisplayAmount

    /** Never a zero. A zero is a number, and this is the absence of one. */
    data object NotCounted : DisplayAmount

    companion object {
        fun of(amount: Satang?): DisplayAmount = amount?.let(::Exact) ?: NotCounted
    }
}
