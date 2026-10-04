package com.moltrax.personalnoteapp.data.remote.supabase

import com.moltrax.personalnoteapp.di.SupabaseConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * Minimal Supabase Realtime client over raw OkHttp WebSocket (Phase 9).
 * Speaks just enough phoenix protocol for `postgres_changes` INSERT
 * subscriptions — no SDK needed (supabase-kt is version-blocked, see Phase 3).
 *
 * - connect: wss://{ref}/realtime/v1/websocket?apikey=KEY&vsn=1.0.0
 * - join: topic realtime:public:{table}, INSERT binding
 * - heartbeat every 25s; exponential-backoff reconnect (max ~2 min)
 */
@Singleton
class RealtimeClient @Inject constructor(
    private val http: OkHttpClient,
    private val config: SupabaseConfig,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun interface Subscription {
        fun close()
    }

    /**
     * Calls [onRecord] with each inserted row (raw JSON object string).
     * Reconnects transparently; call [Subscription.close] to stop.
     */
    fun subscribeInserts(table: String, onRecord: (String) -> Unit): Subscription {
        var closed = false
        var socket: WebSocket? = null
        var ref = 0
        var attempt = 0

        fun sendJoin(ws: WebSocket) {
            ref++
            ws.send(joinMessage(table, config.anonKey, ref))
        }

        val job = scope.launch {
            while (isActive && !closed) {
                val url = websocketUrl(config.url, config.anonKey)
                if (url == null) {
                    delay(60_000L)
                    continue
                }
                var connected = false
                val req = Request.Builder().url(url).build()
                socket = http.newWebSocket(req, object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        attempt = 0
                        connected = true
                        sendJoin(webSocket)
                        scope.launch {
                            while (isActive && !closed && connected) {
                                delay(HEARTBEAT_MS)
                                ref++
                                webSocket.send(heartbeat(ref))
                            }
                        }
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        parsePhoenixRecord(text)?.let { onRecord(it) }
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, r: Response?) {
                        connected = false
                    }

                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        connected = false
                    }
                })
                // Wait until the socket drops, then back off and reconnect.
                while (isActive && !closed && connected) delay(1_000L)
                socket?.close(1000, null)
                socket = null
                if (closed) break
                attempt++
                delay(minOf(BASE_BACKOFF_MS shl attempt.coerceAtMost(6), MAX_BACKOFF_MS))
            }
        }
        return Subscription {
            closed = true
            job.cancel()
            socket?.close(1000, null)
        }
    }

    fun close() {
        scope.cancel()
    }

    companion object {
        const val HEARTBEAT_MS = 25_000L
        const val BASE_BACKOFF_MS = 1_000L
        const val MAX_BACKOFF_MS = 120_000L

        /** wss URL from the project URL; null when unconfigured. */
        fun websocketUrl(projectUrl: String, anonKey: String): String? {
            val base = projectUrl.trim().trimEnd('/')
            if (base.isBlank() || anonKey.isBlank()) return null
            val host = base.removePrefix("https://").removePrefix("http://").substringBefore('/')
            if (!host.endsWith(".supabase.co")) return null
            val scheme = if (base.startsWith("http://")) "ws" else "wss"
            return "$scheme://$host/realtime/v1/websocket?apikey=$anonKey&vsn=1.0.0"
        }

        fun joinMessage(table: String, anonKey: String, ref: Int): String =
            buildJsonObject {
                put("topic", "realtime:public:$table")
                put("event", "phx_join")
                put(
                    "payload",
                    buildJsonObject {
                        put("access_token", anonKey)
                        put(
                            "config",
                            buildJsonObject {
                                put("broadcast", buildJsonObject { put("ack", false) })
                                put("presence", buildJsonObject { put("key", "") })
                                putJsonArray("postgres_changes") {
                                    add(
                                        buildJsonObject {
                                            put("event", "INSERT")
                                            put("schema", "public")
                                            put("table", table)
                                        }
                                    )
                                }
                            }
                        )
                    }
                )
                put("ref", ref.toString())
            }.toString()

        fun heartbeat(ref: Int): String =
            buildJsonObject {
                put("topic", "phoenix")
                put("event", "heartbeat")
                put("payload", buildJsonObject { })
                put("ref", ref.toString())
            }.toString()

        /**
         * Extracts the inserted row JSON from a phoenix `postgres_changes`
         * message; null for everything else (joins, heartbeats, other tables).
         * Pure kotlinx (Android-stub-free) so it is JVM-testable. Pure (JVM-tested).
         */
        fun parsePhoenixRecord(text: String): String? = runCatching {
            val msg = Json.parseToJsonElement(text).jsonObject
            if (msg["event"]?.jsonPrimitive?.content != "postgres_changes") return null
            msg["payload"]?.jsonObject
                ?.get("data")?.jsonObject
                ?.get("record")?.jsonObject
                ?.toString() ?: return null
        }.getOrNull()
    }
}
