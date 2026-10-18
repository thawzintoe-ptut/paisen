package com.ptut.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import java.io.ByteArrayOutputStream
import javax.inject.Inject

/**
 * NFR-1 on iOS (#132). iOS has no permission an app can go without, so the only gate is the code
 * that gets linked: this reads the framework's undefined symbols with `nm -u` and fails on Apple's
 * networking APIs, however they arrived — our own code, a library, or a call not spelled
 * `NSURLSession` that [VerifySourceRules] cannot see.
 *
 * It reads the release framework. A debug link pulls Kotlin/Native's prebuilt platform caches,
 * which reference every Foundation symbol (`NSURLSession` constants included) whether the app
 * calls them or not, so a debug framework would always fail.
 *
 * #191 allows one: `NSURLSession`, which Ktor's Darwin engine uses for shared groups, while
 * [VerifySourceRules] keeps our own Kotlin from naming it. `NSStream` and `CFReadStream`/`CFWriteStream` are not on the list:
 * they read and write files too, and a socket stream needs a `CFSocket` or
 * `CFStreamCreatePairWithSocket*`, which is.
 */
abstract class VerifyNoNetworkSymbols : DefaultTask() {

    /** The framework's binary, a static archive: `ComposeApp.framework/ComposeApp`. */
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val binary: RegularFileProperty

    /** Every undefined symbol checked, so a pass can be audited and the task is not rerun for nothing. */
    @get:OutputFile
    abstract val receipt: RegularFileProperty

    @get:Inject
    abstract val exec: ExecOperations

    private val forbidden = listOf(
        Regex("NSURLConnection") to "NSURLConnection",
        Regex("^_nw_") to "Network.framework",
        Regex("CFSocket|CFHTTP|CFNetwork|CFStreamCreatePairWithSocket") to "CFNetwork sockets",
        Regex("^_(socket|connect|getaddrinfo)$") to "BSD sockets",
    )

    @TaskAction
    fun verify() {
        val out = ByteArrayOutputStream()
        exec.exec {
            commandLine("nm", "-u", binary.get().asFile.absolutePath)
            standardOutput = out
        }
        val undefined = out.toString().lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toSortedSet()

        val hits = undefined.mapNotNull { symbol ->
            forbidden.firstOrNull { (pattern, _) -> pattern.containsMatchIn(symbol) }?.let { (_, why) -> "$symbol ($why)" }
        }
        if (hits.isNotEmpty()) {
            throw GradleException(
                "#191: the iOS framework references networking APIs other than Ktor's NSURLSession.\n" +
                    hits.joinToString("\n") { "  $it" },
            )
        }
        receipt.get().asFile.apply { parentFile.mkdirs() }.writeText(undefined.joinToString("\n"))
    }
}
