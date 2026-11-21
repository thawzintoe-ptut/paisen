package com.ptut.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * A phone that opened schema N keeps its identity hash, so editing N.json in place crashes it on the
 * next launch. A Pixel 7 did on 2026-11-21 after schema 24 was edited in place.
 *
 * Fails when an older schema differs from the newest release tag that holds it, or when the schema
 * Room exports for this build is not the one committed at HEAD.
 */
abstract class VerifySchemaFrozen : DefaultTask() {

    /** Written by Room's copy task, so it cannot also be a task input. */
    @get:Internal
    abstract val schemaDirectory: DirectoryProperty

    /** The source file that holds `const val PTUT_SCHEMA_VERSION = N`. */
    @get:Internal
    abstract val databaseSource: RegularFileProperty

    @get:Internal
    abstract val repositoryRoot: DirectoryProperty

    init {
        outputs.upToDateWhen { false }
    }

    private val versionLine = Regex("""const val PTUT_SCHEMA_VERSION = (\d+)""")

    @TaskAction
    fun verify() {
        val root = repositoryRoot.get().asFile
        val schemas = schemaDirectory.get().asFile
        val current = versionLine.find(databaseSource.get().asFile.readText())?.groupValues?.get(1)?.toInt()
            ?: throw GradleException("No PTUT_SCHEMA_VERSION in ${databaseSource.get().asFile.name}.")
        val tags = git(root, "tag", "--list", "v*", "--sort=-v:refname")?.lines()?.filter { it.isNotBlank() }.orEmpty()

        val problems = mutableListOf<String>()
        (schemas.listFiles().orEmpty().filter { it.isFile && it.name.endsWith(".json") }).forEach { file ->
            val version = file.nameWithoutExtension.toIntOrNull() ?: return@forEach
            if (version >= current) return@forEach
            val path = file.relativeTo(root).invariantSeparatorsPath
            val tag = tags.firstOrNull { git(root, "cat-file", "-e", "$it:$path") != null } ?: return@forEach
            if (git(root, "show", "$tag:$path") != file.readText()) {
                problems += "$path differs from $tag. A released schema never changes; add version ${current + 1} instead."
            }
        }

        val latest = File(schemas, "$current.json")
        val path = latest.relativeTo(root).invariantSeparatorsPath
        when {
            !latest.isFile -> problems += "$path was not exported. Is exportSchema on?"

            git(root, "show", "HEAD:$path") != latest.readText() ->
                problems += "$path as Room exports it (identityHash and all) is not the committed one. Commit it."
        }

        if (problems.isNotEmpty()) throw GradleException(problems.joinToString("\n"))
    }

    /** Stdout, or null when git exits non-zero. */
    private fun git(root: File, vararg args: String): String? {
        val process = ProcessBuilder(listOf("git", *args)).directory(root).redirectErrorStream(false).start()
        val out = process.inputStream.bufferedReader().readText()
        process.errorStream.readAllBytes()
        return if (process.waitFor() == 0) out else null
    }
}
