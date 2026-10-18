package com.ptut.expense.data.local

actual abstract class PtutDatabaseContext

/** iOS resolves its own directory, so there is nothing to hand over — hence a single instance. */
object IosPtutDatabaseContext : PtutDatabaseContext()
