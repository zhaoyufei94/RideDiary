package com.ridediary

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UiSmokeTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private fun text(id: Int)=compose.activity.getString(id)
    @Test fun emptyStateDemoAndAllFourTabs() {
        compose.onNodeWithText(text(R.string.demo_enter)).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.demo_banner)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.summary)).performClick()
        compose.onNodeWithText(text(R.string.history)).assertExists()
        compose.onNodeWithText(text(R.string.statistics)).performClick()
        compose.onNodeWithText(text(R.string.commute_days)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.routes)).performClick()
        compose.onNodeWithText(text(R.string.route_sketch)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.commute)).performClick()
        compose.onNodeWithText(text(R.string.replay)).performScrollTo().performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithText(text(R.string.arrival)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(text(R.string.arrival)).assertExists()
    }
}
