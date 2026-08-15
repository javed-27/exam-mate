package com.exammate.theory

import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TheoryCaptureModelTest {

    @Test
    fun initialState_isViewfinder() = runTest {
        val model = model()

        assertEquals(TheoryCaptureState.Viewfinder, model.state.value)
    }

    @Test
    fun capture_runsRecognizeAndReachesCaptured() = runTest {
        var calls = 0
        val model = model(recognize = {
            calls++
            "Explain the structure of the human heart."
        })

        model.capture()
        advanceUntilIdle()

        assertEquals(
            TheoryCaptureState.Captured("Explain the structure of the human heart."),
            model.state.value,
        )
        assertEquals(1, calls)
    }

    @Test
    fun capture_blankText_isOcrFailed() = runTest {
        val model = model(recognize = { "  \n " })

        model.capture()
        advanceUntilIdle()

        assertEquals(TheoryCaptureState.OcrFailed(BLANK_OCR_MESSAGE), model.state.value)
    }

    @Test
    fun capture_recognizeThrows_isOcrFailedWithMessage() = runTest {
        val model = model(recognize = { throw IOException("boom") })

        model.capture()
        advanceUntilIdle()

        assertEquals(TheoryCaptureState.OcrFailed("boom"), model.state.value)
    }

    @Test
    fun capture_recognizeThrowsWithoutMessage_usesDefaultMessage() = runTest {
        val model = model(recognize = { throw RuntimeException() })

        model.capture()
        advanceUntilIdle()

        assertEquals(TheoryCaptureState.OcrFailed(OCR_FAILURE_MESSAGE), model.state.value)
    }

    @Test
    fun captureWhileCapturing_ignoresSecondCapture() = runTest {
        val started = CompletableDeferred<Unit>()
        val released = CompletableDeferred<Unit>()
        var calls = 0
        val model = model(recognize = {
            calls++
            started.complete(Unit)
            released.await()
            "text"
        })

        model.capture()
        runCurrent()
        started.await()
        model.capture()
        runCurrent()

        assertEquals(1, calls)
        assertEquals(TheoryCaptureState.Capturing, model.state.value)

        released.complete(Unit)
        advanceUntilIdle()
        assertEquals(TheoryCaptureState.Captured("text"), model.state.value)
        assertEquals(1, calls)
    }

    @Test
    fun recapture_cancelsInFlightOcr() = runTest {
        val started = CompletableDeferred<Unit>()
        val released = CompletableDeferred<Unit>()
        val model = model(recognize = {
            started.complete(Unit)
            released.await()
            "text"
        })

        model.capture()
        runCurrent()
        started.await()
        model.recapture()
        released.complete(Unit)
        advanceUntilIdle()

        assertEquals(TheoryCaptureState.Viewfinder, model.state.value)
    }

    @Test
    fun recapture_fromCaptured_returnsToViewfinder() = runTest {
        val model = model(recognize = { "text" })

        model.capture()
        advanceUntilIdle()
        assertTrue(model.state.value is TheoryCaptureState.Captured)

        model.recapture()

        assertEquals(TheoryCaptureState.Viewfinder, model.state.value)
    }

    @Test
    fun close_preventsStateEmissionFromInFlightOcr() = runTest {
        val started = CompletableDeferred<Unit>()
        val released = CompletableDeferred<Unit>()
        val model = model(recognize = {
            started.complete(Unit)
            released.await()
            "text"
        })

        model.capture()
        runCurrent()
        started.await()
        model.close()
        released.complete(Unit)
        advanceUntilIdle()

        assertEquals(TheoryCaptureState.Capturing, model.state.value)
    }

    private fun TestScope.model(
        recognize: suspend () -> String = { "" },
    ): TheoryCaptureModel = TheoryCaptureModel(
        recognize = recognize,
        dispatcher = testDispatcher(),
    )

    private fun TestScope.testDispatcher(): CoroutineDispatcher =
        StandardTestDispatcher(testScheduler)
}
