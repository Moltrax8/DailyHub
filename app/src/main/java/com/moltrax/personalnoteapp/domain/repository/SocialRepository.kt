package com.moltrax.personalnoteapp.domain.repository

import com.moltrax.personalnoteapp.domain.model.FriendRequest
import com.moltrax.personalnoteapp.domain.model.SupabaseProfile
import kotlinx.coroutines.flow.Flow

/**
 * Friends graph (Phase 4). Public identifier is the username, never email.
 */
interface SocialRepository {
    /** Prefix search by username (ilike). */
    suspend fun searchByUsername(prefix: String, limit: Int = 20): List<SupabaseProfile>

    /** Exact profile lookup (citext match). Null when absent. */
    suspend fun getByUsername(username: String): SupabaseProfile?

    suspend fun sendRequest(toUserId: String): FriendRequest
    suspend fun cancelRequest(requestId: String)
    suspend fun acceptRequest(requestId: String)
    suspend fun rejectRequest(requestId: String)

    /** Requests I sent (any status). */
    suspend fun outgoing(): List<FriendRequest>

    /** Requests sent to me (any status). */
    suspend fun incoming(): List<FriendRequest>

    /** Accepted friendships as profile rows. */
    suspend fun friends(): List<SupabaseProfile>

    /** Removes a friendship (deletes the accepted request both ways). */
    suspend fun removeFriend(friendUserId: String)

    /** Live incoming count for badges. */
    fun observeIncoming(): Flow<List<FriendRequest>>
}
