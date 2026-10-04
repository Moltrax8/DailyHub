package com.moltrax.personalnoteapp.data.remote.supabase

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.moltrax.personalnoteapp.BuildConfig
import com.moltrax.personalnoteapp.data.remote.supabase.model.EmailCredentials
import com.moltrax.personalnoteapp.domain.model.FriendRequestStatus
import com.moltrax.personalnoteapp.domain.model.SupabaseProfile
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
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
 * 1. `001_phase3_profiles.sql` + `002_phase4_friends.sql` applied,
 * 2. Auth → Providers → Email → Confirm email OFF (dev project),
 * 3. network access from the emulator.
 *
 * Two throwaway users drive the REAL RLS contract with their own tokens
 * (raw Retrofit, no shared prefs): send → incoming → self-accept DENIED →
 * recipient accepts → friends → remove. Skipped gracefully when unconfigured.
 */
@RunWith(AndroidJUnit4::class)
class SupabaseSocialE2ETest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false }

    private data class Actor(val uid: String, val token: String, val db: SupabaseDbApi)

    private fun backend(): Triple<String, String, OkHttpClient> {
        val url = BuildConfig.SUPABASE_URL.trimEnd('/')
        val key = BuildConfig.SUPABASE_ANON_KEY
        assumeTrue("Supabase keys missing", url.isNotBlank() && key.isNotBlank())
        val http = OkHttpClient.Builder()
            .addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder().header("apikey", key).build())
            }
            .build()
        return Triple(url, key, http)
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
        val (url, _, http) = backend()
        val stamp = System.currentTimeMillis() % 100000
        val email = "soc${tag}${stamp}@gmail.com"
        val username = "soc${tag}${stamp}"
        val res = authApi(url, http).signUp(EmailCredentials(email, "pass1234"))
        assertTrue("signup $tag: HTTP ${res.code()}", res.isSuccessful)
        val session = res.body()!!
        val uid = session.user!!.id
        val bearer = "Bearer ${session.accessToken}"
        // Profile row (RLS: own id only).
        val up = dbApi(url, http).upsertProfile(bearer, body = SupabaseProfile(id = uid, username = username))
        assertTrue("profile $tag: HTTP ${up.code()}", up.isSuccessful)
        return Actor(uid, bearer, dbApi(url, http))
    }

    @Test
    fun send_accept_remove_withRlsDenial() {
        runBlocking {
            val a = signUpFresh("a")
            val b = signUpFresh("b")

            // A sends to B.
            val sent = a.db.sendRequest(a.token, body = mapOf("from_id" to a.uid, "to_id" to b.uid))
            assertTrue("send: HTTP ${sent.code()}", sent.isSuccessful)
            val reqId = sent.body()!!.first().id

            // B sees it incoming.
            val incoming = b.db.receivedRequests(b.token, "eq.${b.uid}")
            assertTrue(incoming.body().orEmpty().any { it.id == reqId })

            // RLS negative: sender cannot self-accept (0 rows, stays pending).
            a.db.answerRequest(a.token, "eq.$reqId", mapOf("status" to "accepted"))
            val stillPending = b.db.receivedRequests(b.token, "eq.${b.uid}")
                .body().orEmpty().first { it.id == reqId }
            assertEquals(FriendRequestStatus.PENDING, stillPending.status)

            // Recipient accepts.
            val accept = b.db.answerRequest(b.token, "eq.$reqId", mapOf("status" to "accepted"))
            assertTrue("accept: HTTP ${accept.code()}", accept.isSuccessful)

            // Cleanup: recipient deletes the edge (parties may delete).
            val del = b.db.deleteRequest(b.token, "eq.$reqId")
            assertTrue("delete: HTTP ${del.code()}", del.isSuccessful)
            // NOTE: delete e2e users in Dashboard → Authentication → Users (dev hygiene).
        }
    }
}
