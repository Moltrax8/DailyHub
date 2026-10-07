package com.moltrax.personalnoteapp.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun `only https github dot com opens`() {
        assertTrue(isTrustedGitHubOpenUrl("https://github.com/login/oauth/authorize?x=1"))
        assertFalse(isTrustedGitHubOpenUrl("http://github.com/login"))
        assertFalse(isTrustedGitHubOpenUrl("https://evil.com/phish"))
        assertFalse(isTrustedGitHubOpenUrl("https://github.com.evil.com/x"))
        assertFalse(isTrustedGitHubOpenUrl("https://objects.githubusercontent.com/x.apk"))
        assertFalse(isTrustedGitHubOpenUrl("not a url"))
    }

    @Test
    fun `push text strips newlines and truncates`() {
        assertEquals("a b c", sanitizePushText("a\nb\r\nc"))
        assertEquals(80, sanitizePushText("x".repeat(200)).length)
        assertEquals("", sanitizePushText("  \n "))
    }
}

/** The install-and-authorize flow redirects with extra parameters; the callback parser must still work. */
class GithubInstallCallbackTest {
    @org.junit.Test fun parsesCodeAndStateAlongsideInstallationParams() {
        val cb = parseGithubCallback(
            "dailyhub://github-callback?code=abc123&installation_id=987654&setup_action=install&state=st4te.sig",
        )
        org.junit.Assert.assertNotNull(cb)
        org.junit.Assert.assertEquals("abc123", cb!!.code)
        org.junit.Assert.assertEquals("st4te.sig", cb.state)
    }

    @org.junit.Test fun rejectsInstallRedirectWithoutCode() {
        // GitHub sends no code when the user only reconfigured an existing installation.
        org.junit.Assert.assertNull(
            parseGithubCallback("dailyhub://github-callback?installation_id=1&setup_action=update&state=x"),
        )
    }
}
