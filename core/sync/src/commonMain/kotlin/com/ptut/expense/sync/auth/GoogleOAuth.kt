package com.ptut.expense.sync.auth

import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.bodyAsText
import io.ktor.http.encodeURLParameter
import io.ktor.http.isSuccess
import io.ktor.http.parameters
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Google sign-in without Google's SDK, for iOS (#187): the system browser shows Google's own page,
 * and a PKCE code comes back to the app's reversed client-id scheme. Exchanging it here keeps every
 * network call in `sync/`, where the source rules allow one.
 */
internal class GoogleOAuth(private val http: HttpClient, private val clientId: String) {

    /** "com.googleusercontent.apps.123-abc": the iOS client id turned around, as Google requires. */
    val callbackScheme: String = clientId.split('.').reversed().joinToString(".")

    private val redirectUri = "$callbackScheme:/oauth2redirect"

    /** A fresh request: the page to open, and the secret the answer has to be traded with. */
    @OptIn(ExperimentalUuidApi::class)
    fun request(): Request {
        val verifier = Uuid.random().toHexString() + Uuid.random().toHexString()
        val state = Uuid.random().toHexString()
        val url = "https://accounts.google.com/o/oauth2/v2/auth" +
            "?client_id=${clientId.encodeURLParameter()}" +
            "&redirect_uri=${redirectUri.encodeURLParameter()}" +
            "&response_type=code" +
            "&scope=${"openid email profile".encodeURLParameter()}" +
            "&code_challenge=${sha256Base64Url(verifier)}" +
            "&code_challenge_method=S256" +
            "&state=$state"
        return Request(url, verifier, state)
    }

    /** The ID token for the code the browser came back with. */
    suspend fun exchange(callbackUrl: String, request: Request): String {
        val query = callbackUrl.substringAfter('?', "").split('&').associate { it.substringBefore('=') to it.substringAfter('=', "") }
        if (query["state"] != request.state) throw AuthException("Google's answer did not match the request")
        query["error"]?.let { throw if (it == "access_denied") SignInCancelled() else AuthException("Google said $it") }
        val code = query["code"]?.decodeUrl() ?: throw AuthException("Google sent no code")
        val response = http.submitForm(
            "https://oauth2.googleapis.com/token",
            parameters {
                append("code", code)
                append("client_id", clientId)
                append("redirect_uri", redirectUri)
                append("code_verifier", request.verifier)
                append("grant_type", "authorization_code")
            },
        )
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) throw AuthException("Google's token exchange said ${response.status.value}")
        return Json.parseToJsonElement(text).jsonObject["id_token"]?.jsonPrimitive?.content
            ?: throw AuthException("Google sent no ID token")
    }

    data class Request(val url: String, val verifier: String, val state: String)
}

private fun String.decodeUrl(): String = buildString {
    var index = 0
    val source = this@decodeUrl
    while (index < source.length) {
        val char = source[index]
        if (char == '%' && index + 2 < source.length) {
            append(source.substring(index + 1, index + 3).toInt(HEX).toChar())
            index += 3
        } else {
            append(if (char == '+') ' ' else char)
            index++
        }
    }
}

private const val HEX = 16
