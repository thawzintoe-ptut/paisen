package com.ptut.expense.agent

import android.content.Context
import android.content.Intent
import com.ptut.expense.onboarding.Onboarding
import com.ptut.expense.platform.work.scanForSlipsNow
import org.koin.core.context.GlobalContext

/**
 * #206: the icon's two shortcuts, and the App Actions feature that names the same two
 * (`res/xml/shortcuts.xml` in androidApp). Each leaves an action in [AgentInbox] for the shell.
 *
 * Scan slips also asks for a run now: an app that was already open scanned when it opened, not
 * since. Before first run is answered there is no access to scan with, so it only opens the queue.
 */
fun Context.receiveAgentShortcut(intent: Intent) {
    val feature = intent.getStringExtra(EXTRA_FEATURE)
    when {
        intent.action == ACTION_ADD_EXPENSE || feature == FEATURE_ADD_EXPENSE -> AgentInbox.offer(ProposedAction.AddTransaction())

        intent.action == ACTION_SCAN_SLIPS || feature == FEATURE_SCAN_SLIPS -> {
            if (GlobalContext.get().get<Onboarding>().isFinished.value) scanForSlipsNow()
            AgentInbox.offer(ProposedAction.ScanSlips)
        }
    }
}

private const val ACTION_ADD_EXPENSE = "com.ptut.expense.action.ADD_EXPENSE"
private const val ACTION_SCAN_SLIPS = "com.ptut.expense.action.SCAN_SLIPS"

/** App Actions passes the matched shortcut's id as `feature` (inline inventory). */
private const val EXTRA_FEATURE = "feature"
private const val FEATURE_ADD_EXPENSE = "add_expense"
private const val FEATURE_SCAN_SLIPS = "scan_slips"
