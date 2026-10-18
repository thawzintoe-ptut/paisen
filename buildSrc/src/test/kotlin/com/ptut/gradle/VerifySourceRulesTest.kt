package com.ptut.gradle

import org.gradle.api.GradleException
import org.gradle.testfixtures.ProjectBuilder
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFailsWith

/**
 * The motion half of the scan. A gate nobody has watched fail is a gate nobody knows works, and
 * these three are the ones with real false-positive risk: a duration is also a Flow timeout and a
 * day in milliseconds, and only one of the three belongs in a design token.
 */
class VerifySourceRulesTest {

    private val root = createTempDirectory("source-rules").toFile()

    @AfterTest
    fun cleanUp() {
        root.deleteRecursively()
    }

    @Test
    fun `a duration typed straight into an animation is refused`() {
        val failure = assertFailsWith<GradleException> {
            scan("ui/ToastHost.kt" to "enter = fadeIn(tween(260))").verify()
        }

        assertContains(failure.message.orEmpty(), "ToastHost.kt:1")
        assertContains(failure.message.orEmpty(), "PtutMotion")
    }

    @Test
    fun `a toast dwell typed into a delay is refused`() {
        assertFailsWith<GradleException> { scan("AppViewModel.kt" to "delay(2_600L)").verify() }
    }

    @Test
    fun `a screen inventing its own curve is refused`() {
        val failure = assertFailsWith<GradleException> {
            scan("home/HomeView.kt" to "val easing = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)").verify()
        }

        assertContains(failure.message.orEmpty(), "ptutMotion.standard")
    }

    /** The failure mode the slice exists to stop: a motion value given a private home on a screen. */
    @Test
    fun `a motion duration hidden in a private constant is refused`() {
        val failure = assertFailsWith<GradleException> {
            scan("ui/ToastHost.kt" to "private const val ENTER_MILLIS = 260").verify()
        }

        assertContains(failure.message.orEmpty(), "it belongs in PtutMotion")
    }

    @Test
    fun `the design package is where these are the right thing to write`() {
        scan(
            "design/Motion.kt" to
                """
                private val StandardEasing = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)
                private const val POP_HOLD_MILLIS = 380
                val spec = tween(340, easing = StandardEasing)
                """.trimIndent(),
        ).verify()
    }

    /**
     * A Flow subscription timeout is a duration and is not motion. Flagging it would push a number
     * with nothing to do with the design into the design tokens, which is worse than leaving it.
     */

    /** #191: shared groups sync, and nothing else may call out. */
    @Test
    fun `a network client imported outside sync is refused`() {
        val failure = assertFailsWith<GradleException> {
            scan("home/HomeViewModel.kt" to "import io.ktor.client.HttpClient").verify()
        }

        assertContains(failure.message.orEmpty(), "only shared groups use the network")
    }

    @Test
    fun `the sync package is where the network client belongs`() {
        scan("sync/FirestoreClient.kt" to "import io.ktor.client.HttpClient").verify()
    }

    @Test
    fun `a subscription timeout is left alone`() {
        scan("rules/RulesViewModel.kt" to "const val SUBSCRIPTION_TIMEOUT_MILLIS = 5_000L").verify()
    }

    @Test
    fun `time arithmetic in the domain is left alone`() {
        scan(
            "domain/ImportFreshness.kt" to
                """
                private const val ONE_DAY_MILLIS = 24L * 60 * 60 * 1000
                private const val ONE_MINUTE_MILLIS = 60L * 1000
                """.trimIndent(),
        ).verify()
    }

    /** A named token read at the call site is the whole point, so it must not trip the scan. */
    @Test
    fun `an animation reading a token is left alone`() {
        scan("ui/ToastHost.kt" to "enter = fadeIn(tween(rise))").verify()
    }

    /** #20: a style built from nothing has no family, so its text loses Geist and `tnum`. */
    @Test
    fun `a screen building its own text style is refused`() {
        val failure = assertFailsWith<GradleException> {
            scan("ledger/LedgerView.kt" to "private val ChipTextStyle = TextStyle(fontSize = 13.5.sp)").verify()
        }

        assertContains(failure.message.orEmpty(), "LedgerView.kt:1")
        assertContains(failure.message.orEmpty(), "MaterialTheme.typography")
    }

    @Test
    fun `a style copied from the theme is left alone`() {
        scan(
            "ledger/LedgerView.kt" to
                "style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)",
        ).verify()
    }

    @Test
    fun `the type layer is where a text style is built`() {
        scan("design/Type.kt" to "private fun style() = TextStyle(fontSize = size.sp)").verify()
    }

    private fun scan(vararg files: Pair<String, String>): VerifySourceRules {
        val written = files.map { (path, content) ->
            File(root, path).apply { parentFile.mkdirs() }.also { it.writeText(content) }
        }
        val project = ProjectBuilder.builder().withProjectDir(root).build()
        return project.tasks.register("verifySourceRules", VerifySourceRules::class.java).get().apply {
            sources.from(written)
            sourceRoot.set(root)
            receipt.set(File(root, "out/receipt.txt"))
        }
    }
}
