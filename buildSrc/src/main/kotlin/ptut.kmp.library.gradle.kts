import com.ptut.gradle.VerifyNativeNames
import com.ptut.gradle.VerifyNoAccountNumbers
import com.ptut.gradle.VerifySourceRules
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Every shared module: Android plus the two iOS targets, the same SDK levels and JVM target, the
// coroutine and date libraries, and the source gates that used to run on the single shared module.
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
    id("org.jetbrains.kotlin.plugin.serialization")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

kotlin {
    android {
        namespace = "com.ptut.expense." + path.removePrefix(":").replace(':', '.')
        compileSdk = libs.findVersion("androidCompileSdk").get().requiredVersion.toInt()
        minSdk = libs.findVersion("androidMinSdk").get().requiredVersion.toInt()

        // Without this the target creates no JVM test task, so commonTest could only run
        // through the iOS simulator.
        withHostTest {}

        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    // No iosX64: androidx.sqlite:sqlite-bundled publishes no Intel-simulator artifact.
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.findLibrary("kotlinx-coroutines-core").get())
            implementation(libs.findLibrary("kotlinx-datetime").get())
            implementation(libs.findLibrary("kotlinx-serialization-json").get())
            implementation(libs.findLibrary("koin-core").get())
            implementation(libs.findLibrary("okio").get())
        }
        commonTest.dependencies {
            // The shared fakes. :core:testing's own tests would depend on themselves.
            if (path != ":core:testing") implementation(project(":core:testing"))
            implementation(libs.findLibrary("kotlin-test").get())
            implementation(libs.findLibrary("kotlinx-coroutines-test").get())
            implementation(libs.findLibrary("turbine").get())
        }
    }
}

// #191: only :core:sync may name Ktor. Declared with `implementation` there, it never reaches
// another module's compile classpath, so no other module can compile an `io.ktor` import.
configurations.configureEach {
    dependencies.withType<ExternalDependency>().configureEach {
        if (group == "io.ktor" && project.path != ":core:sync") {
            throw GradleException("${project.path} declares $group:$name. Only :core:sync may use Ktor (#191).")
        }
    }
}

val sourceDir = layout.projectDirectory.dir("src")

val verifySourceRules = tasks.register<VerifySourceRules>("verifySourceRules") {
    group = "verification"
    description = "Fails on source patterns that break exact money, Bangkok days or the theme layer."
    // Only the *Main source sets. A fixture may hold a raw Long, and a test that says so is
    // not the leak this guards against.
    sources.from(fileTree(sourceDir) { include("*Main/**/*.kt") })
    sourceRoot.set(sourceDir)
    receipt.set(layout.buildDirectory.file("reports/verifySourceRules/scanned.txt"))
}

// The other half of the slip-image ban in `.githooks/pre-commit`: it keeps the digits out of the
// transcripts that replace the pictures.
val verifyNoAccountNumbers = tasks.register<VerifyNoAccountNumbers>("verifyNoAccountNumbers") {
    group = "verification"
    description = "Fails when a test fixture carries something shaped like an account number."
    sources.from(
        fileTree(sourceDir) {
            include("*Test/**/*.kt", "*Test/**/*.json", "*Test/**/*.txt", "*Test/**/*.md")
        },
    )
    sourceRoot.set(sourceDir)
    receipt.set(layout.buildDirectory.file("reports/verifyNoAccountNumbers/scanned.txt"))
}

// Scans the source sets the Kotlin/Native compiler reads. androidMain and androidHostTest only
// ever reach the JVM, where every one of these characters is legal.
val verifyNativeNames = tasks.register<VerifyNativeNames>("verifyNativeNames") {
    group = "verification"
    description = "Fails on a backtick name the iOS target would reject."
    sources.from(fileTree(sourceDir) { include("common*/**/*.kt", "ios*/**/*.kt") })
    sourceRoot.set(sourceDir)
    receipt.set(layout.buildDirectory.file("reports/verifyNativeNames/scanned.txt"))
}

tasks.named("check") { dependsOn(verifySourceRules, verifyNoAccountNumbers, verifyNativeNames) }
