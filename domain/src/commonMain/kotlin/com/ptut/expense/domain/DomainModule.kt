package com.ptut.expense.domain

import com.ptut.expense.domain.folders.ObserveWatchedFoldersUseCase
import com.ptut.expense.domain.ledger.ObserveImportRunsUseCase
import com.ptut.expense.domain.ledger.ObserveLastImportUseCase
import com.ptut.expense.domain.ledger.ObserveTransactionsUseCase
import com.ptut.expense.domain.settings.ObserveAppChoicesUseCase
import com.ptut.expense.domain.settings.settingsUseCases
import com.ptut.expense.domain.shared.ObserveSharedSnapshotUseCase
import com.ptut.expense.domain.shared.sharedUseCases
import com.ptut.expense.domain.slip.GetSoonestEvidenceExpiryUseCase
import com.ptut.expense.domain.slip.ObserveQueuedSlipsUseCase
import com.ptut.expense.domain.slip.recordUseCases
import com.ptut.expense.domain.today.GetHomeTodayUseCase
import com.ptut.expense.domain.today.todayUseCases
import com.ptut.expense.importer.SlipDiscards
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * The reads several areas share; each area binds its own use cases. All are factories, since none holds
 * state; [com.ptut.expense.domain.shared.RunDueRepeatsUseCase] does, so it is a single beside the reminders.
 */
val domainModule: Module = module {
    includes(sharedUseCases, todayUseCases, recordUseCases, settingsUseCases)

    factory { ObserveTransactionsUseCase(get()) }
    factory { ObserveImportRunsUseCase(get()) }
    factory { ObserveLastImportUseCase(get()) }
    factory { ObserveSharedSnapshotUseCase(getOrNull()) }
    factory { ObserveAppChoicesUseCase(getOrNull()) }
    factory { ObserveWatchedFoldersUseCase(getOrNull()) }
    factory { GetHomeTodayUseCase() }
    factory { ObserveQueuedSlipsUseCase(ledger = get(), pendingDiscards = get<SlipDiscards>().pending) }
    factory { GetSoonestEvidenceExpiryUseCase() }
}
