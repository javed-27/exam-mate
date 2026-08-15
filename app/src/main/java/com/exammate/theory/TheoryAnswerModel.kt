package com.exammate.theory

import android.util.Log
import com.exammate.theory.ai.TheoryAiClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

const val ANSWER_FAILURE_MESSAGE = "Could not generate the answer. Try again."

class TheoryAnswerModel(
    private val aiClient: TheoryAiClient,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val _state = MutableStateFlow<TheoryAnswerState>(TheoryAnswerState.Idle)
    val state: StateFlow<TheoryAnswerState> = _state.asStateFlow()

    private var generateJob: Job? = null

    fun generate(question: String) {
        if (question.isBlank()) return
        generateJob?.cancel()
        _state.value = TheoryAnswerState.Generating()
        generateJob = scope.launch {
            try {
                var partial = ""
                aiClient.stream(question).collect { text ->
                    partial += text
                    _state.value = TheoryAnswerState.Generating(partial)
                }
                _state.value = if (partial.isBlank()) {
                    TheoryAnswerState.Error(ANSWER_FAILURE_MESSAGE)
                } else {
                    TheoryAnswerState.Answer(partial.trim())
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "AI generation failed", e)
                _state.value = TheoryAnswerState.Error(
                    e.message?.takeIf { it.isNotBlank() } ?: ANSWER_FAILURE_MESSAGE,
                )
            }
        }
    }

    fun reset() {
        generateJob?.cancel()
        _state.value = TheoryAnswerState.Idle
    }

    fun close() {
        generateJob?.cancel()
        scope.cancel()
    }

    private companion object {
        const val TAG = "TheoryAnswer"
    }
}
