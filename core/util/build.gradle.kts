// Money, day labels and the other small types every layer shares, so :core:ui need not see :data.
plugins {
    id("ptut.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.locale)
        }
    }
}
