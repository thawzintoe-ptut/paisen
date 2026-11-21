package com.ptut.expense.data.local

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class LedgerGateTest {

    private class MemoryFiles(val disk: MutableMap<String, ByteArray> = mutableMapOf()) : LedgerFiles {
        override fun exists(path: String) = path in disk
        override fun copy(from: String, to: String) {
            disk[to] = disk.getValue(from).copyOf()
        }
        override fun move(from: String, to: String) {
            disk[to] = disk.remove(from)!!
        }
        override fun read(path: String) = disk.getValue(path)
    }

    private val files = MemoryFiles(mutableMapOf(DB to byteArrayOf(1), "$DB-wal" to byteArrayOf(2)))

    @Test
    fun aLedgerThatOpensGoesStraightToTheShellAndCopiesNothing() {
        val gate = LedgerGate(DB, files, now = { NOW }) { "database" }

        assertEquals(LedgerOpen.Ready("database"), gate.state.value)
        assertEquals("database", gate.database())
        assertEquals(setOf(DB, "$DB-wal"), files.disk.keys)
    }

    @Test
    fun aRefusedLedgerShowsRecoveryAndKeepsEveryFileWithACopyBeside() {
        val gate = LedgerGate<String>(DB, files, now = { NOW }) { error("identity mismatch") }

        assertEquals(LedgerOpen.Unopened("$DB.unopened-$NOW"), gate.state.value)
        assertEquals(setOf(DB, "$DB-wal", "$DB.unopened-$NOW", "$DB.unopened-$NOW-wal"), files.disk.keys)
        assertContentEquals(byteArrayOf(1), gate.readKeptCopy())
        assertFailsWith<IllegalStateException> { gate.database() }
    }

    @Test
    fun startingFreshMovesTheRefusedFileAsideAndOpensAnEmptyLedger() {
        var refuse = true
        val gate = LedgerGate(DB, files, now = { NOW }) { if (refuse) error("downgrade") else "fresh" }
        refuse = false

        gate.startFresh()

        assertIs<LedgerOpen.Ready<String>>(gate.state.value)
        assertEquals(
            setOf("$DB.unopened-$NOW", "$DB.unopened-$NOW-wal", "$DB.set-aside-$NOW", "$DB.set-aside-$NOW-wal"),
            files.disk.keys,
        )
    }

    private companion object {
        const val DB = "/data/ptut-expense.db"
        const val NOW = 1_700_000_000_000L
    }
}
