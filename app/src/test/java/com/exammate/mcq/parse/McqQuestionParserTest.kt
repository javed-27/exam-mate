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
    fun parsesOptionsWithoutPunctuation_asOcrDelivers() {
        val parsed = McqQuestionParser.parse(
            "All of these countries EXCEPT which one sell Coca-Cola?\n" +
                "A Paraguay\nB Italy\nC Canada\nD Cuba",
        )

        assertEquals("All of these countries EXCEPT which one sell Coca-Cola?", parsed?.question)
        assertEquals(listOf("A Paraguay", "B Italy", "C Canada", "D Cuba"), parsed?.options)
    }

    @Test
    fun parsesNumberedOptionsWithoutPunctuation() {
        val parsed = McqQuestionParser.parse("Which planet is closest to the Sun?\n1 Venus\n2 Mercury\n3 Mars")

        assertEquals("Which planet is closest to the Sun?", parsed?.question)
        assertEquals(listOf("1 Venus", "2 Mercury", "3 Mars"), parsed?.options)
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
    fun parsesBulletedOptions() {
        val parsed = McqQuestionParser.parse(
            """
            When you want to eat chocolate, what brand do you buy?
            • Hershey
            • Mars
            • Twix
            • Whittaker's
            """.trimIndent(),
        )

        assertEquals("When you want to eat chocolate, what brand do you buy?", parsed?.question)
        assertEquals(
            listOf("• Hershey", "• Mars", "• Twix", "• Whittaker's"),
            parsed?.options,
        )
    }

    @Test
    fun parsesRadioGlyphOptions_readAsLetterO() {
        val parsed = McqQuestionParser.parse(
            """
            When you want to eat chocolate, what brand do you buy?
            O Hershey
            O Mars
            O Twix
            O Nestlé
            """.trimIndent(),
        )

        assertEquals("When you want to eat chocolate, what brand do you buy?", parsed?.question)
        assertEquals(
            listOf("O Hershey", "O Mars", "O Twix", "O Nestlé"),
            parsed?.options,
        )
    }

    @Test
    fun stripsUiChromeFromStem_beforeOptions() {
        val parsed = McqQuestionParser.parse(
            """
            <Back
            Pick 1
            Next>
            When you want to eat chocolate, what brand
            do you buy?
            O Hershey
            O Mars
            O Twix
            O Nestlé
            """.trimIndent(),
        )

        assertEquals(
            "When you want to eat chocolate, what brand do you buy?",
            parsed?.question,
        )
        assertEquals(4, parsed?.options?.size)
    }

    @Test
    fun stripsCountdownTimerFromStem() {
        val parsed = McqQuestionParser.parse(
            """
            00:47
            Which of the following is the capital of France?
            A. Berlin
            B. Madrid
            C. Paris
            D. Rome
            """.trimIndent(),
        )

        assertEquals("Which of the following is the capital of France?", parsed?.question)
        assertEquals(4, parsed?.options?.size)
    }

    @Test
    fun timerChange_keepsStemStable() {
        val timerA = "00:47\nWhich of the following is the capital of France?\nA. Berlin\nB. Madrid\nC. Paris\nD. Rome"
        val timerB = "00:46\nWhich of the following is the capital of France?\nA. Berlin\nB. Madrid\nC. Paris\nD. Rome"

        assertEquals(
            McqQuestionParser.parse(timerA)?.question,
            McqQuestionParser.parse(timerB)?.question,
        )
    }

    @Test
    fun sanitize_stripsTimerWithSecondsAndInlineTokens() {
        assertEquals(
            "Time left:\nQuestion?",
            McqQuestionParser.sanitize("Time left: 00:01:23\n00:47 Question?"),
        )
    }

    @Test
    fun stripsExamPortalChromeFromStem() {
        val parsed = McqQuestionParser.parse(
            """
            Descriptive Part
            Type: 4 Answer any 2 of following [5*2=10]
            Q.no 4.1.
            Which of the following is the capital of France?
            A. Berlin
            B. Madrid
            C. Paris
            D. Rome
            Previous
            Next
            Mark for review
            """.trimIndent(),
        )

        assertEquals("Q.no 4.1. Which of the following is the capital of France?", parsed?.question)
        assertEquals(listOf("A. Berlin", "B. Madrid", "C. Paris", "D. Rome"), parsed?.options)
    }

    @Test
    fun stripsKeyboardKeysFromStem() {
        val parsed = McqQuestionParser.parse(
            """
            esc
            F1
            F2
            Which of the following is the capital of France?
            A. Berlin
            B. Madrid
            C. Paris
            D. Rome
            """.trimIndent(),
        )

        assertEquals("Which of the following is the capital of France?", parsed?.question)
        assertEquals(4, parsed?.options?.size)
    }

    @Test
    fun stripsStrayAttachPrefixFromStem() {
        val parsed = McqQuestionParser.parse(
            """
            e Attach
            Scan the QR Code through any QR scanning app
            Which of the following is the capital of France?
            A. Berlin
            B. Madrid
            C. Paris
            D. Rome
            """.trimIndent(),
        )

        assertEquals("Which of the following is the capital of France?", parsed?.question)
        assertEquals(4, parsed?.options?.size)
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
