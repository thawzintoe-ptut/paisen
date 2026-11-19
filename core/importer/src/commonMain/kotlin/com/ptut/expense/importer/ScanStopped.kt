package com.ptut.expense.importer

import com.ptut.expense.domain.ImportFailureKind

/**
 * A slip source that cannot scan throws this, so the journal keeps the [kind] beside the sentence.
 * Any other throwable journals as [ImportFailureKind.Other].
 */
open class ScanStopped(val kind: ImportFailureKind, message: String) : IllegalStateException(message)
