package com.exammate.mcq

import android.graphics.Bitmap
import com.exammate.mcq.ai.McqAiClient
import com.exammate.mcq.ocr.OcrService
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class McqSolverPipelineTest {

    private val QUESTION =
        "Which of the following is the capital of France?\n" +
            "A. Berlin\nB. Madrid\nC. Paris\nD. Rome"

    @Test
    fun initialState_isWaiting() {
        val pipeline = pipeline()

        assertEquals(McqAnswerState.Waiting, pipeline.initialState)
    }

    @Test
    fun newQuestion_reachesReady() = runBlocking {
        val ai = FakeAiClient(answer = SampleAnswer)
        val pipeline = pipeline(ai = ai)

        val result = pipeline.processFrame { QUESTION }

        val ready = result as McqAnswerState.Ready
        assertEquals(SampleAnswer.answer, ready.answer.answer)
        assertEquals(1, ai.calls)
    }

    @Test
    fun unchangedQuestion_doesNotReprocess() = runBlocking {
        val ai = FakeAiClient(answer = SampleAnswer)
        val pipeline = pipeline(ai = ai)

        pipeline.processFrame { QUESTION }
        val result = pipeline.processFrame { QUESTION }

        assertTrue(result is McqAnswerState.Ready)
        assertEquals(1, ai.calls)
    }

    @Test
    fun normalization_allowsDedupeAcrossWhitespace() = runBlocking {
        val ai = FakeAiClient(answer = SampleAnswer)
        val pipeline = pipeline(ai = ai)

        pipeline.processFrame { QUESTION }
        pipeline.processFrame { QUESTION.replace("is the capital", "is   the   capital") }

        assertEquals(1, ai.calls)
    }

    @Test
    fun changedQuestion_reprocesses() = runBlocking {
        val ai = FakeAiClient(answer = SampleAnswer)
        val pipeline = pipeline(ai = ai)

        pipeline.processFrame { QUESTION }
        pipeline.processFrame { "2. What is 2 + 2?\nA. 3\nB. 4\nC. 5\nD. 6" }

        assertEquals(2, ai.calls)
    }

    @Test
    fun emptyText_doesNothing() = runBlocking {
        val ai = FakeAiClient()
        val pipeline = pipeline(ai = ai)

        val result = pipeline.processFrame { "   \n  " }

        assertEquals(McqAnswerState.Waiting, result)
        assertEquals(0, ai.calls)
    }

    @Test
    fun unparseableText_doesNothing() = runBlocking {
        val ai = FakeAiClient()
        val pipeline = pipeline(ai = ai)

        val result = pipeline.processFrame { "Just a sentence without any options" }

        assertEquals(McqAnswerState.Waiting, result)
        assertEquals(0, ai.calls)
    }

    @Test
    fun ocrFailure_keepsCurrentState() = runBlocking {
        val pipeline = pipeline()

        val result = pipeline.processFrame { throw IOException("ocr failed") }

        assertEquals(McqAnswerState.Waiting, result)
    }

    @Test
    fun ocrFailure_afterAnswer_keepsLastAnswer() = runBlocking {
        val ai = FakeAiClient(answer = SampleAnswer)
        val pipeline = pipeline(ai = ai)

        pipeline.processFrame { QUESTION }
        val result = pipeline.processFrame { throw IOException("ocr failed") }

        val ready = result as McqAnswerState.Ready
        assertEquals(SampleAnswer.answer, ready.answer.answer)
    }

    @Test
    fun aiFailure_restoresPreviousAnswer() = runBlocking {
        val ai = FakeAiClient(answer = SampleAnswer)
        val pipeline = pipeline(ai = ai)

        pipeline.processFrame { QUESTION }
        ai.error = IOException("ai failed")
        val result = pipeline.processFrame { "2. What is 2 + 2?\nA. 3\nB. 4\nC. 5\nD. 6" }

        val ready = result as McqAnswerState.Ready
        assertEquals(SampleAnswer.answer, ready.answer.answer)
        assertEquals(2, ai.calls)
    }

    @Test
    fun aiFailure_withNoPreviousAnswer_staysWaiting() = runBlocking {
        val ai = FakeAiClient(error = IOException("ai failed"))
        val pipeline = pipeline(ai = ai)

        val result = pipeline.processFrame { QUESTION }

        assertEquals(McqAnswerState.Waiting, result)
    }

    @Test
    fun throttle_skipsRapidFrames() = runBlocking {
        var now = 1000L
        val clock = { now }
        val ai = FakeAiClient(answer = SampleAnswer)
        val pipeline = pipeline(ai = ai, clock = clock)

        pipeline.processFrame { QUESTION }
        val skipped = pipeline.processFrame { "2. What is 2 + 2?\nA. 3\nB. 4\nC. 5\nD. 6" }

        assertTrue(skipped is McqAnswerState.Ready)
        assertEquals(1, ai.calls)

        now += 600
        pipeline.processFrame { "2. What is 2 + 2?\nA. 3\nB. 4\nC. 5\nD. 6" }
        assertEquals(2, ai.calls)
    }

    @Test
    fun singleFlight_skipsFramesWhileAnalysing() = runBlocking {
        var now = 1000L
        val clock = { now }
        val entered = CompletableDeferred<Unit>()
        val released = CompletableDeferred<Unit>()
        val ai = FakeAiClient(
            answer = SampleAnswer,
            onSolveStart = {
                entered.complete(Unit)
                released.await()
            },
        )
        val pipeline = pipeline(ai = ai, clock = clock)

        val job = launch { pipeline.processFrame { QUESTION } }
        entered.await()

        now += 1000
        val duringFlight = pipeline.processFrame { "2. What is 2 + 2?\nA. 3\nB. 4\nC. 5\nD. 6" }

        released.complete(Unit)
        job.join()

        assertEquals(McqAnswerState.Processing, duringFlight)
        assertEquals(1, ai.calls)
    }

    @Test
    fun markDisplayed_preventsReprocessing() = runBlocking {
        val ai = FakeAiClient(answer = SampleAnswer)
        val pipeline = pipeline(ai = ai)

        pipeline.markDisplayed(QUESTION)
        val result = pipeline.processFrame { QUESTION }

        assertEquals(McqAnswerState.Waiting, result)
        assertEquals(0, ai.calls)
    }

    private fun pipeline(
        ai: FakeAiClient = FakeAiClient(),
        clock: (() -> Long)? = null,
    ): McqSolverPipeline {
        var now = 0L
        val effectiveClock = clock ?: { now += 1000; now }
        return McqSolverPipeline(
            ocr = FakeOcrService(),
            aiClient = ai,
            currentTimeMillis = effectiveClock,
        )
    }

    private class FakeOcrService : OcrService {
        override suspend fun recognize(bitmap: Bitmap): String = ""

        override fun close() = Unit
    }

    private class FakeAiClient(
        var answer: McqAnswer = McqAnswer("q", "a", 1.0, "e"),
        var error: Exception? = null,
        var onSolveStart: (suspend () -> Unit)? = null,
    ) : McqAiClient {
        var calls = 0

        override suspend fun solve(question: String, options: List<String>): McqAnswer {
            calls++
            onSolveStart?.invoke()
            error?.let { throw it }
            return answer.copy(question = question)
        }
    }

    private companion object {
        val SampleAnswer = McqAnswer(
            question = "q",
            answer = "C. Paris",
            confidence = 0.98,
            explanation = "Paris is the capital of France.",
        )
    }
}
