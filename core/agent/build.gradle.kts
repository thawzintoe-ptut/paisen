// #207: the assistant. Grammar first, then the on-device model; speech input on both platforms.
plugins {
    id("ptut.kmp.compose")
}

// Fixed, and different from :core:ui's: two modules cannot share one Res package.
compose.resources {
    packageOfResClass = "com.ptut.expense.agent.resources"
    generateResClass = always
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.data)
            implementation(projects.core.util)
            implementation(projects.core.locale)
            implementation(projects.core.platform)
        }
    }
}
