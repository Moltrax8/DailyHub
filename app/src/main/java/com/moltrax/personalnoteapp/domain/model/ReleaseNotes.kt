package com.moltrax.personalnoteapp.domain.model

import java.net.URI
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** One heading plus its items from a GitHub release body. */
data class ReleaseNoteSection(
    val title: String,
    val icon: String? = null,
    val items: List<String> = emptyList(),
)

/**
 * Structured view of a release body, so the update prompt can show a readable
 * notes card instead of raw Markdown. Pure and JVM-tested.
 *
 * Understands the plain-text house style ("What's New", "Released ...", emoji
 * section headers, "•" bullets) and tolerates light Markdown headings/bold.
 */
data class ReleaseNotes(
    val headline: String? = null,
    val intro: String? = null,
    val released: String? = null,
    val sections: List<ReleaseNoteSection> = emptyList(),
    val changelogUrl: String? = null,
) {
    val isEmpty: Boolean
        get() = headline == null && intro == null && sections.isEmpty() && changelogUrl == null && released == null
}

/**
 * Formats GitHub's ISO-8601 `published_at` for display; null when unusable.
 * Pure so the update prompt can show a localized date without extra Android APIs.
 */
fun formatReleaseDate(
    iso: String?,
    locale: Locale = Locale.getDefault(),
    zone: ZoneId = ZoneId.systemDefault(),
): String? {
    val raw = iso?.trim().orEmpty()
    if (raw.isEmpty()) return null
    return runCatching {
        Instant.parse(raw)
            .atZone(zone)
            .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale))
    }.getOrNull()
}

/** Changelog links are only opened for plain https GitHub URLs. */
fun isSafeChangelogUrl(url: String?): Boolean {
    if (url.isNullOrBlank()) return false
    val uri = runCatching { URI(url) }.getOrNull() ?: return false
    return uri.scheme == "https" &&
        uri.userInfo == null &&
        uri.fragment == null &&
        uri.port in listOf(-1, 443) &&
        uri.host?.lowercase(Locale.ROOT) == "github.com"
}

private val SECTION_ICONS = listOf(
    "✨", "⚡", "🔧", "🛡", "⚠️", "⚠", "🚀", "🐛", "🔒", "📌", "🎨", "🧩", "📝", "♻️", "🛠", "💡", "📦",
)

private val URL_RE = Regex("""https?://\S+""")
private val HEADING_PREFIX = Regex("""^#{1,6}\s*""")
private val TITLE_MARKERS = setOf(
    "what's new", "whats new", "what’s new", "release notes", "yenilikler", "sürüm notları",
)

private class MutableSection(val title: String, val icon: String?) {
    val items = mutableListOf<String>()
}

fun parseReleaseNotes(body: String?): ReleaseNotes {
    val raw = body?.replace("\r\n", "\n")?.trim().orEmpty()
    if (raw.isEmpty()) return ReleaseNotes()

    var headline: String? = null
    var intro: String? = null
    var released: String? = null
    var changelogUrl: String? = null
    val sections = mutableListOf<MutableSection>()
    val paragraph = StringBuilder()

    fun addText(text: String) {
        if (text.isBlank()) return
        val last = sections.lastOrNull()
        if (last != null) {
            last.items += text
        } else if (headline == null) {
            headline = text
        } else if (intro == null) {
            intro = text
        } else {
            intro = intro + "\n\n" + text
        }
    }

    fun flushParagraph() {
        val text = paragraph.toString().trim()
        paragraph.setLength(0)
        addText(text)
    }

    val lines = raw.lines()
    var index = 0
    while (index < lines.size) {
        val original = lines[index].trim()
        index += 1
        if (original.isEmpty()) {
            flushParagraph()
            continue
        }

        val cleaned = cleanInline(original)
        if (cleaned.isEmpty()) {
            flushParagraph()
            continue
        }

        val lower = cleaned.lowercase(Locale.ROOT)
        if (lower in TITLE_MARKERS) continue
        if (lower.startsWith("released ")) {
            released = cleaned.substringAfter(' ').trim().ifEmpty { null }
            continue
        }
        if (lower.startsWith("full changelog") || lower.contains("changelog and technical")) {
            flushParagraph()
            val inline = URL_RE.find(cleaned)?.value
            if (inline != null) {
                changelogUrl = inline
            } else {
                val next = lines.getOrNull(index)?.trim()
                if (next != null && URL_RE.matches(next)) {
                    changelogUrl = next
                    index += 1
                }
            }
            continue
        }

        val icon = SECTION_ICONS.firstOrNull { cleaned.startsWith(it) }
        val isHeading = original.startsWith("#")
        if (icon != null || isHeading) {
            flushParagraph()
            val title = if (icon != null) cleaned.removePrefix(icon).trim() else cleaned
            if (title.isNotEmpty()) {
                // A leading Markdown heading is usually the release title, not a section.
                if (icon == null && sections.isEmpty() && headline == null && intro == null) {
                    headline = title
                } else {
                    sections += MutableSection(title, icon)
                }
            }
            continue
        }

        val bullet = when {
            cleaned.startsWith("•") -> cleaned.removePrefix("•").trim()
            cleaned.startsWith("- ") -> cleaned.removePrefix("- ").trim()
            cleaned.startsWith("* ") -> cleaned.removePrefix("* ").trim()
            else -> null
        }
        if (bullet != null) {
            flushParagraph()
            if (sections.isEmpty()) {
                addText(bullet)
            } else {
                sections.last().items += bullet
            }
            continue
        }

        if (paragraph.isNotEmpty()) paragraph.append(' ')
        paragraph.append(cleaned)
    }
    flushParagraph()

    val parsedSections = sections.map { ReleaseNoteSection(it.title, it.icon, it.items.toList()) }
    if (parsedSections.isEmpty() && headline == null && intro == null) {
        headline = cleanInline(raw.lineSequence().first())
    }
    return ReleaseNotes(
        headline = headline,
        intro = intro,
        released = released,
        sections = parsedSections,
        changelogUrl = changelogUrl,
    )
}

private fun cleanInline(line: String): String =
    line
        .replace(HEADING_PREFIX, "")
        .replace("**", "")
        .replace("__", "")
        .replace("`", "")
        .trim()
