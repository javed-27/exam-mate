package com.exammate.ui.mcq

import android.app.Activity
import android.content.Intent
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.exammate.mcq.CameraPermissionStore
import com.exammate.mcq.McqAnswer
import com.exammate.mcq.McqAnswerProvider
import com.exammate.mcq.McqAnswerState
import com.exammate.mcq.ScreenCaptureRequester
import com.exammate.mcq.ScreenCaptureResult
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class McqPermissionFlowTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun firstEntry_showsCameraRationale() {
        setContent(
            checker = FakeChecker(cameraGranted = false, accessibilityEnabled = false),
            store = FakeStore(denied = false),
        )

        composeRule.onNodeWithText("Camera access").assertIsDisplayed()
        composeRule.onNodeWithText("Allow camera access").assertIsDisplayed()
    }

    @Test
    fun previousCameraDenial_showsDeniedWithRetry() {
        setContent(
            checker = FakeChecker(cameraGranted = false, accessibilityEnabled = false),
            store = FakeStore(denied = true),
        )

        composeRule.onNodeWithText("Camera permission denied").assertIsDisplayed()
        composeRule.onNodeWithText("Retry").assertIsDisplayed()
    }

    @Test
    fun cameraGranted_showsAccessibilityStep() {
        setContent(
            checker = FakeChecker(cameraGranted = true, accessibilityEnabled = false),
            store = FakeStore(denied = false),
        )

        composeRule.onNodeWithText("Accessibility Service").assertIsDisplayed()
        composeRule.onNodeWithText("Open Settings").assertIsDisplayed()
    }

    @Test
    fun cameraAndAccessibilityGranted_showsScreenCaptureStep() {
        setContent(
            checker = FakeChecker(cameraGranted = true, accessibilityEnabled = true),
            store = FakeStore(denied = false),
        )

        composeRule.onNodeWithText("Screen Capture").assertIsDisplayed()
        composeRule.onNodeWithText("Start screen capture").assertIsDisplayed()
    }

    @Test
    fun allGranted_showsCaptureLayout() {
        setContent(
            checker = FakeChecker(cameraGranted = true, accessibilityEnabled = true),
            store = FakeStore(denied = false),
            initialScreenCaptureGranted = true,
            answerProvider = FakeAnswerProvider(McqAnswerState.Waiting),
        )

        composeRule.onNodeWithText("Real-Time MCQ Solver").assertIsDisplayed()
        composeRule.onNodeWithText("Capturing…").assertIsDisplayed()
        composeRule.onNodeWithText("Align the question within the frame").assertIsDisplayed()
        composeRule.onNodeWithText("Waiting for question…").assertIsDisplayed()
        composeRule.onAllNodesWithTag(CAMERA_PREVIEW_TAG).assertCountEquals(1)
        composeRule.onAllNodesWithTag(ANSWER_PANEL_TAG).assertCountEquals(1)
    }

    @Test
    fun captureReady_showsQuestionAnswerExplanationInOrder() {
        setContent(
            checker = FakeChecker(cameraGranted = true, accessibilityEnabled = true),
            store = FakeStore(denied = false),
            initialScreenCaptureGranted = true,
            answerProvider = FakeAnswerProvider(McqAnswerState.Ready(SampleAnswer)),
        )

        composeRule.onNodeWithText("Question").assertIsDisplayed()
        composeRule.onNodeWithText(SampleAnswer.question).assertIsDisplayed()
        composeRule.onNodeWithText(SampleAnswer.answer).assertIsDisplayed()
        composeRule.onNodeWithText("Confidence: 98%").assertIsDisplayed()
        composeRule.onNodeWithText("Explanation").assertIsDisplayed()
        composeRule.onNodeWithText(SampleAnswer.explanation).assertIsDisplayed()

        val questionTop = composeRule.onNodeWithText(SampleAnswer.question)
            .getUnclippedBoundsInRoot().top
        val answerTop = composeRule.onNodeWithText(SampleAnswer.answer)
            .getUnclippedBoundsInRoot().top
        val explanationTop = composeRule.onNodeWithText(SampleAnswer.explanation)
            .getUnclippedBoundsInRoot().top
        assertTrue(questionTop < answerTop)
        assertTrue(answerTop < explanationTop)
    }

    @Test
    fun captureProcessing_showsAnalysing() {
        setContent(
            checker = FakeChecker(cameraGranted = true, accessibilityEnabled = true),
            store = FakeStore(denied = false),
            initialScreenCaptureGranted = true,
            answerProvider = FakeAnswerProvider(McqAnswerState.Processing),
        )

        composeRule.onNodeWithText("Analysing question…").assertIsDisplayed()
    }

    @Test
    fun screenCaptureStep_keepsSingleCameraPreview() {
        setContent(
            checker = FakeChecker(cameraGranted = true, accessibilityEnabled = true),
            store = FakeStore(denied = false),
        )

        composeRule.onAllNodesWithTag(CAMERA_PREVIEW_TAG).assertCountEquals(1)
    }

    @Test
    fun cameraDenied_invokesBackToHome() {
        var wentHome = false
        composeRule.setContent {
            McqSolverScreen(
                onBack = {},
                onBackToHome = { wentHome = true },
                checker = FakeChecker(cameraGranted = false, accessibilityEnabled = false),
                cameraPermissionStore = FakeStore(denied = false),
                screenCaptureRequester = FakeRequester(),
                requestCamera = { _, onResult -> onResult(false) },
            )
        }

        composeRule.onNodeWithText("Allow camera access").performClick()
        composeRule.runOnIdle { assertTrue(wentHome) }
    }

    private fun setContent(
        checker: McqPermissionChecker,
        store: CameraPermissionStore,
        initialScreenCaptureGranted: Boolean = false,
        answerProvider: McqAnswerProvider = FakeAnswerProvider(McqAnswerState.Waiting),
    ) {
        composeRule.setContent {
            McqSolverScreen(
                onBack = {},
                onBackToHome = {},
                checker = checker,
                cameraPermissionStore = store,
                screenCaptureRequester = FakeRequester(),
                initialScreenCaptureGranted = initialScreenCaptureGranted,
                answerProvider = answerProvider,
            )
        }
    }

    private class FakeChecker(
        private val cameraGranted: Boolean,
        private val accessibilityEnabled: Boolean,
    ) : McqPermissionChecker {
        override fun isCameraGranted(): Boolean = cameraGranted
        override fun isAccessibilityEnabled(): Boolean = accessibilityEnabled
    }

    private class FakeStore(private val denied: Boolean) : CameraPermissionStore {
        override var cameraDenied: Boolean = denied
    }

    private class FakeRequester : ScreenCaptureRequester {
        override fun createScreenCaptureIntent(activity: Activity): Intent = Intent()
        override fun parseResult(resultCode: Int, data: Intent?): ScreenCaptureResult =
            ScreenCaptureResult(granted = true, projectionData = data)
    }

    private class FakeAnswerProvider(
        override val initialState: McqAnswerState,
    ) : McqAnswerProvider {
        override val delayMillis: Long = 0L
        override fun next(current: McqAnswerState): McqAnswerState? = null
    }

    private companion object {
        val SampleAnswer = McqAnswer(
            question = "1. Which of the following is the capital of France?",
            answer = "C. Paris",
            confidence = 0.98,
            explanation = "Paris is the capital and most populous city of France.",
        )
    }
}
