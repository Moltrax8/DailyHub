package com.moltrax.personalnoteapp.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Projects-list GitHub import helpers: project-name derivation + dedupe,
 * already-added filtering, selection state. Pure JVM logic.
 */
class GithubImportTest {

    @Test
    fun `name strips owner`() {
        assertEquals("DailyHub", importProjectName("moltrax8/DailyHub"))
        assertEquals("Hello-World", importProjectName("octocat/Hello-World"))
    }

    @Test
    fun `name trims and survives owner-less input`() {
        assertEquals("Solo", importProjectName("  Solo  "))
        assertEquals("c", importProjectName("x/a/b/c"))
    }

    @Test
    fun `name never returns blank`() {
        assertEquals("r", importProjectName("o/r"))
        // Degenerate input falls back to the trimmed input itself.
        assertEquals("owner/", importProjectName("owner/"))
    }

    @Test
    fun `owner extracts owner`() {
        assertEquals("octocat", importRepoOwner("octocat/Hello-World"))
        assertEquals("", importRepoOwner("Solo"))
        assertEquals("", importRepoOwner("  "))
    }

    @Test
    fun `dedupe keeps free names`() {
        assertEquals("API", dedupeImportProjectName("API", "octocat", listOf("Web", "Docs")))
        assertEquals("API", dedupeImportProjectName("  API  ", "octocat", emptyList()))
    }

    @Test
    fun `dedupe appends owner on collision case-insensitively`() {
        assertEquals(
            "API (octocat)",
            dedupeImportProjectName("API", "octocat", listOf("api")),
        )
        assertEquals(
            "API (octocat)",
            dedupeImportProjectName("API", "octocat", listOf("  API  ")),
        )
    }

    @Test
    fun `dedupe appends counter when owner form is taken`() {
        val existing = listOf("API", "API (octocat)")
        assertEquals("API (octocat) 2", dedupeImportProjectName("API", "octocat", existing))
        assertEquals(
            "API (octocat) 3",
            dedupeImportProjectName("API", "octocat", existing + "API (octocat) 2"),
        )
    }

    @Test
    fun `dedupe without owner still terminates`() {
        assertEquals("API 2", dedupeImportProjectName("API", "", listOf("API", "API (x)")))
    }

    private fun repo(id: Long, name: String) = GithubAppRepo(id = id, fullName = name)

    @Test
    fun `already-added unions ids across projects`() {
        assertEquals(
            setOf(1L, 2L, 3L),
            importAlreadyAddedIds(listOf(listOf(1L, 2L), emptyList(), listOf(2L, 3L))),
        )
        assertTrue(importAlreadyAddedIds(emptyList()).isEmpty())
    }

    @Test
    fun `toggle adds and removes`() {
        assertEquals(setOf(7L), toggleImportSelection(emptySet(), 7L))
        assertEquals(emptySet<Long>(), toggleImportSelection(setOf(7L), 7L))
        assertEquals(setOf(1L, 3L), toggleImportSelection(setOf(1L, 2L, 3L), 2L))
    }

    @Test
    fun `selectable excludes already-added`() {
        val repos = listOf(repo(1, "a/A"), repo(2, "b/B"), repo(3, "c/C"))
        assertEquals(
            listOf(repos[0], repos[2]),
            importSelectableRepos(repos, setOf(2L)),
        )
        assertEquals(repos, importSelectableRepos(repos, emptySet()))
        assertTrue(importSelectableRepos(repos, setOf(1L, 2L, 3L)).isEmpty())
    }
}
