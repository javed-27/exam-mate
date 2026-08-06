package com.exammate.mcq.ai

import com.exammate.mcq.McqAnswer
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject

class OllamaMcqClient(
    private val baseUrl: String = OLLAMA_BASE_URL,
    private val model: String = OLLAMA_MODEL,
    private val client: OkHttpClient = defaultClient(),
) : McqAiClient {

    override fun stream(question: String, options: List<String>): Flow<McqAiEvent> = callbackFlow {
        val payload = JSONObject()
            .put("model", model)
            .put("prompt", buildMcqPrompt(question, options))
            .put("stream", true)
            .toString()

        val request = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/api/generate")
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()

        val call = client.newCall(request)
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                close(e)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) {
                        close(IOException("Ollama responded with HTTP ${it.code}"))
                        return
                    }
                    val source = it.body.source()
                    try {
                        val textBuilder = StringBuilder()
                        while (true) {
                            val line = source.readUtf8Line() ?: break
                            if (line.isBlank()) continue
                            val chunk = JSONObject(line)
                            val chunkText = chunk.optString("response")
                            textBuilder.append(chunkText)
                            if (chunk.optBoolean("done")) {
                                val answer = parseOllamaAnswer(textBuilder.toString())
                                trySend(McqAiEvent.Answer(toAnswer(question, answer)))
                                close()
                                return
                            } else if (chunkText.isNotEmpty()) {
                                trySend(McqAiEvent.Text(chunkText))
                            }
                        }
                        close(IOException("Ollama stream ended without done"))
                    } catch (e: Exception) {
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
        fun defaultClient(): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .build()
    }
}
