package com.moltrax.personalnoteapp.data.remote.supabase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Phoenix protocol helpers (Phase 9 Realtime): URL/join/heartbeat shapes and
 * record extraction. The socket loop itself is exercised on-device.
 */
class RealtimeClientTest {

    @Test
    fun `websocket url derives from project url`() {
        assertEquals(
            "wss://xyz.supabase.co/realtime/v1/websocket?apikey=K&vsn=1.0.0",
            RealtimeClient.websocketUrl("https://xyz.supabase.co", "K"),
        )
        assertEquals(
            "wss://xyz.supabase.co/realtime/v1/websocket?apikey=K&vsn=1.0.0",
            RealtimeClient.websocketUrl("https://xyz.supabase.co/rest/v1/", "K"),
        )
        assertNull(RealtimeClient.websocketUrl("", "K"))
        assertNull(RealtimeClient.websocketUrl("https://xyz.supabase.co", ""))
        assertNull(RealtimeClient.websocketUrl("https://example.com", "K"))
    }

    @Test
    fun `join targets the table inserts`() {
        val join = RealtimeClient.joinMessage("app_releases", "K", 1)
        assertTrue(join.contains("realtime:public:app_releases"))
        assertTrue(join.contains("phx_join"))
        assertTrue(join.contains("postgres_changes"))
        assertTrue(join.contains("INSERT"))
    }

    @Test
    fun `record extracts from postgres changes only`() {
        val msg = """{"event":"postgres_changes","topic":"realtime:public:app_releases","payload":{"data":{"record":{"version_code":10100},"type":"INSERT"}}}"""
        val record = RealtimeClient.parsePhoenixRecord(msg)
        assertNotNull(record)
        assertEquals(
            10100,
            kotlinx.serialization.json.Json.parseToJsonElement(record!!)
                .jsonObject["version_code"]?.jsonPrimitive?.content?.toInt(),
        )
        assertNull(RealtimeClient.parsePhoenixRecord("""{"event":"phx_reply","payload":{"status":"ok"}}"""))
        assertNull(RealtimeClient.parsePhoenixRecord("garbage"))
    }
}
