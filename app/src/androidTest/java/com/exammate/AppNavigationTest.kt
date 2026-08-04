package com.exammate

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.exammate.ui.splash.SPLASH_DURATION_MS
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppNavigationTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun splashScreen_isDisplayedInitially() {
        composeTestRule.onNodeWithText("Exam Mate").assertIsDisplayed()
    }

    @Test
    fun splashNavigatesToHome_afterDelay() {
        composeTestRule.onNodeWithText("Exam Mate").assertIsDisplayed()

        composeTestRule.mainClock.advanceTimeBy(SPLASH_DURATION_MS + 500)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Welcome to Exam Mate").assertIsDisplayed()
    }
}
