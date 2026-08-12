package com.exammate.ui.mcq

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.exammate.mcq.McqAnswer
import com.exammate.mcq.McqAnswerState

internal const val ANSWER_PANEL_TAG = "answer_panel"

@Composable
fun AnswerPanel(
    state: McqAnswerState,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag(ANSWER_PANEL_TAG),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        when (state) {
            McqAnswerState.WaitingForOcr -> StatusText("Waiting for OCR…")
            is McqAnswerState.Unparsed -> UnparsedDebug(state.ocrText)
            is McqAnswerState.Processing -> {
                ProgressRow("Waiting for LLM…")
                state.previous?.let { ReadyContent(it) }
            }
            is McqAnswerState.Streaming -> {
                ProgressRow("Generating answer…")
                if (state.partialText.isNotBlank()) {
                    Text(
                        text = state.partialText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                state.previous?.let { ReadyContent(it) }
            }
            is McqAnswerState.Ready -> ReadyContent(state.answer)
            is McqAnswerState.Error -> ErrorContent(state.message, state.previous)
        }
    }
}

@Composable
private fun ProgressRow(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ErrorContent(message: String, previous: McqAnswer?) {
    Text(
        text = "AI request failed",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.error,
    )
    Text(
        text = message.ifBlank { "The answer server is unreachable. Check your server URL and try again." },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
    previous?.let {
        Text(
            text = "Showing last known answer",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 12.dp),
        )
        ReadyContent(it)
    }
}

@Composable
private fun ReadyContent(answer: McqAnswer) {
    SectionLabel("Question")
    Text(
        text = answer.question,
        style = MaterialTheme.typography.bodyLarge,
    )
    SectionLabel("Answer")
    Text(
        text = answer.answer,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.primary,
    )
    Text(
        text = "Confidence: ${(answer.confidence * 100).toInt()}%",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    SectionLabel("Explanation")
    Text(
        text = answer.explanation,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun UnparsedDebug(ocrText: String) {
    Text(
        text = "Question not recognised",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.error,
    )
    Text(
        text = "No 2+ options found in captured text. Raw OCR for debugging:",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
    Text(
        text = ocrText.ifBlank { "(empty)" },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .padding(top = 8.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(8.dp),
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun StatusText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
