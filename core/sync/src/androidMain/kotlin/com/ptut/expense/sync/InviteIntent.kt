package com.ptut.expense.sync

import android.content.Intent

/** #190: an App Link to `/join/<code>` opens the app; the shell picks the code up from here. */
fun receiveInviteLink(intent: Intent) {
    if (intent.action != Intent.ACTION_VIEW) return
    intent.dataString?.let(InviteLinks::offer)
}
