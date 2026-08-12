package com.exammate.mcq

import android.graphics.Bitmap
import com.exammate.mcq.ai.McqAiClient
import com.exammate.mcq.ai.McqAiEvent
import com.exammate.mcq.ocr.OcrService
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class McqSolverPipelineTest {

    private val QUESTION =
        "Which of the following is the capital of France?\n" +
            "A. Berlin\nB. Madrid\nC. Paris\nD. Rome"

    @Test
    fun initialState_isWaitingForOcr() {
        val pipeline = pipeline()

        assertEquals(McqAnswerState.WaitingForOcr, pipeline.state.value)
    }

    @Test
    fun newQuestion_reachesReady() = runTest {
        val ai = FakeAiClient(answer = SampleAnswer)
        val pipeline = pipeline(ai = ai)

        pipeline.processFrame { QUESTION }

        val ready = pipeline.state.value as McqAnswerState.Ready
        assertEquals(SampleAnswer.answer, ready.answer.answer)
        assertEquals(1, ai.calls)
    }

    @Test
    fun unchangedQuestion_doesNotReprocess() = runTest {
        val ai = FakeAiClient(answer = SampleAnswer)
        val pipeline = pipeline(ai = ai)

        pipeline.processFrame { QUESTION }
        pipeline.processFrame { QUESTION }

        assertTrue(pipeline.state.value is McqAnswerState.Ready)
        assertEquals(1, ai.calls)
    }

    @Test
    fun normalization_allowsDedupeAcrossWhitespace() = runTest {
        val ai = FakeAiClient(answer = SampleAnswer)
        val pipeline = pipeline(ai = ai)

        pipeline.processFrame { QUESTION }
        pipeline.processFrame { QUESTION.replace("is the capital", "is   the   capital") }

        assertEquals(1, ai.calls)
    }

    @Test
    fun changingCountdownTimer_doesNotReprocess() = runTest {
        val ai = FakeAiClient(answer = SampleAnswer)
        val pipeline = pipeline(ai = ai)

        pipeline.processFrame { "00:47\n$QUESTION" }
        pipeline.processFrame { "00:46\n$QUESTION" }
        pipeline.processFrame { "00:45\n$QUESTION" }

        assertEquals(1, ai.calls)
        assertTrue(pipeline.state.value is McqAnswerState.Ready)
    }

    @Test
    fun changedQuestion_reprocesses() = runTest {
        val ai = FakeAiClient(answer = SampleAnswer)
        val pipeline = pipeline(ai = ai)

        pipeline.processFrame { QUESTION }
        pipeline.processFrame { "2. What is 2 + 2?\nA. 3\nB. 4\nC. 5\nD. 6" }

        assertEquals(2, ai.calls)
    }

    @Test
    fun emptyText_doesNothing() = runTest {
        val ai = FakeAiClient()
        val pipeline = pipeline(ai = ai)

        pipeline.processFrame { "   \n  " }

        assertEquals(McqAnswerState.WaitingForOcr, pipeline.state.value)
        assertEquals(0, ai.calls)
    }

    @Test
    fun unparseableText_revealsDebugText() = runTest {
        val ai = FakeAiClient()
        val pipeline = pipeline(ai = ai)

        pipeline.processFrame { "Just a sentence without any options" }

        val unparsed = pipeline.state.value as McqAnswerState.Unparsed
        assertEquals("Just a sentence without any options", unparsed.ocrText)
        assertEquals(0, ai.calls)
    }

    @Test
    fun unparseableText_afterAnswer_keepsAnswer() = runTest {
        val ai = FakeAiClient(answer = SampleAnswer)
        val pipeline = pipeline(ai = ai)

        pipeline.processFrame { QUESTION }
        pipeline.processFrame { "stray text with no options" }

        assertTrue(pipeline.state.value is McqAnswerState.Ready)
        assertEquals(1, ai.calls)
    }

    @Test
    fun ocrFailure_keepsCurrentState() = runTest {
        val pipeline = pipeline()

        pipeline.processFrame { throw IOException("ocr failed") }

        assertEquals(McqAnswerState.WaitingForOcr, pipeline.state.value)
    }

    @Test
    fun ocrFailure_afterAnswer_keepsLastAnswer() = runTest {
        val ai = FakeAiClient(answer = SampleAnswer)
        val pipeline = pipeline(ai = ai)

        pipeline.processFrame { QUESTION }
        pipeline.processFrame { throw IOException("ocr failed") }

        val ready = pipeline.state.value as McqAnswerState.Ready
        assertEquals(SampleAnswer.answer, ready.answer.answer)
    }

    @Test
    fun aiFailure_restoresPreviousAnswer() = runTest {
        val ai = FakeAiClient(answer = SampleAnswer)
        val pipeline = pipeline(ai = ai)

        pipeline.processFrame { QUESTION }
        ai.error = IOException("ai failed")
        pipeline.processFrame { "2. What is 2 + 2?\nA. 3\nB. 4\nC. 5\nD. 6" }

        val ready = pipeline.state.value as McqAnswerState.Ready
        assertEquals(SampleAnswer.answer, ready.answer.answer)
        assertEquals(2, ai.calls)
    }

    @Test
    fun aiFailure_withNoPreviousAnswer_staysWaitingForOcr() = runTest {
        val ai = FakeAiClient(error = IOException("ai failed"))
        val pipeline = pipeline(ai = ai)

        pipeline.processFrame { QUESTION }

        assertEquals(McqAnswerState.WaitingForOcr, pipeline.state.value)
    }

    @Test
    fun streaming_exposesPartialTextThenReady() = runTest {
        val ai = FakeAiClient(answer = SampleAnswer, yieldBetweenEmissions = true)
        val pipeline = pipeline(ai = ai)
        val states = mutableListOf<McqAnswerState>()
        backgroundScope.launch { pipeline.state.collect { states += it } }

        pipeline.processFrame { QUESTION }
        runCurrent()

        assertTrue(states.any { it is McqAnswerState.Streaming })
        assertTrue(
            states.any { it is McqAnswerState.Streaming && it.partialText == "Paris is correct." },
        )
        assertTrue(states.last() is McqAnswerState.Ready)
    }

    @Test
    fun throttle_skipsRapidFrames() = runTest {
        var now = 1000L
        val clock = { now }
        val ai = FakeAiClient(answer = SampleAnswer)
        val pipeline = pipeline(ai = ai, clock = clock)

        pipeline.processFrame { QUESTION }
        pipeline.processFrame { "2. What is 2 + 2?\nA. 3\nB. 4\nC. 5\nD. 6" }
        assertEquals(1, ai.calls)

        now += 600
        pipeline.processFrame { "2. What is 2 + 2?\nA. 3\nB. 4\nC. 5\nD. 6" }
        assertEquals(2, ai.calls)
    }

    @Test
    fun singleFlight_skipsFramesWhileStreaming() = runTest {
        var now = 1000L
        val clock = { now }
        val entered = CompletableDeferred<Unit>()
        val released = CompletableDeferred<Unit>()
        val ai = FakeAiClient(
            answer = SampleAnswer,
            onStreamStart = {
                entered.complete(Unit)
                released.await()
            },
        )
        val pipeline = pipeline(ai = ai, clock = clock)

        val job = launch { pipeline.processFrame { QUESTION } }
        entered.await()

        now += 1000
        pipeline.processFrame { "2. What is 2 + 2?\nA. 3\nB. 4\nC. 5\nD. 6" }

        assertEquals(McqAnswerState.Processing(previous = null), pipeline.state.value)
        assertEquals(1, ai.calls)

        released.complete(Unit)
        job.join()
    }

    @Test
    fun markDisplayed_preventsReprocessing() = runTest {
        val ai = FakeAiClient(answer = SampleAnswer)
        val pipeline = pipeline(ai = ai)

        pipeline.markDisplayed(QUESTION)
        pipeline.processFrame { QUESTION }

        assertEquals(McqAnswerState.WaitingForOcr, pipeline.state.value)
        assertEquals(0, ai.calls)
    }

    @Test
    fun restore_seedsStateAndPreventsReprocessing() = runTest {
        val ai = FakeAiClient(answer = SampleAnswer)
        val pipeline = pipeline(ai = ai)

        pipeline.restore(McqAnswerState.Ready(SampleAnswer))
        pipeline.processFrame { QUESTION }

        val ready = pipeline.state.value as McqAnswerState.Ready
        assertEquals(SampleAnswer.answer, ready.answer.answer)
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
        var yieldBetweenEmissions: Boolean = false,
        var onStreamStart: (suspend () -> Unit)? = null,
    ) : McqAiClient {
        var calls = 0

        override fun stream(question: String, options: List<String>): Flow<McqAiEvent> = flow {
            calls++
            onStreamStart?.invoke()
            error?.let { throw it }
            emit(McqAiEvent.Text("Paris is "))
            if (yieldBetweenEmissions) yield()
            emit(McqAiEvent.Text("correct."))
            if (yieldBetweenEmissions) yield()
            emit(McqAiEvent.Answer(answer.copy(question = question)))
        }
    }

    private companion object {
        val SampleAnswer = McqAnswer(
            question = "Which of the following is the capital of France?",
            answer = "C. Paris",
            confidence = 0.98,
            explanation = "Paris is the capital of France.",
        )
    }
}
