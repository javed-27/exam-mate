package com.exammate.mcq.ai

import com.exammate.mcq.McqAnswer

interface McqAiClient {
    suspend fun solve(question: String, options: List<String>): McqAnswer
}
