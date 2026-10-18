package com.ptut.expense.domain.settings

/** A store in memory, counting its writes so a test can hold the holder to writing once. */
class FakeImportSettingsStore(private var held: ImportChoices = ImportChoices()) : ImportSettingsStore {

    var writes = 0
        private set

    override fun read(): ImportChoices = held

    override fun write(choices: ImportChoices) {
        held = choices
        writes++
    }
}

/** The holder over a store nobody else is watching, which is what every screen test wants. */
fun importSettings(choices: ImportChoices = ImportChoices()): ImportSettings =
    ImportSettings(FakeImportSettingsStore(choices))
