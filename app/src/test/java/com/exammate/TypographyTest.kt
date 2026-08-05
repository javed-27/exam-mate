package com.exammate

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.exammate.ui.theme.Typography
import org.junit.Assert.assertEquals
import org.junit.Test

class TypographyTest {

    @Test
    fun screenTitle_is28spBold() {
        val style = Typography.headlineMedium
        assertEquals(28.sp, style.fontSize)
        assertEquals(FontWeight.Bold, style.fontWeight)
    }

    @Test
    fun cardTitle_is20spSemiBold() {
        val style = Typography.titleLarge
        assertEquals(20.sp, style.fontSize)
        assertEquals(FontWeight.SemiBold, style.fontWeight)
    }

    @Test
    fun body_is16sp() {
        assertEquals(16.sp, Typography.bodyLarge.fontSize)
    }

    @Test
    fun caption_is14sp() {
        assertEquals(14.sp, Typography.bodySmall.fontSize)
    }

    @Test
    fun button_is16spMedium() {
        val style = Typography.labelLarge
        assertEquals(16.sp, style.fontSize)
        assertEquals(FontWeight.Medium, style.fontWeight)
    }
}
