package com.moltrax.personalnoteapp.data.repository

import com.moltrax.personalnoteapp.data.remote.github.GitHubConnectApi
import com.moltrax.personalnoteapp.data.remote.github.GitHubPublicApi
import com.moltrax.personalnoteapp.data.remote.github.ConnectActionBody
import com.moltrax.personalnoteapp.data.remote.supabase.SupabaseAuthService
import com.moltrax.personalnoteapp.data.remote.supabase.SupabaseDbApi
import com.moltrax.personalnoteapp.domain.model.GithubActivity
import com.moltrax.personalnoteapp.domain.model.GithubConnection
import com.moltrax.personalnoteapp.domain.model.GithubPublicRepo
import com.moltrax.personalnoteapp.domain.model.GithubRepo
import com.moltrax.personalnoteapp.domain.model.GithubReposResult
import com.moltrax.personalnoteapp.domain.repository.GithubNotConnectedException
import com.moltrax.personalnoteapp.domain.repository.GitHubRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GitHubRepositoryImpl @Inject constructor(
    private val db: SupabaseDbApi?,
    private val auth: SupabaseAuthService,
    private val publicApi: GitHubPublicApi,
    private val connectApi: GitHubConnectApi?,
) : GitHubRepository {

    private fun api(): SupabaseDbApi =
        db ?: throw IllegalStateException("Supabase is not configured.")

    private suspend fun bearer(): String {
        val token = auth.freshToken() ?: throw IllegalStateException("Not signed in.")
        return "Bearer $token"
    }

    private suspend fun myId(): String =
        auth.currentUserId() ?: throw IllegalStateException("Not signed in.")

    /** Per-space in-memory feed (pull-on-open; FCM only notifies). */
    private val feeds = mutableMapOf<String, MutableStateFlow<List<GithubActivity>>>()

    override suspend fun myConnection(): GithubConnection? {
        val res = api().myGithubConnection(bearer(), "eq.${myId()}")
        if (!res.isSuccessful) throw IOException("Connection load failed (HTTP ${res.code()}).")
        return res.body()?.firstOrNull()
    }

    override suspend fun spaceRepos(spaceId: String): List<GithubRepo> {
        val res = api().spaceRepos(bearer(), "eq.$spaceId")
        if (!res.isSuccessful) throw IOException("Repos load failed (HTTP ${res.code()}).")
        return res.body().orEmpty()
    }

    override suspend fun linkRepo(spaceId: String, repoId: Long, fullName: String, private: Boolean) {
        val name = fullName.trim()
        require(repoId > 0) { "Enter the numeric Repo ID (from api.github.com/repos/owner/repo → id)." }
        require(name.matches(Regex("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+"))) {
            "Enter the repo as owner/name (e.g. moltrax8/DailyHub)."
        }
        val res = api().linkRepo(
            bearer(),
            body = buildJsonObject {
                put("id", repoId)
                put("space_id", spaceId)
                put("full_name", name)
                put("private", private)
                put("installed_by", myId())
            },
        )
        if (!res.isSuccessful) throw IOException(linkError(res.code()))
    }

    override suspend fun unlinkRepo(repoId: Long) {
        val res = api().unlinkRepo(bearer(), "eq.$repoId")
        if (!res.isSuccessful) throw IOException("Unlink failed (HTTP ${res.code()}).")
    }

    override suspend fun publicRepos(username: String): List<GithubPublicRepo> {
        val clean = username.trim().trimStart('@')
        require(clean.matches(Regex("[A-Za-z0-9]([A-Za-z0-9-]*[A-Za-z0-9])?"))) {
            "Enter a GitHub username (e.g. moltrax8)."
        }
        val res = runCatching { publicApi.publicRepos(clean) }.getOrElse {
            throw IOException("GitHub is unreachable — check connection.")
        }
        if (!res.isSuccessful) {
            if (res.code() == 404) throw IOException("GitHub user \"$clean\" not found.")
            if (res.code() == 403) throw IOException("GitHub rate limit hit — try again later.")
            throw IOException("GitHub lookup failed (HTTP ${res.code()}).")
        }
        return res.body().orEmpty()
    }

    private fun linkError(code: Int): String = when (code) {
        401, 403 -> "Link denied — only a space owner can link repos (RLS)."
        404 -> "Space not found — pull the space first, then link."
        409 -> "Repo already linked to a space."
        else -> "Link failed (HTTP $code)."
    }

    override fun observeActivity(spaceId: String): Flow<List<GithubActivity>> =
        feeds.getOrPut(spaceId) { MutableStateFlow(emptyList()) }.asStateFlow()

    override suspend fun refreshActivity(spaceId: String) {
        val res = api().spaceActivity(bearer(), "eq.$spaceId")
        if (!res.isSuccessful) throw IOException("Activity load failed (HTTP ${res.code()}).")
        feeds.getOrPut(spaceId) { MutableStateFlow(emptyList()) }.update { res.body().orEmpty() }
    }

    override suspend fun notifPrefs(spaceId: String): Map<String, Boolean> {
        val res = api().myNotifPrefs(bearer(), "eq.${myId()}", "eq.$spaceId")
        if (!res.isSuccessful) throw IOException("Prefs load failed (HTTP ${res.code()}).")
        return res.body().orEmpty().associate { it.kind to it.enabled }
    }

    override suspend fun setNotifPref(spaceId: String, kind: String, enabled: Boolean) {
        val res = api().setNotifPref(
            bearer(),
            body = buildJsonObject {
                put("user_id", myId())
                put("space_id", spaceId)
                put("kind", kind)
                put("enabled", enabled)
            },
        )
        if (!res.isSuccessful) throw IOException("Pref save failed (HTTP ${res.code()}).")
    }

    override suspend fun registerFcmToken(token: String) {
        val clean = token.trim()
        if (clean.isEmpty()) return
        val res = api().registerFcmToken(
            bearer(),
            body = mapOf("user_id" to myId(), "token" to clean),
        )
        if (!res.isSuccessful) throw IOException("Token register failed (HTTP ${res.code()}).")
    }

    // -- GitHub App linking (Edge Functions; token stays server-side) --------

    private fun functions(): GitHubConnectApi =
        connectApi ?: throw IllegalStateException("Supabase is not configured.")

    override suspend fun connectStart(): String {
        val res = functions().connect(bearer(), ConnectActionBody(action = "start"))
        if (!res.isSuccessful) throw IOException("GitHub connect failed (HTTP ${res.code()}).")
        val url = res.body()?.url?.trim().orEmpty()
        if (url.isEmpty()) throw IOException("GitHub connect failed (empty authorize URL).")
        return url
    }

    override suspend fun connectFinish(code: String, state: String): String {
        require(code.isNotBlank() && state.isNotBlank()) { "Invalid GitHub callback." }
        val res = functions().connect(bearer(), ConnectActionBody(action = "finish", code = code, state = state))
        if (!res.isSuccessful) throw IOException("GitHub link failed (HTTP ${res.code()}).")
        val body = res.body()
        if (body?.error != null) throw IOException("GitHub link failed (${body.error}).")
        val login = body?.login?.trim().orEmpty()
        if (login.isEmpty()) throw IOException("GitHub link failed (empty login).")
        return login
    }

    override suspend fun disconnectGitHub() {
        val res = functions().connect(bearer(), ConnectActionBody(action = "disconnect"))
        if (!res.isSuccessful) throw IOException("GitHub disconnect failed (HTTP ${res.code()}).")
    }

    override suspend fun appRepos(): GithubReposResult {
        val res = functions().repos(bearer())
        if (!res.isSuccessful) {
            if (res.code() == 404) throw GithubNotConnectedException()
            throw IOException("Repos load failed (HTTP ${res.code()}).")
        }
        return res.body() ?: GithubReposResult()
    }
}
