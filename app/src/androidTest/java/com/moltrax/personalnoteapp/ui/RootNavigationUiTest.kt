package com.moltrax.personalnoteapp.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.moltrax.personalnoteapp.MainActivity
import com.moltrax.personalnoteapp.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RootNavigationUiTest {
    @get:Rule(order = 0) val permission: GrantPermissionRule = GrantPermissionRule.grant(android.Manifest.permission.POST_NOTIFICATIONS)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    @Test fun rootsShowLocalFeatures_andDetailHidesChrome() {
        val labels = listOf("Notes", "Calendar", "Focus", "Settings", "Tasks")
        labels.forEach { label ->
            compose.onNodeWithContentDescription(label).performClick()
            compose.onNodeWithContentDescription("Notes").assertIsDisplayed()
        }
        compose.onNodeWithContentDescription("New task").performClick()
        compose.onNodeWithText("Save").assertIsDisplayed()
        compose.onNodeWithContentDescription("Notes").assertDoesNotExist()
    }
}
