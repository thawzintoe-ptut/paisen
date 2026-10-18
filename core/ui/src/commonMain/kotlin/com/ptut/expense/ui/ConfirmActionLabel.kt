package com.ptut.expense.ui

/**
 * What a header's confirm action is called on this platform.
 *
 * Android names the action and iOS names the end of the task, which is what #53 asks the edit
 * screen to follow: a top-app-bar Save on one, a navigation-bar Done on the other, under the same
 * rule about when it lights.
 *
 * Copy beside the platform rather than a flag a shared screen branches on, the same shape the
 * slip-import cadence uses. Only a header action reads it. The add sheet's Save is a button in a
 * sheet rather than a bar item, and "Done" would be wrong there on both platforms.
 */
expect val confirmActionLabel: String
