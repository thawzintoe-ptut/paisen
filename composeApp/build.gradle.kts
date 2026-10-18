import com.ptut.gradle.VerifyNoNetworkDependencies
import com.ptut.gradle.VerifyNoNetworkSymbols
import com.ptut.gradle.moduleCoordinates
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType

// The app module, as in android/architecture-samples: it owns navigation and dependency
// injection, depends on every feature, and is the only module that builds the iOS framework.
plugins {
    id("ptut.kmp.compose")
}

kotlin {
    android {
        namespace = "com.ptut.expense.shared"
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
            binaryOption("bundleId", "com.ptut.expense.shared")
            // Swift calls IosSpeechBridgeKt, SpendWindow and SpendPeriod (agent) and IosAuthBridgeKt,
            // IosPushKt and IosSharedRefreshKt (sync). Only exported modules keep those names.
            export(projects.core.agent)
            export(projects.core.sync)
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(projects.core.agent)
            api(projects.core.sync)

            implementation(projects.data)
            implementation(projects.core.util)
            implementation(projects.core.locale)
            implementation(projects.core.platform)
            implementation(projects.core.ui)
            implementation(projects.core.importer)

            implementation(projects.feature.today)
            implementation(projects.feature.ledger)
            implementation(projects.feature.reports)
            implementation(projects.feature.record)
            implementation(projects.feature.add)
            implementation(projects.feature.review)
            implementation(projects.feature.shared)
            implementation(projects.feature.settings)
            implementation(projects.feature.categories)
            implementation(projects.feature.onboarding)

            implementation(libs.androidx.navigation.compose)
            // The Koin graph builds the database and hands out its DAOs.
            implementation(libs.room.runtime)
        }

        androidMain.dependencies {
            implementation(libs.koin.android)
            implementation(libs.androidx.work.runtime)
            implementation(libs.androidx.material3.adaptive)
        }
    }
}

/**
 * NFR-1 and NFR-2, read off the classpaths a device actually gets: the Android runtime, and both
 * iOS klib sets. The Android app module gates its own runtime classpath, which is where a
 * dependency declared there rather than here would show up.
 */
val verifyNoNetworkDependencies =
    tasks.register<VerifyNoNetworkDependencies>("verifyNoNetworkDependencies") {
        group = "verification"
        description = "Fails when a resolved classpath carries an HTTP client or a telemetry SDK."
        listOf(
            "androidRuntimeClasspath",
            "iosArm64CompileKlibraries",
            "iosSimulatorArm64CompileKlibraries",
        ).forEach { name ->
            classpaths.put(name, configurations.getByName(name).moduleCoordinates())
        }
        receipt.set(layout.buildDirectory.file("reports/verifyNoNetworkDependencies/scanned.txt"))
    }

tasks.named("check") { dependsOn(verifyNoNetworkDependencies) }

/**
 * NFR-1 on iOS, read off the linked release framework (#132). Not part of `check` or pre-push:
 * a release link takes minutes, so CI's iOS job runs it after the framework is built.
 */
tasks.register<VerifyNoNetworkSymbols>("verifyNoNetworkSymbols") {
    group = "verification"
    description = "Fails when the linked iOS framework references Apple's networking APIs."
    val framework = kotlin.iosSimulatorArm64().binaries.getFramework(NativeBuildType.RELEASE)
    dependsOn(framework.linkTaskProvider)
    binary.set(layout.file(provider { framework.outputFile.resolve(framework.baseName) }))
    receipt.set(layout.buildDirectory.file("reports/verifyNoNetworkSymbols/undefined.txt"))
}
