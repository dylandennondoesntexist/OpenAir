package com.openair.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.openair.app.MainActivity
import org.junit.Rule
import org.junit.Test

class OpenAirAppTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun waitForPlayback() {
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithContentDescription("Pause")
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun homeAutoPlaysFirstClip() {
        composeRule.onNodeWithTag("home_screen").assertIsDisplayed()
        // The queue loads asynchronously (network when Supabase is configured,
        // seed fallback otherwise) — wait for the first clip to appear.
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithText("For You").fetchSemanticsNodes().isNotEmpty() &&
                composeRule.onAllNodesWithContentDescription("Like").fetchSemanticsNodes().isNotEmpty()
        }
        waitForPlayback()
    }

    @Test
    fun pauseButtonTogglesToPlay() {
        waitForPlayback()
        composeRule.onNodeWithContentDescription("Pause").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithContentDescription("Play")
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun stationModeIsLiveWithNoSkipOrSpeedControls() {
        waitForPlayback()
        composeRule.onNodeWithText("Asbury Park").performClick()

        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("LIVE").fetchSemanticsNodes().isNotEmpty()
        }
        // Live radio: no on-demand skip, no speed control.
        composeRule.onAllNodesWithContentDescription("Next clip")
            .fetchSemanticsNodes().let { assert(it.isEmpty()) }
        composeRule.onAllNodesWithText("1×")
            .fetchSemanticsNodes().let { assert(it.isEmpty()) }
    }

    @Test
    fun bottomNavigationOpensAllSurfaces() {
        composeRule.onNodeWithText("Explore").performClick()
        composeRule.onNodeWithTag("explore_screen").assertIsDisplayed()
        composeRule.onNodeWithText("New Orleans").assertIsDisplayed()

        composeRule.onNodeWithText("Create").performClick()
        composeRule.onNodeWithTag("create_screen").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Start recording").assertIsDisplayed()

        composeRule.onNodeWithText("Inbox").performClick()
        composeRule.onNodeWithTag("inbox_screen").assertIsDisplayed()

        composeRule.onNodeWithText("Profile").performClick()
        composeRule.onNodeWithTag("profile_screen").assertIsDisplayed()
        composeRule.onNodeWithText("Guest").assertIsDisplayed()
        composeRule.onNodeWithText("My clips").assertIsDisplayed()
    }

    @Test
    fun exploreStationTapTunesHomeFeedToLive() {
        composeRule.onNodeWithText("Explore").performClick()
        composeRule.onNodeWithText("New Orleans").performClick()

        composeRule.onNodeWithTag("home_screen").assertIsDisplayed()
        composeRule.onNodeWithText("New Orleans").assertIsDisplayed()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("LIVE").fetchSemanticsNodes().isNotEmpty()
        }
    }
}
