package com.exammate.ui.theory

import android.graphics.Bitmap
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.exammate.mcq.CameraPermissionStore
import com.exammate.theory.TheoryCaptureModel
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TheoryCaptureFlowTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun firstEntry_showsCameraRationale() {
        setContent(cameraGranted = false, denied = false)

        composeRule.onNodeWithText("Camera access").assertIsDisplayed()
        composeRule.onNodeWithText("Allow camera access").assertIsDisplayed()
    }

    @Test
    fun previousDenial_showsDeniedWithRetry() {
        setContent(cameraGranted = false, denied = true)

        composeRule.onNodeWithText("Camera permission denied").assertIsDisplayed()
        composeRule.onNodeWithText("Retry").assertIsDisplayed()
    }

    @Test
    fun grantingPermissionFromStep_showsViewfinder() {
        var granted = false
        setContent(
            cameraGranted = false,
            denied = false,
            requestCamera = { _, onResult ->
                granted = true
                onResult(true)
            },
        )

        composeRule.onNodeWithText("Allow camera access").performClick()

        composeRule.onNodeWithTag(SHUTTER_BUTTON_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("Align the question within the frame").assertIsDisplayed()
        composeRule.runOnIdle { assertTrue(granted) }
    }

    @Test
    fun denyingPermission_invokesBack() {
        var wentBack = false
        setContent(
            cameraGranted = false,
            denied = false,
            onBack = { wentBack = true },
            requestCamera = { _, onResult -> onResult(false) },
        )

        composeRule.onNodeWithText("Allow camera access").performClick()

        composeRule.runOnIdle { assertTrue(wentBack) }
    }

    @Test
    fun capture_showsPreviewCardAndOcrText() {
        val model = model(recognize = { "Explain the structure of the human heart." })
        setContent(
            cameraGranted = true,
            denied = false,
            model = model,
            initialFrame = testBitmap(),
        )

        composeRule.onNodeWithTag(SHUTTER_BUTTON_TAG).performClick()
        waitForOcrText()

        composeRule.onNodeWithTag(CAPTURED_QUESTION_CARD_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("Captured Question").assertIsDisplayed()
        composeRule.onNodeWithText("1/1").assertIsDisplayed()
        composeRule.onNodeWithText("Tap to enlarge").assertIsDisplayed()
        composeRule.onNodeWithTag(CAPTURED_IMAGE_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("Explain the structure of the human heart.").assertIsDisplayed()
        composeRule.onAllNodesWithTag(SHUTTER_BUTTON_TAG).assertCountEquals(0)
    }

    @Test
    fun captureProgress_showsExtractingText() {
        val gate = CompletableDeferred<Unit>()
        val model = model(recognize = {
            gate.await()
            "Explain the human heart."
        })
        setContent(
            cameraGranted = true,
            denied = false,
            model = model,
            initialFrame = testBitmap(),
        )

        composeRule.onNodeWithTag(SHUTTER_BUTTON_TAG).performClick()

        composeRule.onNodeWithText("Extracting question text…").assertIsDisplayed()

        composeRule.runOnIdle { gate.complete(Unit) }
        waitForOcrText()
    }

    @Test
    fun ocrFailure_showsErrorAndRetake() {
        val model = model(recognize = { throw IOException("boom") })
        setContent(
            cameraGranted = true,
            denied = false,
            model = model,
            initialFrame = testBitmap(),
        )

        composeRule.onNodeWithTag(SHUTTER_BUTTON_TAG).performClick()
        waitUntilTag(RETRY_BUTTON_TAG)

        composeRule.onNodeWithText("Couldn't read the question").assertIsDisplayed()
        composeRule.onNodeWithText("boom").assertIsDisplayed()

        composeRule.onNodeWithTag(RETRY_BUTTON_TAG).performClick()

        composeRule.onNodeWithTag(SHUTTER_BUTTON_TAG).assertIsDisplayed()
    }

    @Test
    fun recapture_returnsToViewfinder() {
        val model = model(recognize = { "text" })
        setContent(
            cameraGranted = true,
            denied = false,
            model = model,
            initialFrame = testBitmap(),
        )

        composeRule.onNodeWithTag(SHUTTER_BUTTON_TAG).performClick()
        waitUntilTag(CAPTURED_QUESTION_CARD_TAG)

        composeRule.onNodeWithTag(RECAPTURE_BUTTON_TAG).performClick()

        composeRule.onNodeWithTag(SHUTTER_BUTTON_TAG).assertIsDisplayed()
        composeRule.onAllNodesWithTag(CAPTURED_QUESTION_CARD_TAG).assertCountEquals(0)
    }

    @Test
    fun tapToEnlarge_opensAndDismissesDialog() {
        val model = model(recognize = { "text" })
        setContent(
            cameraGranted = true,
            denied = false,
            model = model,
            initialFrame = testBitmap(),
        )

        composeRule.onNodeWithTag(SHUTTER_BUTTON_TAG).performClick()
        waitUntilTag(CAPTURED_IMAGE_TAG)

        composeRule.onNodeWithTag(CAPTURED_IMAGE_TAG).performClick()

        composeRule.onNodeWithTag(ENLARGED_IMAGE_TAG).assertIsDisplayed()

        composeRule.onNodeWithTag(ENLARGED_IMAGE_TAG).performClick()

        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag(ENLARGED_IMAGE_TAG).fetchSemanticsNodes().isEmpty()
        }
    }

    @Test
    fun stateRestoration_preservesOcrText() {
        val restorationTester = StateRestorationTester(composeRule)
        val model = model(recognize = { "Explain the human heart." })
        restorationTester.setContent {
            TheorySolverScreen(
                onBack = {},
                cameraPermissionStore = FakeStore(denied = false),
                isCameraGranted = { true },
                requestCamera = { _, _ -> },
                model = model,
                initialFrame = testBitmap(),
            )
        }

        composeRule.onNodeWithTag(SHUTTER_BUTTON_TAG).performClick()
        waitForOcrText()
        composeRule.onNodeWithText("Explain the human heart.").assertIsDisplayed()

        restorationTester.emulateSavedInstanceStateRestore()

        composeRule.onNodeWithText("Captured Question").assertIsDisplayed()
        composeRule.onNodeWithText("Explain the human heart.").assertIsDisplayed()
        composeRule.onNodeWithTag(RECAPTURE_BUTTON_TAG).assertIsDisplayed()
    }

    private fun waitForOcrText() {
        waitUntilTag(OCR_TEXT_TAG)
    }

    private fun waitUntilTag(tag: String) {
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun setContent(
        cameraGranted: Boolean,
        denied: Boolean,
        onBack: () -> Unit = {},
        requestCamera: ((String, (Boolean) -> Unit) -> Unit)? = null,
        model: TheoryCaptureModel? = null,
        initialFrame: Bitmap? = null,
    ) {
        composeRule.setContent {
            TheorySolverScreen(
                onBack = onBack,
                cameraPermissionStore = FakeStore(denied),
                isCameraGranted = { cameraGranted },
                requestCamera = requestCamera,
                model = model,
                initialFrame = initialFrame,
            )
        }
    }

    private fun model(recognize: suspend () -> String): TheoryCaptureModel =
        TheoryCaptureModel(recognize = recognize)

    private fun testBitmap(): Bitmap =
        Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)

    private class FakeStore(private val denied: Boolean) : CameraPermissionStore {
        override var cameraDenied: Boolean = denied
    }
}
