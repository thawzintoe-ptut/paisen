package com.ptut.expense.domain.slip

import com.ptut.expense.domain.agent.AnswerQuickAddQuestionUseCase
import com.ptut.expense.domain.ledger.DeleteTransactionUseCase
import com.ptut.expense.domain.ledger.EditTransactionUseCase
import com.ptut.expense.domain.ledger.GetLastRateUseCase
import com.ptut.expense.domain.ledger.ImportStoreRates
import com.ptut.expense.domain.ledger.RecordTransactionUseCase
import com.ptut.expense.domain.ledger.UndoDeleteTransactionUseCase
import com.ptut.expense.domain.record.GetDeleteImpactUseCase
import com.ptut.expense.domain.record.ObserveFilingRulesUseCase
import com.ptut.expense.domain.record.ObserveShareOptionsUseCase
import com.ptut.expense.domain.record.SetPlannedUseCase
import com.ptut.expense.domain.shared.SharedRepository
import org.koin.core.module.Module
import org.koin.dsl.module

/** The use cases behind Add, the record screens, Check slip, Review and shop rules. */
val recordUseCases: Module = module {
    factory { AnswerQuickAddQuestionUseCase(getOrNull()) }

    factory { SetPlannedUseCase(get()) }
    factory { GetDeleteImpactUseCase() }
    // getOrNull, as the record had them: a screen test's graph binds no groups and no rules table.
    factory { ObserveShareOptionsUseCase(getOrNull()) }
    factory { ObserveFilingRulesUseCase(getOrNull()) }

    factory { GetQueuedSlipUseCase(get()) }
    factory { ObserveSlipInQueueUseCase(get()) }
    factory { DiscardSlipWithUndoUseCase(get()) }
    factory { RunSlipImportUseCase(get()) }

    factory { ObserveShopRuleUseCase(get()) }
    factory { RecategoriseShopUseCase(get()) }
    factory { RenameShopUseCase(get()) }
    factory { ForgetShopUseCase(get()) }

    factory { GetLastRateUseCase(getOrNull<ImportStore>()?.let(::ImportStoreRates)) }
    factory { RecordTransactionUseCase(get(), getOrNull<ImportStore>()?.let(::ImportStoreRates)) }
    factory { EditTransactionUseCase(get(), shared = getOrNull()) }
    factory { DeleteTransactionUseCase(get(), shared = getOrNull()) }
    factory { UndoDeleteTransactionUseCase(get(), shared = getOrNull()) }
    factory { ConfirmSlipUseCase(get()) }
    factory {
        val shared = getOrNull<SharedRepository>()
        PaySlipDebtUseCase { draft -> shared?.recordPayment(draft) }
    }
    factory { DiscardSlipUseCase(get()) }
}
