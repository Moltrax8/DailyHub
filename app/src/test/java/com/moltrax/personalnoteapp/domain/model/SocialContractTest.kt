package com.moltrax.personalnoteapp.domain.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Friend-request wire shape (Phase 4): snake_case fields and status values
 * must match the PostgREST contract exactly.
 */
class SocialContractTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `request round-trips with server names`() {
        val req = FriendRequest(id = "r1", fromId = "u-a", toId = "u-b", status = FriendRequestStatus.ACCEPTED)
        val decoded = json.decodeFromString<FriendRequest>(json.encodeToString(req))
        assertEquals(req, decoded)
    }

    @Test
    fun `status decodes server values`() {
        val decoded = json.decodeFromString<FriendRequest>(
            """{"id":"r1","from_id":"a","to_id":"b","status":"rejected"}"""
        )
        assertEquals(FriendRequestStatus.REJECTED, decoded.status)
    }

    @Test
    fun `missing status defaults to pending`() {
        val decoded = json.decodeFromString<FriendRequest>(
            """{"id":"r1","from_id":"a","to_id":"b"}"""
        )
        assertEquals(FriendRequestStatus.PENDING, decoded.status)
    }
}
