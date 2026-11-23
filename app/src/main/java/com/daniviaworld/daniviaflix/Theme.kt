package com.daniviaworld.daniviaflix

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Bg = Color(0xFF0B0B14)
val Surface2 = Color(0xFF15151F)
val Amber = Color(0xFFFFB547)
val Coral = Color(0xFFFF4D6D)
val Violet = Color(0xFF7B2FF7)
val TextMuted = Color(0xFFA9A9BC)

@Composable
fun DaniviaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Amber,
            onPrimary = Color(0xFF1A1000),
            secondary = Coral,
            tertiary = Violet,
            background = Bg,
            surface = Surface2,
            onBackground = Color.White,
            onSurface = Color.White,
        ),
        content = content,
    )
}
