package com.exammate.mcq

data class McqAnswer(
    val question: String,
    val answer: String,
    val confidence: Double,
    val explanation: String,
)

sealed interface McqAnswerState {
    data object Waiting : McqAnswerState
    data object Processing : McqAnswerState
    data class Ready(val answer: McqAnswer) : McqAnswerState
}
