package com.moltrax.personalnoteapp.data.repository

import com.moltrax.personalnoteapp.data.remote.supabase.SupabaseAuthService
import com.moltrax.personalnoteapp.data.remote.supabase.SupabaseDbApi
import com.moltrax.personalnoteapp.domain.model.GithubActivity
import com.moltrax.personalnoteapp.domain.model.GithubConnection
import com.moltrax.personalnoteapp.domain.model.GithubRepo
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
        val res = api().linkRepo(
            bearer(),
            body = buildJsonObject {
                put("id", repoId)
                put("space_id", spaceId)
                put("full_name", fullName.trim())
                put("private", private)
                put("installed_by", myId())
            },
        )
        if (!res.isSuccessful) throw IOException("Link failed (HTTP ${res.code()}).")
    }

    override suspend fun unlinkRepo(repoId: Long) {
        val res = api().unlinkRepo(bearer(), "eq.$repoId")
        if (!res.isSuccessful) throw IOException("Unlink failed (HTTP ${res.code()}).")
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
}
