package com.exammate.mcq.ai

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OllamaMcqClientTest {

    private val server = MockWebServer()

    @Before
    fun setUp() {
        server.start()
    }

    @After
    fun tearDown() {
        server.close()
    }

    private var baseUrlProvider: () -> String = { server.url("/").toString() }
    private var modelProvider: () -> String = { "qwen2.5:7b" }
    private var apiKeyProvider: () -> String = { "" }

    private fun client(apiKey: String = ""): OllamaMcqClient {
        apiKeyProvider = { apiKey }
        return OllamaMcqClient(
            baseUrl = { baseUrlProvider() },
            model = { modelProvider() },
            apiKey = { apiKeyProvider() },
            client = OkHttpClient(),
            fallbackBaseUrls = emptyList(),
        )
    }

    @Test
    fun stream_emitsTextChunksThenAnswer() = runBlocking {
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .body(
                    buildString {
                        appendLine("""{"response":"{\"answer\": \"C. Paris\", \"confid","done":false}""")
                        appendLine("""{"response":"ence\": 0.98, \"explanation\": \"Paris is the capital of France.\"}","done":false}""")
                        appendLine("""{"response":"","done":true}""")
                    },
                )
                .build(),
        )

        val events = client().stream(
            question = "Which of the following is the capital of France?",
            options = listOf("A. Berlin", "B. Madrid", "C. Paris", "D. Rome"),
        ).toList()

        val text = events.filterIsInstance<McqAiEvent.Text>().joinToString("") { it.text }
        val answer = (events.last() as McqAiEvent.Answer).answer

        assertEquals(
            """{"answer": "C. Paris", "confidence": 0.98, "explanation": "Paris is the capital of France."}""",
            text,
        )
        assertEquals("C. Paris", answer.answer)
        assertEquals(0.98, answer.confidence, 0.0001)
        assertEquals("Paris is the capital of France.", answer.explanation)
        assertEquals("Which of the following is the capital of France?", answer.question)

        val recorded = server.takeRequest()
        assertEquals("/api/generate", recorded.url.encodedPath)
        assertTrue(recorded.body?.utf8()?.contains("\"stream\":true") == true)
        assertTrue(recorded.body?.utf8()?.contains("\"model\":\"qwen2.5:7b\"") == true)
        assertTrue(recorded.body?.utf8()?.contains("\"keep_alive\":\"30m\"") == true)
    }

    @Test
    fun stream_sendsBearerAuthHeader_whenApiKeyProvided() = runBlocking {
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .body(buildString {
                    appendLine("""{"response":"{\"answer\": \"C. Paris\", \"confidence\": 0.98, \"explanation\": \"Paris is the capital of France.\"}","done":false}""")
                    appendLine("""{"response":"","done":true}""")
                })
                .build(),
        )

        client(apiKey = "sk-test-123").stream(
            question = "Which of the following is the capital of France?",
            options = listOf("A. Berlin", "B. Madrid", "C. Paris", "D. Rome"),
        ).toList()

        val recorded = server.takeRequest()
        assertEquals("Bearer sk-test-123", recorded.headers["Authorization"])
    }

    @Test
    fun stream_omitsAuthHeader_whenNoApiKey() = runBlocking {
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .body(buildString {
                    appendLine("""{"response":"{\"answer\": \"C. Paris\", \"confidence\": 0.98, \"explanation\": \"Paris is the capital of France.\"}","done":false}""")
                    appendLine("""{"response":"","done":true}""")
                })
                .build(),
        )

        client().stream(
            question = "Which of the following is the capital of France?",
            options = listOf("A. Berlin", "B. Madrid", "C. Paris", "D. Rome"),
        ).toList()

        val recorded = server.takeRequest()
        assertEquals(null, recorded.headers["Authorization"])
    }

    @Test
    fun stream_httpFailure_throws() = runBlocking {
        server.enqueue(MockResponse.Builder().code(500).body("boom").build())

        val thrown = try {
            client().stream("Q?", listOf("A. x", "B. y")).toList()
            null
        } catch (e: Exception) {
            e
        }

        assertTrue(thrown is IOException)
    }

    @Test
    fun stream_malformedModelResponse_throws() = runBlocking {
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .body(buildString {
                    appendLine("""{"response":"definitely not json","done":true}""")
                })
                .build(),
        )

        val thrown = try {
            client().stream("Q?", listOf("A. x", "B. y")).toList()
            null
        } catch (e: Exception) {
            e
        }

        assertTrue(thrown is Exception)
    }

    @Test
    fun stream_fallsBackToNextHost_whenPrimaryUnreachable() = runBlocking {
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .body(buildString {
                    appendLine("""{"response":"{\"answer\": \"C. Paris\", \"confidence\": 0.98, \"explanation\": \"Paris is the capital of France.\"}","done":false}""")
                    appendLine("""{"response":"","done":true}""")
                })
                .build(),
        )

        val events = OllamaMcqClient(
            baseUrl = { "http://127.0.0.1:1" },
            model = { "qwen2.5:7b" },
            client = OkHttpClient.Builder()
                .connectTimeout(2, TimeUnit.SECONDS)
                .readTimeout(2, TimeUnit.SECONDS)
                .build(),
            fallbackBaseUrls = listOf(server.url("/").toString()),
        ).stream(
            question = "Which of the following is the capital of France?",
            options = listOf("A. Berlin", "B. Madrid", "C. Paris", "D. Rome"),
        ).toList()

        val answer = (events.last() as McqAiEvent.Answer).answer
        assertEquals("C. Paris", answer.answer)
    }

    @Test
    fun stream_allHostsUnreachable_throws() = runBlocking {
        val thrown = try {
            OllamaMcqClient(
                baseUrl = { "http://127.0.0.1:1" },
                model = { "qwen2.5:7b" },
                client = OkHttpClient.Builder()
                    .connectTimeout(2, TimeUnit.SECONDS)
                    .readTimeout(2, TimeUnit.SECONDS)
                    .build(),
                fallbackBaseUrls = listOf("http://127.0.0.1:1"),
            ).stream("Q?", listOf("A. x", "B. y")).toList()
            null
        } catch (e: Exception) {
            e
        }

        assertTrue(thrown is IOException)
    }

    @Test
    fun stream_modelNotFound_throws() = runBlocking {
        server.enqueue(
            MockResponse.Builder()
                .code(404)
                .body("""{"error":"model 'llama3' not found"}""")
                .build(),
        )

        val thrown = try {
            client().stream("Q?", listOf("A. x", "B. y")).toList()
            null
        } catch (e: Exception) {
            e
        }

        assertTrue(thrown is IOException)
    }

    @Test
    fun stream_sendsCustomKeepAlive() = runBlocking {
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .body(buildString {
                    appendLine("""{"response":"{\"answer\": \"C. Paris\", \"confidence\": 0.98, \"explanation\": \"Paris is the capital of France.\"}","done":false}""")
                    appendLine("""{"response":"","done":true}""")
                })
                .build(),
        )

        val events = OllamaMcqClient(
            baseUrl = { server.url("/").toString() },
            model = { "qwen2.5:7b" },
            client = OkHttpClient(),
            fallbackBaseUrls = emptyList(),
            keepAlive = "-1",
        ).stream("Q?", listOf("A. x", "B. y")).toList()

        assertTrue(events.last() is McqAiEvent.Answer)

        val recorded = server.takeRequest()
        assertTrue(recorded.body?.utf8()?.contains("\"keep_alive\":\"-1\"") == true)
    }

    @Test
    fun stream_readsProvidersAtCallTime_soRuntimeChangesApply() = runBlocking {
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .body(buildString {
                    appendLine("""{"response":"{\"answer\": \"C. Paris\", \"confidence\": 0.98, \"explanation\": \"Paris is the capital of France.\"}","done":false}""")
                    appendLine("""{"response":"","done":true}""")
                })
                .build(),
        )

        baseUrlProvider = { server.url("/").toString() }
        modelProvider = { "gemma:7b" }
        val client = client()

        client.stream("Q?", listOf("A. x", "B. y")).toList()

        val recorded = server.takeRequest()
        assertTrue(recorded.body?.utf8()?.contains("\"model\":\"gemma:7b\"") == true)

        modelProvider = { "qwen2.5:7b" }
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .body(buildString {
                    appendLine("""{"response":"{\"answer\": \"C. Paris\", \"confidence\": 0.98, \"explanation\": \"Paris is the capital of France.\"}","done":false}""")
                    appendLine("""{"response":"","done":true}""")
                })
                .build(),
        )
        client.stream("Q?", listOf("A. x", "B. y")).toList()

        val second = server.takeRequest()
        assertTrue(second.body?.utf8()?.contains("\"model\":\"qwen2.5:7b\"") == true)
    }
}
