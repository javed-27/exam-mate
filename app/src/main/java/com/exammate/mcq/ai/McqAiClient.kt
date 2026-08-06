package com.exammate.mcq.ai

import com.exammate.mcq.McqAnswer
import kotlinx.coroutines.flow.Flow

sealed interface McqAiEvent {
    data class Text(val text: String) : McqAiEvent
    data class Answer(val answer: McqAnswer) : McqAiEvent
}

interface McqAiClient {
    fun stream(question: String, options: List<String>): Flow<McqAiEvent>
}
