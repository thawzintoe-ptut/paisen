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
 * A backtick name lets a test say a whole sentence, and the JVM accepts nearly anything inside one.
 * Kotlin/Native does not, so a comma in a test name passes on Android and fails the iOS target with
 * "Name contains illegal characters".
 *
 * Nothing surfaces that until someone compiles the iOS test target, and while it is broken
 * commonTest is only ever verified on the JVM. This costs a source scan and fails on the name
 * itself, beside the sentence that has to be rewritten.
 */
abstract class VerifyNativeNames : DefaultTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: ConfigurableFileCollection

    /** Only shortens the paths in the failure message. */
    @get:Internal
    abstract val sourceRoot: DirectoryProperty

    /** A task with no output would re-scan on every build. */
    @get:OutputFile
    abstract val receipt: RegularFileProperty

    /**
     * The compiler's own list, read off FirNativeIdentifierChecker. Every one of these is legal
     * inside a backtick name on the JVM, which is what makes the mistake easy to write and hard
     * to see.
     */
    private val illegal = ".;,()[]{}/<>:\\\$&~*?#|§%@".toSet()

    private val declaration =
        Regex("""\b(?:fun|val|var|class|object|interface)\s+`([^`\n]+)`""")

    /** KDoc writes `Type.member` in prose, and the dot is on the list. */
    private val comment = Regex("""^\s*(?:\*|//|/\*)""")

    @TaskAction
    fun verify() {
        val root = sourceRoot.get().asFile

        val scanned = sources.files.sortedBy { it.invariantSeparatorsPath }

        val rejected = scanned.flatMap { file ->
            val shortPath = file.relativeTo(root).invariantSeparatorsPath
            file.readLines().withIndex().flatMap { (index, line) ->
                if (comment.containsMatchIn(line)) return@flatMap emptySequence<String>()
                declaration.findAll(line).mapNotNull { match ->
                    val name = match.groupValues[1]
                    val found = name.filter { it in illegal }.toSortedSet()
                    if (found.isEmpty()) return@mapNotNull null
                    val characters = found.joinToString(" ") { "\"$it\"" }
                    "$shortPath:${index + 1}  `$name`\n      Kotlin/Native rejects $characters"
                }
            }
        }

        receipt.get().asFile.apply { parentFile.mkdirs() }
            .writeText("${scanned.size} files, ${illegal.size} characters, ${rejected.size} names\n")

        if (rejected.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("${rejected.size} name(s) the iOS target cannot compile:")
                    rejected.forEach { appendLine("  $it") }
                    append("A test name here is a whole sentence, so rewrite the sentence without ")
                    append("the character rather than shortening the name into a label.")
                },
            )
        }
    }
}
