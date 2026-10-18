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
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.ptut.expense.locale.AppText
import com.ptut.expense.platform.notify.notificationsAllowed
import com.ptut.expense.sync.FirebaseConfig
import com.ptut.expense.sync.auth.Account
import com.ptut.expense.sync.auth.AuthRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.context.GlobalContext

/** Starts Firebase Messaging once an account is signed in, never for a phone that only keeps its own ledger. */
fun startPushAfterSignIn(context: Context) {
    val account = GlobalContext.getOrNull()?.getOrNull<AuthRepository>()?.account ?: return
    CoroutineScope(SupervisorJob() + Dispatchers.Default).startWhenSignedIn(account) { startPush(context) }
}

/**
 * Starts Firebase Messaging in a build with a push Worker. Registering hands this phone's
 * installation ID to [PaisenMessagingService.onRegistered], which passes it to [PushTokenHolder].
 * The options come from `firebase.properties`, so there is no Google Services plugin or JSON file.
 * A phone without Google Play services gets no token and polls as before.
 */
private fun startPush(context: Context) {
    if (!FirebaseConfig.pushEnabled || FirebaseConfig.ANDROID_APP_ID.isEmpty() || FirebaseConfig.SENDER_ID.isEmpty()) return
    if (FirebaseApp.getApps(context).isEmpty()) {
        val options = FirebaseOptions.Builder()
            .setApplicationId(FirebaseConfig.ANDROID_APP_ID)
            .setApiKey(FirebaseConfig.API_KEY)
            .setProjectId(FirebaseConfig.PROJECT_ID)
            .setGcmSenderId(FirebaseConfig.SENDER_ID)
            .build()
        FirebaseApp.initializeApp(context, options)
    }
    FirebaseMessaging.getInstance().register()
}

/** A tapped notification's group, from `MainActivity`; the shell opens it once it has drawn. */
fun receivePushTap(intent: Intent) {
    intent.getStringExtra(EXTRA_GROUP_ID)?.let(GroupLinks::offer)
}

/**
 * Every push is data only, so the app decides: with a screen open it syncs at once and shows
 * nothing; otherwise it posts the sentence the Worker sent in this phone's language.
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
        val groupId = message.data[KEY_GROUP_ID] ?: return
        // A sign-out made offline leaves the device document behind: only the account it names hears it.
        if (!signedInAs(message.data[KEY_UID])) return
        if (PushEvents.offer(groupId)) return
        if (!notificationsAllowed()) return
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, AppText.copy.folders.sharedGroupsChannel, NotificationManager.IMPORTANCE_DEFAULT))
        val open = packageManager.getLaunchIntentForPackage(packageName)?.let { launch ->
            launch.putExtra(EXTRA_GROUP_ID, groupId)
            PendingIntent.getActivity(this, groupId.hashCode(), launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        val body = message.data[KEY_BODY].orEmpty()
        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(applicationInfo.icon)
            .setContentTitle(message.data[KEY_TITLE])
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        @Suppress("MissingPermission")
        NotificationManagerCompat.from(this).notify((message.messageId ?: "$groupId$body").hashCode(), notification)
    }
}

private fun tokens(): PushTokenHolder? = GlobalContext.getOrNull()?.getOrNull<PushTokenHolder>()

private fun signedInAs(uid: String?): Boolean {
    val account = GlobalContext.getOrNull()?.getOrNull<AuthRepository>()?.account?.value as? Account.SignedIn
    return uid != null && account?.uid == uid
}

private const val CHANNEL = "shared-groups"
private const val KEY_GROUP_ID = "groupId"
private const val KEY_UID = "uid"
private const val KEY_TITLE = "title"
private const val KEY_BODY = "body"
private const val EXTRA_GROUP_ID = "com.ptut.expense.PUSH_GROUP_ID"
