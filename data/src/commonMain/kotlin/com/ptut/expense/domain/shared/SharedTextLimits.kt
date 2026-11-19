package com.ptut.expense.domain.shared

/** The longest texts the Firestore rules take; a longer one would be refused for good and taken back. */
object SharedTextLimits {
    const val GROUP_NAME = 100
    const val MEMBER_NAME = 60
    const val DESCRIPTION = 500
    const val NOTE = 2000
}
