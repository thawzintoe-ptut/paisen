package com.ptut.expense.sync.push

import com.ptut.expense.graphStarted
import com.ptut.expense.sync.auth.Account
import com.ptut.expense.sync.auth.AuthRepository
import org.koin.mp.KoinPlatform

/*
 * What Swift's `PushDelegate` hands over: the FCM token and the group a push names. Firebase
 * Messaging lives in the Swift target only, so the framework gains no SDK and no network symbol.
 */

/**
 * The token Firebase gave this iPhone, kept for `PushRegistrar`. Firebase can call this before the
 * first frame, so the graph is started here as `receiveSharedSlip` does, and the token is not lost.
 */
fun receivePushToken(token: String) {
    graphStarted
    KoinPlatform.getKoin().getOrNull<PushTokenHolder>()?.set(token)
}

/** A tapped notification's group; it waits in [GroupLinks] until the shell has drawn, as an invite link does. */
fun receivePushTap(groupId: String, uid: String?) {
    if (signedInAs(uid)) GroupLinks.offer(groupId)
}

/** A push that arrived with the app open: no banner, the shell syncs the group at once. */
fun receivePushInForeground(groupId: String, uid: String?) {
    if (signedInAs(uid)) PushEvents.offer(groupId)
}

/**
 * A sign-out made offline leaves the device document behind, and iOS still shows that banner; only
 * the account [uid] names syncs for it or opens its group. A tap can arrive before the first frame.
 */
private fun signedInAs(uid: String?): Boolean {
    graphStarted
    val account = KoinPlatform.getKoin().getOrNull<AuthRepository>()?.account?.value as? Account.SignedIn
    return uid != null && account?.uid == uid
}
