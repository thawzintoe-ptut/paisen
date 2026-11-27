package com.ptut.expense.data.local

import androidx.room.RoomDatabase
import androidx.room.immediateTransaction
import androidx.room.useWriterConnection

/**
 * One write transaction across several DAO calls. Room's DAOs cannot open one themselves outside a
 * `@Transaction` method, so a repository that must write a change and its outbox row together asks here.
 * DAO calls inside [run] use the same connection; a `@Transaction` DAO method inside becomes a savepoint.
 */
interface WriteTransaction {
    suspend fun <T> run(block: suspend () -> T): T

    /** No transaction: for tests and fakes with no database behind them. */
    object None : WriteTransaction {
        override suspend fun <T> run(block: suspend () -> T): T = block()
    }
}

class RoomWriteTransaction(private val database: RoomDatabase) : WriteTransaction {
    override suspend fun <T> run(block: suspend () -> T): T = database.useWriterConnection { it.immediateTransaction { block() } }
}
