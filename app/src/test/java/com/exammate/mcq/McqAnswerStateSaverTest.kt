package com.exammate.mcq

import androidx.compose.runtime.saveable.SaverScope
import org.junit.Assert.assertEquals
import org.junit.Test

class McqAnswerStateSaverTest {

    private val scope = SaverScope { true }

    @Test
    fun waiting_roundTrips() {
        val saved = scope.saveState(McqAnswerState.WaitingForOcr)
        assertEquals(McqAnswerState.WaitingForOcr, McqAnswerStateSaver.restore(saved))
    }

    @Test
    fun unparsed_roundTrips() {
        val state = McqAnswerState.Unparsed("Just a sentence without any options")

        val saved = scope.saveState(state)

        assertEquals(state, McqAnswerStateSaver.restore(saved))
    }

    @Test
    fun processingWithoutPrevious_roundTrips() {
        val state = McqAnswerState.Processing(previous = null)

        val saved = scope.saveState(state)

        assertEquals(state, McqAnswerStateSaver.restore(saved))
    }

    @Test
    fun processingWithPrevious_roundTrips() {
        val state = McqAnswerState.Processing(previous = SampleAnswer)

        val saved = scope.saveState(state)

        assertEquals(state, McqAnswerStateSaver.restore(saved))
    }

    @Test
    fun streamingWithoutPrevious_roundTrips() {
        val state = McqAnswerState.Streaming(previous = null, partialText = "C. Paris")

        val saved = scope.saveState(state)

        assertEquals(state, McqAnswerStateSaver.restore(saved))
    }

    @Test
    fun streamingWithPrevious_roundTrips() {
        val state = McqAnswerState.Streaming(previous = SampleAnswer, partialText = "C. Paris")

        val saved = scope.saveState(state)

        assertEquals(state, McqAnswerStateSaver.restore(saved))
    }

    @Test
    fun ready_roundTrips() {
        val state = McqAnswerState.Ready(
            McqAnswer(
                question = "1. Which of the following is the capital of France?",
                answer = "C. Paris",
                confidence = 0.98,
                explanation = "Paris is the capital and most populous city of France.",
            ),
        )

        val saved = scope.saveState(state)

        assertEquals(state, McqAnswerStateSaver.restore(saved))
    }

    @Test
    fun errorWithoutPrevious_roundTrips() {
        val state = McqAnswerState.Error(message = "connect timed out")

        val saved = scope.saveState(state)

        assertEquals(state, McqAnswerStateSaver.restore(saved))
    }

    @Test
    fun errorWithPrevious_roundTrips() {
        val state = McqAnswerState.Error(message = "connect timed out", previous = SampleAnswer)

        val saved = scope.saveState(state)

        assertEquals(state, McqAnswerStateSaver.restore(saved))
    }

    @Test
    fun unknownKey_restoresToWaitingForOcr() {
        val restored = McqAnswerStateSaver.restore(listOf("unknown"))

        assertEquals(McqAnswerState.WaitingForOcr, restored)
    }

    private fun SaverScope.saveState(state: McqAnswerState): Any =
        checkNotNull(with(McqAnswerStateSaver) { save(state) })

    private companion object {
        val SampleAnswer = McqAnswer(
            question = "Which of the following is the capital of France?",
            answer = "C. Paris",
            confidence = 0.98,
            explanation = "Paris is the capital of France.",
        )
    }
}
