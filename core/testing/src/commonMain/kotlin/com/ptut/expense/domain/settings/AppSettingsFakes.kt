package com.ptut.expense.domain.settings

/** A store in memory, counting its writes so a test can hold the holder to writing once. */
class FakeAppSettingsStore(private var held: AppChoices = AppChoices()) : AppSettingsStore {

    var writes = 0
        private set

    override fun read(): AppChoices = held

    override fun write(choices: AppChoices) {
        held = choices
        writes++
    }
}

/** The holder over a store nobody else is watching, which is what every screen test wants. */
fun appSettings(choices: AppChoices = AppChoices()): AppSettings =
    AppSettings(FakeAppSettingsStore(choices))
