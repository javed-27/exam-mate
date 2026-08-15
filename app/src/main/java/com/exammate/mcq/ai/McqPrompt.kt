package com.exammate.mcq.ai

import com.exammate.BuildConfig
import org.json.JSONException
import org.json.JSONObject

val OLLAMA_BASE_URL: String = BuildConfig.OLLAMA_BASE_URL
val OLLAMA_MODEL: String = BuildConfig.OLLAMA_MODEL
val OLLAMA_API_KEY: String = BuildConfig.OLLAMA_API_KEY

data class OllamaAnswer(
    val answer: String,
    val confidence: Double,
    val explanation: String,
)

fun buildMcqPrompt(question: String, options: List<String>): String =
    buildString {
        appendLine("You are solving a multiple-choice question.")
        appendLine("Question: $question")
        if (options.isEmpty()) {
            appendLine(
                "The question text above may contain the answer options " +
                    "embedded in it. Extract them if present, then select the best answer.",
            )
        } else {
            appendLine("Options:")
            options.forEach { appendLine("- $it") }
        }
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
