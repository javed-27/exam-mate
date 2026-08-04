package com.exammate

import com.exammate.ui.splash.SPLASH_DURATION_MS
import org.junit.Assert.assertEquals
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun splashDuration_isOneAndAHalfSeconds() {
        assertEquals(1500L, SPLASH_DURATION_MS)
    }
}
