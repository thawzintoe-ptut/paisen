// Shared groups (#180–#191).
plugins {
    id("ptut.kmp.feature")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.sync)
            implementation(libs.qrose)
        }
    }
}
