package ru.cherepokivan.standalonevoicechat.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF7AA2F7),
    secondary = Color(0xFF73DACA),
    surface = Color(0xFF182131),
    background = Color(0xFF0D141F),
    onSurface = Color(0xFFEAF0F8),
    onBackground = Color(0xFFEAF0F8)
)

@Composable
fun StandaloneVoiceChatTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkScheme, content = content)
}
