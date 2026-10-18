package com.ptut.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Every file the shared module keeps under `composeResources` has to reach the app's assets.
 *
 * Android reads a bundled font from an asset and, when the asset is missing, draws the system face
 * without a word. Until this check the APK carried none of them: every screen drew Roboto while iOS
 * drew the bundled face, and the font tests, which run only on iOS, could not see it.
 *
 * It reads the *merged* assets, so it holds whichever module or plugin puts a resource there.
 */
abstract class VerifyBundledResources : DefaultTask() {

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val resourceRoot: DirectoryProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val mergedAssets: DirectoryProperty

    /** A task with no output would re-check on every build. */
    @get:OutputFile
    abstract val receipt: RegularFileProperty

    @TaskAction
    fun verify() {
        val root = resourceRoot.get().asFile
        // Compose packs the files under one folder per `Res` package. Matching any of them keeps
        // the package name in the one build script that sets it.
        val packages = mergedAssets.get().asFile.resolve("composeResources")
            .listFiles { file -> file.isDirectory }
            .orEmpty()
        val expected = root.walkTopDown()
            .filter { it.isFile && !it.name.startsWith(".") }
            .map { it.relativeTo(root).invariantSeparatorsPath }
            .sorted()
            .toList()
        val missing = expected.filter { path -> packages.none { it.resolve(path).isFile } }

        receipt.get().asFile.apply { parentFile.mkdirs() }
            .writeText("${expected.size} resource file(s), ${missing.size} missing\n")

        if (missing.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine(
                        "${missing.size} of ${expected.size} resource file(s) are missing from the app's merged assets:",
                    )
                    missing.forEach { appendLine("  $it") }
                    appendLine("Android draws the system face when a bundled font is missing, and says nothing.")
                    append("Check androidResources in the shared module's android block.")
                },
            )
        }
    }
}
