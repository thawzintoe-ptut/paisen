package com.ptut.expense

import android.Manifest
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ptut.expense.agent.AndroidSpeechInput
import com.ptut.expense.agent.SpeechInput
import com.ptut.expense.domain.settings.ImportSettings
import com.ptut.expense.onboarding.Onboarding
import com.ptut.expense.platform.files.LedgerFileStore
import com.ptut.expense.platform.files.SafLedgerFileStore
import com.ptut.expense.platform.photos.LauncherPhotoAccessRequest
import com.ptut.expense.platform.photos.PhotoAccessRequest
import com.ptut.expense.platform.photos.hasPhotoAccess
import com.ptut.expense.platform.photos.photoPermission
import com.ptut.expense.platform.share.SharedSlipReceiver
import com.ptut.expense.platform.work.scanForSlipsNow
import com.ptut.expense.platform.work.watchForSlips
import org.koin.compose.koinInject
import org.koin.core.context.GlobalContext

/**
 * [App] with the pieces that only exist inside an Activity: the two system file pickers, and the
 * photo permission dialog. FR-22 and FR-2.
 *
 * All of them have to be registered from a composable inside the Activity and all of them need
 * something out of the graph, so this is where they meet. The Activity calls this rather than
 * [App] directly.
 */
@Composable
fun PtutAndroidApp() {
    LedgerFilePickers()
    PhotoAccessDialog()
    MicrophoneDialog()
    SlipImportOnOpen()
    App()
}

/**
 * FR-2: the dialog first run's "Allow photo access" button raises.
 *
 * The launcher can only be registered here, and the button that wants it is in common code, so the
 * two meet over the same channel-and-answer handshake the file pickers use. Nothing is launched on
 * open any more: #19 gives the permission a screen that explains it first, and a dialog raised
 * behind that screen would ask the question before the screen got to.
 */
@Composable
private fun PhotoAccessDialog() {
    val request = koinInject<PhotoAccessRequest>() as? LauncherPhotoAccessRequest ?: return

    val ask = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { request.settle() }

    LaunchedEffect(request) {
        request.asks.collect { ask.launch(photoPermission) }
    }
}

/** #212: the microphone dialog Quick add's mic raises on its first tap, over the photo dialog's handshake. */
@Composable
private fun MicrophoneDialog() {
    val speech = koinInject<SpeechInput>() as? AndroidSpeechInput ?: return

    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        speech.settle(granted)
    }

    LaunchedEffect(speech) {
        speech.asks.collect { ask.launch(Manifest.permission.RECORD_AUDIO) }
    }
}

/**
 * FR-2: scan each time the app comes back, as iOS does, so a slip K PLUS saved meanwhile shows at
 * once rather than at the next fifteen-minute job. The job itself starts once, at launch.
 *
 * It waits for first run, because first run is what asks. Before that there is nothing to grant and
 * a scan would only journal a refusal the reader was never offered the chance to avoid. Keying the
 * effect on the answer rather than reading it once is what starts the import the moment the reader
 * allows, instead of on the launch after.
 *
 * A refusal still runs one scan. It fails, and that failure is the honest thing: the journal
 * records why in words that name the fix, and Settings and the Today banner both read from it.
 * What it does not do is schedule the fifteen-minute job, which would only fail every fifteen
 * minutes for as long as the app is installed.
 *
 * FR-3's switch is read here rather than only where it is thrown. An opened app always scans —
 * that is not background work and turning the schedule off never meant turning importing off — but
 * a schedule the reader cancelled must not come back on the next launch.
 */
@Composable
private fun SlipImportOnOpen() {
    val context = LocalContext.current
    val firstRunAnswered by koinInject<Onboarding>().isFinished.collectAsStateWithLifecycle()
    val settings = koinInject<ImportSettings>()

    LaunchedEffect(firstRunAnswered) {
        if (!firstRunAnswered) return@LaunchedEffect
        if (context.hasPhotoAccess() && settings.choices.value.backgroundImport) context.watchForSlips()
    }
    LifecycleResumeEffect(firstRunAnswered) {
        if (firstRunAnswered) context.scanForSlipsNow()
        onPauseOrDispose { }
    }
}

/**
 * FR-1: the Activity's one way into the graph.
 *
 * `androidApp` carries no Koin on its own classpath — it names an Activity, an Application and
 * nothing else — so the intent is handed over here, where the graph the Application started is
 * already reachable. It is the same seam `initKoin` is, one level up.
 */
fun receiveSharedSlip(intent: Intent) {
    GlobalContext.get().get<SharedSlipReceiver>().accept(intent)
}

@Composable
private fun LedgerFilePickers() {
    val store = koinInject<LedgerFileStore>() as? SafLedgerFileStore ?: return

    val save = rememberLauncherForActivityResult(
        // The MIME type decides what the picker offers to create; the name comes with the launch.
        ActivityResultContracts.CreateDocument(JSON),
    ) { store.deliver(it) }

    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        store.deliver(it)
    }

    LaunchedEffect(store) {
        store.picks.collect { pick ->
            when (pick) {
                is SafLedgerFileStore.Pick.Save -> save.launch(pick.suggestedName)
                SafLedgerFileStore.Pick.Open -> open.launch(arrayOf(JSON))
            }
        }
    }
}

private const val JSON = "application/json"
