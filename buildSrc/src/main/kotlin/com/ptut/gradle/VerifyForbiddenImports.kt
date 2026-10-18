package com.ptut.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Fails when a source file imports a package its module may not use.
 *
 * `:core:sync` reads Room directly, so some of `:data`'s Room types are public. A feature module
 * could compile against them; this keeps it from doing so, which the module graph alone cannot.
 */
abstract class VerifyForbiddenImports : DefaultTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: ConfigurableFileCollection

    @get:Internal
    abstract val sourceRoot: DirectoryProperty

    /** Package prefixes, such as `com.ptut.expense.data.local`. */
    @get:Input
    abstract val forbiddenPrefixes: ListProperty<String>

    @get:OutputFile
    abstract val receipt: RegularFileProperty

    @TaskAction
    fun verify() {
        val root = sourceRoot.get().asFile
        val prefixes = forbiddenPrefixes.get()
        val scanned = sources.files.sortedBy { it.invariantSeparatorsPath }
        val importLine = Regex("""^\s*import\s+([\w.]+)""")

        val violations = scanned.flatMap { file ->
            val shortPath = file.relativeTo(root).invariantSeparatorsPath
            file.readLines().withIndex().mapNotNull { (index, line) ->
                val imported = importLine.find(line)?.groupValues?.get(1) ?: return@mapNotNull null
                prefixes.firstOrNull { imported == it || imported.startsWith("$it.") }
                    ?.let { "$shortPath:${index + 1}  $imported\n      a feature reads data through a repository, never $it" }
            }
        }

        receipt.get().asFile.apply { parentFile.mkdirs() }
            .writeText("${scanned.size} files, ${prefixes.size} prefixes, ${violations.size} violations\n")

        if (violations.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("${violations.size} forbidden import(s):")
                    violations.forEach { appendLine("  $it") }
                },
            )
        }
    }
}
