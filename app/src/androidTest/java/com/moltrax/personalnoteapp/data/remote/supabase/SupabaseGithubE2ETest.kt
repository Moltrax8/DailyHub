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
 * 1. `001` + `005_phase7_github.sql` applied,
 * 2. Auth → Providers → Email → Confirm email OFF (dev project),
 * 3. network access from the emulator.
 *
 * Repo link/unlink + notification prefs + FCM token register + activity read
 * (empty until the webhook fires — the webhook itself is exercised by pushing
 * a real GitHub event while watching logcat/function logs). Skipped
 * gracefully when unconfigured.
 */
@RunWith(AndroidJUnit4::class)
class SupabaseGithubE2ETest {

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
        val res = authApi(url, http).signUp(EmailCredentials("gh${tag}${stamp}@example.com", "pass1234"))
        assertTrue("signup $tag: HTTP ${res.code()}", res.isSuccessful)
        val session = res.body()!!
        val uid = session.user!!.id
        val bearer = "Bearer ${session.accessToken}"
        val db = dbApi(url, http)
        assertTrue(db.upsertProfile(bearer, body = SupabaseProfile(id = uid, username = "gh${tag}${stamp}")).isSuccessful)
        return Actor(uid, bearer, db)
    }

    @Test
    fun link_prefs_token_activity() {
        runBlocking {
            val a = signUpFresh("a")

            // Project space for the links to hang on.
            val space = a.db.createSpace(
                a.bearer, body = mapOf("type" to "PROJECT", "name" to "GH", "created_by" to a.uid),
            ).body()!!.first()
            val spaceId = space.id
            assertTrue(a.db.addMember(a.bearer, body = mapOf("space_id" to spaceId, "user_id" to a.uid, "role" to "owner")).isSuccessful)

            // Link + read back + unlink.
            val link = a.db.linkRepo(
                a.bearer,
                body = buildJsonObject {
                    put("id", 123456L)
                    put("space_id", spaceId)
                    put("full_name", "octo/demo")
                    put("private", false)
                    put("installed_by", a.uid)
                },
            )
            assertTrue("link: HTTP ${link.code()}", link.isSuccessful)
            assertEquals(1, a.db.spaceRepos(a.bearer, "eq.$spaceId").body()!!.size)
            assertTrue(a.db.unlinkRepo(a.bearer, "eq.123456").isSuccessful)
            assertTrue(a.db.spaceRepos(a.bearer, "eq.$spaceId").body().orEmpty().isEmpty())

            // Prefs default (absent = enabled) → disable → read back.
            assertTrue(a.db.myNotifPrefs(a.bearer, "eq.${a.uid}", "eq.$spaceId").body().orEmpty().isEmpty())
            assertTrue(
                a.db.setNotifPref(
                    a.bearer,
                    body = buildJsonObject {
                        put("user_id", a.uid)
                        put("space_id", spaceId)
                        put("kind", "comment")
                        put("enabled", false)
                    },
                ).isSuccessful
            )
            val prefs = a.db.myNotifPrefs(a.bearer, "eq.${a.uid}", "eq.$spaceId").body()!!
            assertEquals(false, prefs.single { it.kind == "comment" }.enabled)

            // FCM token register (no push without a device token on file).
            assertTrue(
                a.db.registerFcmToken(a.bearer, body = mapOf("user_id" to a.uid, "token" to "e2e-fake-token")).isSuccessful
            )

            // Activity feed reads (empty until a real webhook event lands).
            val feed = a.db.spaceActivity(a.bearer, "eq.$spaceId")
            assertTrue("feed: HTTP ${feed.code()}", feed.isSuccessful)

            // Cleanup.
            assertTrue(a.db.deleteSpace(a.bearer, "eq.$spaceId").isSuccessful)
            // NOTE: delete e2e users in Dashboard → Authentication → Users (dev hygiene).
        }
    }
}
