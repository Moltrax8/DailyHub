package com.moltrax.personalnoteapp.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Feed kind titles (Phase 8): every writer kind has a human line.
 */
class FeedKindTest {

    @Test
    fun `all writer kinds titled`() {
        assertEquals("Note added", feedKindTitle(FeedKind.NOTE_ADDED))
        assertEquals("Task added", feedKindTitle(FeedKind.TASK_ADDED))
        assertEquals("Link added", feedKindTitle(FeedKind.LINK_ADDED))
        assertEquals("Card added", feedKindTitle(FeedKind.ITEM_ADDED))
        assertEquals("Comment added", feedKindTitle(FeedKind.COMMENT_ADDED))
        assertEquals("Member joined", feedKindTitle(FeedKind.MEMBER_JOINED))
        assertEquals("Member left", feedKindTitle(FeedKind.MEMBER_LEFT))
    }

    @Test
    fun `unknown kind passes through`() {
        assertEquals("file.added", feedKindTitle("file.added"))
    }
}
