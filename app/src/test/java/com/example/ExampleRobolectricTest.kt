package com.example

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun launchMainActivityAndNavigateTabs() {
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("query_dashboard_list").assertExists()
        composeTestRule.onNodeWithTag("nav_tab_packages").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("packages_ussd_list").assertExists()
        composeTestRule.onNodeWithTag("nav_tab_calculator").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("smart_calculator_list").assertExists()
        composeTestRule.onNodeWithTag("nav_tab_history").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("history_export_list").assertExists()
    }
}
