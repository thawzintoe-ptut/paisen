package com.ptut.expense.data.local

/**
 * What each platform needs in order to name a file. Android resolves its data directory from a
 * `Context`; iOS resolves its own, so the difference lives in this type instead of leaking into
 * every caller.
 *
 * Abstract because Android's actual is a typealias to `Context`, which is abstract — a final
 * expect would not match it.
 */
expect abstract class PtutDatabaseContext
