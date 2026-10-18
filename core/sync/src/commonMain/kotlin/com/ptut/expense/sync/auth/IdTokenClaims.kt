package com.ptut.expense.sync.auth

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlin.io.encoding.Base64

/**
 * When this account signed in on this phone, in seconds, from the ID token's `auth_time`. Firebase's
 * server sets it and a refreshed token keeps it, so a phone's own clock never changes it.
 */
internal fun authTimeOf(idToken: String): Long? = runCatching {
    val payload = idToken.split('.')[1]
    val json = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL).decode(payload).decodeToString()
    Json.parseToJsonElement(json).jsonObject["auth_time"]?.jsonPrimitive?.long
}.getOrNull()
