package com.moltrax.personalnoteapp.domain.repository

import com.moltrax.personalnoteapp.domain.model.GithubActivity
import com.moltrax.personalnoteapp.domain.model.GithubConnection
import com.moltrax.personalnoteapp.domain.model.GithubRepo
import kotlinx.coroutines.flow.Flow

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

    fun observeActivity(spaceId: String): Flow<List<GithubActivity>>
    suspend fun refreshActivity(spaceId: String)

    /** Per-kind toggles (absent = enabled). */
    suspend fun notifPrefs(spaceId: String): Map<String, Boolean>
    suspend fun setNotifPref(spaceId: String, kind: String, enabled: Boolean)

    /** Registers/refreshes this device's FCM token server-side. */
    suspend fun registerFcmToken(token: String)

    companion object {
        /** First event slice (toggles + feed stay in sync with the function). */
        val KINDS = listOf("issue.opened", "pr.opened", "comment", "review", "state_changed", "release")
    }
}
