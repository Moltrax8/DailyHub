package com.moltrax.personalnoteapp.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** One membership system for Duo hubs and Projects (Phase 5+). */
@Serializable
enum class SpaceType {
    @SerialName("DUO") DUO,
    @SerialName("PROJECT") PROJECT,
}

@Serializable
data class Space(
    val id: String,
    val type: SpaceType,
    val name: String? = null,
    @SerialName("created_by") val createdBy: String? = null,
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("updated_at") val updatedAt: String = "",
)

@Serializable
data class SpaceMember(
    @SerialName("space_id") val spaceId: String,
    @SerialName("user_id") val userId: String,
    val role: String = "member",
    @SerialName("joined_at") val joinedAt: String = "",
) {
    val isOwner: Boolean get() = role == "owner"
}

@Serializable
data class SharedNote(
    val id: String,
    @SerialName("space_id") val spaceId: String,
    val author: String? = null,
    val title: String? = null,
    @SerialName("body_md") val bodyMd: String? = null,
    @SerialName("updated_at") val updatedAt: String = "",
)

@Serializable
data class SharedTask(
    val id: String,
    @SerialName("space_id") val spaceId: String,
    val title: String,
    @SerialName("is_done") val isDone: Boolean = false,
    val assignee: String? = null,
    @SerialName("due_at") val dueAt: Long? = null,
    @SerialName("sort_order") val sortOrder: Long = 0L,
    @SerialName("updated_at") val updatedAt: String = "",
)

@Serializable
data class SpaceLink(
    val id: String,
    @SerialName("space_id") val spaceId: String,
    val url: String,
    val title: String? = null,
    @SerialName("created_by") val createdBy: String? = null,
    @SerialName("created_at") val createdAt: String = "",
)

/** Display name for a Duo hub (the other member's username, resolved by UI). */
fun duoName(otherUsername: String?): String = otherUsername ?: "Duo"
