package com.exammate.mcq

interface McqAnswerProvider {
    val initialState: McqAnswerState
    val delayMillis: Long
    fun next(current: McqAnswerState): McqAnswerState?
}

class DemoAnswerProvider : McqAnswerProvider {
    override val initialState: McqAnswerState = McqAnswerState.Waiting
    override val delayMillis: Long = 1500L
    override fun next(current: McqAnswerState): McqAnswerState? = when (current) {
        McqAnswerState.Waiting -> McqAnswerState.Processing
        McqAnswerState.Processing -> McqAnswerState.Ready(DemoAnswer)
        is McqAnswerState.Ready -> null
    }
}

private val DemoAnswer = McqAnswer(
    question = "1. Which of the following is the capital of France?",
    answer = "C. Paris",
    confidence = 0.98,
    explanation = "Paris is the capital and most populous city of France.",
)
