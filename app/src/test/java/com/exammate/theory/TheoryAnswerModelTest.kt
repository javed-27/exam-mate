package com.exammate.theory

import com.exammate.theory.ai.TheoryAiClient
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TheoryAnswerModelTest {

    @Test
    fun initialState_isIdle() = runTest {
        val model = model()

        assertEquals(TheoryAnswerState.Idle, model.state.value)
    }

    @Test
    fun generate_streamsPartialTextAndReachesAnswer() = runTest {
        val release = CompletableDeferred<Unit>()
        val model = model(stream = {
            flow {
                emit("The ")
                release.await()
                emit("human heart pumps blood.")
            }
        })

        model.generate("Explain the human heart.")
        runCurrent()

        assertEquals(TheoryAnswerState.Generating("The "), model.state.value)

        release.complete(Unit)
        advanceUntilIdle()

        assertEquals(TheoryAnswerState.Answer("The human heart pumps blood."), model.state.value)
    }

    @Test
    fun generate_blankQuestion_staysIdle() = runTest {
        var calls = 0
        val model = model(stream = {
            calls++
            flow { emit("answer") }
        })

        model.generate("   ")
        advanceUntilIdle()

        assertEquals(TheoryAnswerState.Idle, model.state.value)
        assertEquals(0, calls)
    }

    @Test
    fun generate_emptyStream_isError() = runTest {
        val model = model(stream = { flow {} })

        model.generate("Question")
        advanceUntilIdle()

        assertEquals(TheoryAnswerState.Error(ANSWER_FAILURE_MESSAGE), model.state.value)
    }

    @Test
    fun generate_streamThrows_isErrorWithMessage() = runTest {
        val model = model(stream = { throw IOException("boom") })

        model.generate("Question")
        advanceUntilIdle()

        assertEquals(TheoryAnswerState.Error("boom"), model.state.value)
    }

    @Test
    fun generateAfterAnswer_restartsGeneration() = runTest {
        val model = model(stream = { flow { emit("first answer") } })

        model.generate("Q1")
        advanceUntilIdle()
        assertEquals(TheoryAnswerState.Answer("first answer"), model.state.value)

        model.generate("Q2")
        assertEquals(TheoryAnswerState.Generating(""), model.state.value)
        advanceUntilIdle()
        assertEquals(TheoryAnswerState.Answer("first answer"), model.state.value)
    }

    @Test
    fun generateWhileGenerating_cancelsPrevious() = runTest {
        val firstStarted = CompletableDeferred<Unit>()
        val model = model(stream = { question ->
            flow {
                if (question == "Q1") {
                    firstStarted.complete(Unit)
                    firstStarted.await()
                    emit("old answer")
                } else {
                    emit("new answer")
                }
            }
        })

        model.generate("Q1")
        runCurrent()
        model.generate("Q2")
        runCurrent()

        assertEquals(TheoryAnswerState.Answer("new answer"), model.state.value)

        firstStarted.complete(Unit)
        advanceUntilIdle()
        assertEquals(TheoryAnswerState.Answer("new answer"), model.state.value)
    }

    @Test
    fun reset_returnsToIdle() = runTest {
        val model = model(stream = { flow { emit("answer") } })

        model.generate("Question")
        advanceUntilIdle()
        assertEquals(TheoryAnswerState.Answer("answer"), model.state.value)

        model.reset()

        assertEquals(TheoryAnswerState.Idle, model.state.value)
    }

    @Test
    fun reset_duringGeneration_staysIdle() = runTest {
        val release = CompletableDeferred<Unit>()
        val model = model(stream = {
            flow {
                emit("partial")
                release.await()
            }
        })

        model.generate("Question")
        runCurrent()
        model.reset()
        release.complete(Unit)
        advanceUntilIdle()

        assertEquals(TheoryAnswerState.Idle, model.state.value)
    }

    @Test
    fun close_preventsStateEmissionFromInFlightGeneration() = runTest {
        val started = CompletableDeferred<Unit>()
        val released = CompletableDeferred<Unit>()
        val model = model(stream = {
            flow {
                started.complete(Unit)
                released.await()
                emit("late")
            }
        })

        model.generate("Question")
        runCurrent()
        model.close()
        released.complete(Unit)
        advanceUntilIdle()

        assertEquals(TheoryAnswerState.Generating(""), model.state.value)
    }

    private fun TestScope.model(
        stream: (String) -> Flow<String> = { flow { emit("answer") } },
    ): TheoryAnswerModel = TheoryAnswerModel(
        aiClient = TheoryAiClient { question -> stream(question) },
        dispatcher = testDispatcher(),
    )

    private fun TestScope.testDispatcher(): CoroutineDispatcher =
        StandardTestDispatcher(testScheduler)
}
