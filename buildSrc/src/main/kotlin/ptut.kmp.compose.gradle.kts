import org.jetbrains.compose.ExperimentalComposeLibrary

// A shared module that draws, or reads Compose resources.
plugins {
    id("ptut.kmp.library")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

kotlin {
    android {
        // Compose packs fonts and files into Android assets only when this is on. Without it the
        // APK carries no bundled font, and Android draws the system face for every one, silently.
        if (file("src/commonMain/composeResources").exists()) {
            androidResources {
                enable = true
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(libs.findLibrary("androidx-lifecycle-viewmodel-compose").get())
            implementation(libs.findLibrary("androidx-lifecycle-runtime-compose").get())
            implementation(libs.findLibrary("compose-ui-backhandler").get())
            implementation(libs.findLibrary("koin-compose").get())
            implementation(libs.findLibrary("koin-compose-viewmodel").get())
        }
        androidMain.dependencies {
            // Brings androidx.core and androidx.activity, which the Android sources call directly.
            implementation(libs.findLibrary("androidx-activity-compose").get())
        }
        commonTest.dependencies {
            @OptIn(ExperimentalComposeLibrary::class)
            implementation(compose.uiTest)
        }
    }
}
