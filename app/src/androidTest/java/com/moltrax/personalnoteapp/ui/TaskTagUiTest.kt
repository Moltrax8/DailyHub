package com.moltrax.personalnoteapp.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.moltrax.personalnoteapp.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI end-to-end (Phase 2) on a real device/emulator: creates a tagged task
 * through the actual screens and verifies the Home multi-tag filter chips.
 * Runs against the real app (Hilt + Room + navigation, no test doubles).
 */
@RunWith(AndroidJUnit4::class)
class TaskTagUiTest {

    @get:Rule(order = 0)
    val permission: GrantPermissionRule =
        GrantPermissionRule.grant(android.Manifest.permission.POST_NOTIFICATIONS)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun createTaggedTask_chipAppearsAndFiltersIt() {
        dismissLoginIfPresent()

        val stamp = (System.currentTimeMillis() % 100000).toString()
        val title = "UiTask$stamp"
        val tag = "UiTag$stamp"

        // New task via the Home FAB.
        compose.onNodeWithContentDescription("New task").performClick()
        compose.onNodeWithText("Title").performTextInput(title)

        // New tag via the category field + add button.
        compose.onNodeWithText("New category").performScrollTo()
        compose.onNodeWithText("New category").performTextInput(tag)
        compose.onNodeWithContentDescription("Add category").performClick()
        // The tag chip is now selected on the task.
        compose.onNodeWithText(tag).assertIsDisplayed()

        compose.onNodeWithText("Save").performClick()

        // The save runs in viewModelScope (untracked by Espresso): wait for Home
        // (FAB exists only there) before asserting, or detail nodes pass vacuously.
        compose.waitUntil(timeoutMillis = 15_000) {
            compose.onAllNodesWithContentDescription("New task")
                .fetchSemanticsNodes().size == 1
        }

        // Home shows the task and the new filter chip.
        compose.onNodeWithText(title).assertIsDisplayed()
        compose.onNodeWithText(tag).performScrollTo()
        compose.onNodeWithText(tag).assertIsDisplayed()

        // Filtering by the tag keeps the task visible.
        compose.onNodeWithText(tag).performClick()
        compose.onNodeWithText(title).assertIsDisplayed()

        // Back to unfiltered: still visible.
        compose.onNodeWithText("All categories").performScrollTo()
        compose.onNodeWithText("All categories").performClick()
        compose.onNodeWithText(title).assertIsDisplayed()
    }

    /**
     * With Drive sync enabled the app starts on Login; debug builds cannot
     * sign in (debug SHA-1 unregistered), so tests continue offline.
     */
    private fun dismissLoginIfPresent() {
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithContentDescription("New task").fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodesWithText("Continue offline").fetchSemanticsNodes().isNotEmpty()
        }
        if (compose.onAllNodesWithText("Continue offline").fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithText("Continue offline").performClick()
        }
    }
}
