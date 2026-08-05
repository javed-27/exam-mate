package com.exammate.mcq

import androidx.compose.runtime.saveable.SaverScope
import org.junit.Assert.assertEquals
import org.junit.Test

class McqAnswerStateSaverTest {

    private val scope = SaverScope { true }

    @Test
    fun waiting_roundTrips() {
        val saved = scope.saveState(McqAnswerState.Waiting)
        assertEquals(McqAnswerState.Waiting, McqAnswerStateSaver.restore(saved))
    }

    @Test
    fun processing_roundTrips() {
        val saved = scope.saveState(McqAnswerState.Processing)
        assertEquals(McqAnswerState.Processing, McqAnswerStateSaver.restore(saved))
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
    fun unknownKey_restoresToWaiting() {
        val restored = McqAnswerStateSaver.restore(listOf("unknown"))

        assertEquals(McqAnswerState.Waiting, restored)
    }

    private fun SaverScope.saveState(state: McqAnswerState): Any =
        checkNotNull(with(McqAnswerStateSaver) { save(state) })
}
