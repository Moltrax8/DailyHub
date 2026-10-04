package com.moltrax.personalnoteapp.data.remote.supabase.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** GoTrue user object (subset we use). */
@Serializable
data class GoTrueUser(
    val id: String,
    val email: String? = null,
)

/** GoTrue session (sign-up / token responses). */
@Serializable
data class GoTrueSession(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String = "",
    @SerialName("expires_in") val expiresIn: Long = 3600L,
    val user: GoTrueUser? = null,
)

/** Stored session + when it was saved (for expiry math). */
@Serializable
data class StoredSession(
    val session: GoTrueSession,
    val savedAt: Long,
)

@Serializable
data class EmailCredentials(
    val email: String,
    val password: String,
)

@Serializable
data class RefreshRequest(
    @SerialName("refresh_token") val refreshToken: String,
)

/**
 * True when the token must be refreshed: expired or expiring within
 * [marginSecs]. Pure (JVM-tested).
 */
fun isSessionExpired(expiresInSecs: Long, savedAtMs: Long, nowMs: Long, marginSecs: Long = 60L): Boolean =
    nowMs + marginSecs * 1000L >= savedAtMs + expiresInSecs * 1000L

/**
 * Human-readable message from a GoTrue error body, e.g.
 * `{"message":"Invalid login credentials"}` or `{"msg":"User already
 * registered"}`. Falls back to the HTTP code. Pure (JVM-tested).
 */
fun parseGoTrueError(httpCode: Int, body: String?): String {
    if (!body.isNullOrBlank()) {
        // Handles escaped quotes inside server messages (e.g. Email address \"x\" is invalid).
        val quoted = Regex("\"(?:message|msg|error_description)\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
            .find(body)?.groupValues?.getOrNull(1)
            ?.replace("\\\"", "\"")?.replace("\\\\", "\\")
        if (!quoted.isNullOrBlank()) return quoted
    }
    return when (httpCode) {
        400 -> "Invalid request."
        401 -> "Invalid login credentials."
        422 -> "Account already exists — sign in instead."
        429 -> "Too many attempts — try again later."
        else -> "Authentication failed (HTTP $httpCode)."
    }
}
