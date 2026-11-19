package com.ptut.expense.sync

/** The HTTP statuses sync reads, and which of them a later try can pass. */
internal object FirestoreStatus {
    /** [FirestoreException.status] for a call that got no answer, or one that was not Firestore's. */
    const val NO_ANSWER = 0
    const val BAD_REQUEST = 400
    const val UNAUTHORIZED = 401
    const val FORBIDDEN = 403
    const val NOT_FOUND = 404
    const val TIMEOUT = 408
    const val CONFLICT = 409
    const val TOO_MANY_REQUESTS = 429

    /** An expired session, a timeout or a rate limit can pass later; no other 4xx will. */
    private val RETRYABLE = setOf(UNAUTHORIZED, TIMEOUT, TOO_MANY_REQUESTS)

    fun isPermanent(status: Int): Boolean = status in CLIENT_ERRORS && status !in RETRYABLE

    private val CLIENT_ERRORS = 400..499
}
