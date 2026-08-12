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
    data object WaitingForOcr : McqAnswerState
    data class Unparsed(val ocrText: String) : McqAnswerState
    data class Processing(val previous: McqAnswer? = null) : McqAnswerState
    data class Streaming(val previous: McqAnswer? = null, val partialText: String) : McqAnswerState
    data class Ready(val answer: McqAnswer) : McqAnswerState
    data class Error(val message: String, val previous: McqAnswer? = null) : McqAnswerState
}

private fun List<Any?>.withAnswer(answer: McqAnswer?): List<Any?> =
    if (answer == null) this.plusElement(false)
    else this.plusElement(listOf(true, answer.question, answer.answer, answer.confidence, answer.explanation))

private fun Any?.toAnswerOrNull(): McqAnswer? {
    if (this !is List<*>) return null
    if (firstOrNull() != true) return null
    return McqAnswer(
        question = this[1] as String,
        answer = this[2] as String,
        confidence = this[3] as Double,
        explanation = this[4] as String,
    )
}

val McqAnswerStateSaver: Saver<McqAnswerState, Any> = listSaver(
    save = { state ->
        when (state) {
            McqAnswerState.WaitingForOcr -> listOf("waiting")
            is McqAnswerState.Unparsed -> listOf("unparsed", state.ocrText)
            is McqAnswerState.Processing ->
                listOf("processing").withAnswer(state.previous)
            is McqAnswerState.Streaming ->
                listOf("streaming", state.partialText).withAnswer(state.previous)
            is McqAnswerState.Ready -> listOf(
                "ready",
                state.answer.question,
                state.answer.answer,
                state.answer.confidence,
                state.answer.explanation,
            )
            is McqAnswerState.Error ->
                listOf("error", state.message).withAnswer(state.previous)
        }
    },
    restore = { saved ->
        when (saved[0]) {
            "waiting" -> McqAnswerState.WaitingForOcr
            "unparsed" -> McqAnswerState.Unparsed(saved[1] as String)
            "processing" -> McqAnswerState.Processing(saved.getOrNull(1).toAnswerOrNull())
            "streaming" -> McqAnswerState.Streaming(
                previous = saved.getOrNull(2).toAnswerOrNull(),
                partialText = saved[1] as String,
            )
            "ready" -> McqAnswerState.Ready(
                McqAnswer(
                    question = saved[1] as String,
                    answer = saved[2] as String,
                    confidence = saved[3] as Double,
                    explanation = saved[4] as String,
                ),
            )
            "error" -> McqAnswerState.Error(
                message = saved[1] as String,
                previous = saved.getOrNull(2).toAnswerOrNull(),
            )
            else -> McqAnswerState.WaitingForOcr
        }
    },
)
