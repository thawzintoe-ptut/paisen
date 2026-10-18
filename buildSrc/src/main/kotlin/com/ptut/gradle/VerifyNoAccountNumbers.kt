package com.ptut.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * NFR-5: account numbers are read off a slip and discarded, never stored. The one place that rule
 * can quietly fail is a committed fixture, because a transcript looks like test data rather than
 * like someone's bank details.
 *
 * A mask counts only when it kept a digit. K PLUS prints the account with its last four still
 * showing, and four digits beside a merchant and a timestamp identify an account. A fixture that
 * masked every position carries nothing, and rejecting that would push the next person towards a
 * worse redaction rather than a better one.
 */
abstract class VerifyNoAccountNumbers : DefaultTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: ConfigurableFileCollection

    /** Only shortens the paths in the failure message. */
    @get:Internal
    abstract val sourceRoot: DirectoryProperty

    /** A task with no output would re-scan on every build. */
    @get:OutputFile
    abstract val receipt: RegularFileProperty

    private data class Shape(val pattern: Regex, val what: String)

    private val shapes = listOf(
        Shape(
            pattern = Regex("""[\dxX*]{3}-[\dxX*]-[\dxX*]{4,6}-[\dxX*]"""),
            what = "a Thai bank account number, masked or not",
        ),
        Shape(
            pattern = Regex("""\b\d{4}[ -]\d{4}[ -]\d{4}[ -]\d{4}\b"""),
            what = "a card number",
        ),
        Shape(
            pattern = Regex("""\b\d{10,}\b"""),
            what = "a 10-digit run — a phone number, a national ID or an unformatted account",
        ),
    )

    @TaskAction
    fun verify() {
        val root = sourceRoot.get().asFile
        val scanned = sources.files.sortedBy { it.invariantSeparatorsPath }

        val found = scanned.flatMap { file ->
            val shortPath = file.relativeTo(root).invariantSeparatorsPath
            file.readLines().withIndex().flatMap { (index, line) ->
                shapes.filter { shape -> shape.pattern.findAll(line).any { it.value.any(Char::isDigit) } }
                    .map { shape -> "$shortPath:${index + 1}  looks like ${shape.what}" }
            }
        }

        receipt.get().asFile.apply { parentFile.mkdirs() }
            .writeText("${scanned.size} files, ${found.size} findings\n")

        if (found.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("${found.size} possible account number(s) in test fixtures:")
                    found.forEach { appendLine("  $it") }
                    append("NFR-5: a slip's account number is discarded while it is read. ")
                    append("Redact the fixture — do not commit it and clean it up later.")
                },
            )
        }
    }
}
