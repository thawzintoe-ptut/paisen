package com.ptut.expense.domain

import kotlin.coroutines.cancellation.CancellationException

/**
 * [runCatching] for suspend code: a failure becomes a [Result], but a cancellation is thrown on, so
 * a screen that leaves stops its work instead of treating the stop as an error.
 */
inline fun <T> runSuspendCatching(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (failure: Exception) {
    Result.failure(failure)
}
