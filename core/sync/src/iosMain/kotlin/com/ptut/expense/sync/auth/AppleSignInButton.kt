package com.ptut.expense.sync.auth

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import com.ptut.expense.design.SkySizes
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCAction
import platform.AuthenticationServices.ASAuthorizationAppleIDButton
import platform.AuthenticationServices.ASAuthorizationAppleIDButtonStyle
import platform.AuthenticationServices.ASAuthorizationAppleIDButtonTypeContinue
import platform.Foundation.NSSelectorFromString
import platform.UIKit.UIControlEventTouchUpInside
import platform.darwin.NSObject

/**
 * #198: Apple's own button, drawn by UIKit, so its logo, wording and sizes are Apple's. Black on
 * a light sheet and white on a dark one; the corners match the Sky pills beside it.
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun AppleSignInButton(onClick: () -> Unit, enabled: Boolean, modifier: Modifier) {
    val dark = isSystemInDarkTheme()
    val target = remember { TapTarget() }
    target.onTap = onClick
    // A button's style is fixed when it is made, so a theme change makes a new one.
    key(dark) {
        UIKitView(
            factory = {
                ASAuthorizationAppleIDButton(
                    authorizationButtonType = ASAuthorizationAppleIDButtonTypeContinue,
                    authorizationButtonStyle = if (dark) ASAuthorizationAppleIDButtonStyle.ASAuthorizationAppleIDButtonStyleWhite else ASAuthorizationAppleIDButtonStyle.ASAuthorizationAppleIDButtonStyleBlack,
                ).apply {
                    cornerRadius = SkySizes.button.value / 2.0
                    addTarget(target, action = NSSelectorFromString("tap"), forControlEvents = UIControlEventTouchUpInside)
                }
            },
            update = { it.enabled = enabled },
            modifier = modifier.height(SkySizes.button),
        )
    }
}

@OptIn(BetaInteropApi::class)
private class TapTarget : NSObject() {
    var onTap: () -> Unit = {}

    @ObjCAction
    fun tap() = onTap()
}
