package io.github.oondraa.opensisakhk.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = EmberDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDBCE),
    onPrimaryContainer = Color(0xFF3A0B00),
    secondary = Color(0xFF77574B),
    background = Paper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = Color(0xFFEDE8E1),
    onSurfaceVariant = Color(0xFF5E5A57),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFCFAF7),
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFF1EDE7),
    surfaceContainerHighest = Color(0xFFEBE6DF),
    outlineVariant = Color(0x1F0E0E10),
)

private val DarkColors = darkColorScheme(
    primary = EmberLight,
    onPrimary = Color(0xFF3A0B00),
    primaryContainer = Color(0xFF5C1A00),
    onPrimaryContainer = Color(0xFFFFDBCE),
    secondary = Color(0xFFE7BDB0),
    background = Ink,
    onBackground = Paper,
    surface = Ink,
    onSurface = Paper,
    surfaceVariant = Color(0xFF2A282C),
    onSurfaceVariant = Color(0xFFA9A5A0),
    surfaceContainerLowest = Color(0xFF0A0A0B),
    surfaceContainerLow = Color(0xFF151417),
    surfaceContainer = InkSurface,
    surfaceContainerHigh = Color(0xFF232226),
    surfaceContainerHighest = Color(0xFF2D2C30),
    outlineVariant = Color(0x1FF6F3EE),
)

/** Colours the Material scheme has no slot for. */
data class Extra(val green: Color)

val LocalExtra = staticCompositionLocalOf { Extra(GreenLight) }

/** `dynamicColor`: Android 12+ takes the colours from the wallpaper (Material You); older phones get ink and ember. */
@Composable
fun OpenSISAKHKTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    CompositionLocalProvider(LocalExtra provides Extra(if (darkTheme) GreenDark else GreenLight)) {
        MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
    }
}
