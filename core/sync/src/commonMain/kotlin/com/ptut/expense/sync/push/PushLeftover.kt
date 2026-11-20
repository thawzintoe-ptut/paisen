package com.ptut.expense.sync.push

/** L3: a device document sign-out could not delete, by installation ID and the account it named. */
data class PushLeftover(val token: String, val uid: String)

/** Where this phone keeps a [PushLeftover] across launches until it is settled. */
internal interface PushLeftovers {
    fun read(): PushLeftover?

    fun write(leftover: PushLeftover?)

    /**
     * Makes FCM refuse [token], so the Worker deletes its document on its next send to it. False
     * when that failed or this platform cannot, and the leftover waits for a later start.
     */
    suspend fun retire(token: String): Boolean
}

internal expect fun pushLeftovers(): PushLeftovers
