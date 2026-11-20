package com.ptut.expense.domain.shared

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Every shared group, expense and payment on this phone, as they change; empty in a build without groups. */
class ObserveSharedSnapshotUseCase(private val repository: SharedRepository? = null) {
    operator fun invoke(): StateFlow<SharedSnapshot> = repository?.snapshot ?: MutableStateFlow(SharedSnapshot.Empty)
}
