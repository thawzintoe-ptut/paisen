package com.ptut.gradle

import org.gradle.api.GradleException
import org.gradle.testfixtures.ProjectBuilder
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFailsWith

class VerifyBundledResourcesTest {

    private val root = createTempDirectory("bundled-resources").toFile()
    private val resources = File(root, "composeResources")
    private val assets = File(root, "assets")

    @AfterTest
    fun cleanUp() {
        root.deleteRecursively()
    }

    @Test
    fun `a font the app's assets lack fails and the message names it`() {
        resource("font/geist_light.ttf")
        resource("files/geist_ofl.txt")
        packed("com.ptut.expense.resources/files/geist_ofl.txt")

        val failure = assertFailsWith<GradleException> { task().verify() }

        assertContains(failure.message.orEmpty(), "font/geist_light.ttf")
    }

    @Test
    fun `assets with no compose resources at all fail on every file`() {
        resource("font/geist_regular.ttf")
        resource("files/geist_ofl.txt")

        val failure = assertFailsWith<GradleException> { task().verify() }

        assertContains(failure.message.orEmpty(), "2 of 2")
    }

    @Test
    fun `assets that carry every resource pass and leave a receipt`() {
        resource("font/geist_light.ttf")
        resource("files/geist_ofl.txt")
        packed("com.ptut.expense.resources/font/geist_light.ttf")
        packed("com.ptut.expense.resources/files/geist_ofl.txt")

        val task = task()
        task.verify()

        assertContains(task.receipt.get().asFile.readText(), "2 resource file(s), 0 missing")
    }

    @Test
    fun `a hidden file such as a Finder index is not a resource`() {
        resource("font/geist_light.ttf")
        resource("font/.DS_Store")
        packed("com.ptut.expense.resources/font/geist_light.ttf")

        task().verify()
    }

    private fun resource(path: String) =
        File(resources, path).apply { parentFile.mkdirs() }.writeText("x")

    private fun packed(path: String) =
        File(assets, "composeResources/$path").apply { parentFile.mkdirs() }.writeText("x")

    private fun task(): VerifyBundledResources {
        assets.mkdirs()
        val project = ProjectBuilder.builder().withProjectDir(root).build()
        return project.tasks.register("verifyBundledResources", VerifyBundledResources::class.java).get().apply {
            resourceRoot.set(resources)
            mergedAssets.set(assets)
            receipt.set(File(root, "out/receipt.txt"))
        }
    }
}
