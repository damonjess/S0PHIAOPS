package com.sophia.ops.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Sophia Ops design tokens.
 *
 * Dark-first palette for a modern security-tool aesthetic:
 * deep blue-black surfaces, a cyan primary accent, and semantic
 * status colors that are shared across every screen.
 */

private val SophiaDarkColorScheme = darkColorScheme(
    primary = Color(0xFF4CC9F0),
    onPrimary = Color(0xFF04141C),
    primaryContainer = Color(0xFF0E3B4E),
    onPrimaryContainer = Color(0xFFB8E9FF),
    secondary = Color(0xFF8DA4F8),
    onSecondary = Color(0xFF0B1230),
    secondaryContainer = Color(0xFF232C55),
    onSecondaryContainer = Color(0xFFDCE3FF),
    tertiary = Color(0xFFF2B45C),
    onTertiary = Color(0xFF2A1A00),
    tertiaryContainer = Color(0xFF4A3100),
    onTertiaryContainer = Color(0xFFFFDDB8),
    error = Color(0xFFFF6B7A),
    onError = Color(0xFF3A0008),
    errorContainer = Color(0xFF5C1220),
    onErrorContainer = Color(0xFFFFD9DC),
    background = Color(0xFF0A0E14),
    onBackground = Color(0xFFE4EBF3),
    surface = Color(0xFF0F141B),
    onSurface = Color(0xFFE4EBF3),
    surfaceVariant = Color(0xFF18202B),
    onSurfaceVariant = Color(0xFF9AACC0),
    surfaceContainer = Color(0xFF131A23),
    surfaceContainerHigh = Color(0xFF1A2330),
    outline = Color(0xFF3A4757),
    outlineVariant = Color(0xFF25303D),
    inverseSurface = Color(0xFFE4EBF3),
    inverseOnSurface = Color(0xFF0F141B),
)

private val SophiaLightColorScheme = lightColorScheme(
    primary = Color(0xFF00698B),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFC4E8FF),
    onPrimaryContainer = Color(0xFF00202B),
    secondary = Color(0xFF4A5DB8),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDFE3FF),
    onSecondaryContainer = Color(0xFF0B1230),
    tertiary = Color(0xFF8A5A00),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDFB8),
    onTertiaryContainer = Color(0xFF2A1A00),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF5F7FA),
    onBackground = Color(0xFF111720),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF111720),
    surfaceVariant = Color(0xFFEDF1F6),
    onSurfaceVariant = Color(0xFF44566B),
    surfaceContainer = Color(0xFFF0F3F8),
    surfaceContainerHigh = Color(0xFFE9EDF4),
    outline = Color(0xFFB9C4D1),
    outlineVariant = Color(0xFFD8E0EA),
    inverseSurface = Color(0xFF111720),
    inverseOnSurface = Color(0xFFF5F7FA),
)

/** True while the app is rendering with the dark (default) palette. */
val LocalSophiaDark = compositionLocalOf { true }

/** Typography tuned for a dense, legible security console. */
private val SophiaTypography = Typography(
    headlineMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.3).sp,
    ),
    headlineSmall = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.2).sp,
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        letterSpacing = 0.4.sp,
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        letterSpacing = 0.8.sp,
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        letterSpacing = 0.4.sp,
    ),
)

/**
 * Semantic status colors shared by every screen.
 * All values adapt to the active theme (dark-first by default).
 */
@Immutable
object SophiaThemeColors {
    val statusGreen: Color
        @Composable get() = if (LocalSophiaDark.current) Color(0xFF45F08A) else Color(0xFF1B7A4B)
    val statusRed: Color
        @Composable get() = if (LocalSophiaDark.current) Color(0xFFFF6B7A) else Color(0xFFC62828)
    val statusYellow: Color
        @Composable get() = if (LocalSophiaDark.current) Color(0xFFFFC24B) else Color(0xFFB26A00)
    val statusOrange: Color
        @Composable get() = if (LocalSophiaDark.current) Color(0xFFFFA76B) else Color(0xFFB85100)
    val statusBlue: Color
        @Composable get() = if (LocalSophiaDark.current) Color(0xFF7CC7FF) else Color(0xFF0277BD)
    val cardContainer: Color
        @Composable get() = if (LocalSophiaDark.current) Color(0xFF18202B) else Color(0xFFFFFFFF)
}

@Composable
fun SophiaOpsTheme(
    useDarkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (useDarkTheme) SophiaDarkColorScheme else SophiaLightColorScheme

    CompositionLocalProvider(LocalSophiaDark provides useDarkTheme) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = SophiaTypography,
            content = content
        )
    }
}
