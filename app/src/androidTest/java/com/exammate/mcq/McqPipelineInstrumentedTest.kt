package com.exammate.mcq

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.exammate.mcq.ai.McqAiClient
import com.exammate.mcq.ocr.OcrService
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class McqPipelineInstrumentedTest {

    @Test
    fun onFrame_newQuestion_returnsReadyAnswer() = runBlocking {
        val ocr = FakeOcrService(listOf(SAMPLE_TEXT))
        val ai = FakeAiClient(SampleAnswer)
        val pipeline = McqSolverPipeline(ocr, ai, minFrameIntervalMillis = 0L)

        val result = pipeline.onFrame(bitmap())

        assertTrue(result is McqAnswerState.Ready)
        assertEquals(SampleAnswer, (result as McqAnswerState.Ready).answer)
        assertEquals(1, ai.callCount)
    }

    @Test
    fun onFrame_unchangedQuestion_doesNotReprocess() = runBlocking {
        val ocr = FakeOcrService(listOf(SAMPLE_TEXT, SAMPLE_TEXT))
        val ai = FakeAiClient(SampleAnswer)
        val pipeline = McqSolverPipeline(ocr, ai, minFrameIntervalMillis = 0L)

        val first = pipeline.onFrame(bitmap())
        val second = pipeline.onFrame(bitmap())

        assertTrue(first is McqAnswerState.Ready)
        assertTrue(second is McqAnswerState.Ready)
        assertEquals(1, ai.callCount)
    }

    @Test
    fun onFrame_unparseableFrameAfterAnswer_keepsLastAnswer() = runBlocking {
        val ocr = FakeOcrService(listOf(SAMPLE_TEXT, "just some stray text with no options"))
        val ai = FakeAiClient(SampleAnswer)
        val pipeline = McqSolverPipeline(ocr, ai, minFrameIntervalMillis = 0L)

        val first = pipeline.onFrame(bitmap())
        val second = pipeline.onFrame(bitmap())

        assertTrue(first is McqAnswerState.Ready)
        assertTrue(second is McqAnswerState.Ready)
        assertEquals(SampleAnswer, (second as McqAnswerState.Ready).answer)
    }

    @Test
    fun onFrame_ocrFailureAfterAnswer_keepsLastAnswer() = runBlocking {
        val ocr = FakeOcrService(listOf(SAMPLE_TEXT))
        val ai = FakeAiClient(SampleAnswer)
        val pipeline = McqSolverPipeline(ocr, ai, minFrameIntervalMillis = 0L)

        val first = pipeline.onFrame(bitmap())
        ocr.failNext = true
        val second = pipeline.onFrame(bitmap())

        assertTrue(first is McqAnswerState.Ready)
        assertTrue(second is McqAnswerState.Ready)
        assertEquals(SampleAnswer, (second as McqAnswerState.Ready).answer)
    }

    private fun bitmap(): Bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)

    private class FakeOcrService(
        private val responses: List<String>,
    ) : OcrService {
        private var index = 0
        var failNext = false

        override suspend fun recognize(bitmap: Bitmap): String {
            if (failNext) throw IllegalStateException("ocr failure")
            return responses[index.coerceAtMost(responses.lastIndex)].also { index++ }
        }

        override fun close() = Unit
    }

    private class FakeAiClient(
        private val answer: McqAnswer,
    ) : McqAiClient {
        var callCount = 0

        override suspend fun solve(question: String, options: List<String>): McqAnswer {
            callCount++
            return answer
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
