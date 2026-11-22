package com.ptut.expense.sync

import com.ptut.expense.data.local.PtutDatabaseContext

/** Whether "signed in on another phone" waits to be read, kept across launches. */
internal interface EndedHereNotice {
    fun read(): Boolean

    fun write(pending: Boolean)
}

internal expect fun endedHereNotice(context: PtutDatabaseContext): EndedHereNotice

/** For a [SharedSync] built without a platform store, as in tests. */
internal class MemoryEndedHereNotice(private var pending: Boolean = false) : EndedHereNotice {
    override fun read(): Boolean = pending

    override fun write(pending: Boolean) {
        this.pending = pending
    }
}
