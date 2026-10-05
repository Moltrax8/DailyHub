package com.moltrax.personalnoteapp.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/** GitHub identity link (token itself stays server-side, Phase 7). */
@Serializable
data class GithubConnection(
    @SerialName("user_id") val userId: String,
    @SerialName("github_login") val githubLogin: String? = null,
    val scopes: List<String> = emptyList(),
)

@Serializable
data class GithubRepo(
    val id: Long,
    @SerialName("space_id") val spaceId: String,
    @SerialName("full_name") val fullName: String,
    val private: Boolean = false,
)

@Serializable
data class GithubActivity(
    val id: String,
    @SerialName("space_id") val spaceId: String,
    @SerialName("repo_full") val repoFull: String? = null,
    val kind: String,
    /** Raw payload (numbers/strings mix from the webhook) — read via [refText]. */
    val ref: JsonObject = JsonObject(emptyMap()),
    @SerialName("created_at") val createdAt: String = "",
) {
    /** Human line: title/number/sender when present. */
    fun summary(): String {
        val title = refText("title") ?: refText("name")
        val number = refText("number") ?: refText("tag")
        val sender = refText("sender")
        return listOfNotNull(
            title,
            number?.let { "#$it" },
            sender?.let { "by $it" },
        ).joinToString(" ").ifBlank { kind }
    }

    private fun refText(key: String): String? =
        runCatching { ref[key]?.jsonPrimitive?.content }.getOrNull()
}

/** Per-kind push toggle row. */
@Serializable
data class NotifPrefRow(
    @SerialName("user_id") val userId: String,
    @SerialName("space_id") val spaceId: String,
    val kind: String,
    val enabled: Boolean = true,
)

/** Public repo entry from api.github.com (no token needed for public repos). */
@Serializable
data class GithubPublicRepo(
    val id: Long,
    @SerialName("full_name") val fullName: String,
    val private: Boolean = false,
    val description: String? = null,
    val fork: Boolean = false,
)

/** First event slice (workflow_failed arrives later). */
fun githubActivityTitle(kind: String): String = when (kind) {
    "issue.opened" -> "Issue opened"
    "pr.opened" -> "PR opened"
    "comment" -> "New comment"
    "review" -> "Review submitted"
    "state_changed" -> "State changed"
    "release" -> "Release published"
    else -> kind
}
