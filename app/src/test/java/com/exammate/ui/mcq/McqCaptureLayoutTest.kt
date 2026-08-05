package com.exammate.ui.mcq

import androidx.compose.ui.unit.Dp
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class McqCaptureLayoutTest {

    @Test
    fun widerThanTall_isLandscape() {
        assertTrue(isLandscape(Dp(320f), Dp(180f)))
    }

    @Test
    fun tallerThanWide_isPortrait() {
        assertFalse(isLandscape(Dp(180f), Dp(320f)))
    }

    @Test
    fun equalWidthAndHeight_isPortrait() {
        assertFalse(isLandscape(Dp(240f), Dp(240f)))
    }
}
