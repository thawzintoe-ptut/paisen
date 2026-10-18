package com.ptut.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

/**
 * NFR-1 ("no outbound request at all") and NFR-2 ("no analytics, no crash reporting") are promises
 * about what the code *can* do, so they are checked at build time. A runtime test that fires a
 * request and expects it to fail asserts the test machine, not the app.
 *
 * #191 narrowed NFR-1: shared groups sync through Ktor's client (Android and Darwin engines), and
 * [VerifySourceRules] keeps every `io.ktor` import under `sync/`. Every other HTTP client is still
 * banned: one arriving on a classpath means somebody intends to make a call outside that code, and
 * that intent should be argued about in review. It is also the only classpath gate iOS gets.
 *
 * On the ML Kit graph specifically: `com.google.mlkit:text-recognition` is the bundled-model
 * artifact, kept for NFR-1 over its Play-services sibling that downloads the model on first use.
 * It pulls `com.google.android.datatransport:transport-backend-cct`, Google's telemetry uploader,
 * which is why that graph is not blanket-forbidden here — the app's manifest removes its backend,
 * and [VerifyNoTelemetryUpload] fails if one survives the merge. Blanket-forbidding `com.google.firebase` would also flag
 * `firebase-encoders`, which only encodes. The prefixes below name the artifacts that actually
 * carry data off the device.
 */
abstract class VerifyNoNetworkDependencies : DefaultTask() {

    /** Configuration name to the `group:name` coordinates it resolves. */
    @get:Input
    abstract val classpaths: MapProperty<String, Set<String>>

    /** A task with no output would re-check on every build. */
    @get:OutputFile
    abstract val receipt: RegularFileProperty

    /** The colon keeps a group from matching a longer one: `com.squareup.okhttp` misses okhttp3. */
    private data class Forbidden(val group: String, val namePrefix: String = "", val why: String) {
        val coordinatePrefix = "$group:$namePrefix"
    }

    private val forbidden = listOf(
        // NFR-1: anything that can open an HTTP connection, except Ktor's client for sync/ (#191).
        Forbidden("com.squareup.okhttp3", why = "OkHttp"),
        Forbidden("com.squareup.okhttp", why = "OkHttp 2"),
        Forbidden("com.squareup.retrofit2", why = "Retrofit"),
        Forbidden("org.apache.httpcomponents", why = "Apache HttpClient"),
        Forbidden("org.apache.httpcomponents.client5", why = "Apache HttpClient 5"),
        Forbidden("com.google.http-client", why = "Google HTTP client"),
        Forbidden("com.android.volley", why = "Volley"),
        // NFR-2: anything that carries usage or crash data off the device.
        Forbidden("com.google.firebase", "firebase-analytics", why = "Firebase Analytics"),
        Forbidden("com.google.firebase", "firebase-crashlytics", why = "Crashlytics"),
        Forbidden("com.google.firebase", "firebase-perf", why = "Firebase Performance"),
        Forbidden("com.google.android.gms", "play-services-measurement", why = "Google Analytics"),
        Forbidden("io.sentry", why = "Sentry"),
    )

    @TaskAction
    fun verify() {
        val classpaths = classpaths.get().toSortedMap()

        val findings = classpaths.flatMap { (classpath, coordinates) ->
            coordinates.mapNotNull { coordinate ->
                forbidden
                    .firstOrNull { coordinate.startsWith(it.coordinatePrefix) }
                    ?.let { "$coordinate  ${it.why}, on $classpath" }
            }
        }

        val scanned = classpaths.values.sumOf { it.size }
        receipt.get().asFile.apply { parentFile.mkdirs() }
            .writeText("${classpaths.size} classpaths, $scanned modules, ${findings.size} findings\n")

        if (findings.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("${findings.size} module(s) that can move data off the device:")
                    findings.forEach { appendLine("  $it") }
                    appendLine("NFR-1: v1 makes no outbound request at all. NFR-2: nothing leaves the device.")
                    appendLine("If it arrived transitively, find out who asked for it:")
                    append("  ./gradlew ${path.substringBeforeLast(':')}:dependencies --configuration <name>")
                },
            )
        }
    }
}
