package com.exammate.mcq

import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import com.exammate.mcq.ai.McqAiClient
import com.exammate.mcq.ai.McqAiEvent
import com.exammate.mcq.ocr.OcrService
import com.exammate.mcq.parse.McqQuestionParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex

interface McqPipeline {
    val state: StateFlow<McqAnswerState>

    suspend fun onFrame(bitmap: Bitmap)

    fun markDisplayed(text: String) = Unit

    fun restore(state: McqAnswerState) = Unit
}

class McqSolverPipeline(
    private val ocr: OcrService,
    private val aiClient: McqAiClient,
    private val parser: McqQuestionParser = McqQuestionParser,
    private val minFrameIntervalMillis: Long = 500L,
    private val currentTimeMillis: () -> Long = { SystemClock.elapsedRealtime() },
) : McqPipeline {

    private val mutex = Mutex()
    private val _state = MutableStateFlow<McqAnswerState>(McqAnswerState.Waiting)
    override val state: StateFlow<McqAnswerState> = _state.asStateFlow()

    private var lastProcessedStem: String? = null
    private var lastProcessedTime: Long = 0L

    override suspend fun onFrame(bitmap: Bitmap) {
        processFrame { ocr.recognize(bitmap) }
    }

    internal suspend fun processFrame(produceText: suspend () -> String) {
        if (isThrottled()) return
        if (!mutex.tryLock()) return
        try {
            lastProcessedTime = currentTimeMillis()
            consume(produceText())
        } catch (e: Exception) {
            Log.d(TAG, "Frame processing failed", e)
        } finally {
            mutex.unlock()
        }
    }

    override fun markDisplayed(text: String) {
        lastProcessedStem = parser.parse(text)?.let { parser.normalize(it.question) }
            ?: parser.normalize(text)
    }

    override fun restore(state: McqAnswerState) {
        _state.value = state
        if (state is McqAnswerState.Ready) markDisplayed(state.answer.question)
    }

    private suspend fun consume(text: String) {
        val normalized = parser.normalize(text)
        Log.d(TAG, "OCR text: ${normalized.take(600)}")
        val parsed = parser.parse(text) ?: run {
            Log.d(TAG, "Parse failed: no >=2 lettered/numbered option lines")
            return
        }
        val stem = parser.normalize(parsed.question)
        Log.d(TAG, "Parsed stem: $stem")
        if (stem == lastProcessedStem) {
            Log.d(TAG, "Dedupe: stem already processed, skipping")
            return
        }
        val previous = _state.value
        if (previous !is McqAnswerState.Ready) _state.value = McqAnswerState.Processing
        try {
            val question = parsed.question
            val options = parsed.options
            var partialText = ""
            aiClient.stream(question, options).collect { event ->
                when (event) {
                    is McqAiEvent.Text -> {
                        partialText += event.text
                        _state.value = McqAnswerState.Streaming(partialText)
                    }
                    is McqAiEvent.Answer -> {
                        lastProcessedStem = stem
                        Log.d(TAG, "Answer ready: ${event.answer.answer}")
                        _state.value = McqAnswerState.Ready(event.answer)
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "AI stream failed", e)
            _state.value = previous
        }
    }

    private fun isThrottled(): Boolean =
        currentTimeMillis() - lastProcessedTime < minFrameIntervalMillis

    private companion object {
        const val TAG = "McqPipeline"
    }
}
