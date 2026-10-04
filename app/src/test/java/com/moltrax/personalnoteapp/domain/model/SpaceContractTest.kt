package com.moltrax.personalnoteapp.domain.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Shared-space wire shapes (Phase 5): snake_case fields and enums must match
 * the PostgREST contract exactly.
 */
class SpaceContractTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `space round-trips with server names`() {
        val space = Space(id = "s1", type = SpaceType.DUO, name = "Duo",
            createdBy = "u1", createdAt = "2026-01-01T00:00:00Z", updatedAt = "2026-01-02T00:00:00Z")
        assertEquals(space, json.decodeFromString<Space>(json.encodeToString(space)))
    }

    @Test
    fun `shared task round-trips`() {
        val task = SharedTask(id = "t1", spaceId = "s1", title = "Buy milk",
            isDone = true, assignee = "u2", dueAt = 123L, sortOrder = 2L)
        val decoded = json.decodeFromString<SharedTask>(
            """{"id":"t1","space_id":"s1","title":"Buy milk","is_done":true,"assignee":"u2","due_at":123,"sort_order":2}"""
        )
        assertEquals(task, decoded)
    }

    @Test
    fun `duoName falls back`() {
        assertEquals("Marta", duoName("Marta"))
        assertEquals("Duo", duoName(null))
    }
}
