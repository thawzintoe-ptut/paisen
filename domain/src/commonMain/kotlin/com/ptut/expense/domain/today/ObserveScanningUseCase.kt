package com.ptut.expense.domain.today

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Whether a scan is running: the fact, not the importer, so a screen that shows it cannot start one. */
class ObserveScanningUseCase(private val isScanning: Flow<Boolean> = flowOf(false)) {
    operator fun invoke(): Flow<Boolean> = isScanning
}
