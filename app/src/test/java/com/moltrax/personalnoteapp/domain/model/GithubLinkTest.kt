package com.moltrax.personalnoteapp.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * GitHub App link helpers: deep-link callback parsing + repo filtering.
 * Pure JVM logic (no Android classes).
 */
class GithubLinkTest {

    @Test
    fun `callback parses code and state`() {
        val cb = parseGithubCallback("dailyhub://github-callback?code=abc123&state=xyz789")
        assertEquals(GithubCallback("abc123", "xyz789"), cb)
    }

    @Test
    fun `callback rejects wrong host`() {
        assertNull(parseGithubCallback("dailyhub://auth/callback?code=a&state=b"))
    }

    @Test
    fun `callback rejects missing code or state`() {
        assertNull(parseGithubCallback("dailyhub://github-callback?code=abc"))
        assertNull(parseGithubCallback("dailyhub://github-callback?state=xyz"))
        assertNull(parseGithubCallback("dailyhub://github-callback"))
        assertNull(parseGithubCallback(null))
        assertNull(parseGithubCallback(""))
        assertNull(parseGithubCallback("not a uri at all"))
    }

    @Test
    fun `callback decodes url-encoded values`() {
        val cb = parseGithubCallback("dailyhub://github-callback?code=a%2Bb&state=x%3Dy")
        assertEquals(GithubCallback("a+b", "x=y"), cb)
    }

    private fun repo(id: Long, name: String, fork: Boolean = false) =
        GithubAppRepo(id = id, fullName = name, fork = fork)

    @Test
    fun `filter matches name case-insensitively`() {
        val repos = listOf(
            repo(1, "octocat/Hello-World"),
            repo(2, "octocat/Spoon-Knife"),
            repo(3, "moltrax8/DailyHub"),
        )
        assertEquals(listOf(repos[2]), filterGithubAppRepos(repos, "dailyhub"))
        assertEquals(listOf(repos[0], repos[1]), filterGithubAppRepos(repos, "OCTOCAT"))
        assertEquals(repos, filterGithubAppRepos(repos, "  "))
    }

    @Test
    fun `filter hides forks when asked`() {
        val repos = listOf(
            repo(1, "octocat/Hello-World"),
            repo(2, "octocat/Spoon-Knife", fork = true),
        )
        assertEquals(repos, filterGithubAppRepos(repos, "", hideForks = false))
        assertEquals(listOf(repos[0]), filterGithubAppRepos(repos, "", hideForks = true))
        // Query + fork hiding combine.
        assertEquals(emptyList<GithubAppRepo>(), filterGithubAppRepos(repos, "spoon", hideForks = true))
        assertEquals(listOf(repos[1]), filterGithubAppRepos(repos, "spoon", hideForks = false))
    }
}
