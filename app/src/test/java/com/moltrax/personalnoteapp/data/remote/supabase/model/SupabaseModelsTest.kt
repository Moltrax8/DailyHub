package com.moltrax.personalnoteapp.data.remote.supabase.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * GoTrue REST helpers — surfaced auth errors and token-expiry math.
 */
class SupabaseModelsTest {

    @Test
    fun `server message wins over http code`() {
        assertEquals(
            "Invalid login credentials",
            parseGoTrueError(400, """{"message":"Invalid login credentials"}"""),
        )
        assertEquals(
            "User already registered",
            parseGoTrueError(422, """{"msg":"User already registered"}"""),
        )
    }

    @Test
    fun `empty body falls back to code`() {
        assertEquals("Invalid login credentials.", parseGoTrueError(401, null))
        assertEquals("Account already exists — sign in instead.", parseGoTrueError(422, ""))
        assertEquals("Authentication failed (HTTP 500).", parseGoTrueError(500, "{}"))
    }

    @Test
    fun `fresh token is not expired`() {
        assertFalse(isSessionExpired(expiresInSecs = 3600L, savedAtMs = 0L, nowMs = 1000L))
    }

    @Test
    fun `past expiry counts as expired`() {
        assertTrue(isSessionExpired(expiresInSecs = 3600L, savedAtMs = 0L, nowMs = 3600_001L))
    }

    @Test
    fun `margin triggers early refresh`() {
        assertTrue(isSessionExpired(expiresInSecs = 3600L, savedAtMs = 0L, nowMs = 3540_001L, marginSecs = 60L))
        assertFalse(isSessionExpired(expiresInSecs = 3600L, savedAtMs = 0L, nowMs = 3539_999L, marginSecs = 60L))
    }
}
