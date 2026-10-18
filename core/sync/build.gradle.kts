import java.io.StringReader
import java.util.Properties

// #187–#191: shared groups over Firebase's REST APIs, sign-in and push. The only module that may
// use the network: Ktor is declared here with `implementation` and nowhere else.
plugins {
    id("ptut.kmp.compose")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.data)
            implementation(projects.core.util)
            implementation(projects.core.locale)
            implementation(projects.core.platform)
            implementation(libs.ktor.client.core)
            // SharedSync reads and writes the shared-group tables through PtutDatabase directly.
            implementation(libs.room.runtime)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.android)
            implementation(libs.androidx.credentials)
            implementation(libs.androidx.credentials.play.services)
            implementation(libs.google.id)
            implementation(libs.firebase.messaging)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
            // The Apple sign-in button draws at a Sky size.
            implementation(projects.core.ui)
        }
    }
}

/**
 * #188: the Firebase project's public settings, read from `firebase.properties` at the repo root.
 * That file is gitignored and optional: without it every value is empty, FirebaseConfig.enabled is
 * false, and shared groups stay on this phone. See `firebase/README.md`.
 */
val firebaseProperties = providers.fileContents(rootProject.layout.projectDirectory.file("firebase.properties")).asText.orElse("")
val generateFirebaseConfig = tasks.register("generateFirebaseConfig") {
    val output = layout.buildDirectory.dir("generated/firebaseConfig/kotlin")
    inputs.property("properties", firebaseProperties)
    outputs.dir(output)
    doLast {
        val values = Properties().apply { load(StringReader(firebaseProperties.get())) }
        fun value(key: String) = values.getProperty(key, "").trim().replace("\\", "\\\\").replace("\"", "\\\"")
        val file = output.get().file("com/ptut/expense/sync/FirebaseConfig.kt").asFile
        file.parentFile.mkdirs()
        file.writeText(
            buildString {
                appendLine("package com.ptut.expense.sync")
                appendLine()
                appendLine("/** Generated from firebase.properties by :core:sync:generateFirebaseConfig. Do not edit. */")
                appendLine("internal object FirebaseConfig {")
                appendLine("    const val API_KEY = \"${value("apiKey")}\"")
                appendLine("    const val PROJECT_ID = \"${value("projectId")}\"")
                appendLine("    const val GOOGLE_WEB_CLIENT_ID = \"${value("googleWebClientId")}\"")
                appendLine("    const val GOOGLE_IOS_CLIENT_ID = \"${value("googleIosClientId")}\"")
                appendLine("    const val LINK_DOMAIN = \"${value("linkDomain")}\"")
                appendLine("    const val REGION = \"${value("region")}\"")
                appendLine("    const val PUSH_URL = \"${value("pushUrl")}\"")
                appendLine("    const val ANDROID_APP_ID = \"${value("androidAppId")}\"")
                appendLine("    const val SENDER_ID = \"${value("senderId")}\"")
                appendLine("    const val IOS_PUSH_READY = ${value("iosPushReady") == "true"}")
                appendLine()
                appendLine("    /** Shared groups sync only when the project is configured. */")
                appendLine("    val enabled: Boolean get() = API_KEY.isNotEmpty() && PROJECT_ID.isNotEmpty() && LINK_DOMAIN.isNotEmpty()")
                appendLine()
                appendLine("    /** Shared-group push only when sync is on and a Worker is set. */")
                appendLine("    val pushEnabled: Boolean get() = enabled && PUSH_URL.isNotEmpty()")
                appendLine("}")
            },
        )
    }
}
kotlin.sourceSets.commonMain {
    kotlin.srcDir(generateFirebaseConfig.map { it.outputs.files.singleFile })
}
