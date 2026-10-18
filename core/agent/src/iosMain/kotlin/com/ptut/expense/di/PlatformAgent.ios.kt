package com.ptut.expense.di

import com.ptut.expense.agent.IosLanguageModel
import com.ptut.expense.agent.IosSpeechInput
import com.ptut.expense.agent.LanguageModelPort
import com.ptut.expense.agent.SpeechInput
import com.ptut.expense.data.local.PtutDatabaseContext

/** #208: Swift owns LiteRT-LM's API; Kotlin reaches it through the bridge `iOSApp` installs. */
actual fun languageModel(): LanguageModelPort = IosLanguageModel()

/** #212: Swift owns `SFSpeechRecognizer`; Kotlin reaches it through the bridge `iOSApp` installs. */
actual fun speechInput(context: PtutDatabaseContext): SpeechInput = IosSpeechInput()
