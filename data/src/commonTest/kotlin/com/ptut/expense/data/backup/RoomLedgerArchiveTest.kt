package com.ptut.expense.data.backup

import com.ptut.expense.data.local.dao.CategoryDao
import com.ptut.expense.data.local.dao.LedgerMergeDao
import com.ptut.expense.data.local.dao.TransactionDao
import com.ptut.expense.data.local.entity.CategoryEntity
import com.ptut.expense.data.local.entity.SubCategoryEntity
import com.ptut.expense.data.local.entity.TransactionEntity
import com.ptut.expense.data.repository.RoomLedgerArchive
import com.ptut.expense.domain.backup.BackupUnreadable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RoomLedgerArchiveTest {

    private val satangExact = row(id = "t1", amountMinor = 2_825, reference = "0142K9")
    private val byHand = row(id = "t2", amountMinor = 7_000, reference = null, source = "Manual")

    @Test
    fun `export then import into an empty ledger returns the same rows satang for satang`() = runTest {
        val file = archive(holding = listOf(satangExact, byHand)).export()

        val empty = FakeLedgerTable()
        val report = archive(table = empty).restore(file)

        assertEquals(2, report.added)
        assertEquals(listOf(satangExact, byHand), empty.rows.sortedBy { it.id })
        // FR-9: 28.25 baht is 2825 satang on both sides. A Double here would land 2824 or 2826.
        assertEquals(2_825L, empty.rows.single { it.id == "t1" }.amountMinor)
    }

    @Test
    fun `importing the same file twice adds no rows`() = runTest {
        val file = archive(holding = listOf(satangExact, byHand)).export()

        val table = FakeLedgerTable()
        archive(table = table).restore(file)
        val second = archive(table = table).restore(file)

        assertEquals(0, second.added)
        assertEquals(2, second.alreadyHeld)
        assertEquals(2, table.rows.size)
    }

    @Test
    fun `importing into a ledger that already holds rows keeps them`() = runTest {
        val existing = row(id = "kept", amountMinor = 50_000, reference = "999")
        val table = FakeLedgerTable(existing)
        val file = archive(holding = listOf(satangExact)).export()

        archive(table = table).restore(file)

        assertTrue(existing in table.rows)
        assertEquals(2, table.rows.size)
    }

    @Test
    fun `a row that reached the ledger by scan is not added again by file`() = runTest {
        // Same reference, amount and instant under a different id: one slip, photographed and
        // also exported. The F3 content key is what catches it, not the primary key.
        val rephotographed = satangExact.copy(id = "different-id")
        val file = archive(holding = listOf(rephotographed)).export()

        val table = FakeLedgerTable(satangExact)
        val report = archive(table = table).restore(file)

        assertEquals(0, report.added)
        assertEquals(1, table.rows.size)
    }

    @Test
    fun `a file written by a newer format is refused by version rather than read as far as it goes`() = runTest {
        val newer = archive(holding = listOf(satangExact)).export().replace("\"format\": 3", "\"format\": 4")

        val failure = assertFailsWith<BackupUnreadable> { archive().restore(newer) }

        assertTrue(failure.message!!.contains("format 4"), failure.message!!)
    }

    @Test
    fun `text that is not a ledger file is refused`() = runTest {
        assertFailsWith<BackupUnreadable> { archive().restore("""{"hello":"world"}""") }
    }

    @Test
    fun `an unknown direction refuses the whole file rather than guessing`() = runTest {
        val tampered = archive(holding = listOf(satangExact)).export()
            .replace("\"Expense\"", "\"Outgoing\"")
        val table = FakeLedgerTable()

        assertFailsWith<BackupUnreadable> { archive(table = table).restore(tampered) }

        // Nothing lands, so a refused file leaves the ledger exactly as it was.
        assertEquals(0, table.rows.size)
    }

    @Test
    fun `a category this install does not hold becomes no category and the row still lands`() = runTest {
        val foreign = row(id = "t9", amountMinor = 1_000, reference = "r9").copy(categoryId = "hobbies")
        val file = archive(holding = listOf(foreign)).export()

        val table = FakeLedgerTable()
        archive(table = table).restore(file)

        assertEquals(1, table.rows.size)
        assertEquals(null, table.rows.single().categoryId)
        assertEquals(1_000L, table.rows.single().amountMinor)
    }

    @Test
    fun `export then import keeps the sub-category`() = runTest {
        val filed = satangExact.copy(subCategoryId = "coffee")
        val file = archive(holding = listOf(filed)).export()

        val table = FakeLedgerTable()
        archive(table = table).restore(file)

        assertEquals("coffee", table.rows.single().subCategoryId)
    }

    /** The file is a contract: one written before #110 has no sub-category field at all. */
    @Test
    fun `a backup written before sub-categories still imports`() = runTest {
        val before = """
            {"format": 1, "exportedAtEpochMillis": ${satangExact.occurredAtEpochMillis}, "transactions": [
              {"id": "t1", "occurredAtEpochMillis": ${satangExact.occurredAtEpochMillis}, "amountMinor": 2825,
               "direction": "Expense", "categoryId": "food", "source": "Scan", "reference": "0142K9"}
            ]}
        """.trimIndent()

        val table = FakeLedgerTable()
        archive(table = table).restore(before)

        assertEquals(1, table.rows.size)
        assertEquals(null, table.rows.single().subCategoryId)
    }

    /** A sub-category id this install does not hold would fail the column's foreign key. */
    @Test
    fun `a sub-category this install does not hold is dropped and the row still lands`() = runTest {
        val foreign = satangExact.copy(subCategoryId = "matcha")
        val file = archive(holding = listOf(foreign)).export()

        val table = FakeLedgerTable()
        archive(table = table).restore(file)

        assertEquals(1, table.rows.size)
        assertEquals("food", table.rows.single().categoryId)
        assertEquals(null, table.rows.single().subCategoryId)
    }

    private fun archive(
        holding: List<TransactionEntity> = emptyList(),
        table: FakeLedgerTable = FakeLedgerTable(),
    ) = RoomLedgerArchive(
        transactionDao = TransactionsOnDisk(holding),
        categoryDao = SeededCategories(setOf("food", "transport")),
        mergeDao = table,
    )

    private fun row(
        id: String,
        amountMinor: Long,
        reference: String?,
        source: String = "Scan",
    ) = TransactionEntity(
        id = id,
        occurredAtEpochMillis = 1_755_000_000_000,
        amountMinor = amountMinor,
        direction = "Expense",
        categoryId = "food",
        counterparty = "Lawson 108",
        source = source,
        reference = reference,
        editedAt = null,
    )
}

