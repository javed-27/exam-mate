package com.exammate.mcq

import org.junit.Assert.assertEquals
import org.junit.Test

class McqPermissionManagerTest {

    @Test
    fun requestCamera_whenCameraPending_andNeverDenied() {
        val state = McqPermissionState()

        assertEquals(
            PermissionAction.REQUEST_CAMERA,
            nextAction(state, cameraDenied = false),
        )
    }

    @Test
    fun showCameraDenied_whenCameraPending_andPreviouslyDenied() {
        val state = McqPermissionState()

        assertEquals(
            PermissionAction.SHOW_CAMERA_DENIED,
            nextAction(state, cameraDenied = true),
        )
    }

    @Test
    fun requestAccessibility_whenCameraGranted_andAccessibilityPending() {
        val state = McqPermissionState(camera = StepStatus.GRANTED)

        assertEquals(
            PermissionAction.REQUEST_ACCESSIBILITY,
            nextAction(state, cameraDenied = false),
        )
    }

    @Test
    fun showStepDenied_whenAccessibilityDenied() {
        val state = McqPermissionState(
            camera = StepStatus.GRANTED,
            accessibility = StepStatus.DENIED,
        )

        assertEquals(
            PermissionAction.SHOW_STEP_DENIED,
            nextAction(state, cameraDenied = false),
        )
    }

    @Test
    fun requestScreenCapture_whenCameraAndAccessibilityGranted_andScreenCapturePending() {
        val state = McqPermissionState(
            camera = StepStatus.GRANTED,
            accessibility = StepStatus.GRANTED,
        )

        assertEquals(
            PermissionAction.REQUEST_SCREEN_CAPTURE,
            nextAction(state, cameraDenied = false),
        )
    }

    @Test
    fun showStepDenied_whenScreenCaptureDenied() {
        val state = McqPermissionState(
            camera = StepStatus.GRANTED,
            accessibility = StepStatus.GRANTED,
            screenCapture = StepStatus.DENIED,
        )

        assertEquals(
            PermissionAction.SHOW_STEP_DENIED,
            nextAction(state, cameraDenied = false),
        )
    }

    @Test
    fun startCapture_whenAllPermissionsGranted() {
        val state = McqPermissionState(
            camera = StepStatus.GRANTED,
            accessibility = StepStatus.GRANTED,
            screenCapture = StepStatus.GRANTED,
        )

        assertEquals(
            PermissionAction.START_CAPTURE,
            nextAction(state, cameraDenied = false),
        )
    }
}
