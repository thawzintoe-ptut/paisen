package com.ptut.expense.design

import androidx.compose.runtime.Composable

/**
 * Whether this device draws the nav pill as frosted glass (#123). Both platforms blur with Compose's
 * `BlurEffect`, so the pill stays one common composable.
 *
 * Android blurs through `RenderEffect` from 12 (API 31). Below that the effect draws nothing and the
 * glass would be clear, so the pill stays solid. Android has no Reduce Transparency setting.
 *
 * iOS blurs with Skia rather than a `UIVisualEffectView`, which would need a UIKit view under the
 * whole NavHost. It stays solid while Settings › Accessibility › Reduce Transparency is on.
 */
@Composable
expect fun systemAllowsFrost(): Boolean
