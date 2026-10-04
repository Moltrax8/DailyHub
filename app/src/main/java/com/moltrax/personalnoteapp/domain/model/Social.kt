package com.moltrax.personalnoteapp.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Directed friend request row (Phase 4, Supabase `friend_requests`). */
@Serializable
data class FriendRequest(
    val id: String,
    @SerialName("from_id") val fromId: String,
    @SerialName("to_id") val toId: String,
    val status: FriendRequestStatus = FriendRequestStatus.PENDING,
)

@Serializable
enum class FriendRequestStatus {
    @SerialName("pending") PENDING,
    @SerialName("accepted") ACCEPTED,
    @SerialName("rejected") REJECTED,
}

/** Symmetric friendship derived from an accepted request (one direction). */
@Serializable
data class Friend(
    @SerialName("user_id") val userId: String,
    @SerialName("friend_id") val friendId: String,
)
