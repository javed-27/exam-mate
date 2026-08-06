package com.exammate.mcq

import android.graphics.Bitmap
import android.os.SystemClock
import com.exammate.mcq.ai.McqAiClient
import com.exammate.mcq.ocr.OcrService
import com.exammate.mcq.parse.McqQuestionParser
import kotlinx.coroutines.sync.Mutex

interface McqPipeline {
    val initialState: McqAnswerState

    suspend fun onFrame(bitmap: Bitmap): McqAnswerState

    fun markDisplayed(text: String) = Unit
}

class McqSolverPipeline(
    private val ocr: OcrService,
    private val aiClient: McqAiClient,
    private val parser: McqQuestionParser = McqQuestionParser,
    private val minFrameIntervalMillis: Long = 500L,
    private val currentTimeMillis: () -> Long = { SystemClock.elapsedRealtime() },
) : McqPipeline {

    override val initialState: McqAnswerState = McqAnswerState.Waiting

    private val mutex = Mutex()
    private var state: McqAnswerState = McqAnswerState.Waiting
    private var lastProcessedText: String? = null
    private var lastProcessedTime: Long = 0L

    override suspend fun onFrame(bitmap: Bitmap): McqAnswerState =
        processFrame { ocr.recognize(bitmap) }

    internal suspend fun processFrame(produceText: suspend () -> String): McqAnswerState {
        if (isThrottled()) return state
        if (!mutex.tryLock()) return state
        try {
            lastProcessedTime = currentTimeMillis()
            return consume(produceText())
        } catch (e: Exception) {
            return state
        } finally {
            mutex.unlock()
        }
    }

    override fun markDisplayed(text: String) {
        lastProcessedText = parser.normalize(text)
    }

    private suspend fun consume(text: String): McqAnswerState {
        val normalized = parser.normalize(text)
        if (normalized.isEmpty()) return state
        if (normalized == lastProcessedText) return state
        val parsed = parser.parse(normalized) ?: return state
        val previous = state
        if (previous !is McqAnswerState.Ready) state = McqAnswerState.Processing
        return try {
            val answer = aiClient.solve(parsed.question, parsed.options)
            lastProcessedText = normalized
            state = McqAnswerState.Ready(answer)
            state
        } catch (e: Exception) {
            state = previous
            state
        }
    }

    private fun isThrottled(): Boolean =
        currentTimeMillis() - lastProcessedTime < minFrameIntervalMillis
}
