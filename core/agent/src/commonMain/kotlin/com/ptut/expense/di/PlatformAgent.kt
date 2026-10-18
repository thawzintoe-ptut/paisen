package com.ptut.expense.di

import com.ptut.expense.agent.LanguageModelPort
import com.ptut.expense.agent.SpeechInput
import com.ptut.expense.data.local.PtutDatabaseContext

/**
 * #207 and #208: LiteRT-LM on Android, the same model through a Swift bridge on iOS. Each side
 * says what it can do; neither carries the model pack yet, so both report it as not offered.
 */
expect fun languageModel(): LanguageModelPort

/** #212: on-device speech for Quick add. Android needs a `Context`; iOS reaches it through a Swift bridge. */
expect fun speechInput(context: PtutDatabaseContext): SpeechInput
