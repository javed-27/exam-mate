package com.exammate

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.exammate.mcq.SharedPrefsCameraPermissionStore
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

        advanceToHome()

        composeTestRule.onNodeWithText("AI Study Assistant").assertIsDisplayed()
        composeTestRule.onNodeWithText("Real-Time MCQ Solver").assertIsDisplayed()
        composeTestRule.onNodeWithText("Theory Question Solver").assertIsDisplayed()
    }

    @Test
    fun mcqCard_navigatesToMcqPermissionFlow() {
        setCameraDenied(false)
        advanceToHome()

        composeTestRule.onNodeWithText("Real-Time MCQ Solver").performClick()

        composeTestRule.onNodeWithText("MCQ Solver").assertIsDisplayed()
        composeTestRule.onNodeWithText("Camera access").assertIsDisplayed()
        composeTestRule.onNodeWithText("Allow camera access").assertIsDisplayed()
    }

    @Test
    fun reEntryAfterCameraDenial_showsDeniedWithRetry() {
        setCameraDenied(true)
        advanceToHome()

        composeTestRule.onNodeWithText("Real-Time MCQ Solver").performClick()

        composeTestRule.onNodeWithText("Camera permission denied").assertIsDisplayed()
        composeTestRule.onNodeWithText("Retry").assertIsDisplayed()
    }

    @Test
    fun theoryCard_navigatesToTheorySolver() {
        advanceToHome()

        composeTestRule.onNodeWithText("Theory Question Solver").performClick()

        composeTestRule.onNodeWithText("Theory Question Solver").assertIsDisplayed()
        composeTestRule.onNodeWithText("Coming soon").assertIsDisplayed()
    }

    @Test
    fun backFromMcqSolver_returnsToHome() {
        setCameraDenied(false)
        advanceToHome()

        composeTestRule.onNodeWithText("Real-Time MCQ Solver").performClick()
        composeTestRule.onNodeWithContentDescription("Back").performClick()

        composeTestRule.onNodeWithText("AI Study Assistant").assertIsDisplayed()
    }

    private fun advanceToHome() {
        composeTestRule.mainClock.advanceTimeBy(SPLASH_DURATION_MS + 500)
        composeTestRule.waitForIdle()
    }

    private fun setCameraDenied(denied: Boolean) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        SharedPrefsCameraPermissionStore(context).cameraDenied = denied
    }
}
