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
    secondary = Color(0xFF00C853),
    tertiary = Color(0xFF72DFFF),
    background = Color.Black,
    surface = Color(0xFF121212),
    surfaceVariant = Color(0xFF1E1E1E),
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onBackground = Color(0xFF00FF00),
    onSurface = Color.White,
    onSurfaceVariant = Color(0xFFB0BEC5),
    outlineVariant = Color(0xFF333333)
)

// Light theme — for accessibility and daytime use
private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF1B5E20), // Dark green for readability
    secondary = Color(0xFF2E7D32),
    tertiary = Color(0xFF0277BD),
    background = Color(0xFFF5F5F5),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE8F5E9),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color(0xFF1B1B1B),
    onSurface = Color(0xFF1B1B1B),
    onSurfaceVariant = Color(0xFF555555),
    outlineVariant = Color(0xFFCCCCCC)
)

object SophiaThemeColors {
    val statusGreen: Color
        @Composable get() = if (isSystemInDarkTheme()) Color(0xFF00FF00) else Color(0xFF2E7D32)
    val statusRed: Color
        @Composable get() = if (isSystemInDarkTheme()) Color(0xFFFF5252) else Color(0xFFC62828)
    val statusYellow: Color
        @Composable get() = if (isSystemInDarkTheme()) Color(0xFFFFD166) else Color(0xFFE65100)
    val statusBlue: Color
        @Composable get() = if (isSystemInDarkTheme()) Color(0xFF72DFFF) else Color(0xFF0277BD)
    val cardContainer: Color
        @Composable get() = if (isSystemInDarkTheme()) Color(0xFF1E1E1E) else Color(0xFFFFFFFF)
}

@Composable
fun SophiaOpsTheme(
    useDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
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

