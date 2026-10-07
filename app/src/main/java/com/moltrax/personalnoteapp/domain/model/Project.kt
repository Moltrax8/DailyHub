package com.moltrax.personalnoteapp.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Board columns — deliberately small (no Jira clone). */
@Serializable
enum class ProjectStatus {
    @SerialName("Idea") IDEA,
    @SerialName("Planned") PLANNED,
    @SerialName("Developing") DEVELOPING,
    @SerialName("Finished") FINISHED,
}

/**
 * Server values are "Idea" / "Planned" / "Developing" / "Finished" (see the @SerialName above), but the
 * enum constants are upper case. A case-sensitive valueOf() mapped EVERYTHING except an exact "IDEA" to
 * IDEA, so a moved card was saved correctly but always came back in the Idea column.
 */
fun projectStatusOf(raw: String?): ProjectStatus =
    ProjectStatus.entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) }
        ?: ProjectStatus.IDEA

@Serializable
data class Project(
    @SerialName("space_id") val spaceId: String,
    @SerialName("description_md") val descriptionMd: String? = null,
    @SerialName("board_columns") val boardColumns: List<String> = listOf("Idea", "Planned", "Developing", "Finished"),
)

@Serializable
data class ProjectItem(
    val id: String,
    @SerialName("space_id") val spaceId: String,
    val title: String,
    val status: ProjectStatus = ProjectStatus.IDEA,
    @SerialName("body_md") val bodyMd: String? = null,
    @SerialName("linked_url") val linkedUrl: String? = null,
    @SerialName("sort_order") val sortOrder: Long = 0L,
    @SerialName("updated_at") val updatedAt: String = "",
)

@Serializable
data class ProjectComment(
    val id: String,
    @SerialName("space_id") val spaceId: String,
    @SerialName("ref_type") val refType: String? = null,
    @SerialName("ref_id") val refId: String? = null,
    val author: String? = null,
    @SerialName("body_md") val bodyMd: String? = null,
    @SerialName("created_at") val createdAt: String = "",
)

/** Groups items per board column, preserving each column's sort order. Pure. */
fun groupBoardItems(
    items: List<ProjectItem>,
    columns: List<String> = listOf("Idea", "Planned", "Developing", "Finished"),
): Map<String, List<ProjectItem>> {
    val byStatus = items.groupBy { it.status.name.lowercase() }
    val grouped = columns.associateWith { col ->
        byStatus[col.lowercase()].orEmpty().sortedBy { it.sortOrder }
    }.toMutableMap()
    // Unknown statuses (forward compat) must never vanish: fold them into the
    // Idea column (first column) so no card is hidden with data loss.
    val known = columns.map { it.lowercase() }.toSet()
    val unknown = items.filter { it.status.name.lowercase() !in known }.sortedBy { it.sortOrder }
    if (unknown.isNotEmpty()) {
        val ideaKey = columns.firstOrNull() ?: "Idea"
        grouped[ideaKey] = (grouped[ideaKey].orEmpty() + unknown).sortedBy { it.sortOrder }
    }
    return grouped
}
