package com.exammate.mcq

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.exammate.mcq.ai.McqAiClient
import com.exammate.mcq.ai.McqAiEvent
import com.exammate.mcq.ocr.OcrService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class McqPipelineInstrumentedTest {

    @Test
    fun onFrame_newQuestion_reachesReady() {
        val ocr = FakeOcrService(listOf(SAMPLE_TEXT))
        val ai = FakeAiClient(SampleAnswer)
        val pipeline = McqSolverPipeline(ocr, ai, minFrameIntervalMillis = 0L)

        sendFrame(pipeline, whiteBitmap())
        waitFor { pipeline.state.value is McqAnswerState.Ready }

        val ready = pipeline.state.value as McqAnswerState.Ready
        assertEquals(SampleAnswer, ready.answer)
        assertEquals(1, ai.callCount)
    }

    @Test
    fun onFrame_unchangedQuestion_doesNotReprocess() {
        val ocr = FakeOcrService(listOf(SAMPLE_TEXT, SAMPLE_TEXT))
        val ai = FakeAiClient(SampleAnswer)
        val pipeline = McqSolverPipeline(ocr, ai, minFrameIntervalMillis = 0L)

        sendFrame(pipeline, whiteBitmap())
        sendFrame(pipeline, blackBitmap())
        waitFor { ocr.callCount == 2 }
        waitFor { pipeline.state.value is McqAnswerState.Ready }

        assertTrue(pipeline.state.value is McqAnswerState.Ready)
        assertEquals(1, ai.callCount)
    }

    @Test
    fun onFrame_identicalFrames_skipOcr() {
        val ocr = FakeOcrService(listOf(SAMPLE_TEXT))
        val ai = FakeAiClient(SampleAnswer)
        val pipeline = McqSolverPipeline(ocr, ai, minFrameIntervalMillis = 0L)

        sendFrame(pipeline, whiteBitmap())
        sendFrame(pipeline, whiteBitmap())
        waitFor { pipeline.state.value is McqAnswerState.Ready }

        assertEquals(1, ocr.callCount)
        assertEquals(1, pipeline.ocrFramesSkipped)
        assertEquals(2, pipeline.framesDelivered)
        assertEquals(1, ai.callCount)
    }

    @Test
    fun onFrame_changedFrames_runOcrButDedupeAnswer() {
        val ocr = FakeOcrService(listOf(SAMPLE_TEXT, SAMPLE_TEXT))
        val ai = FakeAiClient(SampleAnswer)
        val pipeline = McqSolverPipeline(ocr, ai, minFrameIntervalMillis = 0L)

        sendFrame(pipeline, whiteBitmap())
        sendFrame(pipeline, blackBitmap())
        waitFor { ocr.callCount == 2 }
        waitFor { pipeline.state.value is McqAnswerState.Ready }

        assertEquals(2, ocr.callCount)
        assertEquals(1, ai.callCount)
    }

    @Test
    fun onFrame_unparseableFrameAfterAnswer_keepsLastAnswer() {
        val ocr = FakeOcrService(listOf(SAMPLE_TEXT, "just some stray text with no options"))
        val ai = FakeAiClient(SampleAnswer)
        val pipeline = McqSolverPipeline(ocr, ai, minFrameIntervalMillis = 0L)

        sendFrame(pipeline, whiteBitmap())
        sendFrame(pipeline, blackBitmap())
        waitFor { ocr.callCount == 2 }
        waitFor { pipeline.state.value is McqAnswerState.Ready }

        val ready = pipeline.state.value as McqAnswerState.Ready
        assertEquals(SampleAnswer, ready.answer)
    }

    @Test
    fun onFrame_ocrFailureAfterAnswer_keepsLastAnswer() {
        val ocr = FakeOcrService(listOf(SAMPLE_TEXT))
        val ai = FakeAiClient(SampleAnswer)
        val pipeline = McqSolverPipeline(ocr, ai, minFrameIntervalMillis = 0L)

        sendFrame(pipeline, whiteBitmap())
        waitFor { pipeline.state.value is McqAnswerState.Ready }
        ocr.failNext = true
        sendFrame(pipeline, blackBitmap())
        waitFor { ocr.callCount == 2 }

        val ready = pipeline.state.value as McqAnswerState.Ready
        assertEquals(SampleAnswer, ready.answer)
    }

    private fun sendFrame(pipeline: McqSolverPipeline, bitmap: Bitmap) {
        runBlocking { pipeline.onFrame(bitmap) }
    }

    private fun waitFor(predicate: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 5000
        while (SystemClock.elapsedRealtime() < deadline) {
            if (predicate()) return
            Thread.sleep(20)
        }
        fail("Timed out waiting for condition")
    }

    private fun whiteBitmap(): Bitmap =
        Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
            .apply { eraseColor(0xFFFFFFFF.toInt()) }

    private fun blackBitmap(): Bitmap =
        Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
            .apply { eraseColor(0xFF000000.toInt()) }

    private class FakeOcrService(
        private val responses: List<String>,
    ) : OcrService {
        private var index = 0
        var failNext = false
        var callCount = 0

        override suspend fun recognize(bitmap: Bitmap): String {
            callCount++
            if (failNext) throw IllegalStateException("ocr failure")
            return responses[index.coerceAtMost(responses.lastIndex)].also { index++ }
        }

        override fun close() = Unit
    }

    private class FakeAiClient(
        private val answer: McqAnswer,
    ) : McqAiClient {
        var callCount = 0

        override fun stream(question: String, options: List<String>): Flow<McqAiEvent> = flow {
            callCount++
            emit(McqAiEvent.Text("Paris"))
            emit(McqAiEvent.Answer(answer))
        }
    }

    private companion object {
        const val SAMPLE_TEXT = "1. What is the capital of France?\nA. Paris\nB. Rome\nC. Berlin\nD. Madrid"

        val SampleAnswer = McqAnswer(
            question = "1. What is the capital of France?",
            answer = "C. Paris",
            confidence = 0.98,
            explanation = "Paris is the capital of France.",
        )
    }
}
