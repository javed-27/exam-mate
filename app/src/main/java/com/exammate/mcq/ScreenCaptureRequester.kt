package com.exammate.mcq

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager

data class ScreenCaptureResult(
    val granted: Boolean,
    val projectionData: Intent?,
)

fun isScreenCaptureGranted(resultCode: Int, hasProjectionData: Boolean): Boolean =
    resultCode == Activity.RESULT_OK && hasProjectionData

interface ScreenCaptureRequester {
    fun createScreenCaptureIntent(activity: Activity): Intent

    fun parseResult(resultCode: Int, data: Intent?): ScreenCaptureResult
}

class DefaultScreenCaptureRequester : ScreenCaptureRequester {

    override fun createScreenCaptureIntent(activity: Activity): Intent {
        val manager = activity.getSystemService(MediaProjectionManager::class.java)
            ?: error("MediaProjectionManager is not available")
        return manager.createScreenCaptureIntent()
    }

    override fun parseResult(resultCode: Int, data: Intent?): ScreenCaptureResult =
        if (isScreenCaptureGranted(resultCode, data != null)) {
            ScreenCaptureResult(granted = true, projectionData = data)
        } else {
            ScreenCaptureResult(granted = false, projectionData = null)
        }
}
