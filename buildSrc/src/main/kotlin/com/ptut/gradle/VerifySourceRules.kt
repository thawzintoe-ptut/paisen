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
 * Cheaper than a lint rule, runs everywhere, and every pattern has a requirement behind it.
 * See docs/design/2026-08-17-implementation-plan.md §C.
 */
abstract class VerifySourceRules : DefaultTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: ConfigurableFileCollection

    /** Only shortens the paths in the failure message. */
    @get:Internal
    abstract val sourceRoot: DirectoryProperty

    /** A task with no output would re-scan on every build. */
    @get:OutputFile
    abstract val receipt: RegularFileProperty

    /** [allowedIn] holds the path fragments where a pattern is the right thing to write. */
    private data class Rule(
        val pattern: Regex,
        val why: String,
        val allowedIn: List<String> = emptyList(),
    )

    private val rules = listOf(
        Rule(
            pattern = Regex("""Color\(0x|Color\.White|Color\.Black"""),
            why = "hardcoded colour — every colour comes from PtutColors",
            allowedIn = listOf("/design/"),
        ),
        Rule(
            pattern = Regex("""TimeZone\.currentSystemDefault\(\)"""),
            why = "device time zone — FR-19: a Bangkok day is not the traveller's day",
            // The date and time picker reads the phone's clock by the reader's choice; totals do not.
            allowedIn = listOf("/ui/sky/SkyDateTimePicker.kt"),
        ),
        Rule(
            pattern = Regex("""\.raw\b"""),
            why = "Satang.raw — FR-9: use the operators, not the Long underneath",
            allowedIn = listOf("/domain/"),
        ),
        Rule(
            pattern = Regex("""\.toDouble\(\)|\.toFloat\(\)"""),
            why = "float from money — FR-9: a ratio goes through Satang.ratioTo",
            allowedIn = listOf("/domain/Money.kt"),
        ),
        Rule(
            pattern = Regex("""\b\d+(\.\d+)?\.dp\b"""),
            why = "raw Dp literal — sizes come from Spacing or Sizes",
            allowedIn = listOf("/design/"),
        ),
        Rule(
            pattern = Regex("""androidx\.compose\.material\.icons"""),
            why = "Material icon — the design ships Heroicons v2, so draw a PtutIcon",
        ),
        Rule(
            // #20. A style built from nothing has no family, so its text falls back to the system
            // face and loses tabular figures. The Ledger's search field and chips did exactly that.
            pattern = Regex("""\bTextStyle\("""),
            why = "bare TextStyle — start from MaterialTheme.typography or ptutType and copy it",
            allowedIn = listOf("/design/"),
        ),
        Rule(
            // iOS has no permission to withhold, so the source is the only cheap gate there.
            pattern = Regex("""\b(NSURLSession|NSURLConnection|URLSession)\b"""),
            why = "Apple networking API — #191: only Ktor, under sync/, talks to the network",
        ),
        Rule(
            // #191: shared groups sync, and nothing else may call out. Your own spending, slips
            // and reports stay on the phone because no file outside sync/ can name the client.
            pattern = Regex("""\bimport io\.ktor\b"""),
            why = "network client outside sync/ — #191: only shared groups use the network",
            allowedIn = listOf("/sync/"),
        ),
        Rule(
            pattern = Regex("""\b(tween|snap|delay)\(\s*\d|durationMillis\s*=\s*\d"""),
            why = "raw animation duration — NFR-8: timings come from PtutMotion",
            allowedIn = listOf("/design/"),
        ),
        Rule(
            // A screen inventing its own curve is how two screens end up easing differently.
            pattern = Regex("""\bCubicBezierEasing\("""),
            why = "hand-built easing — NFR-8: use MaterialTheme.ptutMotion.standard or .overshoot",
            allowedIn = listOf("/design/"),
        ),
        Rule(
            // FR-21. The design's own argument: deleting "is the one action that silently makes
            // the totals wrong if it happens by accident, and the slip image is already gone by
            // then". A swipe cannot ask, so the row has to be opened before it can be removed.
            pattern = Regex("""\bSwipeToDismiss\w*|\brememberSwipeToDismissBoxState\b"""),
            why = "swipe to delete — FR-21: a delete is confirmed on the record's own screen",
        ),
        Rule(
            // Motion vocabulary only. A Flow subscription timeout and a day in milliseconds are
            // also durations, and neither belongs in a design token.
            pattern = Regex(
                """const val \w*(ENTER|EXIT|POP|RISE|FADE|HOLD|DWELL|UNDO|ANIM)\w*_?(MILLIS|MS)\b""",
            ),
            why = "motion duration given a private home — NFR-8: it belongs in PtutMotion",
            allowedIn = listOf("/design/"),
        ),
    )

    @TaskAction
    fun verify() {
        val root = sourceRoot.get().asFile
        val scanned = sources.files.sortedBy { it.invariantSeparatorsPath }

        val violations = scanned.flatMap { file ->
            val path = file.invariantSeparatorsPath
            val applicable = rules.filter { rule -> rule.allowedIn.none { it in path } }
            if (applicable.isEmpty()) return@flatMap emptyList<String>()

            val shortPath = file.relativeTo(root).invariantSeparatorsPath
            file.readLines().withIndex().flatMap { (index, line) ->
                applicable
                    .filter { it.pattern.containsMatchIn(line) }
                    .map { rule -> "$shortPath:${index + 1}  ${line.trim()}\n      ${rule.why}" }
            }
        }

        receipt.get().asFile.apply { parentFile.mkdirs() }
            .writeText("${scanned.size} files, ${rules.size} rules, ${violations.size} violations\n")

        if (violations.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("${violations.size} source rule violation(s):")
                    violations.forEach { appendLine("  $it") }
                    append("Each pattern has a requirement behind it — see ")
                    append("docs/design/2026-08-17-implementation-plan.md §C.")
                },
            )
        }
    }
}
