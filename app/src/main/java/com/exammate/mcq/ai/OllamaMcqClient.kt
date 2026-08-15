package com.exammate.mcq.ai

import android.util.Log
import com.exammate.mcq.McqAnswer
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject

class OllamaMcqClient(
    private val baseUrl: () -> String = { OLLAMA_BASE_URL },
    private val model: () -> String = { OLLAMA_MODEL },
    private val apiKey: () -> String = { OLLAMA_API_KEY },
    private val client: OkHttpClient = defaultClient(),
    private val fallbackBaseUrls: List<String> = DEFAULT_FALLBACK_BASE_URLS,
    private val keepAlive: String = DEFAULT_KEEP_ALIVE,
) : McqAiClient {

    override fun stream(question: String, options: List<String>): Flow<McqAiEvent> = flow {
        val candidates = buildList {
            add(baseUrl().trimEnd('/'))
            addAll(fallbackBaseUrls.map { it.trimEnd('/') })
        }.distinct()

        var lastError: Exception? = null
        for (candidate in candidates) {
            try {
                Log.d(TAG, "Streaming to $candidate (question len=${question.length}, options=${options.size})")
                emitAll(streamFrom(candidate, question, options))
                Log.d(TAG, "Stream from $candidate completed")
                return@flow
            } catch (e: Exception) {
                Log.w(TAG, "Stream from $candidate failed", e)
                lastError = e
            }
        }
        throw lastError ?: IOException("Ollama unreachable on all hosts: $candidates")
    }

    private fun streamFrom(
        baseUrl: String,
        question: String,
        options: List<String>,
    ): Flow<McqAiEvent> = callbackFlow {
        val payload = JSONObject()
            .put("model", model())
            .put("prompt", buildMcqPrompt(question, options))
            .put("stream", true)
            .put("keep_alive", keepAlive)
            .toString()

        val requestBuilder = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/api/generate")
            .post(payload.toRequestBody("application/json".toMediaType()))
        if (apiKey().isNotBlank()) {
            requestBuilder.header("Authorization", "Bearer ${apiKey()}")
        }
        val request = requestBuilder.build()

        val call = client.newCall(request)
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.w(TAG, "Call failed to $baseUrl", e)
                close(e)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) {
                        Log.w(TAG, "Ollama responded with HTTP ${it.code}")
                        close(IOException("Ollama responded with HTTP ${it.code}"))
                        return
                    }
                    Log.d(TAG, "Response OK from $baseUrl; reading stream")
                    val source = it.body.source()
                    try {
                        val textBuilder = StringBuilder()
                        var chunks = 0
                        while (true) {
                            val line = source.readUtf8Line() ?: break
                            if (line.isBlank()) continue
                            val chunk = JSONObject(line)
                            val chunkText = chunk.optString("response")
                            textBuilder.append(chunkText)
                            chunks++
                            if (chunk.optBoolean("done")) {
                                Log.d(TAG, "Stream done after $chunks chunks")
                                val answer = parseOllamaAnswer(textBuilder.toString())
                                trySend(McqAiEvent.Answer(toAnswer(question, answer)))
                                close()
                                return
                            } else if (chunkText.isNotEmpty()) {
                                trySend(McqAiEvent.Text(chunkText))
                            }
                        }
                        Log.w(TAG, "Ollama stream ended without done after $chunks chunks")
                        close(IOException("Ollama stream ended without done"))
                    } catch (e: Exception) {
                        Log.w(TAG, "Error reading stream from $baseUrl", e)
                        close(e)
                    }
                }
            }
        })
        awaitClose { call.cancel() }
    }

    private fun toAnswer(question: String, parsed: OllamaAnswer): McqAnswer =
        McqAnswer(
            question = question,
            answer = parsed.answer,
            confidence = parsed.confidence,
            explanation = parsed.explanation,
        )

    private companion object {
        const val TAG = "OllamaMcqClient"
        const val DEFAULT_KEEP_ALIVE = "30m"
        val DEFAULT_FALLBACK_BASE_URLS: List<String> =
            listOf("http://localhost:11434", "http://10.0.2.2:11434")

        fun defaultClient(): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(4, TimeUnit.SECONDS)
                .readTimeout(90, TimeUnit.SECONDS)
                .build()
    }
}
