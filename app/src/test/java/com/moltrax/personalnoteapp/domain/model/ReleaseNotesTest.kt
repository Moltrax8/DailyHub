package com.moltrax.personalnoteapp.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.util.Locale

class ReleaseNotesTest {

    private val houseStyle = """
        What's New
        Released November 8, 2026

        Update checks work again — DailyHub can find and install its own updates.

        This release restores the in-app update check, which had been failing silently since 1.2.0.

        ✨ New Features
        • "Check for updates" now really queries GitHub Releases.
        • Automatic checks when the app opens work again.

        ⚡ Improvements
        • Metadata and downloads use separate HTTP clients.

        🔧 Bug Fixes
        • "Could not check" no longer appears while online.

        🛡 Security
        • APK downloads stay pinned to GitHub hosts.
        • Checksums are verified against GitHub's digest.

        ⚠️ Important Notes
        • On 1.2.0 or 1.3.0, install manually once.
        • Brand and keyboard fixes are included.

        Full changelog and technical details:
        https://github.com/Moltrax8/DailyHub/compare/v1.3.0...v1.3.1
    """.trimIndent()

    @Test
    fun `house style maps to headline intro sections and changelog`() {
        val notes = parseReleaseNotes(houseStyle)

        assertEquals("November 8, 2026", notes.released)
        assertEquals("Update checks work again — DailyHub can find and install its own updates.", notes.headline)
        assertTrue(notes.intro!!.startsWith("This release restores the in-app update check"))
        assertEquals("https://github.com/Moltrax8/DailyHub/compare/v1.3.0...v1.3.1", notes.changelogUrl)

        assertEquals(5, notes.sections.size)
        assertEquals(listOf("✨", "⚡", "🔧", "🛡", "⚠️"), notes.sections.map { it.icon })
        assertEquals(
            listOf("New Features", "Improvements", "Bug Fixes", "Security", "Important Notes"),
            notes.sections.map { it.title },
        )
        assertEquals(listOf(2, 1, 1, 2, 2), notes.sections.map { it.items.size })
        assertEquals("\"Check for updates\" now really queries GitHub Releases.", notes.sections[0].items[0])
    }

    @Test
    fun `plain text becomes the headline`() {
        val notes = parseReleaseNotes("Just a small maintenance release.")
        assertEquals("Just a small maintenance release.", notes.headline)
        assertNull(notes.intro)
        assertTrue(notes.sections.isEmpty())
    }

    @Test
    fun `markdown headings and bold are cleaned`() {
        val notes = parseReleaseNotes(
            """
            # DailyHub 1.2.0

            Intro paragraph with **bold** text and `code`.

            ## Highlights
            - Standalone notes.
            - Custom widgets.

            ## Removed
            - Workouts.
            """.trimIndent(),
        )

        assertEquals("DailyHub 1.2.0", notes.headline)
        assertEquals("Intro paragraph with bold text and code.", notes.intro)
        assertEquals(listOf("Highlights", "Removed"), notes.sections.map { it.title })
        assertEquals(listOf("Standalone notes.", "Custom widgets."), notes.sections[0].items)
    }

    @Test
    fun `crlf bodies and bullet-only bodies parse`() {
        val notes = parseReleaseNotes("What's New\r\n• One\r\n• Two\r\n")
        assertEquals("One", notes.headline)
        assertEquals("Two", notes.intro)
    }

    @Test
    fun `empty and null bodies stay empty`() {
        assertTrue(parseReleaseNotes(null).isEmpty)
        assertTrue(parseReleaseNotes("").isEmpty)
        assertTrue(parseReleaseNotes("   \n\n ").isEmpty)
    }

    @Test
    fun `published date is localized and bad input falls back to null`() {
        assertEquals(
            "November 8, 2026",
            formatReleaseDate("2026-11-08T09:24:00Z", Locale.US, ZoneId.of("UTC")),
        )
        assertNull(formatReleaseDate(null, Locale.US, ZoneId.of("UTC")))
        assertNull(formatReleaseDate("yesterday", Locale.US, ZoneId.of("UTC")))
        assertNull(formatReleaseDate("  ", Locale.US, ZoneId.of("UTC")))
    }

    @Test
    fun `only https github changelog links are safe`() {
        assertTrue(isSafeChangelogUrl("https://github.com/Moltrax8/DailyHub/compare/v1.3.1...v1.3.2"))
        assertTrue(isSafeChangelogUrl("https://github.com/Moltrax8/DailyHub/releases/tag/v1.3.2"))
        assertFalse(isSafeChangelogUrl("http://github.com/Moltrax8/DailyHub"))
        assertFalse(isSafeChangelogUrl("https://evil.example.com/github.com"))
        assertFalse(isSafeChangelogUrl("https://user@github.com/Moltrax8/DailyHub"))
        assertFalse(isSafeChangelogUrl("javascript:alert(1)"))
        assertFalse(isSafeChangelogUrl(null))
        assertFalse(isSafeChangelogUrl(""))
    }
}
