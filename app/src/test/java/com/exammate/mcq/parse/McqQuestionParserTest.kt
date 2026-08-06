package com.exammate.mcq.parse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class McqQuestionParserTest {

    @Test
    fun parsesQuestionWithLetterOptions() {
        val parsed = McqQuestionParser.parse(
            """
            1. Which of the following is the capital of France?
            A. Berlin
            B. Madrid
            C. Paris
            D. Rome
            """.trimIndent(),
        )

        assertEquals("1. Which of the following is the capital of France?", parsed?.question)
        assertEquals(
            listOf("A. Berlin", "B. Madrid", "C. Paris", "D. Rome"),
            parsed?.options,
        )
    }

    @Test
    fun parsesNumberedOptions() {
        val parsed = McqQuestionParser.parse(
            """
            Which planet is closest to the Sun?
            1. Venus
            2. Mercury
            3. Mars
            """.trimIndent(),
        )

        assertEquals("Which planet is closest to the Sun?", parsed?.question)
        assertEquals(listOf("1. Venus", "2. Mercury", "3. Mars"), parsed?.options)
    }

    @Test
    fun parsesSingleLineQuestion() {
        val parsed = McqQuestionParser.parse("Capital of India?\nA. Delhi\nB. Mumbai\nC. Kolkata\nD. Chennai")

        assertEquals("Capital of India?", parsed?.question)
        assertEquals(listOf("A. Delhi", "B. Mumbai", "C. Kolkata", "D. Chennai"), parsed?.options)
    }

    @Test
    fun optionFormatVariants() {
        val parsed = McqQuestionParser.parse(
            "Choose one:\na) red\nb) green\nc) blue\nd) yellow",
        )

        assertEquals("Choose one:", parsed?.question)
        assertEquals(listOf("a) red", "b) green", "c) blue", "d) yellow"), parsed?.options)
    }

    @Test
    fun numberedStem_usesLetterOptions() {
        val parsed = McqQuestionParser.parse(
            """
            1. What is 2 + 2?
            A. 3
            B. 4
            C. 5
            """.trimIndent(),
        )

        assertEquals("1. What is 2 + 2?", parsed?.question)
        assertEquals(listOf("A. 3", "B. 4", "C. 5"), parsed?.options)
    }

    @Test
    fun missingOptions_returnsNull() {
        assertNull(McqQuestionParser.parse("A sentence with no options."))
        assertNull(McqQuestionParser.parse("What is the capital?\nA. Only one option"))
    }

    @Test
    fun blankText_returnsNull() {
        assertNull(McqQuestionParser.parse(""))
        assertNull(McqQuestionParser.parse("   \n\t "))
    }

    @Test
    fun normalize_collapsesWhitespace() {
        val normalized = McqQuestionParser.normalize("  Which   planet   \n\n   is  this?  ")

        assertEquals("Which planet\n\nis this?", normalized)
    }
}
