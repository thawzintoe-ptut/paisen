// The slip pipeline: photo sources, on-device OCR (ML Kit on Android, Vision on iOS) and import.
plugins {
    id("ptut.kmp.compose")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.data)
            implementation(projects.core.ui)
            implementation(projects.core.util)
            implementation(projects.core.locale)
            implementation(projects.core.platform)
        }
        androidMain.dependencies {
            implementation(libs.mlkit.text.recognition)
        }
    }
}
