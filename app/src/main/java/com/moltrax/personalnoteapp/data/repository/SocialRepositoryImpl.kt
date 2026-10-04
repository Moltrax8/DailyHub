package com.moltrax.personalnoteapp.data.repository

import com.moltrax.personalnoteapp.data.remote.supabase.SupabaseAuthService
import com.moltrax.personalnoteapp.data.remote.supabase.SupabaseDbApi
import com.moltrax.personalnoteapp.domain.model.FriendRequest
import com.moltrax.personalnoteapp.domain.model.FriendRequestStatus
import com.moltrax.personalnoteapp.domain.model.SupabaseProfile
import com.moltrax.personalnoteapp.domain.repository.SocialRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.IOException
import java.net.URLEncoder
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SocialRepositoryImpl @Inject constructor(
    private val db: SupabaseDbApi?,
    private val auth: SupabaseAuthService,
) : SocialRepository {

    private fun api(): SupabaseDbApi =
        db ?: throw IllegalStateException("Supabase is not configured.")

    private suspend fun bearer(): String {
        val token = auth.freshToken() ?: throw IllegalStateException("Not signed in.")
        return "Bearer $token"
    }

    private suspend fun myId(): String =
        auth.currentUserId() ?: throw IllegalStateException("Not signed in.")

    override suspend fun searchByUsername(prefix: String, limit: Int): List<SupabaseProfile> {
        val q = prefix.trim()
        if (q.isEmpty()) return emptyList()
        val pattern = "ilike.*${URLEncoder.encode(q, "UTF-8")}*"
        val res = api().searchProfiles(bearer(), pattern, limit)
        if (!res.isSuccessful) throw IOException("Search failed (HTTP ${res.code()}).")
        val me = myId()
        return res.body().orEmpty().filter { it.id != me }
    }

    override suspend fun getByUsername(username: String): SupabaseProfile? {
        // citext equality is case-insensitive server-side.
        val res = api().getProfileByUsername(bearer(), "eq.$username")
        if (!res.isSuccessful) throw IOException("Lookup failed (HTTP ${res.code()}).")
        return res.body()?.firstOrNull()
    }

    override suspend fun sendRequest(toUserId: String): FriendRequest {
        val me = myId()
        require(toUserId != me) { "Cannot befriend yourself." }
        val id = UUID.randomUUID().toString()
        val res = api().sendRequest(
            bearer(),
            body = buildJsonObject {
                put("id", id)
                put("from_id", me)
                put("to_id", toUserId)
            },
        )
        if (!res.isSuccessful) throw IOException(parseError(res.code(), res.errorBody()?.string()))
        return FriendRequest(id, me, toUserId, FriendRequestStatus.PENDING)
    }

    override suspend fun cancelRequest(requestId: String) {
        deleteById(requestId, "Cancel failed")
    }

    override suspend fun acceptRequest(requestId: String) {
        // RLS denies non-recipients with 0 updated rows (200, empty body) rather
        // than an error; callers re-read lists afterwards, so a denial is a silent
        // no-op in the UI (the row visibly stays pending).
        val res = api().answerRequest(bearer(), "eq.$requestId", mapOf("status" to "accepted"))
        if (!res.isSuccessful) throw IOException(parseError(res.code(), res.errorBody()?.string()))
    }

    override suspend fun rejectRequest(requestId: String) {
        val res = api().answerRequest(bearer(), "eq.$requestId", mapOf("status" to "rejected"))
        if (!res.isSuccessful) throw IOException(parseError(res.code(), res.errorBody()?.string()))
    }

    override suspend fun outgoing(): List<FriendRequest> {
        val res = api().sentRequests(bearer(), "eq.${myId()}")
        if (!res.isSuccessful) throw IOException("Load failed (HTTP ${res.code()}).")
        return res.body().orEmpty()
    }

    override suspend fun incoming(): List<FriendRequest> {
        val res = api().receivedRequests(bearer(), "eq.${myId()}")
        if (!res.isSuccessful) throw IOException("Load failed (HTTP ${res.code()}).")
        return res.body().orEmpty()
    }

    override suspend fun friends(): List<SupabaseProfile> {
        val me = myId()
        return (outgoing() + incoming())
            .filter { it.status == FriendRequestStatus.ACCEPTED }
            .distinctBy { if (it.fromId == me) it.toId else it.fromId }
            .let { accepted ->
                // Resolve counterpart ids to profiles in one lookup per friend.
                accepted.mapNotNull { r ->
                    val other = if (r.fromId == me) r.toId else r.fromId
                    profileById(other)
                }
            }
    }

    override suspend fun removeFriend(friendUserId: String) {
        val me = myId()
        val edge = (outgoing() + incoming()).firstOrNull {
            it.status == FriendRequestStatus.ACCEPTED &&
                ((it.fromId == me && it.toId == friendUserId) || (it.fromId == friendUserId && it.toId == me))
        } ?: return
        deleteById(edge.id, "Remove failed")
    }

    override fun observeIncoming(): Flow<List<FriendRequest>> = flow {
        while (true) {
            emit(runCatching { incoming().filter { it.status == FriendRequestStatus.PENDING } }.getOrDefault(emptyList()))
            delay(POLL_MS)
        }
    }

    private suspend fun profileById(userId: String): SupabaseProfile? {
        val res = api().getProfile(bearer(), "eq.$userId")
        if (!res.isSuccessful) return null
        return res.body()?.firstOrNull()
    }

    private suspend fun deleteById(requestId: String, what: String) {
        val res = api().deleteRequest(bearer(), "eq.$requestId")
        if (!res.isSuccessful) throw IOException("$what (HTTP ${res.code()}).")
    }

    private fun parseError(code: Int, body: String?): String {
        if (!body.isNullOrBlank()) {
            val msg = Regex("\"message\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.getOrNull(1)
            if (!msg.isNullOrBlank()) {
                if (msg.contains("duplicate", ignoreCase = true)) return "Request already sent."
                return msg
            }
        }
        return "Request failed (HTTP $code)."
    }

    private companion object {
        // No polling GitHub from the phone (that rule is for GitHub); friend
        // requests refresh on open + every 30s while the social screens live.
        // True push arrives with Phase 7 (FCM).
        const val POLL_MS = 30_000L
    }
}
