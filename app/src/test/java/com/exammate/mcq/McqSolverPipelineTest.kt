package com.exammate.mcq

import android.graphics.Bitmap
import com.exammate.mcq.ai.McqAiClient
import com.exammate.mcq.ai.McqAiEvent
import com.exammate.mcq.ocr.OcrService
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
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
    private val NEXT_QUESTION = "2. What is 2 + 2?\nA. 3\nB. 4\nC. 5\nD. 6"

    @Test
    fun initialState_isWaitingForOcr() {
        val pipeline = pipeline()

        assertEquals(McqAnswerState.WaitingForOcr, pipeline.state.value)
    }

    @Test
    fun newQuestion_reachesReady() = runTest {
        val ai = FakeAiClient(answer = SampleAnswer)
        val pipeline = pipeline(ai = ai, dispatcher = testDispatcher())

        pipeline.processFrame { QUESTION }
        advanceUntilIdle()

        val ready = pipeline.state.value as McqAnswerState.Ready
        assertEquals(SampleAnswer.answer, ready.answer.answer)
        assertEquals(1, ai.calls)
    }

    @Test
    fun unchangedQuestion_doesNotReprocess() = runTest {
        val ai = FakeAiClient(answer = SampleAnswer)
        val pipeline = pipeline(ai = ai, dispatcher = testDispatcher())

        pipeline.processFrame { QUESTION }
        advanceUntilIdle()
        pipeline.processFrame { QUESTION }
        advanceUntilIdle()

        assertTrue(pipeline.state.value is McqAnswerState.Ready)
        assertEquals(1, ai.calls)
    }

    @Test
    fun normalization_allowsDedupeAcrossWhitespace() = runTest {
        val ai = FakeAiClient(answer = SampleAnswer)
        val pipeline = pipeline(ai = ai, dispatcher = testDispatcher())

        pipeline.processFrame { QUESTION }
        advanceUntilIdle()
        pipeline.processFrame { QUESTION.replace("is the capital", "is   the   capital") }
        advanceUntilIdle()

        assertEquals(1, ai.calls)
    }

    @Test
    fun changingCountdownTimer_doesNotReprocess() = runTest {
        val ai = FakeAiClient(answer = SampleAnswer)
        val pipeline = pipeline(ai = ai, dispatcher = testDispatcher())

        pipeline.processFrame { "00:47\n$QUESTION" }
        advanceUntilIdle()
        pipeline.processFrame { "00:46\n$QUESTION" }
        advanceUntilIdle()
        pipeline.processFrame { "00:45\n$QUESTION" }
        advanceUntilIdle()

        assertEquals(1, ai.calls)
        assertTrue(pipeline.state.value is McqAnswerState.Ready)
    }

    @Test
    fun changedQuestion_reprocesses() = runTest {
        val ai = FakeAiClient(answer = SampleAnswer)
        val pipeline = pipeline(ai = ai, dispatcher = testDispatcher())

        pipeline.processFrame { QUESTION }
        advanceUntilIdle()
        pipeline.processFrame { NEXT_QUESTION }
        advanceUntilIdle()

        assertEquals(2, ai.calls)
    }

    @Test
    fun emptyText_doesNothing() = runTest {
        val ai = FakeAiClient()
        val pipeline = pipeline(ai = ai, dispatcher = testDispatcher())

        pipeline.processFrame { "   \n  " }
        advanceUntilIdle()

        assertEquals(McqAnswerState.WaitingForOcr, pipeline.state.value)
        assertEquals(0, ai.calls)
    }

    @Test
    fun unparseableText_revealsDebugText() = runTest {
        val ai = FakeAiClient()
        val pipeline = pipeline(ai = ai, dispatcher = testDispatcher())

        pipeline.processFrame { "Just a sentence without any options" }
        advanceUntilIdle()

        val unparsed = pipeline.state.value as McqAnswerState.Unparsed
        assertEquals("Just a sentence without any options", unparsed.ocrText)
        assertEquals(0, ai.calls)
    }

    @Test
    fun unparseableText_afterAnswer_keepsAnswer() = runTest {
        val ai = FakeAiClient(answer = SampleAnswer)
        val pipeline = pipeline(ai = ai, dispatcher = testDispatcher())

        pipeline.processFrame { QUESTION }
        advanceUntilIdle()
        pipeline.processFrame { "stray text with no options" }
        advanceUntilIdle()

        assertTrue(pipeline.state.value is McqAnswerState.Ready)
        assertEquals(1, ai.calls)
    }

    @Test
    fun ocrFailure_keepsCurrentState() = runTest {
        val pipeline = pipeline(dispatcher = testDispatcher())

        pipeline.processFrame { throw IOException("ocr failed") }
        advanceUntilIdle()

        assertEquals(McqAnswerState.WaitingForOcr, pipeline.state.value)
    }

    @Test
    fun ocrFailure_afterAnswer_keepsLastAnswer() = runTest {
        val ai = FakeAiClient(answer = SampleAnswer)
        val pipeline = pipeline(ai = ai, dispatcher = testDispatcher())

        pipeline.processFrame { QUESTION }
        advanceUntilIdle()
        pipeline.processFrame { throw IOException("ocr failed") }
        advanceUntilIdle()

        val ready = pipeline.state.value as McqAnswerState.Ready
        assertEquals(SampleAnswer.answer, ready.answer.answer)
    }

    @Test
    fun aiFailure_afterAnswer_setsErrorWithPrevious() = runTest {
        val ai = FakeAiClient(answer = SampleAnswer)
        val pipeline = pipeline(ai = ai, dispatcher = testDispatcher())

        pipeline.processFrame { QUESTION }
        advanceUntilIdle()
        ai.error = IOException("ai failed")
        pipeline.processFrame { NEXT_QUESTION }
        advanceUntilIdle()

        val error = pipeline.state.value as McqAnswerState.Error
        assertEquals("ai failed", error.message)
        assertEquals(SampleAnswer, error.previous)
        assertEquals(2, ai.calls)
    }

    @Test
    fun aiFailure_withNoPreviousAnswer_setsError() = runTest {
        val ai = FakeAiClient(error = IOException("ai failed"))
        val pipeline = pipeline(ai = ai, dispatcher = testDispatcher())

        pipeline.processFrame { QUESTION }
        advanceUntilIdle()

        val error = pipeline.state.value as McqAnswerState.Error
        assertEquals("ai failed", error.message)
        assertEquals(null, error.previous)
    }

    @Test
    fun streaming_exposesPartialTextThenReady() = runTest {
        val ai = FakeAiClient(answer = SampleAnswer, yieldBetweenEmissions = true)
        val pipeline = pipeline(ai = ai, dispatcher = testDispatcher())
        val states = mutableListOf<McqAnswerState>()
        backgroundScope.launch { pipeline.state.collect { states += it } }

        pipeline.processFrame { QUESTION }
        advanceUntilIdle()

        assertTrue(states.any { it is McqAnswerState.Streaming })
        assertTrue(
            states.any { it is McqAnswerState.Streaming && it.partialText == "Paris is correct." },
        )
        assertEquals("ready should be last: $states", true, pipeline.state.value is McqAnswerState.Ready)
    }

    @Test
    fun throttle_skipsRapidFrames() = runTest {
        var now = 1000L
        val clock = { now }
        val ai = FakeAiClient(answer = SampleAnswer)
        val pipeline = pipeline(ai = ai, clock = clock, dispatcher = testDispatcher())

        pipeline.processFrame { QUESTION }
        advanceUntilIdle()
        pipeline.processFrame { NEXT_QUESTION }
        assertEquals(1, ai.calls)

        now += 600
        pipeline.processFrame { NEXT_QUESTION }
        advanceUntilIdle()
        assertEquals(2, ai.calls)
    }

    @Test
    fun newStemWhileStreaming_cancelsOldAndProcessesNew() = runTest {
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
        val pipeline = pipeline(ai = ai, clock = clock, dispatcher = testDispatcher())

        val job = launch { pipeline.processFrame { QUESTION } }
        entered.await()
        assertEquals(1, ai.calls)

        now += 1000
        pipeline.processFrame { NEXT_QUESTION }
        runCurrent()
        assertEquals(2, ai.calls)
        assertTrue(pipeline.state.value is McqAnswerState.Processing)

        released.complete(Unit)
        advanceUntilIdle()
        job.join()

        val ready = pipeline.state.value as McqAnswerState.Ready
        assertEquals("2. What is 2 + 2?", ready.answer.question)
    }

    @Test
    fun sameStemWhileStreaming_isNotRestarted() = runTest {
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
        val pipeline = pipeline(ai = ai, clock = clock, dispatcher = testDispatcher())

        val job = launch { pipeline.processFrame { QUESTION } }
        entered.await()
        assertEquals(1, ai.calls)

        now += 1000
        pipeline.processFrame { QUESTION }
        runCurrent()
        assertEquals(1, ai.calls)

        released.complete(Unit)
        advanceUntilIdle()
        job.join()
        assertTrue(pipeline.state.value is McqAnswerState.Ready)
    }

    @Test
    fun aiFailure_sameStem_notRetriedWithinBackoff() = runTest {
        var now = 1000L
        val clock = { now }
        val ai = FakeAiClient(error = IOException("boom"))
        val pipeline = pipeline(ai = ai, clock = clock, dispatcher = testDispatcher())

        pipeline.processFrame { QUESTION }
        advanceUntilIdle()
        assertTrue(pipeline.state.value is McqAnswerState.Error)

        now += 1000
        pipeline.processFrame { QUESTION }
        advanceUntilIdle()
        assertEquals(1, ai.calls)
    }

    @Test
    fun aiFailure_sameStem_retriesAfterBackoff() = runTest {
        var now = 1000L
        val clock = { now }
        val ai = FakeAiClient(error = IOException("boom"))
        val pipeline = pipeline(ai = ai, clock = clock, dispatcher = testDispatcher())

        pipeline.processFrame { QUESTION }
        advanceUntilIdle()
        assertTrue(pipeline.state.value is McqAnswerState.Error)

        now += 5000
        pipeline.processFrame { QUESTION }
        advanceUntilIdle()
        assertEquals(2, ai.calls)
    }

    @Test
    fun markDisplayed_preventsReprocessing() = runTest {
        val ai = FakeAiClient(answer = SampleAnswer)
        val pipeline = pipeline(ai = ai, dispatcher = testDispatcher())

        pipeline.markDisplayed(QUESTION)
        pipeline.processFrame { QUESTION }
        advanceUntilIdle()

        assertEquals(McqAnswerState.WaitingForOcr, pipeline.state.value)
        assertEquals(0, ai.calls)
    }

    @Test
    fun restore_seedsStateAndPreventsReprocessing() = runTest {
        val ai = FakeAiClient(answer = SampleAnswer)
        val pipeline = pipeline(ai = ai, dispatcher = testDispatcher())

        pipeline.restore(McqAnswerState.Ready(SampleAnswer))
        pipeline.processFrame { QUESTION }
        advanceUntilIdle()

        val ready = pipeline.state.value as McqAnswerState.Ready
        assertEquals(SampleAnswer.answer, ready.answer.answer)
        assertEquals(0, ai.calls)
    }

    private fun TestScope.testDispatcher(): CoroutineDispatcher =
        StandardTestDispatcher(testScheduler)

    private fun pipeline(
        ocr: FakeOcrService = FakeOcrService(),
        ai: FakeAiClient = FakeAiClient(),
        clock: (() -> Long)? = null,
        dispatcher: CoroutineDispatcher = Dispatchers.Default,
    ): McqSolverPipeline {
        var now = 0L
        val effectiveClock = clock ?: { now += 1000; now }
        return McqSolverPipeline(
            ocr = ocr,
            aiClient = ai,
            currentTimeMillis = effectiveClock,
            dispatcher = dispatcher,
        )
    }

    private class FakeOcrService(var result: String = "") : OcrService {
        var calls = 0

        override suspend fun recognize(bitmap: Bitmap): String {
            calls++
            return result
        }

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
