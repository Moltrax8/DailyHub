package com.moltrax.personalnoteapp.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.moltrax.personalnoteapp.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotesUiTest {
    @get:Rule(order = 0) val permission: GrantPermissionRule = GrantPermissionRule.grant(android.Manifest.permission.POST_NOTIFICATIONS)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    @Test fun createPinSearchEditAndDeleteLocalNote() {
        val title = "NotesCheck${System.currentTimeMillis()}"
        compose.onNodeWithContentDescription("Notes").performClick()
        compose.onNodeWithContentDescription("New note").performClick()
        compose.onNodeWithText("Title").performTextInput(title)
        compose.onNodeWithText("Note", substring = false).performTextInput("Local-only body")
        compose.onNodeWithContentDescription("Pin note").performClick()
        compose.onNodeWithText("Save").performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Pinned").assertIsDisplayed()
        compose.onNodeWithText("Search notes").performTextInput(title)
        compose.onNodeWithText(title).performClick()
        compose.onNodeWithText("Local-only body").performTextReplacement("Edited local body")
        compose.onNodeWithText("Save").performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Edited local body").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(title).performClick()
        compose.onNodeWithContentDescription("Delete").performClick()
        compose.onNodeWithText("Delete", substring = false).performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithContentDescription("New note").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(title).assertDoesNotExist()
    }
}
