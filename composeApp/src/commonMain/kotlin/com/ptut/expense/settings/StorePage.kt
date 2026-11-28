package com.ptut.expense.settings

/** The Play Store app's own page for [storeId]. Opens straight in the store, so rating is one tap. */
fun storeAppPage(storeId: String): String = "market://details?id=$storeId"

/** The same page on the web, for a phone with no store app and for the link a friend receives. */
fun storeWebPage(storeId: String): String = "https://play.google.com/store/apps/details?id=$storeId"
