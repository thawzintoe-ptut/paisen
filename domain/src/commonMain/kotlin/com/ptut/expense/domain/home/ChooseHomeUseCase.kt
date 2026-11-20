package com.ptut.expense.domain.home

import com.ptut.expense.domain.HomeChoice

/** #41: the ledger's money and zone. Nothing happens where no home is offered or no book is bound. */
class ChooseHomeUseCase(private val book: HomeBook? = null) {
    suspend operator fun invoke(home: HomeChoice?) {
        home?.let { book?.choose(it) }
    }
}
