package com.ptut.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * #191: the app holds INTERNET since shared groups sync, so the permission no longer keeps the rest
 * of the app quiet. What still must not happen is a library uploading on its own. ML Kit's bundled
 * reader drags in `com.google.android.datatransport`, whose CCT backend uploads usage telemetry
 * once a socket is allowed; the app's manifest removes that backend, and this gate fails if any
 * datatransport backend survives the merge.
 *
 * It reads the *merged* manifest on purpose. A library can contribute a backend the app's own
 * manifest never mentions, and that is exactly the case worth catching.
 */
abstract class VerifyNoTelemetryUpload : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val mergedManifest: RegularFileProperty

    /** A task with no output would re-check on every build. */
    @get:OutputFile
    abstract val receipt: RegularFileProperty

    @TaskAction
    fun verify() {
        val manifest = mergedManifest.get().asFile
        val backends = telemetryBackends(manifest)

        receipt.get().asFile.apply { parentFile.mkdirs() }
            .writeText("${backends.size} telemetry backend(s) declared\n")

        if (backends.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("The merged manifest declares ${backends.size} telemetry upload backend(s):")
                    backends.forEach { appendLine("  $it") }
                    appendLine("#191: only shared groups may use the network. Remove the backend with")
                    appendLine("tools:node=\"remove\" in androidApp's manifest, beside the CCT one.")
                    append("Merged manifest: ${manifest.absolutePath}")
                },
            )
        }
    }

    /** Every `backend:` meta-data a datatransport discovery service would hand an uploader to. */
    private fun telemetryBackends(manifest: File): List<String> {
        val document = DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(manifest)

        val nodes = document.getElementsByTagName("meta-data")
        return (0 until nodes.length).mapNotNull { index ->
            nodes.item(index).attributes
                ?.getNamedItemNS(ANDROID_NAMESPACE, "name")
                ?.nodeValue
                ?.takeIf { it.startsWith(BACKEND_PREFIX) }
        }.sorted()
    }

    private companion object {
        const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
        const val BACKEND_PREFIX = "backend:"
    }
}
