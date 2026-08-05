package com.exammate.mcq

enum class PermissionAction {
    REQUEST_CAMERA,
    REQUEST_ACCESSIBILITY,
    REQUEST_SCREEN_CAPTURE,
    SHOW_CAMERA_DENIED,
    SHOW_STEP_DENIED,
    START_CAPTURE,
}

fun nextAction(
    state: McqPermissionState,
    cameraDenied: Boolean,
): PermissionAction {
    return when {
        state.camera != StepStatus.GRANTED ->
            if (cameraDenied) PermissionAction.SHOW_CAMERA_DENIED
            else PermissionAction.REQUEST_CAMERA
        state.accessibility != StepStatus.GRANTED ->
            if (state.accessibility == StepStatus.DENIED) PermissionAction.SHOW_STEP_DENIED
            else PermissionAction.REQUEST_ACCESSIBILITY
        state.screenCapture != StepStatus.GRANTED ->
            if (state.screenCapture == StepStatus.DENIED) PermissionAction.SHOW_STEP_DENIED
            else PermissionAction.REQUEST_SCREEN_CAPTURE
        else -> PermissionAction.START_CAPTURE
    }
}
