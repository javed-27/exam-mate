package com.exammate.theory.ai

import kotlinx.coroutines.flow.Flow

fun interface TheoryAiClient {
    fun stream(question: String): Flow<String>
}
