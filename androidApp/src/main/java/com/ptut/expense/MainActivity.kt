package com.ptut.expense

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.ptut.expense.agent.receiveAgentShortcut
import com.ptut.expense.sync.push.receivePushTap
import com.ptut.expense.sync.receiveInviteLink

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Transparent bars so the app surface runs behind them, matching the design where the
        // status bar sits on the surface colour. auto() flips icon contrast with the theme.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent { PtutAndroidApp() }
        receiveSharedSlip(intent)
        // A restored Activity holds the same intent; its back stack already holds the join screen.
        if (savedInstanceState == null) {
            receiveInviteLink(intent)
            receiveAgentShortcut(intent)
            receivePushTap(intent)
        }
    }

    /** FR-1, #190 and #206 again, for a share, an invite, a shortcut or a tapped push into an app that was already open. `singleTop` routes it here. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        receiveSharedSlip(intent)
        receiveInviteLink(intent)
        receiveAgentShortcut(intent)
        receivePushTap(intent)
    }
}
