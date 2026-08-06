package com.exammate.mcq.parse

data class ParsedMcq(
    val question: String,
    val options: List<String>,
)

object McqQuestionParser {

    private val letteredOption = Regex("""^([A-Ha-h])[:.)]\s+(.+)$""")
    private val numberedOption = Regex("""^(\d{1,2})[:.)]\s+(.+)$""")

    fun normalize(text: String): String =
        text.lines()
            .map { it.trim().replace(Regex("""\s+"""), " ") }
            .joinToString("\n")
            .trim()

    fun parse(ocrText: String): ParsedMcq? {
        val normalized = normalize(ocrText)
        if (normalized.isEmpty()) return null
        val lines = normalized.split("\n")

        val lettered = optionLines(lines, letteredOption)
        val numbered = optionLines(lines, numberedOption)
        val selected = when {
            lettered.size >= 2 -> lettered
            numbered.size >= 2 -> numbered
            else -> return null
        }

        val firstOptionIndex = selected.first().index
        val stem = lines.take(firstOptionIndex)
            .filter { it.isNotEmpty() }
            .joinToString(" ")
            .trim()
        if (stem.isEmpty()) return null

        return ParsedMcq(
            question = stem,
            options = selected.map { it.line },
        )
    }

    private data class OptionLine(val index: Int, val line: String)

    private fun optionLines(lines: List<String>, regex: Regex): List<OptionLine> =
        lines.mapIndexedNotNull { index, line ->
            if (regex.matches(line)) OptionLine(index, line) else null
        }
}
