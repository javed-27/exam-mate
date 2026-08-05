package com.exammate.ui.mcq

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

fun isLandscape(maxWidth: Dp, maxHeight: Dp): Boolean = maxWidth > maxHeight

@Composable
fun McqCaptureLayout(
    landscape: Boolean,
    modifier: Modifier = Modifier,
    captureArea: @Composable (Modifier) -> Unit,
    answerArea: @Composable (Modifier) -> Unit,
) {
    if (landscape) {
        Row(modifier = modifier.fillMaxSize()) {
            captureArea(Modifier.weight(0.7f).fillMaxHeight())
            answerArea(Modifier.weight(0.3f).fillMaxHeight())
        }
    } else {
        Column(modifier = modifier.fillMaxSize()) {
            captureArea(Modifier.weight(0.7f).fillMaxWidth())
            answerArea(Modifier.weight(0.3f).fillMaxWidth())
        }
    }
}
