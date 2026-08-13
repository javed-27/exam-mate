package com.exammate.mcq

import android.graphics.Bitmap

/**
 * Cheap visual-change detection for camera frames. A 16x16 grayscale downscale is compared
 * with the previous frame; OCR is skipped while the captured content is effectively unchanged.
 */
internal object McqFrameDiff {

    const val GRAY_EDGE = 16

    fun toGray16(bitmap: Bitmap): IntArray {
        val scaled = Bitmap.createScaledBitmap(bitmap, GRAY_EDGE, GRAY_EDGE, true)
        val pixels = IntArray(GRAY_EDGE * GRAY_EDGE)
        scaled.getPixels(pixels, 0, GRAY_EDGE, 0, 0, GRAY_EDGE, GRAY_EDGE)
        if (scaled !== bitmap) scaled.recycle()
        return pixels
    }

    fun meanAbsDiff(previous: IntArray, current: IntArray): Float {
        var sum = 0
        for (i in previous.indices) {
            val p = grayOf(previous[i])
            val c = grayOf(current[i])
            sum += if (p > c) p - c else c - p
        }
        return sum.toFloat() / previous.size
    }

    fun grayOf(pixel: Int): Int {
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF
        return (r + g + b) / 3
    }
}
