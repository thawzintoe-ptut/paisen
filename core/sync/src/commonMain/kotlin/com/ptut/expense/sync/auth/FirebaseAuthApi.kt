package com.ptut.expense.sync.auth

import com.ptut.expense.sync.FirestoreStatus
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.encodeURLParameter
import io.ktor.http.isSuccess
import io.ktor.http.parameters
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** What Firebase Auth hands back after a sign-in or a refresh. */
internal data class AuthTokens(
    val uid: String,
    val idToken: String,
    val refreshToken: String,
    val expiresAtEpochMillis: Long,
    val displayName: String? = null,
    val email: String? = null,
)

/**
 * [sessionEnded]: the refresh token no longer works, so only signing in again helps (#195).
 * [needsFreshSignIn]: Firebase deletes an account only after a recent sign-in.
 */
internal class AuthException(
    message: String,
    val sessionEnded: Boolean = false,
    val needsFreshSignIn: Boolean = false,
) : Exception(message)

/** What [FirebaseAuthRepository] asks of Firebase Auth, so a test can stand in for it. */
internal interface AuthApi {
    suspend fun signInWithIdp(providerId: String, idToken: String, rawNonce: String?, nowMillis: Long): AuthTokens

    suspend fun refresh(refreshToken: String, nowMillis: Long): AuthTokens

    suspend fun delete(idToken: String)
}

/**
 * #187: Firebase Auth's REST API. A Google or Apple ID token from the phone becomes a Firebase
 * account; the refresh token keeps it signed in; delete removes the account itself.
 */
internal class FirebaseAuthApi(private val http: HttpClient, private val apiKey: String) : AuthApi {

    /** [providerId] is "google.com" or "apple.com". Apple needs the raw nonce it was asked with. */
    override suspend fun signInWithIdp(providerId: String, idToken: String, rawNonce: String?, nowMillis: Long): AuthTokens {
        val postBody = buildString {
            append("id_token=${idToken.encodeURLParameter()}&providerId=$providerId")
            if (rawNonce != null) append("&nonce=${rawNonce.encodeURLParameter()}")
        }
        val json = postJson(
            "https://identitytoolkit.googleapis.com/v1/accounts:signInWithIdp?key=$apiKey",
            buildJsonObject {
                put("postBody", postBody)
                put("requestUri", "http://localhost")
                put("returnSecureToken", true)
                put("returnIdpCredential", true)
            },
        )
        return AuthTokens(
            uid = json.text("localId"),
            idToken = json.text("idToken"),
            refreshToken = json.text("refreshToken"),
            expiresAtEpochMillis = nowMillis + json.text("expiresIn").toLong() * MILLIS_PER_SECOND,
            displayName = json.textOrNull("displayName") ?: json.textOrNull("firstName"),
            email = json.textOrNull("email"),
        )
    }

    override suspend fun refresh(refreshToken: String, nowMillis: Long): AuthTokens {
        val json = answered {
            http.submitForm(
                "https://securetoken.googleapis.com/v1/token?key=$apiKey",
                parameters {
                    append("grant_type", "refresh_token")
                    append("refresh_token", refreshToken)
                },
            )
        }
        return AuthTokens(
            uid = json.text("user_id"),
            idToken = json.text("id_token"),
            refreshToken = json.text("refresh_token"),
            expiresAtEpochMillis = nowMillis + json.text("expires_in").toLong() * MILLIS_PER_SECOND,
        )
    }

    /** Apple guideline 5.1.1(v): the account goes, and with it what Firebase holds for it. */
    override suspend fun delete(idToken: String) {
        postJson("https://identitytoolkit.googleapis.com/v1/accounts:delete?key=$apiKey", buildJsonObject { put("idToken", idToken) })
    }

    private suspend fun postJson(url: String, body: JsonObject): JsonObject = answered {
        http.post(url) {
            contentType(ContentType.Application.Json)
            setBody(body.toString())
        }
    }

    /** No answer, or one that is not JSON, fails as an [AuthException], which sync reads as offline. */
    private suspend fun answered(request: suspend () -> HttpResponse): JsonObject = try {
        val response = request()
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) throw authFailure(response.status.value, text)
        Json.parseToJsonElement(text).jsonObject
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: AuthException) {
        throw failure
    } catch (failure: Exception) {
        throw AuthException(failure.message ?: "No answer from Firebase Auth")
    }

    private fun JsonObject.text(key: String): String = textOrNull(key) ?: throw AuthException("Firebase Auth left out $key")

    private fun JsonObject.textOrNull(key: String): String? = this[key]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }

    private companion object {
        const val MILLIS_PER_SECOND = 1_000L
    }
}

/** A refused Firebase Auth call, read once from its [status] and [body]. */
internal fun authFailure(status: Int, body: String): AuthException {
    val ended = status in SESSION_STATUSES && SESSION_ENDED.any { it in body }
    return AuthException(
        "Firebase Auth said $status: ${body.take(MESSAGE_LENGTH)}",
        sessionEnded = ended,
        needsFreshSignIn = CREDENTIAL_TOO_OLD in body,
    )
}

private const val MESSAGE_LENGTH = 300

private val SESSION_STATUSES = setOf(FirestoreStatus.BAD_REQUEST, FirestoreStatus.UNAUTHORIZED, FirestoreStatus.FORBIDDEN)

/** Firebase's codes for a refresh token that will never work again. */
private val SESSION_ENDED = listOf("TOKEN_EXPIRED", "INVALID_REFRESH_TOKEN", "USER_DISABLED", "USER_NOT_FOUND", "INVALID_GRANT")

/** Firebase's code for a delete that needs a recent sign-in. */
private const val CREDENTIAL_TOO_OLD = "CREDENTIAL_TOO_OLD"
