package com.sophia.ops.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Dark theme — Matrix-style green-on-black (original theme)
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF00FF00), // Matrix Green
    secondary = Color(0xFF003300),
    background = Color.Black,
    surface = Color.Black,
    onPrimary = Color.Black,
    onSecondary = Color.Green,
    onBackground = Color.Green,
    onSurface = Color.Green
)

// Light theme — for accessibility and daytime use
private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF1B5E20), // Dark green for readability
    secondary = Color(0xFF2E7D32),
    background = Color(0xFFF5F5F5),
    surface = Color(0xFFFFFFFF),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color(0xFF1B1B1B),
    onSurface = Color(0xFF1B1B1B)
)

@Composable
fun SophiaOpsTheme(
    useDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = androidx.compose.ui.platform.LocalContext.current
            if (useDarkTheme) DarkColorScheme
            else LightColorScheme
        }
        useDarkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
