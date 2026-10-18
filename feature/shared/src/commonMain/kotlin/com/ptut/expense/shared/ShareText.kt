package com.ptut.expense.shared

import androidx.compose.runtime.Composable

/** The platform's own share sheet for a line of text: an invite link going to LINE (#190). */
@Composable
expect fun rememberShareText(): (String) -> Unit
