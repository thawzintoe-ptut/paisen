package com.ptut.expense.domain.play

import com.ptut.expense.domain.Satang
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate

/**
 * J05: the importer's way into the jar. A transfer slip to the chosen savings account fills the jar
 * instead of the ledger, and every transfer to one of the reader's own accounts is noted for J04.
 */
interface JarInbox {

    /** The open jar whose savings account is [account], or null. */
    suspend fun goalFor(account: String): Goal?

    /** False when a deposit with [key] filled the jar before. */
    suspend fun deposit(goal: Goal, key: String, amount: Satang, day: LocalDate, atEpochMillis: Long, account: String): Boolean

    suspend fun noteOwnAccount(account: String, holder: String, atEpochMillis: Long)

    /** No jar: every slip goes to the ledger, as before J05. */
    object None : JarInbox {
        override suspend fun goalFor(account: String): Goal? = null

        override suspend fun deposit(goal: Goal, key: String, amount: Satang, day: LocalDate, atEpochMillis: Long, account: String): Boolean = false

        override suspend fun noteOwnAccount(account: String, holder: String, atEpochMillis: Long) = Unit
    }
}

class PlayBookJarInbox(private val book: PlayBook) : JarInbox {

    override suspend fun goalFor(account: String): Goal? =
        book.goals.first().lastOrNull { it.archivedAtEpochMillis == null && it.savingsAccount == account }

    override suspend fun deposit(goal: Goal, key: String, amount: Satang, day: LocalDate, atEpochMillis: Long, account: String): Boolean =
        book.deposit(GoalDeposit(id = key, goalId = goal.id, amount = amount, day = day, atEpochMillis = atEpochMillis, source = DepositSource.Slip, account = account))

    override suspend fun noteOwnAccount(account: String, holder: String, atEpochMillis: Long) = book.seeAccount(account, holder, atEpochMillis)
}
