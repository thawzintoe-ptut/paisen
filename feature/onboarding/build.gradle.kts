// First run.
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
