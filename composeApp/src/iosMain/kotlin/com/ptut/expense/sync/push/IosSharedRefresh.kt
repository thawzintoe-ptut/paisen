package com.ptut.expense.sync.push

import com.ptut.expense.domain.shared.SharedGroup
import com.ptut.expense.domain.shared.SharedRepository
import com.ptut.expense.domain.shared.SharedSnapshot
import com.ptut.expense.graphStarted
import com.ptut.expense.locale.AppText
import com.ptut.expense.shared.displayName
import com.ptut.expense.sync.SharedSync
import com.ptut.expense.sync.auth.Account
import com.ptut.expense.sync.auth.AuthRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.mp.KoinPlatform
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNUserNotificationCenter

/*
 * The free stand-in for push on iOS, until the owner has an APNs key: iOS wakes the app now and
 * then (BGAppRefreshTask, Swift's PushDelegate), the app pulls its shared groups, and what other
 * members added since says so as a local notification. iOS decides when; it can be hours.
 */

private var running: Job? = null

/**
 * One background refresh. [done] is called once, whatever happens, so Swift can tell iOS the task
 * finished. Nothing runs once `iosPushReady` is set: real pushes then say the same thing at once.
 */
fun runSharedRefresh(done: () -> Unit) {
    graphStarted
    val koin = KoinPlatform.getKoin()
    val sync = koin.getOrNull<SharedSync>()
    val repository = koin.getOrNull<SharedRepository>()
    val account = koin.getOrNull<AuthRepository>()?.account?.value as? Account.SignedIn
    if (iosPushReady || sync == null || repository == null || account == null || !sync.available) {
        done()
        return
    }
    running = CoroutineScope(Dispatchers.Default).launch {
        try {
            // A cold start in the background has not read Room yet; Empty is the value before it has.
            val before = repository.snapshot.first { it !== SharedSnapshot.Empty }
            runCatching { sync.syncAll() }
            val after = repository.snapshot.value
            notifyNew(before, after, account.uid)
        } finally {
            done()
        }
    }
}

/** iOS is taking the time back; what has been posted stays posted. */
fun cancelSharedRefresh() {
    running?.cancel()
    running = null
}

/** Rows the pull brought in, written by someone else, in groups this phone already held. */
private fun notifyNew(before: SharedSnapshot, after: SharedSnapshot, uid: String) {
    val known = before.entryIds()
    after.groups.filter { group -> before.group(group.id) != null }.forEach { group ->
        val fresh = after.newIn(group, known)
        when {
            fresh.isEmpty() -> Unit
            fresh.size > SINGLE_NOTICES -> post(group, uid, "summary-${group.id}", summary(group, fresh.map { it.author }))
            else -> fresh.forEach { post(group, uid, "shared-${it.id}", it.sentence) }
        }
    }
}

private class Fresh(val id: String, val author: String, val sentence: String)

private fun SharedSnapshot.entryIds(): Set<String> =
    expenseVersions.map { it.versionId }.toSet() + payments.map { it.id } + paymentRemovals.map { it.id }

private fun SharedSnapshot.newIn(group: SharedGroup, known: Set<String>): List<Fresh> {
    val language = AppText.language.value
    fun notices(author: String) = PushNotices(group.name, group.displayName(author), group.members)
    fun String?.other(): String? = this?.takeIf { !group.isMe(it) }
    val versions = expenseVersions.filter { it.groupId == group.id && it.versionId !in known }.mapNotNull { version ->
        version.authorMemberId.other()?.let { Fresh(version.versionId, it, notices(it).expense(version, expenseVersions).body(language)) }
    }
    val paid = payments.filter { it.groupId == group.id && it.id !in known }.mapNotNull { payment ->
        payment.authorMemberId.other()?.let { Fresh(payment.id, it, notices(it).payment(payment).body(language)) }
    }
    val removals = paymentRemovals.filter { it.groupId == group.id && it.id !in known }.mapNotNull { removal ->
        removal.authorMemberId.other()?.let { Fresh(removal.id, it, notices(it).paymentRemoval(removal).body(language)) }
    }
    return versions + paid + removals
}

/** "Mali added 5", or "5 new" when several members did. */
private fun summary(group: SharedGroup, authors: List<String>): String {
    val text = AppText.copy.shared.home
    return authors.distinct().singleOrNull()?.let { text.addedSince(group.displayName(it), authors.size) } ?: text.newSince(authors.size)
}

/** Delivered at once. The tap opens the group through the same path a push takes ([receivePushTap]). */
private fun post(group: SharedGroup, uid: String, id: String, body: String) {
    val content = UNMutableNotificationContent().apply {
        setTitle(group.name)
        setBody(body)
        setSound(UNNotificationSound.defaultSound())
        setUserInfo(mapOf<Any?, Any?>("groupId" to group.id, "uid" to uid))
    }
    UNUserNotificationCenter.currentNotificationCenter()
        .addNotificationRequest(UNNotificationRequest.requestWithIdentifier(id, content, trigger = null), withCompletionHandler = null)
}

/** Above this many new rows in one group, one line says how many instead of one notice each. */
private const val SINGLE_NOTICES = 3
