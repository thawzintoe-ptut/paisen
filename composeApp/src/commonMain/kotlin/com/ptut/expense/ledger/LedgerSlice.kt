package com.ptut.expense.ledger

import com.ptut.expense.nav.PtutRoute
import kotlinx.datetime.LocalDate

/** The slice a [PtutRoute.LedgerSlice] names, its ISO days read back into dates (#220). */
fun PtutRoute.LedgerSlice.scope(): LedgerScope = LedgerScope(LocalDate.parse(from), LocalDate.parse(to), categoryId)
