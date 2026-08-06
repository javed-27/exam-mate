package com.exammate.mcq.ai

import org.json.JSONException
import org.json.JSONObject

const val OLLAMA_BASE_URL = "http://10.0.2.2:11434"
const val OLLAMA_MODEL = "qwen2.5:7b"

data class OllamaAnswer(
    val answer: String,
    val confidence: Double,
    val explanation: String,
)

fun buildMcqPrompt(question: String, options: List<String>): String =
    buildString {
        appendLine("You are solving a multiple-choice question.")
        appendLine("Question: $question")
        appendLine("Options:")
        options.forEach { appendLine("- $it") }
        appendLine(
            """Reply with ONLY a JSON object in this exact shape: """ +
                """{"answer": "<option text>", "confidence": 0.0-1.0, "explanation": "<one short sentence>"}""",
        )
    }.trim()

fun extractJson(responseText: String): String {
    val trimmed = responseText.trim()
    val start = trimmed.indexOf('{')
    val end = trimmed.lastIndexOf('}')
    return if (start >= 0 && end > start) trimmed.substring(start, end + 1) else trimmed
}

fun parseOllamaAnswer(responseText: String): OllamaAnswer {
    val json = JSONObject(extractJson(responseText))
    val answer = json.optString("answer").trim()
    if (answer.isEmpty()) throw JSONException("Missing answer field")
    val confidence = json.getDouble("confidence").coerceIn(0.0, 1.0)
    val explanation = json.optString("explanation").trim()
    return OllamaAnswer(answer = answer, confidence = confidence, explanation = explanation)
}
