package com.exammate.mcq.ai

import com.exammate.mcq.McqAnswer
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class OllamaMcqClient(
    private val baseUrl: String = OLLAMA_BASE_URL,
    private val model: String = OLLAMA_MODEL,
    private val client: OkHttpClient = defaultClient(),
) : McqAiClient {

    override suspend fun solve(question: String, options: List<String>): McqAnswer =
        withContext(Dispatchers.IO) {
            val payload = JSONObject()
                .put("model", model)
                .put("prompt", buildMcqPrompt(question, options))
                .put("stream", false)
                .toString()

            val request = Request.Builder()
                .url("${baseUrl.trimEnd('/')}/api/generate")
                .post(payload.toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Ollama responded with HTTP ${response.code}")
                }
                val body = response.body?.string() ?: throw IOException("Ollama returned an empty body")
                val outer = JSONObject(body)
                val parsed = parseOllamaAnswer(outer.getString("response"))
                McqAnswer(
                    question = question,
                    answer = parsed.answer,
                    confidence = parsed.confidence,
                    explanation = parsed.explanation,
                )
            }
        }

    private companion object {
        fun defaultClient(): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()
    }
}
