package com.ptut.expense.di

import com.ptut.expense.agent.AndroidSpeechInput
import com.ptut.expense.agent.LanguageModelPort
import com.ptut.expense.agent.SpeechInput
import com.ptut.expense.agent.UnavailableLanguageModel
import com.ptut.expense.data.local.PtutDatabaseContext

/** #207: LiteRT-LM and the Play AI pack arrive once #205's eval numbers confirm FunctionGemma. */
actual fun languageModel(): LanguageModelPort = UnavailableLanguageModel()

/** #212: the Activity registers the microphone dialog against this, as it does the photo one. */
actual fun speechInput(context: PtutDatabaseContext): SpeechInput = AndroidSpeechInput(context)
