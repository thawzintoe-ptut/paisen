// The platform context and the small OS services: files, notifications, sharing, background work.
plugins {
    id("ptut.kmp.compose")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.locale)
        }
        androidMain.dependencies {
            implementation(libs.androidx.work.runtime)
        }
    }
}
