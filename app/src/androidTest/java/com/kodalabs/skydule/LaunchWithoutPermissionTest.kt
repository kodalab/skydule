package com.kodalabs.skydule

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import org.junit.Rule
import org.junit.Test

/**
 * First launch, before calendar access is granted: the app must show the landing page, not crash.
 * (Build 49 crashed here on Android 16: it watched the calendar without permission.)
 * Runs on its own, first, in a freshly installed app: see the "UI tests" workflow.
 */
@OptIn(ExperimentalTestApi::class)
class LaunchWithoutPermissionTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test fun firstLaunchShowsTheLandingPage() {
        compose.waitUntil(20_000) { compose.onAllNodesWithText("Get started").fetchSemanticsNodes().isNotEmpty() }
    }
}
