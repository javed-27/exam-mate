package com.exammate.mcq.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONException

class McqPromptTest {

    @Test
    fun prompt_containsQuestionAndOptions() {
        val prompt = buildMcqPrompt(
            "Which of the following is the capital of France?",
            listOf("A. Berlin", "B. Madrid", "C. Paris", "D. Rome"),
        )

        assertTrue(prompt.contains("Which of the following is the capital of France?"))
        assertTrue(prompt.contains("A. Berlin"))
        assertTrue(prompt.contains("D. Rome"))
    }

    @Test
    fun prompt_instructsJsonReply() {
        val prompt = buildMcqPrompt("Q?", listOf("A. x", "B. y"))

        assertTrue(prompt.contains("JSON"))
        assertTrue(prompt.contains("confidence"))
    }

    @Test
    fun prompt_withoutOptions_instructsExtraction() {
        val prompt = buildMcqPrompt("A. Berlin\nB. Madrid\nC. Paris\nD. Rome", emptyList())

        assertTrue(prompt.contains("may contain the answer options"))
        assertTrue(prompt.contains("A. Berlin"))
    }

    @Test
    fun extractJson_handlesMarkdownFences() {
        val wrapped = "```json\n{\"answer\": \"C. Paris\", \"confidence\": 0.98, \"explanation\": \"It is the capital.\"}\n```"

        assertEquals(
            "{\"answer\": \"C. Paris\", \"confidence\": 0.98, \"explanation\": \"It is the capital.\"}",
            extractJson(wrapped),
        )
    }

    @Test
    fun extractJson_returnsTextWhenNoJson() {
        assertEquals("hello", extractJson("hello"))
    }

    @Test
    fun parseOllamaAnswer_parsesValidJson() {
        val parsed = parseOllamaAnswer(
            """{"answer": "C. Paris", "confidence": 0.98, "explanation": "Paris is the capital of France."}""",
        )

        assertEquals("C. Paris", parsed.answer)
        assertEquals(0.98, parsed.confidence, 0.0001)
        assertEquals("Paris is the capital of France.", parsed.explanation)
    }

    @Test
    fun parseOllamaAnswer_clampsOutOfRangeConfidence() {
        val parsed = parseOllamaAnswer("""{"answer": "A", "confidence": 98, "explanation": "e"}""")

        assertEquals(1.0, parsed.confidence, 0.0001)
    }

    @Test(expected = JSONException::class)
    fun parseOllamaAnswer_missingAnswer_throws() {
        parseOllamaAnswer("""{"confidence": 0.5, "explanation": "e"}""")
    }

    @Test(expected = JSONException::class)
    fun parseOllamaAnswer_missingConfidence_throws() {
        parseOllamaAnswer("""{"answer": "A", "explanation": "e"}""")
    }
}
