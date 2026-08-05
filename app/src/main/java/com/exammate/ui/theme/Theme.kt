package com.exammate.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = PrimaryBlue,
    secondary = SecondaryPurple,
    tertiary = SuccessGreen,
    error = ErrorRed,
    background = BackgroundWhite,
    surface = SurfaceLightGray,
)

@Composable
fun ExamMateTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = Typography,
        shapes = Shapes,
        content = content,
    )
}
