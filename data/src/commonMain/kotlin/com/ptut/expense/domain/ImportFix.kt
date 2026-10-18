package com.ptut.expense.domain

/**
 * What a reader can actually do about an importer that is not delivering.
 *
 * The two states that carry a fix do not share one. Nothing having run at all means the folder was
 * never reachable, so the lever is where slips come from. A run that stopped after working means
 * access was granted once, so the lever is the scan itself. A scan that failed just now is almost
 * always photo access taken back or cut to a few photos, and only the system page can widen it.
 */
enum class ImportFix { GrantPhotoAccess, ResumeWatching, AllowAllPhotos }

/**
 * Null under three days on purpose. C-5 makes a quiet stretch a plausible healthy device with
 * nothing spent on it, and an action offered there would read as "import failed" when nothing has.
 */
fun importFix(sinceLastImportMillis: Long?): ImportFix? = when {
    sinceLastImportMillis == null -> ImportFix.GrantPhotoAccess
    importFreshness(sinceLastImportMillis) == ImportFreshness.Broken -> ImportFix.ResumeWatching
    else -> null
}
