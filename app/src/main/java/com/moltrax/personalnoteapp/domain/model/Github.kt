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

/** Repo reachable through the linked GitHub App installation(s) (server-held token). */
@Serializable
data class GithubAppRepo(
    val id: Long,
    @SerialName("full_name") val fullName: String,
    val private: Boolean = false,
    val fork: Boolean = false,
    val description: String? = null,
)

/** Result of the `github-repos` function. */
@Serializable
data class GithubReposResult(
    val login: String = "",
    val repos: List<GithubAppRepo> = emptyList(),
    @SerialName("install_url") val installUrl: String? = null,
)

/** Parsed `dailyhub://github-callback?code=...&state=...` deep link. */
data class GithubCallback(val code: String, val state: String)

/**
 * Parses the GitHub OAuth callback deep link. Pure JVM logic (no Android
 * classes) so it stays unit-testable. Returns null unless the URI targets
 * the github-callback host and carries non-blank code + state.
 */
fun parseGithubCallback(uriString: String?): GithubCallback? {
    if (uriString.isNullOrBlank()) return null
    val uri = runCatching { java.net.URI(uriString.trim()) }.getOrNull() ?: return null
    if (uri.scheme != "dailyhub") return null
    // java.net.URI treats "dailyhub://github-callback?..." host as "github-callback".
    if (uri.host != "github-callback") return null
    val params = uri.rawQuery.orEmpty().split('&').mapNotNull {
        val eq = it.indexOf('=')
        if (eq <= 0) null else it.substring(0, eq) to it.substring(eq + 1)
    }.toMap()
    fun decode(s: String): String = runCatching {
        java.net.URLDecoder.decode(s, "UTF-8")
    }.getOrNull().orEmpty()
    val code = decode(params["code"].orEmpty()).trim()
    val state = decode(params["state"].orEmpty()).trim()
    if (code.isEmpty() || state.isEmpty()) return null
    return GithubCallback(code, state)
}

/**
 * Filters the account's repos by name query (case-insensitive substring on
 * full_name) with optional fork hiding. Pure logic, unit-tested.
 */
fun filterGithubAppRepos(
    repos: List<GithubAppRepo>,
    query: String,
    hideForks: Boolean = false,
): List<GithubAppRepo> {
    val q = query.trim().lowercase()
    return repos.filter { repo ->
        (!hideForks || !repo.fork) &&
            (q.isEmpty() || repo.fullName.lowercase().contains(q))
    }
}
