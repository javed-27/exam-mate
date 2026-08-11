package com.exammate.ui.mcq

import android.Manifest
import android.app.Activity
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.exammate.mcq.CameraPermissionStore
import com.exammate.mcq.McqAnswer
import com.exammate.mcq.McqSolverPipeline
import com.exammate.mcq.ScreenCaptureRequester
import com.exammate.mcq.ScreenCaptureResult
import com.exammate.mcq.ai.McqAiClient
import com.exammate.mcq.ai.McqAiEvent
import com.exammate.mcq.ocr.MlKitOcrService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ContinuousCaptureTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun cameraKeepsDeliveringFramesAfterFirstProcess() {
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(
            "pm grant com.exammate android.permission.CAMERA",
        ).close()

        val pipeline = McqSolverPipeline(
            ocr = MlKitOcrService(),
            aiClient = FakeAiClient(),
            minFrameIntervalMillis = 0L,
        )
        composeRule.setContent {
            McqSolverScreen(
                onBack = {},
                onBackToHome = {},
                checker = object : McqPermissionChecker {
                    override fun isCameraGranted(): Boolean = true
                    override fun isAccessibilityEnabled(): Boolean = true
                },
                cameraPermissionStore = object : CameraPermissionStore {
                    override var cameraDenied: Boolean = false
                },
                screenCaptureRequester = object : ScreenCaptureRequester {
                    override fun createScreenCaptureIntent(activity: Activity): Intent = Intent()
                    override fun parseResult(resultCode: Int, data: Intent?): ScreenCaptureResult =
                        ScreenCaptureResult(granted = true, projectionData = null)
                },
                initialScreenCaptureGranted = true,
                pipeline = pipeline,
            )
        }

        composeRule.waitUntil(timeoutMillis = 15_000) { pipeline.framesDelivered >= 3 }
        val firstCount = pipeline.framesDelivered
        composeRule.waitUntil(timeoutMillis = 15_000) { pipeline.framesDelivered > firstCount + 2 }
        assertTrue(
            "capture should keep delivering frames: $firstCount -> ${pipeline.framesDelivered}",
            pipeline.framesDelivered > firstCount,
        )
    }

    private class FakeAiClient : McqAiClient {
        override fun stream(question: String, options: List<String>): Flow<McqAiEvent> = flow {
            emit(
                McqAiEvent.Answer(
                    McqAnswer(question = question, answer = "C", confidence = 1.0, explanation = ""),
                ),
            )
        }
    }
}
