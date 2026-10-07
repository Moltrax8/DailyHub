package com.moltrax.personalnoteapp.domain.repository

import com.moltrax.personalnoteapp.domain.model.GithubActivity
import com.moltrax.personalnoteapp.domain.model.GithubConnection
import com.moltrax.personalnoteapp.domain.model.GithubPublicRepo
import com.moltrax.personalnoteapp.domain.model.GithubRepo
import com.moltrax.personalnoteapp.domain.model.GithubReposResult
import kotlinx.coroutines.flow.Flow
import java.io.IOException

/** Thrown when the server has no GitHub connection for this user. */
class GithubNotConnectedException : IOException("not_connected")

/**
 * GitHub integration (Phase 7). No PAT on device: OAuth connects
 * server-side, the app only links repos and reads activity. Push arrives
 * via FCM (Phase 7) into the `dev_activity` channel.
 */
interface GitHubRepository {
    suspend fun myConnection(): GithubConnection?
    suspend fun spaceRepos(spaceId: String): List<GithubRepo>

    /**
     * Links a repo the user can access (id + full_name from the repo picker,
     * which lists accessible repos through the server-held token).
     */
    suspend fun linkRepo(spaceId: String, repoId: Long, fullName: String, private: Boolean)
    suspend fun unlinkRepo(repoId: Long)

    /**
     * Public repos of a GitHub username (api.github.com, no token). Only public
     * repos are listed — private ones still go through manual ID entry.
     */
    suspend fun publicRepos(username: String): List<GithubPublicRepo>

    fun observeActivity(spaceId: String): Flow<List<GithubActivity>>
    suspend fun refreshActivity(spaceId: String)

    /** Per-kind toggles (absent = enabled). */
    suspend fun notifPrefs(spaceId: String): Map<String, Boolean>
    suspend fun setNotifPref(spaceId: String, kind: String, enabled: Boolean)

    /** Registers/refreshes this device's FCM token server-side. */
    suspend fun registerFcmToken(token: String)

    /**
     * GitHub App linking (spec github-link-spec.md). No token ever touches the
     * device: `connectStart` returns the authorize URL to open in a browser,
     * the `dailyhub://github-callback` deep link carries code+state back, and
     * `connectFinish` completes the link server-side, returning the login.
     */
    suspend fun connectStart(mode: String? = null): String
    suspend fun connectFinish(code: String, state: String): String
    suspend fun disconnectGitHub()

    /**
     * Repos the linked account can access through the App installation(s).
     * Throws [GithubNotConnectedException] when no account is linked.
     */
    suspend fun appRepos(): GithubReposResult

    companion object {
        /** First event slice (toggles + feed stay in sync with the function). */
        val KINDS = listOf("issue.opened", "pr.opened", "comment", "review", "state_changed", "release")
    }
}
