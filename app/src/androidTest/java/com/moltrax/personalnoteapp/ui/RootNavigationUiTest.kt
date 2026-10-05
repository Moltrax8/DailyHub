package com.moltrax.personalnoteapp.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.moltrax.personalnoteapp.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Root navigation shell: the five root destinations share one app-level
 * bottom bar (owned by DailyHubScaffold, not by HomeScreen), detail flows
 * hide it, and the Tasks/Calendar segmented control switches sibling views.
 */
@RunWith(AndroidJUnit4::class)
class RootNavigationUiTest {

    @get:Rule(order = 0)
    val permission: GrantPermissionRule =
        GrantPermissionRule.grant(android.Manifest.permission.POST_NOTIFICATIONS)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    private fun barLabel(label: String) {
        // Bottom-bar labels are unique (screen titles differ: "My Workouts",
        // "Friends", "Profile" header vs same-text bar item is handled by
        // asserting at least one node is displayed).
        compose.onAllNodesWithText(label)[0].assertIsDisplayed()
    }

    @Test
    fun rootDestinations_shareOneBottomBar_detailHidesIt() {
        dismissLoginIfPresent()

        // Root chrome is visible on Tasks (bar labels present alongside content).
        barLabel("Workout")
        barLabel("Projects")
        barLabel("Social")
        compose.onNodeWithText("Calendar").assertIsDisplayed()

        // Switch roots via the shared bar; each lands on its screen.
        compose.onAllNodesWithText("Workout")[0].performClick()
        compose.onNodeWithText("My Workouts").assertIsDisplayed()
        compose.onAllNodesWithText("Projects")[0].performClick()
        compose.onAllNodesWithText("Social")[0].performClick()
        compose.onNodeWithText("Friends").assertIsDisplayed()

        // Back to Tasks; Tasks/Calendar segmented control is present.
        compose.onAllNodesWithText("Tasks")[0].performClick()
        compose.onNodeWithText("Calendar").assertIsDisplayed()

        // Detail flow hides the root bar (focused chrome, no bottom tabs).
        compose.onNodeWithContentDescription("New task").performClick()
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText("Save").fetchSemanticsNodes().isNotEmpty()
        }
        assert(
            compose.onAllNodesWithText("Social").fetchSemanticsNodes().isEmpty(),
        ) { "Root bottom bar must be hidden on TaskDetail" }
    }

    /**
     * With Drive sync enabled the app starts on Login; debug builds cannot
     * sign in (debug SHA-1 unregistered), so tests continue offline.
     */
    private fun dismissLoginIfPresent() {
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText("Calendar").fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodesWithText("Continue offline").fetchSemanticsNodes().isNotEmpty()
        }
        if (compose.onAllNodesWithText("Continue offline").fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithText("Continue offline").performClick()
        }
    }
}
