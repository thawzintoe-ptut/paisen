// The use cases: every rule, sum and multi-step write a screen needs, so a ViewModel only turns
// events into calls and results into state. Models, repository interfaces and pure functions stay
// in :data; the services sit beside it.
plugins {
    id("ptut.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.data)
            implementation(projects.core.util)
            implementation(projects.core.locale)
            implementation(projects.core.platform)
            implementation(projects.core.sync)
            implementation(projects.core.importer)
            implementation(projects.core.agent)
        }
    }
}
