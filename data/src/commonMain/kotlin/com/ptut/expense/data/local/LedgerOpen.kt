package com.ptut.expense.data.local

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What opening the ledger at launch gave: the database, or a copy kept beside a file Room refused. */
sealed interface LedgerOpen<out D> {
    data class Ready<D>(val database: D) : LedgerOpen<D>

    /** [keptCopy] is the path of the copied main file; its -wal and -shm sit beside it when they existed. */
    data class Unopened(val keptCopy: String) : LedgerOpen<Nothing>
}

/** The few file moves recovery needs, so the decision is testable without a disk. */
interface LedgerFiles {
    fun exists(path: String): Boolean
    fun copy(from: String, to: String)
    fun move(from: String, to: String)
    fun read(path: String): ByteArray
}

/**
 * Opens the ledger once at launch and never deletes it. A file Room refuses (an identity mismatch,
 * a downgrade, a corrupt page) is copied aside first, so whatever the person picks next, the
 * original bytes survive.
 */
class LedgerGate<D>(
    private val path: String,
    private val files: LedgerFiles,
    private val now: () -> Long,
    private val open: () -> D,
) {
    private val current = MutableStateFlow(tryOpen())
    val state: StateFlow<LedgerOpen<D>> = current.asStateFlow()

    /** The open database. Only the shell asks for it, and only after [state] said Ready. */
    fun database(): D = (current.value as? LedgerOpen.Ready)?.database ?: error("The ledger did not open.")

    fun readKeptCopy(): ByteArray? = (current.value as? LedgerOpen.Unopened)?.let { files.read(it.keptCopy) }

    /** Moves the refused file aside, beside the copy already kept, and opens an empty ledger. */
    fun startFresh() {
        if (current.value is LedgerOpen.Ready) return
        val aside = "$path.set-aside-${now()}"
        SIDE_FILES.forEach { suffix -> if (files.exists(path + suffix)) files.move(path + suffix, aside + suffix) }
        current.value = tryOpen()
    }

    private fun tryOpen(): LedgerOpen<D> = try {
        LedgerOpen.Ready(open())
    } catch (refused: Exception) {
        LedgerOpen.Unopened(keepUnopened())
    }

    private fun keepUnopened(): String {
        val kept = "$path.unopened-${now()}"
        SIDE_FILES.forEach { suffix -> if (files.exists(path + suffix)) files.copy(path + suffix, kept + suffix) }
        return kept
    }

    private companion object {
        val SIDE_FILES = listOf("", "-wal", "-shm")
    }
}
