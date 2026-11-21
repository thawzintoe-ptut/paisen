package com.ptut.expense.platform.crash

import com.ptut.expense.data.local.PtutDatabaseContext

/** The crash folder's few operations, so pruning and redaction are testable without a disk. */
interface CrashFiles {
    fun list(): List<String>
    fun write(name: String, text: String)
    fun read(name: String): String
    fun delete(name: String)
    fun rename(from: String, to: String)
}

/** What a report says about the build. Nothing here comes from the ledger. */
data class CrashHeader(val appVersion: String, val schemaVersion: Int, val device: String)

/**
 * A crash written to app-private storage and nowhere else. It holds the build, the thread and the
 * stack frames only: exception messages are dropped, because one can quote an amount, a name or
 * a line of slip text.
 */
class CrashJournal(
    private val files: CrashFiles,
    private val header: CrashHeader,
    private val now: () -> Long,
) {
    fun record(thread: String, error: Throwable) {
        files.write(
            "${now()}$NEW",
            buildString {
                appendLine("App: ${header.appVersion}")
                appendLine("Schema: ${header.schemaVersion}")
                appendLine("Device: ${header.device}")
                appendLine("Thread: $thread")
                appendLine()
                append(redact(error.stackTraceToString()))
            },
        )
        prune()
    }

    /** The newest report not yet answered, for the one-time prompt. */
    fun unseen(): String? = reports().lastOrNull(::isWaiting)?.let(files::read)

    /** Not now and Share both answer every waiting report, so the prompt shows once. */
    fun markSeen() {
        reports().filter(::isWaiting).forEach { files.rename(it, it.removeSuffix(NEW) + SEEN) }
    }

    private fun isWaiting(name: String) = !name.endsWith(SEEN)

    private fun prune() {
        reports().dropLast(KEPT).forEach(files::delete)
    }

    private fun reports(): List<String> = files.list()
        .filter { it.endsWith(NEW) }
        .sortedBy { it.substringBefore('.').toLongOrNull() ?: 0L }

    companion object {
        const val KEPT = 10

        /** A seen report ends in [SEEN], which also ends in this. */
        private const val NEW = ".txt"
        private const val SEEN = ".seen.txt"

        /** Keeps exception class names and frames; drops every message after the class name. */
        fun redact(trace: String): String = trace.lineSequence().joinToString("\n") { line ->
            val trimmed = line.trimStart()
            val indent = line.take(line.length - trimmed.length)
            when {
                trimmed.isEmpty() || trimmed.startsWith("at ") || trimmed.startsWith("...") -> line
                trimmed.startsWith(CAUSED) -> indent + CAUSED + className(trimmed.removePrefix(CAUSED))
                trimmed.startsWith(SUPPRESSED) -> indent + SUPPRESSED + className(trimmed.removePrefix(SUPPRESSED))
                else -> indent + className(trimmed)
            }
        }

        private fun className(text: String): String = text.substringBefore(':').trim()
        private const val CAUSED = "Caused by: "
        private const val SUPPRESSED = "Suppressed: "
    }
}

/** App-private `files/crash/` on this platform. */
expect fun crashFiles(context: PtutDatabaseContext): CrashFiles

/** Writes a report for any uncaught crash, then hands it to whatever handler was there before. */
expect fun CrashJournal.install()
