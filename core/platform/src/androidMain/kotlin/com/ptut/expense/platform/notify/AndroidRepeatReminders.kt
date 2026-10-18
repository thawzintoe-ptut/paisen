package com.ptut.expense.platform.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.ptut.expense.data.local.PtutDatabaseContext
import com.ptut.expense.locale.AppText
import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.TimeUnit

/** #182 on Android: one WorkManager job per repeat, posting a local notification when it runs. */
private class AndroidRepeatReminders(private val context: Context) : RepeatReminders {

    override fun allowed(): Boolean = context.notificationsAllowed()

    override suspend fun replaceAll(plans: List<ReminderPlan>) {
        val work = WorkManager.getInstance(context)
        work.cancelAllWorkByTag(TAG)
        val now = System.currentTimeMillis()
        plans.filter { it.atEpochMillis > now }.forEach { plan ->
            work.enqueueUniqueWork(
                "$TAG-${plan.id}",
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<RepeatReminderWorker>()
                    .setInitialDelay(plan.atEpochMillis - now, TimeUnit.MILLISECONDS)
                    .setInputData(workDataOf(KEY_ID to plan.id, KEY_TITLE to plan.title, KEY_BODY to plan.body))
                    .addTag(TAG)
                    .build(),
            )
        }
    }
}

/** Posts one reminder. WorkManager builds it by reflection, so everything it needs is in its input. */
class RepeatReminderWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        if (!context.notificationsAllowed()) return Result.success()
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, AppText.copy.folders.repeatsChannel, NotificationManager.IMPORTANCE_DEFAULT))
        val open = context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
            PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_IMMUTABLE)
        }
        val id = inputData.getString(KEY_ID).orEmpty()
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(context.applicationInfo.icon)
            .setContentTitle(inputData.getString(KEY_TITLE))
            .setContentText(inputData.getString(KEY_BODY))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        @Suppress("MissingPermission")
        NotificationManagerCompat.from(context).notify(id.hashCode(), notification)
        return Result.success()
    }
}

fun Context.notificationsAllowed(): Boolean {
    val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    return granted && NotificationManagerCompat.from(this).areNotificationsEnabled()
}

actual fun repeatReminders(context: PtutDatabaseContext): RepeatReminders = AndroidRepeatReminders(context.applicationContext)

/** Android 13 and later ask; older versions only have the app-wide switch in system settings. */
@Composable
actual fun rememberNotificationAsk(): NotificationAsk {
    val context = LocalContext.current
    val pending = remember { arrayOfNulls<CompletableDeferred<Boolean>>(1) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        pending[0]?.complete(granted)
        pending[0] = null
    }
    return remember(context, launcher) {
        NotificationAsk {
            if (context.notificationsAllowed()) return@NotificationAsk true
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return@NotificationAsk false
            val answer = CompletableDeferred<Boolean>()
            pending[0] = answer
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            answer.await() && context.notificationsAllowed()
        }
    }
}

private const val TAG = "repeat-reminder"
private const val CHANNEL = "repeats"
private const val KEY_ID = "id"
private const val KEY_TITLE = "title"
private const val KEY_BODY = "body"
