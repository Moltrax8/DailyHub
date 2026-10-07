package com.moltrax.personalnoteapp.data.repository

import com.moltrax.personalnoteapp.BuildConfig
import com.moltrax.personalnoteapp.data.local.preferences.AppPreferences
import com.moltrax.personalnoteapp.data.remote.supabase.RealtimeClient
import com.moltrax.personalnoteapp.di.SupabaseConfig
import com.moltrax.personalnoteapp.domain.model.AppRelease
import com.moltrax.personalnoteapp.domain.model.isAllowedApkHost
import com.moltrax.personalnoteapp.domain.model.isUpdateAvailable
import com.moltrax.personalnoteapp.domain.model.parseGithubLatestRelease
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Automatic update checks (Phase 9, opt-in, default OFF). The app NEVER polls
 * GitHub: one Supabase fetch at startup (when it was closed) + Realtime
 * INSERT subscription while open. Download comes from the GitHub release
 * asset URL stored on the row.
 */
@Singleton
class UpdateRepository @Inject constructor(
    private val http: OkHttpClient,
    private val config: SupabaseConfig,
    private val realtime: RealtimeClient,
    private val prefs: AppPreferences,
) {
    private val json = Json { ignoreUnknownKeys = true }

    private val _latest = MutableStateFlow<AppRelease?>(null)
    val latest: Flow<AppRelease?> = _latest.asStateFlow()

    fun observeEnabled(): Flow<Boolean> = prefs.autoUpdate

    suspend fun setEnabled(v: Boolean) = prefs.setAutoUpdate(v)

    /**
     * Single check (also refreshes the flow). Null when unavailable. [allowGitHubFallback] is for the
     * user-initiated "Check for updates" tap only: when the Supabase `app_releases` table has no row
     * (it is only filled by the release webhook) ONE request goes to GitHub's public releases endpoint.
     * Background/startup checks keep it false, so the app still never polls GitHub on its own.
     */
    suspend fun checkNow(allowGitHubFallback: Boolean = false): AppRelease? {
        val release = fetchLatest() ?: if (allowGitHubFallback) fetchLatestFromGitHub() else null
        if (release != null) _latest.update { release }
        return release
    }

    private suspend fun fetchLatestFromGitHub(): AppRelease? = kotlinx.coroutines.withContext(
        kotlinx.coroutines.Dispatchers.IO,
    ) {
        val req = Request.Builder()
            .url("https://api.github.com/repos/Moltrax8/DailyHub/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .get()
            .build()
        runCatching {
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                parseGithubLatestRelease(resp.body?.string().orEmpty())
            }
        }.getOrNull()
    }

    /** Live INSERT subscription on app_releases; returns a close handle. */
    fun subscribeReleases(onRelease: (AppRelease) -> Unit): RealtimeClient.Subscription =
        realtime.subscribeInserts("app_releases") { recordJson ->
            runCatching {
                val release = json.decodeFromString<AppRelease>(recordJson)
                _latest.update { release }
                onRelease(release)
            }
        }

    fun isNewerThanInstalled(release: AppRelease, installedCode: Int = BuildConfig.VERSION_CODE): Boolean =
        isUpdateAvailable(release, installedCode)

    companion object {
        /** Hard cap for APK downloads: abort and delete the partial file past this. */
        const val MAX_APK_BYTES = 200L * 1024 * 1024
    }

    /** Streams the APK to [dest] (cache). Throws on HTTP/network errors. */
    suspend fun downloadApk(apkUrl: String, dest: File, onProgress: (Long, Long?) -> Unit = { _, _ -> }) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            require(apkUrl.startsWith("https://")) { "Refusing non-HTTPS URL." }
            require(isAllowedApkHost(apkUrl)) { "Refusing untrusted download host." }
            val req = Request.Builder().url(apkUrl).get().build()
            try {
                http.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) throw IOException("Download failed (HTTP ${resp.code}).")
                    val body = resp.body ?: throw IOException("Empty download.")
                    val total = body.contentLength().takeIf { it > 0 }
                    if (total != null && total > MAX_APK_BYTES) throw IOException("Update file too large.")
                    dest.parentFile?.mkdirs()
                    dest.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        var read: Int
                        var done = 0L
                        while (body.byteStream().read(buf).also { read = it } != -1) {
                            done += read
                            if (done > MAX_APK_BYTES) throw IOException("Update file too large.")
                            out.write(buf, 0, read)
                            onProgress(done, total)
                        }
                    }
                }
            } catch (e: Exception) {
                // Never leave a truncated/oversized partial file behind.
                runCatching { if (dest.exists()) dest.delete() }
                throw e
            }
        }
    }

    private suspend fun fetchLatest(): AppRelease? {
        if (!config.isConfigured) return null
        val req = Request.Builder()
            .url("${config.url}/rest/v1/app_releases?select=*&order=version_code.desc&limit=1")
            .header("apikey", config.anonKey)
            .get()
            .build()
        return runCatching {
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body?.string().orEmpty()
                json.decodeFromString<List<AppRelease>>(body).firstOrNull()
            }
        }.getOrNull()
    }
}
