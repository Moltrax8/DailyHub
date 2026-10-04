package com.moltrax.personalnoteapp.data.remote.supabase

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.moltrax.personalnoteapp.BuildConfig
import com.moltrax.personalnoteapp.data.remote.supabase.model.EmailCredentials
import com.moltrax.personalnoteapp.domain.model.SupabaseProfile
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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
 * 1. `001` + `004_phase6_projects.sql` applied (004 includes spaces tables),
 * 2. Auth → Providers → Email → Confirm email OFF (dev project),
 * 3. network access from the emulator.
 *
 * Two users, raw Retrofit with their own tokens: A creates a PROJECT space +
 * project row → adds item → moves it → comments → B (member) reads all →
 * outsider C sees nothing. Skipped gracefully when unconfigured.
 */
@RunWith(AndroidJUnit4::class)
class SupabaseProjectsE2ETest {

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
        val res = authApi(url, http).signUp(EmailCredentials("prj${tag}${stamp}@gmail.com", "pass1234"))
        assertTrue("signup $tag: HTTP ${res.code()}", res.isSuccessful)
        val session = res.body()!!
        val uid = session.user!!.id
        val bearer = "Bearer ${session.accessToken}"
        val db = dbApi(url, http)
        assertTrue(db.upsertProfile(bearer, body = SupabaseProfile(id = uid, username = "prj${tag}${stamp}")).isSuccessful)
        return Actor(uid, bearer, db)
    }

    private fun projectBody(spaceId: String, desc: String?): JsonObject = buildJsonObject {
        put("space_id", spaceId)
        if (desc != null) put("description_md", desc)
    }

    @Test
    fun projectBoard_endToEnd() {
        runBlocking {
            val a = signUpFresh("a")
            val b = signUpFresh("b")
            val c = signUpFresh("c")

            // A creates a PROJECT space + project row, adds B as member.
            val space = a.db.createSpace(
                a.bearer, body = mapOf("type" to "PROJECT", "name" to "E2E", "created_by" to a.uid),
            ).body()!!.first()
            val spaceId = space.id
            assertTrue(a.db.addMember(a.bearer, body = mapOf("space_id" to spaceId, "user_id" to a.uid, "role" to "owner")).isSuccessful)
            assertTrue(a.db.addMember(a.bearer, body = mapOf("space_id" to spaceId, "user_id" to b.uid, "role" to "member")).isSuccessful)
            val up = a.db.upsertProject(a.bearer, body = projectBody(spaceId, "demo"))
            assertTrue("upsert project: HTTP ${up.code()}", up.isSuccessful)

            // A adds an item, moves it, comments.
            val item = a.db.createProjectItem(
                a.bearer,
                body = buildJsonObject {
                    put("space_id", spaceId)
                    put("title", "Card 1")
                    put("status", "Idea")
                    put("sort_order", 0L)
                },
            ).body()!!.first()
            assertTrue(
                a.db.updateProjectItem(
                    a.bearer, "eq.${item.id}",
                    body = buildJsonObject { put("status", "Planned") },
                ).isSuccessful
            )
            assertTrue(
                a.db.createComment(
                    a.bearer,
                    body = mapOf("space_id" to spaceId, "ref_type" to "project_item", "ref_id" to item.id, "author" to a.uid, "body_md" to "nice"),
                ).isSuccessful
            )

            // B (member) reads everything.
            val bItems = b.db.projectItems(b.bearer, "eq.$spaceId").body()!!
            assertEquals(1, bItems.size)
            assertEquals(
                com.moltrax.personalnoteapp.domain.model.ProjectStatus.PLANNED,
                bItems.first().status,
            )
            assertEquals(1, b.db.itemComments(b.bearer, "eq.$spaceId", "eq.${item.id}").body()!!.size)

            // Outsider C sees nothing.
            assertTrue(c.db.projectItems(c.bearer, "eq.$spaceId").body().orEmpty().isEmpty())

            // Cleanup: creator deletes the space (cascades all project rows).
            assertTrue(a.db.deleteSpace(a.bearer, "eq.$spaceId").isSuccessful)
            // NOTE: delete e2e users in Dashboard → Authentication → Users (dev hygiene).
        }
    }
}
