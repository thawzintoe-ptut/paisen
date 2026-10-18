import com.android.build.api.artifact.SingleArtifact
import com.ptut.gradle.VerifyBundledResources
import com.ptut.gradle.VerifyNoNetworkDependencies
import com.ptut.gradle.VerifyNoTelemetryUpload
import com.ptut.gradle.moduleCoordinates
import java.io.StringReader
import java.util.Properties

// AGP 9 applies Kotlin itself (android.builtInKotlin), so this module must not declare
// org.jetbrains.kotlin.android or the compose compiler plugin — both are already on the
// classpath and re-requesting them with a version fails resolution.
plugins {
    // By id, not alias: buildSrc already put these on the classpath with a version, and
    // re-requesting a version here fails resolution.
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    // #213: generates the debug build's AppFunctions service and its XML.
    id("com.google.devtools.ksp")
}

val linkDomain: String = providers.fileContents(rootProject.layout.projectDirectory.file("firebase.properties")).asText
    .map { text -> Properties().apply { load(StringReader(text)) }.getProperty("linkDomain").orEmpty() }
    .orElse("")
    .get()
    .ifBlank { "paisen.invalid" }

// A release tag passes its version as -PpaisenVersionName and -PpaisenVersionCode. Without them,
// every build keeps the values in defaultConfig.
val tagVersionName = providers.gradleProperty("paisenVersionName")
val tagVersionCode = providers.gradleProperty("paisenVersionCode").map(String::toInt)

// The upload key comes from the `release` environment as variables, never on the command line.
// Without PAISEN_UPLOAD_STORE_FILE a release build stays unsigned, as before.
val uploadStoreFile = providers.environmentVariable("PAISEN_UPLOAD_STORE_FILE")
fun uploadSecret(name: String): String =
    providers.environmentVariable(name).orNull ?: error("$name is not set, but PAISEN_UPLOAD_STORE_FILE is")

android {
    namespace = "com.ptut.expense"
    compileSdk = libs.versions.androidCompileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.ptut.expense"
        minSdk = libs.versions.androidMinSdk.get().toInt()
        targetSdk = libs.versions.androidTargetSdk.get().toInt()
        versionCode = tagVersionCode.getOrElse(1)
        versionName = tagVersionName.getOrElse("0.1.0")
        // #190: the invite links' host. Without firebase.properties it is a name that never resolves.
        manifestPlaceholders["paisenLinkDomain"] = linkDomain
    }

    signingConfigs {
        if (uploadStoreFile.isPresent) {
            create("upload") {
                storeFile = file(uploadStoreFile.get())
                storePassword = uploadSecret("PAISEN_UPLOAD_STORE_PASSWORD")
                keyAlias = uploadSecret("PAISEN_UPLOAD_KEY_ALIAS")
                keyPassword = uploadSecret("PAISEN_UPLOAD_KEY_PASSWORD")
            }
        }
    }

    buildFeatures {
        compose = true
        // For BuildConfig.DEBUG. It is the one fact common code cannot work out for itself, and
        // it decides whether the graph binds the injectable slip source at all.
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("upload")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(projects.composeApp)
    implementation(libs.androidx.activity.compose)

    // #213: Gemini's actions, debug only until Google's Early Access Program says yes. The source
    // and the service live in src/debug, so a release build is unchanged.
    debugImplementation(libs.androidx.appfunctions)
    add("kspDebug", libs.androidx.appfunctions.compiler)
}

// The app module gathers every AppFunction into the XML the system indexes.
ksp { arg("appfunctions:aggregateAppFunctions", "true") }

/**
 * NFR-1, per variant, because release is what ships and debug is what everyone runs.
 *
 * Two lifecycle tasks so the hook and the CI job have one stable name each to call, rather than
 * one that changes the day a build type is added.
 */
val verifyNoTelemetryUpload = tasks.register("verifyNoTelemetryUpload") {
    group = "verification"
    description = "Fails when a merged manifest keeps a telemetry upload backend. #191: only shared groups use the network."
}

val verifyNoNetworkDependencies = tasks.register("verifyNoNetworkDependencies") {
    group = "verification"
    description = "Fails when a runtime classpath carries an HTTP client or a telemetry SDK."
}

val verifyBundledResources = tasks.register("verifyBundledResources") {
    group = "verification"
    description = "Fails when a shared composeResources file is missing from an app variant's assets."
}

androidComponents {
    onVariants { variant ->
        val suffix = variant.name.replaceFirstChar(Char::uppercase)

        val telemetry = tasks.register<VerifyNoTelemetryUpload>("verifyNoTelemetryUpload$suffix") {
            group = "verification"
            description = "Fails when the merged ${variant.name} manifest keeps a telemetry upload backend."
            mergedManifest.set(variant.artifacts.get(SingleArtifact.MERGED_MANIFEST))
            receipt.set(layout.buildDirectory.file("reports/verifyNoTelemetryUpload/${variant.name}.txt"))
        }
        verifyNoTelemetryUpload.configure { dependsOn(telemetry) }

        val dependencies = tasks.register<VerifyNoNetworkDependencies>("verifyNoNetworkDependencies$suffix") {
            group = "verification"
            description = "Fails when the ${variant.name} runtime classpath carries an HTTP client."
            val runtime = variant.runtimeConfiguration
            classpaths.put(runtime.name, runtime.moduleCoordinates())
            receipt.set(layout.buildDirectory.file("reports/verifyNoNetworkDependencies/${variant.name}.txt"))
        }
        verifyNoNetworkDependencies.configure { dependsOn(dependencies) }

        // One check per module that keeps composeResources: the fonts in :core:ui, the Zawgyi model in :core:agent.
        listOf("core/ui", "core/agent").forEach { module ->
            val name = module.substringAfterLast('/').replaceFirstChar(Char::uppercase)
            val resources = tasks.register<VerifyBundledResources>("verifyBundledResources$suffix$name") {
                group = "verification"
                description = "Fails when the merged ${variant.name} assets lack a $module composeResources file."
                resourceRoot.set(rootProject.layout.projectDirectory.dir("$module/src/commonMain/composeResources"))
                mergedAssets.set(variant.artifacts.get(SingleArtifact.ASSETS))
                receipt.set(layout.buildDirectory.file("reports/verifyBundledResources/${variant.name}-$name.txt"))
            }
            verifyBundledResources.configure { dependsOn(resources) }
        }
    }
}

tasks.named("check") { dependsOn(verifyNoTelemetryUpload, verifyNoNetworkDependencies, verifyBundledResources) }