/**
 * The two keys the real table declares: the primary id, and the F3 content index over reference,
 * amount and instant. SQLite counts NULLs as distinct, so a row with no reference collides on its
 * id alone — which is what makes re-importing a hand-typed row idempotent.
 *
 * The real enforcement is the entity's own `@Index(unique = true)`; this models it so the merge's
 * counting can be driven without a database.
 */
private class FakeLedgerTable(vararg initial: TransactionEntity) : LedgerMergeDao() {

    val rows: List<TransactionEntity> get() = stored.toList()

    private val stored = initial.toMutableList()

    override suspend fun insertIfNew(row: TransactionEntity): Long {
        val clashes = stored.any { held ->
            held.id == row.id || (
                row.reference != null &&
                    held.reference == row.reference &&
                    held.amountMinor == row.amountMinor &&
                    held.occurredAtEpochMillis == row.occurredAtEpochMillis
                )
        }
        if (clashes) return -1L
        stored += row
        return stored.size.toLong()
    }
}

/**
 * What the export reads, and the sub-category lookup the import checks against. Nothing else is
 * reached; the archive never observes and never writes through this.
 */
private class TransactionsOnDisk(
    private val rows: List<TransactionEntity>,
    /** Sub-category to its parent, as this install's `sub_category` table holds them. */
    private val subCategories: Map<String, String> = mapOf("coffee" to "food"),
) : TransactionDao {

    override suspend fun all(): List<TransactionEntity> = rows

    override fun observeBetween(
        fromEpochMillis: Long,
        untilEpochMillis: Long,
    ): Flow<List<TransactionEntity>> = never()

    override fun observeSince(fromEpochMillis: Long): Flow<List<TransactionEntity>> = never()

    override fun observeAll(): Flow<List<TransactionEntity>> = never()

    override suspend fun insert(row: TransactionEntity) = never<Unit>()

    override suspend fun insertIfNew(row: TransactionEntity): Long = never()

    override suspend fun deleteById(id: String) = never<Unit>()

    override suspend fun applyEdit(
        id: String,
        amountMinor: Long,
        categoryId: String?,
        subCategoryId: String?,
        memo: String?,
        occurredAtEpochMillis: Long?,
        editedAtEpochMillis: Long,
    ) = never<Unit>()

    override suspend fun subCategoryUnder(subCategoryId: String, categoryId: String): String? =
        subCategoryId.takeIf { subCategories[it] == categoryId }

    private fun <T> never(): T = throw AssertionError("The archive does not read the ledger this way.")
}

private class SeededCategories(private val known: Set<String>) : CategoryDao {

    override suspend fun ids(): List<String> = known.toList()

    override fun observeAll(): Flow<List<CategoryEntity>> = MutableStateFlow(emptyList())

    override suspend fun upsert(category: CategoryEntity) = editsAreNotThisTest<Unit>()

    override suspend fun delete(id: String) = editsAreNotThisTest<Unit>()

    override suspend fun reassign(from: String, to: String) = editsAreNotThisTest<Unit>()

    override suspend fun setSortOrder(id: String, place: Int) = editsAreNotThisTest<Unit>()

    override suspend fun transactionCount(id: String): Int = editsAreNotThisTest()

    override suspend fun reassignRules(from: String, to: String) = editsAreNotThisTest<Unit>()

    override suspend fun ruleCount(id: String): Int = editsAreNotThisTest()

    override suspend fun rememberDeletedBuiltIn(id: String) = editsAreNotThisTest<Unit>()

    override suspend fun upsertSubCategories(rows: List<SubCategoryEntity>) = editsAreNotThisTest<Unit>()

    override suspend fun deleteSubCategories(ids: List<String>) = editsAreNotThisTest<Unit>()

    private fun <T> editsAreNotThisTest(): T =
        throw AssertionError("The archive never edits a category; it only checks the ids exist.")
}
