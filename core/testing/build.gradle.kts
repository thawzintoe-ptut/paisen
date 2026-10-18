// Fakes and test helpers that tests in more than one module share. Never a dependency of production code.
plugins {
    id("ptut.kmp.compose")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.data)
            implementation(projects.core.importer)
            implementation(projects.core.util)
            implementation(projects.core.locale)
            implementation(projects.core.platform)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.kotlin.test)
        }
        androidMain.dependencies {
            // @BeforeTest and @AfterTest resolve to JUnit on the JVM; a main source set does not infer it.
            implementation(kotlin("test-junit"))
        }
    }
}

// These fakes were test sources until the module split, and some carry recorded slip data, so the
// account-number scan keeps reading them.
tasks.named<com.ptut.gradle.VerifyNoAccountNumbers>("verifyNoAccountNumbers") {
    sources.from(fileTree(layout.projectDirectory.dir("src")) { include("commonMain/**/*.kt") })
}
