// The add sheet, typed or spoken (#212).
plugins {
    id("ptut.kmp.feature")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.agent)
        }
    }
}
