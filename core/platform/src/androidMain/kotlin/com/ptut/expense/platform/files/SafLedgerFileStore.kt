package com.ptut.expense.platform.files

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.ptut.expense.locale.AppText
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.withContext

/**
 * FR-22 on Android, through the Storage Access Framework.
 *
 * SAF rather than a path the app picks: NFR-1 says nothing leaves the device without the person
 * saying so, and the system picker *is* that consent. It also means no storage permission — the
 * grant is the one file they chose, and it ends there.
 *
 * The picker needs an Activity, which this has no business holding. So it asks over [picks] and
 * waits, and [deliver] is how the answer comes back. The pending answer lives here and not in the
 * composable that launches, so a rotation mid-pick cannot lose it.
 */
class SafLedgerFileStore(private val context: Context) : LedgerFileStore {

    /** What the host should open. Save carries the name to put in the field. */
    sealed interface Pick {
        data class Save(val suggestedName: String) : Pick

        data object Open : Pick
    }

    /**
     * Buffered, like the toast channel: a request must not be dropped because the host had not
     * started collecting yet, which would leave the caller waiting on an answer nobody will give.
     */
    private val requests = Channel<Pick>(Channel.BUFFERED)

    val picks: Flow<Pick> = requests.receiveAsFlow()

    private var answer: CompletableDeferred<Uri?>? = null

    /** Called by the host when the picker closes. Null means it was dismissed. */
    fun deliver(uri: Uri?) {
        answer?.complete(uri)
        answer = null
    }

    override suspend fun write(suggestedName: String, contents: String): String? {
        val target = ask(Pick.Save(suggestedName)) ?: return null
        withContext(Dispatchers.IO) {
            val stream = context.contentResolver.openOutputStream(target)
                ?: error(AppText.copy.folders.fileNotOpenedForWriting)
            stream.use { it.write(contents.encodeToByteArray()) }
        }
        return target.displayName()
    }

    override suspend fun read(): String? {
        val source = ask(Pick.Open) ?: return null
        return withContext(Dispatchers.IO) {
            val stream = context.contentResolver.openInputStream(source)
                ?: error(AppText.copy.folders.fileNotOpenedForReading)
            stream.use { it.readBytes().decodeToString() }
        }
    }

    private suspend fun ask(pick: Pick): Uri? {
        val pending = CompletableDeferred<Uri?>()
        answer = pending
        requests.send(pick)
        return pending.await()
    }

    /**
     * The name the person gave the file, for the line that confirms the save. A content URI's
     * last path segment is a document id and reads as nothing they chose.
     */
    private fun Uri.displayName(): String {
        val cursor = context.contentResolver.query(this, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        return cursor?.use { if (it.moveToFirst()) it.getString(0) else null } ?: lastPathSegment.orEmpty()
    }
}
