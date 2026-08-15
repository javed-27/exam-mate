package com.exammate.theory

sealed interface TheoryAnswerState {
    data object Idle : TheoryAnswerState
    data class Generating(val partialText: String = "") : TheoryAnswerState
    data class Answer(val text: String) : TheoryAnswerState
    data class Error(val message: String) : TheoryAnswerState
}
