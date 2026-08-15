package com.exammate.theory

sealed interface TheoryCaptureState {
    data object Viewfinder : TheoryCaptureState
    data object Capturing : TheoryCaptureState
    data class Captured(val ocrText: String) : TheoryCaptureState
    data class OcrFailed(val message: String) : TheoryCaptureState
}
