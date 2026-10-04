package com.moltrax.personalnoteapp.data.remote.supabase

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.moltrax.personalnoteapp.BuildConfig
import com.moltrax.personalnoteapp.data.remote.supabase.model.EmailCredentials
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
 * 1. `001` + `002` + `003_phase5_spaces.sql` applied,
 * 2. Auth → Providers → Email → Confirm email OFF (dev project),
 * 3. network access from the emulator.
 *
 * Two users, raw Retrofit with their own tokens (real RLS): A creates a Duo
 * hub with B → B lists it → A adds a note → B reads it → outsider C gets an
 * empty list (member-only policy). Skipped gracefully when unconfigured.
 */
@RunWith(AndroidJUnit4::class)
class SupabaseSpacesE2ETest {

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
        val res = authApi(url, http).signUp(EmailCredentials("spc${tag}${stamp}@example.com", "pass1234"))
        assertTrue("signup $tag: HTTP ${res.code()}", res.isSuccessful)
        val session = res.body()!!
        val uid = session.user!!.id
        val bearer = "Bearer ${session.accessToken}"
        val db = dbApi(url, http)
        val up = db.upsertProfile(bearer, body = SupabaseProfile(id = uid, username = "spc${tag}${stamp}"))
        assertTrue("profile $tag: HTTP ${up.code()}", up.isSuccessful)
        return Actor(uid, bearer, db)
    }

    @Test
    fun duoHub_notesVisibleToMembers_hiddenFromOutsiders() {
        runBlocking {
            val a = signUpFresh("a")
            val b = signUpFresh("b")
            val c = signUpFresh("c")

            // A creates a Duo hub with B.
            val created = a.db.createSpace(
                a.bearer,
                body = mapOf("type" to "DUO", "name" to null, "created_by" to a.uid),
            )
            assertTrue("create space: HTTP ${created.code()}", created.isSuccessful)
            val spaceId = created.body()!!.first().id
            assertTrue(a.db.addMember(a.bearer, body = mapOf("space_id" to spaceId, "user_id" to a.uid, "role" to "owner")).isSuccessful)
            assertTrue(a.db.addMember(a.bearer, body = mapOf("space_id" to spaceId, "user_id" to b.uid, "role" to "member")).isSuccessful)

            // A adds a note; B reads it.
            val note = a.db.createNote(
                a.bearer,
                body = mapOf("space_id" to spaceId, "author" to a.uid, "title" to "Hi", "body_md" to "hello"),
            )
            assertTrue("create note: HTTP ${note.code()}", note.isSuccessful)
            val bNotes = b.db.spaceNotes(b.bearer, "eq.$spaceId")
            assertTrue("B reads: HTTP ${bNotes.code()}", bNotes.isSuccessful)
            assertEquals(1, bNotes.body()!!.size)
            assertEquals("Hi", bNotes.body()!!.first().title)

            // Outsider C sees no spaces and no notes (member-only RLS).
            val cSpaces = c.db.mySpaces(c.bearer)
            assertTrue("C spaces: HTTP ${cSpaces.code()}", cSpaces.isSuccessful)
            assertTrue(cSpaces.body().orEmpty().none { it.id == spaceId })
            val cNotes = c.db.spaceNotes(c.bearer, "eq.$spaceId")
            assertTrue("C notes: HTTP ${cNotes.code()}", cNotes.isSuccessful)
            assertTrue(cNotes.body().orEmpty().isEmpty())

            // Cleanup: creator deletes the hub (cascades members/notes).
            assertTrue(a.db.deleteSpace(a.bearer, "eq.$spaceId").isSuccessful)
            // NOTE: delete e2e users in Dashboard → Authentication → Users (dev hygiene).
        }
    }
}
