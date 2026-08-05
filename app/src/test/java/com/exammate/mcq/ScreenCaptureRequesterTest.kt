package com.exammate.mcq

import android.app.Activity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenCaptureRequesterTest {

    private val requester = DefaultScreenCaptureRequester()

    @Test
    fun granted_whenResultOk_andHasProjectionData() {
        assertTrue(isScreenCaptureGranted(Activity.RESULT_OK, hasProjectionData = true))
    }

    @Test
    fun notGranted_whenResultOk_butNoProjectionData() {
        assertFalse(isScreenCaptureGranted(Activity.RESULT_OK, hasProjectionData = false))
    }

    @Test
    fun notGranted_whenResultCancelled_evenWithProjectionData() {
        assertFalse(isScreenCaptureGranted(Activity.RESULT_CANCELED, hasProjectionData = true))
    }

    @Test
    fun notGranted_whenResultCancelled_andNoProjectionData() {
        assertFalse(isScreenCaptureGranted(Activity.RESULT_CANCELED, hasProjectionData = false))
    }

    @Test
    fun parseResult_returnsDenied_whenResultOk_butDataMissing() {
        val result = requester.parseResult(Activity.RESULT_OK, data = null)

        assertFalse(result.granted)
        assertNull(result.projectionData)
    }

    @Test
    fun parseResult_returnsDenied_whenCancelled() {
        val result = requester.parseResult(Activity.RESULT_CANCELED, data = null)

        assertFalse(result.granted)
        assertNull(result.projectionData)
    }
}
