package com.ptut.expense

import androidx.compose.ui.window.ComposeUIViewController
import com.ptut.expense.data.local.IosPtutDatabaseContext
import com.ptut.expense.di.AppBuild
import com.ptut.expense.di.initKoin
import com.ptut.expense.platform.share.SharedSlipReceiver
import com.ptut.expense.platform.work.SlipImportSchedule
import com.ptut.expense.sync.InviteLinks
import org.koin.mp.KoinPlatform
import platform.Foundation.NSURL
import platform.UIKit.UIViewController
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.Platform

/**
 * iOS has no Application class, so the graph is started here. `by lazy` because SwiftUI may
 * rebuild the hosting controller and a second `startKoin` throws.
 */
@OptIn(ExperimentalNativeApi::class)
internal val graphStarted: Unit by lazy {
    initKoin(IosPtutDatabaseContext, AppBuild(debug = Platform.isDebugBinary))
}

/**
 * FR-3 starts with the UI: the album has to be watched from the first launch, not from the first
 * time someone opens Settings. Siri may start the app in the background (#206); that only needs
 * [graphStarted], and a question about spending should not read the album.
 */
private val koinStarted: Unit by lazy {
    graphStarted
    KoinPlatform.getKoin().get<SlipImportSchedule>().start()
}

/** Entry point the Xcode wrapper calls. */
fun MainViewController(): UIViewController {
    koinStarted
    return ComposeUIViewController { App() }
}

/**
 * FR-1: the picture iOS handed this app from the share sheet.
 *
 * `iosApp` carries no Koin on its own classpath — it names a SwiftUI scene and a hosting view and
 * nothing else — so the URL is handed over here, where the graph is already reachable. It starts
 * the graph itself because a share into an app that was not running arrives before the first frame.
 */
fun receiveSharedSlip(url: NSURL) {
    koinStarted
    KoinPlatform.getKoin().get<SharedSlipReceiver>().accept(url)
}

/**
 * #190: a universal link to `/join/<code>`. It waits in [InviteLinks] until the shell has drawn,
 * the same way a link that started the app does.
 */
fun receiveInviteLink(url: NSURL) {
    url.absoluteString?.let(InviteLinks::offer)
}
