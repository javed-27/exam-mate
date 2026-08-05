package com.exammate.mcq

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver

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

val McqAnswerStateSaver: Saver<McqAnswerState, Any> = listSaver(
    save = { state ->
        when (state) {
            McqAnswerState.Waiting -> listOf("waiting")
            McqAnswerState.Processing -> listOf("processing")
            is McqAnswerState.Ready -> listOf(
                "ready",
                state.answer.question,
                state.answer.answer,
                state.answer.confidence,
                state.answer.explanation,
            )
        }
    },
    restore = { saved ->
        when (saved[0]) {
            "waiting" -> McqAnswerState.Waiting
            "processing" -> McqAnswerState.Processing
            "ready" -> McqAnswerState.Ready(
                McqAnswer(
                    question = saved[1] as String,
                    answer = saved[2] as String,
                    confidence = saved[3] as Double,
                    explanation = saved[4] as String,
                ),
            )
            else -> McqAnswerState.Waiting
        }
    },
)
