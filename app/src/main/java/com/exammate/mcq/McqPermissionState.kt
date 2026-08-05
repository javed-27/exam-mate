package com.exammate.mcq

enum class StepStatus { PENDING, GRANTED, DENIED }

data class McqPermissionState(
    val camera: StepStatus = StepStatus.PENDING,
    val accessibility: StepStatus = StepStatus.PENDING,
    val screenCapture: StepStatus = StepStatus.PENDING,
) {
    val allGranted: Boolean
        get() = camera == StepStatus.GRANTED &&
            accessibility == StepStatus.GRANTED &&
            screenCapture == StepStatus.GRANTED
}
