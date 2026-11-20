package com.ptut.expense.sync.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.installations.FirebaseInstallations
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.ptut.expense.domain.runSuspendCatching
import com.ptut.expense.domain.shared.SharedRepository
import com.ptut.expense.locale.AppText
import com.ptut.expense.platform.notify.notificationsAllowed
import com.ptut.expense.sync.FirebaseConfig
import com.ptut.expense.sync.SharedSync
import com.ptut.expense.sync.auth.Account
import com.ptut.expense.sync.auth.AuthRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.koin.core.context.GlobalContext

/** The application context, for the leftover store and for retiring an installation; set at launch. */
@Volatile
private var appContext: Context? = null

/** Whether this process has started Firebase Messaging, so a retired installation registers again. */
@Volatile
private var pushStarted = false

private val pushScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

/** Starts Firebase Messaging once an account is signed in, never for a phone that only keeps its own ledger. */
fun startPushAfterSignIn(context: Context) {
    appContext = context.applicationContext
    val account = GlobalContext.getOrNull()?.getOrNull<AuthRepository>()?.account ?: return
    pushScope.startWhenSignedIn(account) { startPush(context) }
}

/**
 * Starts Firebase Messaging in a build with a push Worker. Registering hands this phone's
 * installation ID to [PaisenMessagingService.onRegistered], which passes it to [PushTokenHolder].
 * The options come from `firebase.properties`, so there is no Google Services plugin or JSON file.
 * A phone without Google Play services gets no token and polls as before.
 */
private fun startPush(context: Context) {
    if (!FirebaseConfig.pushEnabled || !firebaseAppReady(context)) return
    pushStarted = true
    FirebaseMessaging.getInstance().register()
}

/** Makes the default FirebaseApp from `firebase.properties` once; false when the build has no Android app id. */
internal fun firebaseAppReady(context: Context): Boolean {
    if (!FirebaseConfig.enabled || FirebaseConfig.ANDROID_APP_ID.isEmpty() || FirebaseConfig.SENDER_ID.isEmpty()) return false
    if (FirebaseApp.getApps(context).isEmpty()) {
        val options = FirebaseOptions.Builder()
            .setApplicationId(FirebaseConfig.ANDROID_APP_ID)
            .setApiKey(FirebaseConfig.API_KEY)
            .setProjectId(FirebaseConfig.PROJECT_ID)
            .setGcmSenderId(FirebaseConfig.SENDER_ID)
            .build()
        FirebaseApp.initializeApp(context, options)
    }
    return true
}

/**
 * A tapped notification's group, from `MainActivity`; the shell opens it once it has drawn. L5: the
 * activity is exported, so any app can send this extra, and only a group this phone holds opens.
 */
fun receivePushTap(intent: Intent) {
    val groupId = intent.getStringExtra(EXTRA_GROUP_ID) ?: return
    val snapshot = GlobalContext.getOrNull()?.getOrNull<SharedRepository>()?.snapshot ?: return
    pushScope.launch { heldGroup(groupId, snapshot)?.let(GroupLinks::offer) }
}

/**
 * Every push is data only, so the app decides: with a screen open it syncs at once and shows
 * nothing; otherwise it words the notice from this phone's copy of the group (M6), or posts an old
 * build's sentence as the Worker cleaned it.
 */
class PaisenMessagingService : FirebaseMessagingService() {

    /** At each start and whenever FCM moves this phone to a new installation ID. */
    override fun onRegistered(installationId: String) {
        tokens()?.set(installationId)
    }

    /** Play services older than 26.12 still registers with a classic token, through this callback. */
    @Deprecated("Only Play services older than 26.12 calls it; newer ones call onRegistered.")
    override fun onNewToken(token: String) {
        tokens()?.set(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val push = ReceivedPush.from(message.data) ?: return
        val groupId = push.groupId
        // A sign-out made offline leaves the device document behind: only the account it names hears it.
        if (!signedInAs(push.uid)) return
        if (PushEvents.offer(groupId)) return
        if (!notificationsAllowed()) return
        val words = wordsFor(push)
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, AppText.copy.folders.sharedGroupsChannel, NotificationManager.IMPORTANCE_DEFAULT))
        val open = packageManager.getLaunchIntentForPackage(packageName)?.let { launch ->
            launch.putExtra(EXTRA_GROUP_ID, groupId)
            PendingIntent.getActivity(this, groupId.hashCode(), launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(applicationInfo.icon)
            .setContentTitle(words.title)
            .setContentText(words.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(words.body))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        @Suppress("MissingPermission")
        NotificationManagerCompat.from(this).notify((message.messageId ?: "$groupId${words.body}").hashCode(), notification)
    }

    /** Blocks this service's own worker thread, for no longer than the reader's short sync. */
    private fun wordsFor(push: ReceivedPush): PushWords {
        val koin = GlobalContext.getOrNull()
        val repository = koin?.getOrNull<SharedRepository>() ?: return PushWords(push.title.orEmpty(), push.body.orEmpty())
        val sync = koin.getOrNull<SharedSync>()
        val reader = PushReader(repository.snapshot, sync = { sync?.syncAll() })
        return runBlocking { reader.words(push, AppText.copy.shared) }
    }
}

/** L3 on Android: kept in the app's own preferences, which OS backups leave out. */
internal actual fun pushLeftovers(): PushLeftovers = PreferencesPushLeftovers

private object PreferencesPushLeftovers : PushLeftovers {
    private fun preferences() = appContext?.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    override fun read(): PushLeftover? {
        val preferences = preferences() ?: return null
        val token = preferences.getString(KEY_TOKEN, null) ?: return null
        val uid = preferences.getString(KEY_UID, null) ?: return null
        return PushLeftover(token, uid)
    }

    override fun write(leftover: PushLeftover?) {
        val editor = preferences()?.edit() ?: return
        if (leftover == null) editor.clear() else editor.putString(KEY_TOKEN, leftover.token).putString(KEY_UID, leftover.uid)
        editor.apply()
    }

    /**
     * Deleting the installation retires its ID at FCM, so the Worker's next send to it fails and
     * removes the document. An ID that is no longer this installation's was retired already.
     */
    override suspend fun retire(token: String): Boolean {
        val context = appContext ?: return false
        if (!firebaseAppReady(context)) return false
        return runSuspendCatching {
            val installations = FirebaseInstallations.getInstance()
            if (installations.id.await() == token) {
                installations.delete().await()
                if (pushStarted) FirebaseMessaging.getInstance().register()
            }
        }.isSuccess
    }

    private const val FILE = "push_leftover"
    private const val KEY_TOKEN = "token"
    private const val KEY_UID = "uid"
}

private fun tokens(): PushTokenHolder? = GlobalContext.getOrNull()?.getOrNull<PushTokenHolder>()

private fun signedInAs(uid: String?): Boolean {
    val account = GlobalContext.getOrNull()?.getOrNull<AuthRepository>()?.account?.value as? Account.SignedIn
    return uid != null && account?.uid == uid
}

private const val CHANNEL = "shared-groups"
private const val EXTRA_GROUP_ID = "com.ptut.expense.PUSH_GROUP_ID"
