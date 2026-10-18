package com.ptut.gradle

import org.gradle.api.GradleException
import org.gradle.testfixtures.ProjectBuilder
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFailsWith

class VerifyNativeNamesTest {

    private val root = createTempDirectory("native-names").toFile()

    @AfterTest
    fun cleanUp() {
        root.deleteRecursively()
    }

    @Test
    fun `a comma in a test name fails and the message names the file the line and the character`() {
        val task = task(
            "SlipTest.kt" to
                """
                class SlipTest {
                    @Test
                    fun `a run that cannot scan is a failure, and says why`() = Unit
                }
                """.trimIndent(),
        )

        val failure = assertFailsWith<GradleException> { task.verify() }

        assertContains(failure.message.orEmpty(), "SlipTest.kt:3")
        assertContains(failure.message.orEmpty(), "\",\"")
    }

    @Test
    fun `a comma between parameters is not part of the name`() {
        val task = task(
            "PadTest.kt" to
                """
                class PadTest {
                    @Test
                    fun `an amount the slip stated starts on the pad`(a: Int, b: Int) = Unit
                }
                """.trimIndent(),
        )

        task.verify()
    }

    @Test
    fun `the comma is not the only character the iOS target refuses`() {
        val task = task(
            "ParserTest.kt" to
                """
                class ParserTest {
                    @Test
                    fun `a slip that prints 50% of the total`() = Unit

                    @Test
                    fun `an amount read off Satang#raw`() = Unit
                }
                """.trimIndent(),
        )

        val failure = assertFailsWith<GradleException> { task.verify() }

        assertContains(failure.message.orEmpty(), "\"%\"")
        assertContains(failure.message.orEmpty(), "\"#\"")
    }

    @Test
    fun `a clean run leaves a receipt so the scan is not repeated on every build`() {
        val task = task(
            "CleanTest.kt" to
                """
                class CleanTest {
                    @Test
                    fun `a name the iOS target accepts`() = Unit
                }
                """.trimIndent(),
        )

        task.verify()

        val receipt = task.receipt.get().asFile.readText()
        assertContains(receipt, "1 files")
        assertContains(receipt, "24 characters")
    }

    @Test
    fun `a backtick name on something other than a function is refused just the same`() {
        val task = task(
            "FixtureTest.kt" to
                """
                class FixtureTest {
                    private val `a slip with no amount, filed anyway` = 1
                }
                """.trimIndent(),
        )

        val failure = assertFailsWith<GradleException> { task.verify() }

        assertContains(failure.message.orEmpty(), "FixtureTest.kt:2")
    }

    @Test
    fun `a backtick span in a doc comment is prose and never a declaration`() {
        val task = task(
            "DocTest.kt" to
                """
                /** Reads what class `Satang.raw` holds, which the domain owns. */
                class DocTest
                """.trimIndent(),
        )

        task.verify()
    }

    private fun task(vararg files: Pair<String, String>): VerifyNativeNames {
        val written = files.map { (path, content) ->
            File(root, path).apply { parentFile.mkdirs() }.also { it.writeText(content) }
        }
        val project = ProjectBuilder.builder().withProjectDir(root).build()
        return project.tasks.register("verifyNativeNames", VerifyNativeNames::class.java).get().apply {
            sources.from(written)
            sourceRoot.set(root)
            receipt.set(File(root, "out/receipt.txt"))
        }
    }
}
