package com.ptut.expense.ledger

import com.ptut.expense.domain.LedgerFilter

/** The three chips the Show sheet opens on: what the money was, not how the row got there. */
val PrimaryLedgerFilters: List<LedgerFilter> = listOf(LedgerFilter.All, LedgerFilter.Spending, LedgerFilter.Income)

/** The rest, behind "More filters": auto-filed, by hand and edited. */
val MoreLedgerFilters: List<LedgerFilter> = LedgerFilter.entries - PrimaryLedgerFilters

/** A sheet opened while a hidden filter is on shows it, or the dot on the sliders would point at nothing. */
fun LedgerFilter.opensMoreFilters(): Boolean = this in MoreLedgerFilters
