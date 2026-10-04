package com.moltrax.personalnoteapp.data.repository

import com.moltrax.personalnoteapp.data.remote.supabase.SupabaseAuthService
import com.moltrax.personalnoteapp.data.remote.supabase.SupabaseDbApi
import com.moltrax.personalnoteapp.di.SupabaseConfig
import com.moltrax.personalnoteapp.domain.model.SupabaseProfile
import com.moltrax.personalnoteapp.domain.repository.ProfileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileRepositoryImpl @Inject constructor(
    private val db: SupabaseDbApi?,
    private val auth: SupabaseAuthService,
    private val config: SupabaseConfig,
    private val http: OkHttpClient,
) : ProfileRepository {

    private suspend fun bearer(): String {
        val token = auth.freshToken() ?: throw IllegalStateException("Not signed in.")
        return "Bearer $token"
    }

    override suspend fun getMyProfile(userId: String): SupabaseProfile? {
        val api = db ?: throw IllegalStateException("Supabase is not configured.")
        val res = api.getProfile(bearer(), "eq.$userId")
        if (!res.isSuccessful) throw IOException("Profile load failed (HTTP ${res.code()}).")
        return res.body()?.firstOrNull()
    }

    override suspend fun upsertMyProfile(profile: SupabaseProfile) {
        val api = db ?: throw IllegalStateException("Supabase is not configured.")
        val res = api.upsertProfile(bearer(), body = profile)
        if (!res.isSuccessful) {
            throw IOException(parsePostgrestError(res.code(), res.errorBody()?.string()))
        }
    }

    override suspend fun uploadAvatar(userId: String, bytes: ByteArray, extension: String): String {
        val token = auth.freshToken() ?: throw IllegalStateException("Not signed in.")
        val ext = extension.trimStart('.').lowercase().ifBlank { "jpg" }
        val path = "$userId/avatar.$ext"
        val mime = if (ext == "png") "image/png" else "image/jpeg"
        val req = Request.Builder()
            .url("${config.url}/storage/v1/object/avatars/$path")
            .header("apikey", config.anonKey)
            .header("Authorization", "Bearer $token")
            .header("x-upsert", "true")
            .post(bytes.toRequestBody(mime.toMediaType()))
            .build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("Avatar upload failed (HTTP ${resp.code}).")
        }
        // Cache-bust so a replaced avatar shows instantly (public bucket, CDN-cached).
        val bust = System.currentTimeMillis()
        return "${config.url}/storage/v1/object/public/avatars/$path?t=$bust"
    }

    override fun isSignedIn(): Flow<Boolean> =
        auth.sessionState.map { it is com.moltrax.personalnoteapp.data.remote.supabase.SessionState.SignedIn }

    private fun parsePostgrestError(code: Int, body: String?): String {
        if (!body.isNullOrBlank()) {
            val msg = runCatching {
                JSONObject(body).optString("message").takeIf { it.isNotBlank() }
            }.getOrNull()
            if (!msg.isNullOrBlank()) {
                // Unique violation (duplicate username) → friendly message.
                if (msg.contains("duplicate", ignoreCase = true)) return "Username is taken."
                return msg
            }
        }
        return "Profile save failed (HTTP $code)."
    }
}
