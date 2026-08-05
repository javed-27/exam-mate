package com.exammate.ui.mcq

import android.app.Activity
import android.content.Intent
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.exammate.mcq.CameraPermissionStore
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
    fun allGranted_showsCaptureSessionActive() {
        setContent(
            checker = FakeChecker(cameraGranted = true, accessibilityEnabled = true),
            store = FakeStore(denied = false),
            initialScreenCaptureGranted = true,
        )

        composeRule.onNodeWithText("Capture session active").assertIsDisplayed()
        composeRule.onAllNodesWithTag(CAMERA_PREVIEW_TAG).assertCountEquals(1)
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
    ) {
        composeRule.setContent {
            McqSolverScreen(
                onBack = {},
                onBackToHome = {},
                checker = checker,
                cameraPermissionStore = store,
                screenCaptureRequester = FakeRequester(),
                initialScreenCaptureGranted = initialScreenCaptureGranted,
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
}
