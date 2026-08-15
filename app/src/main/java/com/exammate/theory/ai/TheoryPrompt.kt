package com.exammate.theory.ai

fun buildTheoryPrompt(question: String): String = buildString {
    appendLine("You are a helpful study assistant answering a descriptive exam question for a student.")
    appendLine("Question:")
    appendLine(question)
    appendLine()
    appendLine("Write a clear and complete answer using short paragraphs and bullet points where helpful.")
}.trim()
