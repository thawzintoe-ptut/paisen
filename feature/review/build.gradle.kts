// The slip review queue, confirming a slip, shop rules and the debug slip screen.
plugins {
    id("ptut.kmp.feature")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.importer)
        }
    }
}
