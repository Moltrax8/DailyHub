package com.moltrax.personalnoteapp.data.remote.supabase

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.moltrax.personalnoteapp.BuildConfig
import com.moltrax.personalnoteapp.data.remote.supabase.model.EmailCredentials
import com.moltrax.personalnoteapp.domain.model.SupabaseProfile
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * PREPARED — runs on the VM phone when the backend is ready. Requires:
 * 1. `001` + `003` + `006_phase8_expanded.sql` applied,
 * 2. Auth → Providers → Email → Confirm email OFF (dev project),
 * 3. network access from the emulator.
 *
 * Two users, raw Retrofit with their own tokens: message round-trip,
 * event validation (end < start rejected client-side is covered by JVM…
 * here: server accepts a valid event), file row CRUD, feed read, and an
 * outsider denied on messages. Skipped gracefully when unconfigured.
 */
@RunWith(AndroidJUnit4::class)
class SupabaseExpandedE2ETest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false }

    private data class Actor(val uid: String, val bearer: String, val db: SupabaseDbApi)

    private fun backend(): Pair<String, OkHttpClient> {
        val url = BuildConfig.SUPABASE_URL.trimEnd('/')
        val key = BuildConfig.SUPABASE_ANON_KEY
        assumeTrue("Supabase keys missing", url.isNotBlank() && key.isNotBlank())
        val http = OkHttpClient.Builder()
            .addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder().header("apikey", key).build())
            }
            .build()
        return url to http
    }

    private fun dbApi(url: String, http: OkHttpClient): SupabaseDbApi =
        Retrofit.Builder()
            .baseUrl("$url/rest/v1/")
            .client(http)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(SupabaseDbApi::class.java)

    private fun authApi(url: String, http: OkHttpClient): SupabaseAuthApi =
        Retrofit.Builder()
            .baseUrl("$url/auth/v1/")
            .client(http)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(SupabaseAuthApi::class.java)

    private suspend fun signUpFresh(tag: String): Actor {
        val (url, http) = backend()
        val stamp = System.currentTimeMillis() % 100000
        val res = authApi(url, http).signUp(EmailCredentials("exp${tag}${stamp}@example.com", "pass1234"))
        assertTrue("signup $tag: HTTP ${res.code()}", res.isSuccessful)
        val session = res.body()!!
        val uid = session.user!!.id
        val bearer = "Bearer ${session.accessToken}"
        val db = dbApi(url, http)
        assertTrue(db.upsertProfile(bearer, body = SupabaseProfile(id = uid, username = "exp${tag}${stamp}")).isSuccessful)
        return Actor(uid, bearer, db)
    }

    private suspend fun makeDuo(a: Actor, b: Actor): String {
        val space = a.db.createSpace(
            a.bearer, body = mapOf("type" to "DUO", "name" to null, "created_by" to a.uid),
        ).body()!!.first()
        val spaceId = space.id
        assertTrue(a.db.addMember(a.bearer, body = mapOf("space_id" to spaceId, "user_id" to a.uid, "role" to "owner")).isSuccessful)
        assertTrue(a.db.addMember(a.bearer, body = mapOf("space_id" to spaceId, "user_id" to b.uid, "role" to "member")).isSuccessful)
        return spaceId
    }

    @Test
    fun chat_event_file_feed_roundTrip_outsiderDenied() {
        runBlocking {
            val a = signUpFresh("a")
            val b = signUpFresh("b")
            val c = signUpFresh("c")
            val spaceId = makeDuo(a, b)

            // Chat round-trip.
            assertTrue(
                a.db.sendMessage(a.bearer, body = mapOf("space_id" to spaceId, "author" to a.uid, "body" to "hi")).isSuccessful
            )
            val bMsgs = b.db.spaceMessages(b.bearer, "eq.$spaceId").body()!!
            assertEquals(1, bMsgs.size)
            assertEquals("hi", bMsgs.first().body)

            // Event round-trip.
            val now = System.currentTimeMillis()
            val ev = a.db.createEvent(
                a.bearer,
                body = buildJsonObject {
                    put("space_id", spaceId)
                    put("title", "Kickoff")
                    put("start_at", now + 3600_000L)
                    put("created_by", a.uid)
                },
            )
            assertTrue("event: HTTP ${ev.code()}", ev.isSuccessful)
            assertEquals(1, b.db.spaceEvents(b.bearer, "eq.$spaceId").body()!!.size)

            // File row round-trip (bytes travel via storage in the app path).
            val row = a.db.createFileRow(
                a.bearer,
                body = buildJsonObject {
                    put("space_id", spaceId)
                    put("path", "$spaceId/e2e.txt")
                    put("size", 3L)
                    put("created_by", a.uid)
                },
            )
            assertTrue("file row: HTTP ${row.code()}", row.isSuccessful)
            assertEquals(1, b.db.spaceFiles(b.bearer, "eq.$spaceId").body()!!.size)

            // Feed is readable by members.
            val feed = b.db.spaceFeed(b.bearer, "eq.$spaceId")
            assertTrue("feed: HTTP ${feed.code()}", feed.isSuccessful)

            // Outsider sees nothing anywhere.
            assertTrue(c.db.spaceMessages(c.bearer, "eq.$spaceId").body().orEmpty().isEmpty())
            assertTrue(c.db.spaceEvents(c.bearer, "eq.$spaceId").body().orEmpty().isEmpty())
            assertTrue(c.db.spaceFiles(c.bearer, "eq.$spaceId").body().orEmpty().isEmpty())
            assertTrue(c.db.spaceFeed(c.bearer, "eq.$spaceId").body().orEmpty().isEmpty())

            // Cleanup cascades everything.
            assertTrue(a.db.deleteSpace(a.bearer, "eq.$spaceId").isSuccessful)
            // NOTE: delete e2e users in Dashboard → Authentication → Users (dev hygiene).
        }
    }
}
