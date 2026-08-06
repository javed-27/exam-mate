package com.exammate.ui.mcq

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.exammate.mcq.CameraPermissionStore
import com.exammate.mcq.McqAnswer
import com.exammate.mcq.McqAnswerState
import com.exammate.mcq.McqPipeline
import com.exammate.mcq.ScreenCaptureRequester
import com.exammate.mcq.ScreenCaptureResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
            pipeline = FakePipeline(McqAnswerState.Waiting),
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
            pipeline = FakePipeline(McqAnswerState.Ready(SampleAnswer)),
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
            pipeline = FakePipeline(McqAnswerState.Processing),
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
    fun landscapeLayout_showsCaptureLeftOfAnswer() {
        composeRule.setContent {
            McqCaptureLayout(
                landscape = true,
                captureArea = { modifier -> Box(modifier = modifier.testTag(CAPTURE_AREA_TAG)) {} },
                answerArea = { modifier -> Box(modifier = modifier.testTag(ANSWER_AREA_TAG)) {} },
            )
        }

        val captureRight = composeRule.onNodeWithTag(CAPTURE_AREA_TAG)
            .getUnclippedBoundsInRoot().right
        val answerLeft = composeRule.onNodeWithTag(ANSWER_AREA_TAG)
            .getUnclippedBoundsInRoot().left
        assertTrue(captureRight <= answerLeft)
    }

    @Test
    fun portraitLayout_stacksCaptureAboveAnswer() {
        composeRule.setContent {
            McqCaptureLayout(
                landscape = false,
                captureArea = { modifier -> Box(modifier = modifier.testTag(CAPTURE_AREA_TAG)) {} },
                answerArea = { modifier -> Box(modifier = modifier.testTag(ANSWER_AREA_TAG)) {} },
            )
        }

        val captureBottom = composeRule.onNodeWithTag(CAPTURE_AREA_TAG)
            .getUnclippedBoundsInRoot().bottom
        val answerTop = composeRule.onNodeWithTag(ANSWER_AREA_TAG)
            .getUnclippedBoundsInRoot().top
        assertTrue(captureBottom <= answerTop)
    }

    @Test
    fun answerSurvivesRecreation() {
        val restorationTester = StateRestorationTester(composeRule)
        restorationTester.setContent {
            McqSolverScreen(
                onBack = {},
                onBackToHome = {},
                checker = FakeChecker(cameraGranted = true, accessibilityEnabled = true),
                cameraPermissionStore = FakeStore(denied = false),
                screenCaptureRequester = FakeRequester(),
                initialScreenCaptureGranted = true,
                pipeline = FakePipeline(McqAnswerState.Ready(SampleAnswer)),
            )
        }

        composeRule.onNodeWithText(SampleAnswer.answer).assertIsDisplayed()

        restorationTester.emulateSavedInstanceStateRestore()

        composeRule.onNodeWithText("Real-Time MCQ Solver").assertIsDisplayed()
        composeRule.onNodeWithText(SampleAnswer.answer).assertIsDisplayed()
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
        pipeline: McqPipeline = FakePipeline(McqAnswerState.Waiting),
    ) {
        composeRule.setContent {
            McqSolverScreen(
                onBack = {},
                onBackToHome = {},
                checker = checker,
                cameraPermissionStore = store,
                screenCaptureRequester = FakeRequester(),
                initialScreenCaptureGranted = initialScreenCaptureGranted,
                pipeline = pipeline,
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

    private class FakePipeline(
        initial: McqAnswerState,
    ) : McqPipeline {
        override val state: StateFlow<McqAnswerState> = MutableStateFlow(initial)

        override suspend fun onFrame(bitmap: Bitmap) = Unit
    }

    private companion object {
        const val CAPTURE_AREA_TAG = "capture_area"
        const val ANSWER_AREA_TAG = "answer_area"

        val SampleAnswer = McqAnswer(
            question = "1. Which of the following is the capital of France?",
            answer = "C. Paris",
            confidence = 0.98,
            explanation = "Paris is the capital and most populous city of France.",
        )
    }
}
