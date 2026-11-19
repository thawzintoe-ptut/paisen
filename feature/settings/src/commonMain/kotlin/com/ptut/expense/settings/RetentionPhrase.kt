package com.ptut.expense.settings

import com.ptut.expense.locale.AppText
import kotlin.time.Duration

/** How long a kept slip lives, in the words every screen says it in. One place, so none drift. */
fun retentionPhrase(retention: Duration): String = AppText.copy.phrases.days(retention.inWholeDays)
