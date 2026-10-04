package com.moltrax.personalnoteapp.domain.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * GitHub wire shapes + display mapping (Phase 7).
 */
class GithubContractTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `activity with mixed ref payload decodes`() {
        val decoded = json.decodeFromString<GithubActivity>(
            """{"id":"a1","space_id":"s1","repo_full":"o/r","kind":"pr.opened","ref":{"number":42,"title":"Fix it","sender":"u"}}"""
        )
        assertEquals("pr.opened", decoded.kind)
        assertEquals("Fix it #42 by u", decoded.summary())
    }

    @Test
    fun `empty ref falls back to kind`() {
        val decoded = json.decodeFromString<GithubActivity>(
            """{"id":"a1","space_id":"s1","kind":"release"}"""
        )
        assertEquals("release", decoded.summary())
    }

    @Test
    fun `kind titles cover the first slice`() {
        assertEquals("Issue opened", githubActivityTitle("issue.opened"))
        assertEquals("PR opened", githubActivityTitle("pr.opened"))
        assertEquals("New comment", githubActivityTitle("comment"))
        assertEquals("Review submitted", githubActivityTitle("review"))
        assertEquals("State changed", githubActivityTitle("state_changed"))
        assertEquals("Release published", githubActivityTitle("release"))
        assertEquals("workflow_failed", githubActivityTitle("workflow_failed"))
    }

    @Test
    fun `link body serializes id as number`() {
        val body = buildJsonObject {
            put("id", 123L)
            put("space_id", "s1")
            put("full_name", "o/r")
            put("private", false)
        }
        assertEquals(
            """{"id":123,"space_id":"s1","full_name":"o/r","private":false}""",
            json.encodeToString(body),
        )
    }
}
