package com.exammate.mcq.ai

import java.io.IOException
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

    private fun client(): OllamaMcqClient =
        OllamaMcqClient(
            baseUrl = server.url("/").toString(),
            client = OkHttpClient(),
        )

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
}
