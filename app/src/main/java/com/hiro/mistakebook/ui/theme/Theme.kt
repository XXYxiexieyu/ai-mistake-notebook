package com.hiro.mistakebook.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView

val Ink = Color(0xFF19332D)
val Green = Color(0xFF176B57)
val GreenLight = Color(0xFFD8EEE7)
val Amber = Color(0xFFB8740A)
val Paper = Color(0xFFF6FAF8)
val Surface = Color(0xFFFFFFFF)
val Muted = Color(0xFF65736E)
val Red = Color(0xFFB33A3A)

private val LightColors = lightColorScheme(
    primary = Green,
    onPrimary = Color.White,
    primaryContainer = GreenLight,
    onPrimaryContainer = Ink,
    secondary = Amber,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE8BD),
    onSecondaryContainer = Color(0xFF4B2D00),
    background = Paper,
    onBackground = Ink,
    surface = Surface,
    onSurface = Ink,
    surfaceVariant = Color(0xFFEAF0ED),
    onSurfaceVariant = Muted,
    error = Red,
    outline = Color(0xFFB7C4BF)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7FD5B9),
    onPrimary = Color(0xFF04382C),
    primaryContainer = Color(0xFF135444),
    onPrimaryContainer = Color(0xFFC4F1E2),
    secondary = Color(0xFFFFC56A),
    background = Color(0xFF101714),
    onBackground = Color(0xFFE4EFEB),
    surface = Color(0xFF17211D),
    onSurface = Color(0xFFE4EFEB),
    surfaceVariant = Color(0xFF26332E),
    onSurfaceVariant = Color(0xFFB8C8C2),
    error = Color(0xFFFFB4AB)
)

@Composable
fun MistakeBookTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = colors.background.toArgb()
            window.navigationBarColor = colors.background.toArgb()
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }
    }
    MaterialTheme(
        colorScheme = colors,
        content = content
    )
}
