package com.ptut.expense.data.repository

import androidx.room.Room
import com.ptut.expense.data.local.PtutDatabase
import com.ptut.expense.data.local.entity.SubCategoryEntity
import com.ptut.expense.data.local.openPtutDatabase
import com.ptut.expense.domain.ReviewReason
import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.slip.ConfirmedSlip
import com.ptut.expense.domain.slip.FiledSlip
import com.ptut.expense.domain.slip.QueuedSlipEntry
import com.ptut.expense.domain.slip.SlipRead
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The confirm and file writes against the real schema. The host suite drives a fake store that
 * only mirrors these DAOs, so this is the check on the DAOs themselves.
 */
class RoomImportStoreTest {

    @Test
    fun aConfirmedSlipIsKnownByItsPictureSoTheNextScanSkipsIt() = withStore { store, _ ->
        store.queue(queued())
        store.confirm(confirmed())

        assertTrue(store.isImported("hash-1"), "the next scan would read the confirmed slip again")
        assertNull(store.queuedPictureFor("hash-1"))
    }

    @Test
    fun aConfirmedSlipIsFiledAtTheMomentItWasConfirmed() = withStore { store, database ->
        store.queue(queued())
        store.confirm(confirmed())

        assertEquals(CONFIRMED_AT, database.transactionDao().all().single().filedAt)
    }

    @Test
    fun aSlipTheImporterFilesIsFiledAtTheTimeOfItsRun() = withStore { store, database ->
        store.file(
            FiledSlip(
                id = "row-2",
                hash = "hash-2",
                read = SlipRead.Complete(
                    counterparty = MERCHANT,
                    amount = Satang(6_500),
                    occurredAtEpochMillis = SLIP_AT,
                    reference = "016230195028BQR06235",
                ),
                categoryId = "food",
                filedAtEpochMillis = RUN_AT,
            ),
        )

        assertEquals(RUN_AT, database.transactionDao().all().single().filedAt)
    }

    @Test
    fun aConfirmedSlipKeepsTheSubCategoryTheReaderPicked() = withStore { store, database ->
        database.subCategoryDao().upsert(SubCategoryEntity(id = "coffee", categoryId = "food", name = "Coffee"))
        store.queue(queued())
        store.confirm(confirmed().copy(subCategoryId = "coffee"))

        assertEquals("coffee", database.transactionDao().all().single().subCategoryId)
    }

    @Test
    fun aConfirmedSlipDropsASubCategoryFromAnotherCategory() = withStore { store, database ->
        database.subCategoryDao().upsert(SubCategoryEntity(id = "bts", categoryId = "transport", name = "BTS"))
        store.queue(queued())
        store.confirm(confirmed().copy(subCategoryId = "bts"))

        val saved = database.transactionDao().all().single()
        assertEquals("food", saved.categoryId)
        assertNull(saved.subCategoryId)
    }

    private fun withStore(test: suspend (RoomImportStore, PtutDatabase) -> Unit): TestResult = runTest {
        val database = Room.inMemoryDatabaseBuilder<PtutDatabase>().openPtutDatabase()
        val store = RoomImportStore(
            transactionDao = database.transactionDao(),
            importedSlipDao = database.importedSlipDao(),
            queuedSlipDao = database.queuedSlipDao(),
            importRunDao = database.importRunDao(),
            ruleDao = database.ruleDao(),
            slipConfirmationDao = database.slipConfirmationDao(),
        )
        try {
            test(store, database)
        } finally {
            database.close()
        }
    }

    private fun queued() = QueuedSlipEntry(
        id = "picture-1",
        hash = "hash-1",
        counterparty = MERCHANT,
        amount = null,
        occurredAtEpochMillis = SLIP_AT,
        reference = null,
        capturedAtEpochMillis = SLIP_AT,
        reason = ReviewReason.AmountUnclear,
    )

    private fun confirmed() = ConfirmedSlip(
        transactionId = "row-1",
        ruleId = "rule-1",
        queuedSlipId = "picture-1",
        counterparty = MERCHANT,
        categoryId = "food",
        amount = Satang(15_500),
        occurredAtEpochMillis = SLIP_AT,
        reference = null,
        confirmedAtEpochMillis = CONFIRMED_AT,
    )

    private companion object {
        const val MERCHANT = "LAWSON108_P3793 THE LIVIN"
        const val SLIP_AT = 1_755_000_000_000L
        const val CONFIRMED_AT = 1_759_560_000_000L
        const val RUN_AT = 1_759_563_600_000L
    }
}
