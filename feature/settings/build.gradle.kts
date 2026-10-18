// Settings and every screen it opens, including photo folders.
plugins {
    id("ptut.kmp.feature")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.importer)
            implementation(projects.core.agent)
        }
    }
}
