package com.exammate.mcq.ai

import java.io.IOException
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
    fun solve_returnsParsedAnswer() = runBlocking {
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .body(
                    """{"model":"llama3","response":"{\"answer\": \"C. Paris\", \"confidence\": 0.98, \"explanation\": \"Paris is the capital of France.\"}","done":true}""",
                )
                .build(),
        )

        val answer = client().solve(
            question = "Which of the following is the capital of France?",
            options = listOf("A. Berlin", "B. Madrid", "C. Paris", "D. Rome"),
        )

        assertEquals("C. Paris", answer.answer)
        assertEquals(0.98, answer.confidence, 0.0001)
        assertEquals("Paris is the capital of France.", answer.explanation)
        assertEquals("Which of the following is the capital of France?", answer.question)

        val recorded = server.takeRequest()
        assertEquals("/api/generate", recorded.url.encodedPath)
        assertTrue(recorded.body?.utf8()?.contains("\"stream\":false") == true)
        assertTrue(recorded.body?.utf8()?.contains("\"model\":\"llama3\"") == true)
    }

    @Test
    fun solve_httpFailure_throws() = runBlocking {
        server.enqueue(MockResponse.Builder().code(500).body("boom").build())

        val thrown = try {
            client().solve("Q?", listOf("A. x", "B. y"))
            null
        } catch (e: Exception) {
            e
        }

        assertTrue(thrown is IOException)
    }

    @Test
    fun solve_malformedModelResponse_throws() = runBlocking {
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .body("""{"response":"definitely not json"}""")
                .build(),
        )

        val thrown = try {
            client().solve("Q?", listOf("A. x", "B. y"))
            null
        } catch (e: Exception) {
            e
        }

        assertTrue(thrown is Exception)
    }
}
