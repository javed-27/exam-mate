package com.exammate.mcq

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class McqAnswerProviderTest {

    private val provider = DemoAnswerProvider()

    @Test
    fun initialState_isWaiting() {
        assertEquals(McqAnswerState.Waiting, provider.initialState)
    }

    @Test
    fun waiting_transitionsToProcessing() {
        assertEquals(McqAnswerState.Processing, provider.next(McqAnswerState.Waiting))
    }

    @Test
    fun processing_transitionsToReady_withSampleAnswer() {
        val ready = provider.next(McqAnswerState.Processing) as McqAnswerState.Ready

        assertEquals("1. Which of the following is the capital of France?", ready.answer.question)
        assertEquals("C. Paris", ready.answer.answer)
        assertEquals(0.98, ready.answer.confidence, 0.0)
        assertEquals("Paris is the capital and most populous city of France.", ready.answer.explanation)
    }

    @Test
    fun ready_staysReady() {
        val ready = McqAnswerState.Ready(McqAnswer("q", "a", 1.0, "e"))

        assertNull(provider.next(ready))
    }
}
