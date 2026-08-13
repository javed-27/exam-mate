package com.exammate.mcq

import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import com.exammate.mcq.ai.McqAiClient
import com.exammate.mcq.ai.McqAiEvent
import com.exammate.mcq.ocr.OcrService
import com.exammate.mcq.parse.McqQuestionParser
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

interface McqPipeline {
    val state: StateFlow<McqAnswerState>

    suspend fun onFrame(bitmap: Bitmap)

    fun markDisplayed(text: String) = Unit

    fun restore(state: McqAnswerState) = Unit

    fun close() = Unit
}

class McqSolverPipeline(
    private val ocr: OcrService,
    private val aiClient: McqAiClient,
    private val parser: McqQuestionParser = McqQuestionParser,
    private val minFrameIntervalMillis: Long = 500L,
    private val changeThreshold: Int = DEFAULT_CHANGE_THRESHOLD,
    private val aiRetryBackoffMillis: Long = DEFAULT_AI_RETRY_BACKOFF_MILLIS,
    private val currentTimeMillis: () -> Long = { SystemClock.elapsedRealtime() },
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : McqPipeline {

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val _state = MutableStateFlow<McqAnswerState>(McqAnswerState.WaitingForOcr)
    override val state: StateFlow<McqAnswerState> = _state.asStateFlow()

    private var lastFramePixels: IntArray? = null
    private var lastProcessedStem: String? = null
    private var lastFailureStem: String? = null
    private var lastFailureTime: Long = 0L
    private var lastProcessedTime: Long = 0L
    private var processingJob: Job? = null
    private var processingStem: String? = null

    internal var framesDelivered: Int = 0
        private set
    internal var ocrFramesSkipped: Int = 0
        private set

    override suspend fun onFrame(bitmap: Bitmap) {
        framesDelivered++
        val current = McqFrameDiff.toGray16(bitmap)
        if (!frameChanged(lastFramePixels, current, changeThreshold)) {
            Log.d(TAG, "Frame skipped: content unchanged")
            ocrFramesSkipped++
            return
        }
        lastFramePixels = current
        processFrame { ocr.recognize(bitmap) }
    }

    internal suspend fun processFrame(produceText: suspend () -> String) {
        if (isThrottled()) {
            Log.d(TAG, "Frame skipped: throttled")
            return
        }
        lastProcessedTime = currentTimeMillis()

        val text = try {
            produceText()
        } catch (e: Exception) {
            Log.d(TAG, "OCR failed; keeping current state", e)
            return
        }
        consume(text)
    }

    override fun markDisplayed(text: String) {
        lastProcessedStem = parser.parse(text)?.let { parser.normalize(it.question) }
            ?: parser.sanitize(text)
    }

    override fun restore(state: McqAnswerState) {
        _state.value = state
        if (state is McqAnswerState.Ready) markDisplayed(state.answer.question)
    }

    override fun close() {
        processingJob?.cancel()
        scope.cancel()
    }

    private fun consume(text: String) {
        val normalized = parser.normalize(text)
        Log.d(TAG, "OCR text: ${normalized.take(600)}")
        if (normalized.isEmpty()) {
            Log.d(TAG, "OCR returned empty text")
        }
        val parsed = parser.parse(text) ?: run {
            Log.d(TAG, "Parse failed: no >=2 lettered/numbered option lines")
            val current = _state.value
            if (normalized.isNotBlank() && current !is McqAnswerState.Ready && current !is McqAnswerState.Error) {
                _state.value = McqAnswerState.Unparsed(normalized.take(MAX_DEBUG_TEXT_LENGTH))
            }
            return
        }
        val stem = parser.normalize(parsed.question)
        Log.d(TAG, "Parsed stem: $stem")

        if (stem == processingStem) {
            Log.d(TAG, "Already processing this stem; skipping")
            return
        }
        if (stem == lastProcessedStem) {
            Log.d(TAG, "Dedupe: stem already processed, skipping")
            return
        }
        val now = currentTimeMillis()
        if (stem == lastFailureStem && now - lastFailureTime < aiRetryBackoffMillis) {
            Log.d(TAG, "Backoff: failed stem retried too soon, skipping")
            return
        }

        processingJob?.cancel()
        processingStem = stem
        val previous = (_state.value as? McqAnswerState.Ready)?.answer
        _state.value = McqAnswerState.Processing(previous = previous)
        processingJob = scope.launch {
            try {
                var partialText = ""
                aiClient.stream(parsed.question, parsed.options).collect { event ->
                    when (event) {
                        is McqAiEvent.Text -> {
                            partialText += event.text
                            _state.value = McqAnswerState.Streaming(
                                previous = previous,
                                partialText = partialText,
                            )
                        }
                        is McqAiEvent.Answer -> {
                            lastProcessedStem = stem
                            lastFailureStem = null
                            processingStem = null
                            processingJob = null
                            Log.d(TAG, "Answer ready: ${event.answer.answer}")
                            _state.value = McqAnswerState.Ready(event.answer)
                        }
                    }
                }
            } catch (e: CancellationException) {
                Log.d(TAG, "AI stream cancelled for new question")
                throw e
            } catch (e: Exception) {
                Log.d(TAG, "AI stream failed", e)
                lastFailureStem = stem
                lastFailureTime = currentTimeMillis()
                processingStem = null
                processingJob = null
                _state.value = McqAnswerState.Error(
                    message = e.message ?: "AI request failed",
                    previous = previous,
                )
            }
        }
    }

    private fun isThrottled(): Boolean =
        currentTimeMillis() - lastProcessedTime < minFrameIntervalMillis

    private companion object {
        const val TAG = "McqPipeline"
        const val MAX_DEBUG_TEXT_LENGTH = 1500
        const val DEFAULT_CHANGE_THRESHOLD = 4
        const val DEFAULT_AI_RETRY_BACKOFF_MILLIS = 5000L
    }
}

internal fun frameChanged(
    previous: IntArray?,
    current: IntArray,
    threshold: Int,
): Boolean = previous == null || McqFrameDiff.meanAbsDiff(previous, current) >= threshold
