package com.moltrax.personalnoteapp.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Expanded shared content (Phase 8). Events are single instances for now
 * (recurrence rules deferred); messages pull+poll until Realtime (Phase 9).
 */
@Serializable
data class SpaceEvent(
    val id: String,
    @SerialName("space_id") val spaceId: String,
    val title: String,
    @SerialName("start_at") val startAt: Long,
    @SerialName("end_at") val endAt: Long? = null,
    val rrule: String? = null,
    @SerialName("created_by") val createdBy: String? = null,
    @SerialName("created_at") val createdAt: String = "",
)

@Serializable
data class SpaceMessage(
    val id: String,
    @SerialName("space_id") val spaceId: String,
    val author: String? = null,
    val body: String,
    @SerialName("created_at") val createdAt: String = "",
)

@Serializable
data class SpaceFile(
    val id: String,
    @SerialName("space_id") val spaceId: String,
    val path: String,
    val size: Long = 0L,
    @SerialName("created_by") val createdBy: String? = null,
    @SerialName("created_at") val createdAt: String = "",
) {
    val displayName: String get() = path.substringAfterLast('/')
}

@Serializable
data class FeedEntry(
    val id: String,
    @SerialName("space_id") val spaceId: String,
    val kind: String,
    val ref: JsonObject = JsonObject(emptyMap()),
    val actor: String? = null,
    @SerialName("created_at") val createdAt: String = "",
)

/** Feed kinds written by the app (best-effort, never breaks features). */
object FeedKind {
    const val NOTE_ADDED = "note.added"
    const val TASK_ADDED = "task.added"
    const val LINK_ADDED = "link.added"
    const val ITEM_ADDED = "item.added"
    const val COMMENT_ADDED = "comment.added"
    const val MEMBER_JOINED = "member.joined"
    const val MEMBER_LEFT = "member.left"
}

/** Human line per feed kind (JVM-tested). */
fun feedKindTitle(kind: String): String = when (kind) {
    FeedKind.NOTE_ADDED -> "Note added"
    FeedKind.TASK_ADDED -> "Task added"
    FeedKind.LINK_ADDED -> "Link added"
    FeedKind.ITEM_ADDED -> "Card added"
    FeedKind.COMMENT_ADDED -> "Comment added"
    FeedKind.MEMBER_JOINED -> "Member joined"
    FeedKind.MEMBER_LEFT -> "Member left"
    else -> kind
}
