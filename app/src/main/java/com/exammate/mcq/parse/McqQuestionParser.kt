package com.exammate.mcq.parse

data class ParsedMcq(
    val question: String,
    val options: List<String>,
)

object McqQuestionParser {

    private val letteredOption = Regex("""^([A-Ha-h])[:.)]\s+(.+)$""")
    private val numberedOption = Regex("""^(\d{1,2})[:.)]\s+(.+)$""")
    private val bulletedOption = Regex("""^([•◦▪▫‣»·*\-–—])\s*(.+)$""")
    private val radioOption = Regex("""^[Oo]\s+[A-Za-z].+$""")
    private val chromeLine = Regex(
        """(?i)^(back|next|previous|prev|pick \d+|select \d+|choose \d+|question \d+|save|cancel|skip|submit|loading|mark(ed)? for review|descriptive part|type\b|(e\s+)?attach|scan(ning)? the qr|white plain sheets|handwritten answer sheets|write your answer|esc|f\d{1,2})\b""",
    )
    private val navMarker = Regex("""[<>«»]""")
    private val timerToken = Regex("""\b\d{1,2}:\d{2}(?::\d{2})?\b""")

    fun normalize(text: String): String =
        text.lines()
            .map { it.trim().replace(Regex("""\s+"""), " ") }
            .joinToString("\n")
            .trim()

    fun sanitize(text: String): String =
        normalize(text.replace(timerToken, ""))

    fun parse(ocrText: String): ParsedMcq? {
        val normalized = sanitize(ocrText)
        if (normalized.isEmpty()) return null
        val lines = normalized.split("\n")

        val selected = listOf(
            optionLines(lines, letteredOption),
            optionLines(lines, numberedOption),
            optionLines(lines, bulletedOption),
            optionLines(lines, radioOption),
        ).firstOrNull { it.size >= 2 } ?: return null

        val firstOptionIndex = selected.first().index
        val stem = stripLeadingChrome(lines.take(firstOptionIndex))
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

    private fun stripLeadingChrome(lines: List<String>): List<String> =
        lines.dropWhile { line ->
            line.contains(navMarker) || chromeLine.find(line)?.range?.first == 0
        }
}
