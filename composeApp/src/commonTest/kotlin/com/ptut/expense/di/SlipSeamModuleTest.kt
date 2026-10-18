package com.ptut.expense.di

import com.ptut.expense.importer.FakeSlipSource
import com.ptut.expense.importer.FakeSlipTextReader
import com.ptut.expense.platform.UnavailableSlipSource
import com.ptut.expense.platform.UnavailableSlipTextReader
import com.ptut.expense.platform.ocr.RoutingSlipTextReader
import com.ptut.expense.platform.ocr.SlipTextReader
import com.ptut.expense.platform.photos.CompositeSlipSource
import com.ptut.expense.platform.photos.InMemorySlipSource
import com.ptut.expense.platform.photos.SlipSource
import kotlinx.coroutines.test.runTest
import org.koin.core.Koin
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * #59's last done-means: the debug menu is unreachable in a release build.
 *
 * Asserted against a resolved graph rather than by reading the source, because the guard is one
 * `if` and an `if` is exactly what a careless edit inverts. Nothing else would catch it — a
 * release build that quietly bound the fake reader still compiles, still passes every other test,
 * and ships an app that reads transcripts instead of slips.
 *
 * #17 gave the platforms a real pair to bind, so each case is asked twice: once for a platform
 * with none — iOS until #18 — and once for one that has them.
 */
class SlipSeamModuleTest {

    @Test
    fun `a release build cannot reach the injectable slip source`() = withSeams(debug = false) {
        assertNull(getOrNull<InMemorySlipSource>(), "a release build can inject a fake slip")
        assertIs<UnavailableSlipSource>(get<SlipSource>())
    }

    @Test
    fun `a release build cannot reach the fake reader either`() = withSeams(debug = false) {
        // Otherwise a shipped app would decode any image as UTF-8 and file whatever fell out.
        assertIs<UnavailableSlipTextReader>(get<SlipTextReader>())
    }

    @Test
    fun `a debug build gets the injectable pair`() = withSeams(debug = true) {
        assertIs<InMemorySlipSource>(get<SlipSource>())
        assertIs<FakeSlipTextReader>(get<SlipTextReader>())
    }

    /**
     * The panel injects through the same instance the importer scans, so the two have to resolve
     * to one object. Two singles of the same type would leave the button writing into a folder
     * nothing reads.
     */
    @Test
    fun `the panel and the importer share one folder`() = withSeams(debug = true) {
        assertIs<InMemorySlipSource>(get<SlipSource>())
        kotlin.test.assertSame(get<InMemorySlipSource>(), get<SlipSource>())
    }

    @Test
    fun `a release build on a wired platform gets that platform's pair`() =
        withSeams(debug = false, platform = androidLike()) {
            assertNull(getOrNull<InMemorySlipSource>(), "a release build can inject a fake slip")
            assertIs<FakeSlipSource>(get<SlipSource>())
            assertIs<FakeSlipTextReader>(get<SlipTextReader>())
        }

    /**
     * A debug build on a phone has to do both jobs at once: read the real folder, and still take
     * an injected transcript from the debug menu.
     */
    @Test
    fun `a debug build on a wired platform scans the real folder as well as the injected one`() =
        withSeams(debug = true, platform = androidLike()) {
            get<InMemorySlipSource>().inject("a transcript", capturedAtEpochMillis = 200L)
            val source = get<SlipSource>()
            assertIs<CompositeSlipSource>(source)

            runTest {
                assertEquals(listOf("platform:1", "injected:injected-1"), source.candidates().map { it.id })
            }
        }

    @Test
    fun `a debug build on a wired platform reads pictures and transcripts`() =
        withSeams(debug = true, platform = androidLike()) {
            assertIs<RoutingSlipTextReader>(get<SlipTextReader>())
        }

    /** A platform with a folder and a reader of its own, standing in for what #17 binds. */
    private fun androidLike() = SlipSeams(
        source = FakeSlipSource(mapOf("1" to ("a real slip" to 100L))),
        reader = FakeSlipTextReader(),
    )

    /** An isolated graph, so nothing here touches the app's global Koin. */
    private fun withSeams(debug: Boolean, platform: SlipSeams? = null, assertions: Koin.() -> Unit) {
        val platformModule = platform?.let { seams -> module { single { seams } } }
        val application = koinApplication {
            modules(listOfNotNull(platformModule, slipSeamModule(AppBuild(debug))))
        }
        try {
            application.koin.assertions()
        } finally {
            application.close()
        }
    }
}
