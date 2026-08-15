package com.exammate.theory

import android.util.Log
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

const val OCR_FAILURE_MESSAGE = "Could not read the question. Try again."
const val BLANK_OCR_MESSAGE = "Could not read the question. Adjust the frame and try again."

class TheoryCaptureModel(
    private val recognize: suspend () -> String,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val _state = MutableStateFlow<TheoryCaptureState>(TheoryCaptureState.Viewfinder)
    val state: StateFlow<TheoryCaptureState> = _state.asStateFlow()

    private var captureJob: Job? = null

    fun capture() {
        if (_state.value is TheoryCaptureState.Capturing) return
        _state.value = TheoryCaptureState.Capturing
        captureJob = scope.launch {
            val text = try {
                recognize()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "OCR failed on captured frame", e)
                _state.value = TheoryCaptureState.OcrFailed(
                    e.message?.takeIf { it.isNotBlank() } ?: OCR_FAILURE_MESSAGE,
                )
                return@launch
            }
            _state.value = if (text.isBlank()) {
                TheoryCaptureState.OcrFailed(BLANK_OCR_MESSAGE)
            } else {
                TheoryCaptureState.Captured(text)
            }
        }
    }

    fun recapture() {
        captureJob?.cancel()
        _state.value = TheoryCaptureState.Viewfinder
    }

    fun close() {
        captureJob?.cancel()
        scope.cancel()
    }

    private companion object {
        const val TAG = "TheoryCapture"
    }
}
