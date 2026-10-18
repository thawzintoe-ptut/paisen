package com.ptut.expense.platform.notify

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.ptut.expense.data.local.PtutDatabaseContext
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNTimeIntervalNotificationTrigger
import platform.UserNotifications.UNUserNotificationCenter
import kotlin.coroutines.resume
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.DurationUnit

/**
 * #182 on iOS: one pending local notification per repeat. Without permission iOS keeps them and
 * shows nothing, so scheduling does not need to ask first.
 */
private class IosRepeatReminders : RepeatReminders {

    override fun allowed(): Boolean = true

    override suspend fun replaceAll(plans: List<ReminderPlan>) {
        val center = UNUserNotificationCenter.currentNotificationCenter()
        center.removeAllPendingNotificationRequests()
        val now = Clock.System.now().toEpochMilliseconds()
        plans.filter { it.atEpochMillis > now }.forEach { plan ->
            val content = UNMutableNotificationContent().apply {
                setTitle(plan.title)
                setBody(plan.body)
                setSound(UNNotificationSound.defaultSound())
            }
            val wait = (plan.atEpochMillis - now).milliseconds.toDouble(DurationUnit.SECONDS)
            val trigger = UNTimeIntervalNotificationTrigger.triggerWithTimeInterval(wait, repeats = false)
            center.addNotificationRequest(UNNotificationRequest.requestWithIdentifier("repeat-${plan.id}", content, trigger), withCompletionHandler = null)
        }
    }
}

actual fun repeatReminders(context: PtutDatabaseContext): RepeatReminders = IosRepeatReminders()

@Composable
actual fun rememberNotificationAsk(): NotificationAsk = remember {
    NotificationAsk {
        suspendCancellableCoroutine { continuation ->
            UNUserNotificationCenter.currentNotificationCenter().requestAuthorizationWithOptions(
                UNAuthorizationOptionAlert or UNAuthorizationOptionSound,
            ) { granted, _ -> continuation.resume(granted) }
        }
    }
}
