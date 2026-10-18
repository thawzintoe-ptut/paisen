// The Android, Kotlin, Compose, KSP and Room plugins reach every module through buildSrc, whose
// convention plugins apply them by id. Requesting them here with a version would fail resolution.
plugins {
    alias(libs.plugins.spotless)
}

// #22: style is checked in CI, never in a git hook. The rules ktlint cannot fix are off in .editorconfig.
// ktlint caches .editorconfig in the Gradle daemon: after editing it, run ./gradlew --stop first.
spotless {
    kotlin {
        target("**/src/**/*.kt")
        targetExclude("**/build/**")
        ktlint(libs.versions.ktlint.get()).setEditorConfigPath(rootProject.file(".editorconfig"))
    }
    kotlinGradle {
        target("*.gradle.kts", "*/*.gradle.kts", "*/*/*.gradle.kts", "buildSrc/src/main/kotlin/*.gradle.kts")
        ktlint(libs.versions.ktlint.get()).setEditorConfigPath(rootProject.file(".editorconfig"))
    }
}
