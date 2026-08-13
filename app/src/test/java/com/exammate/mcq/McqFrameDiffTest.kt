package com.exammate.mcq

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class McqFrameDiffTest {

    private val edge = McqFrameDiff.GRAY_EDGE

    @Test
    fun grayOf_ignoresAlpha_usesLuminance() {
        assertEquals((0x11 + 0x22 + 0x33) / 3, McqFrameDiff.grayOf(0xFF112233.toInt()))
    }

    @Test
    fun grayOf_treatsBlackAsZero() {
        assertEquals(0, McqFrameDiff.grayOf(0xFF000000.toInt()))
    }

    @Test
    fun grayOf_treatsWhiteAs255() {
        assertEquals(255, McqFrameDiff.grayOf(0xFFFFFFFF.toInt()))
    }

    @Test
    fun meanAbsDiff_samePixels_zero() {
        val pixels = grayPixels(edge * edge) { 128 }
        assertEquals(0.0f, McqFrameDiff.meanAbsDiff(pixels, pixels.copyOf()), 0.001f)
    }

    @Test
    fun meanAbsDiff_whiteVsBlack_isFullRange() {
        val white = grayPixels(edge * edge) { 255 }
        val black = grayPixels(edge * edge) { 0 }
        assertEquals(255.0f, McqFrameDiff.meanAbsDiff(white, black), 0.001f)
    }

    @Test
    fun meanAbsDiff_averagesPerPixelDifference() {
        val previous = grayPixels(edge * edge) { 10 }
        val current = grayPixels(edge * edge) { 20 }
        assertEquals(10.0f, McqFrameDiff.meanAbsDiff(previous, current), 0.001f)
    }

    @Test
    fun frameChanged_firstFrame_isChanged() {
        assertTrue(frameChanged(previous = null, current = grayPixels(4) { 0 }, threshold = 4))
    }

    @Test
    fun frameChanged_identicalFrames_isNotChanged() {
        val pixels = grayPixels(4) { 100 }
        assertFalse(frameChanged(previous = pixels, current = pixels.copyOf(), threshold = 4))
    }

    @Test
    fun frameChanged_belowThreshold_isNotChanged() {
        val previous = grayPixels(4) { 100 }
        val current = grayPixels(4) { 101 }
        assertFalse(frameChanged(previous = previous, current = current, threshold = 4))
    }

    @Test
    fun frameChanged_aboveThreshold_isChanged() {
        val previous = grayPixels(4) { 0 }
        val current = grayPixels(4) { 255 }
        assertTrue(frameChanged(previous = previous, current = current, threshold = 4))
    }

    private fun grayPixels(size: Int, gray: (Int) -> Int): IntArray {
        val pixels = IntArray(size)
        for (i in pixels.indices) {
            val g = gray(i)
            pixels[i] = (0xFF shl 24) or (g shl 16) or (g shl 8) or g
        }
        return pixels
    }
}
