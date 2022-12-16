package com.zhufucdev.motion_emulator.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorPalette = darkColorScheme(
    primary = Purple200,
    secondary = Purple700,
    tertiary = Teal200
)

private val LightColorPalette = lightColorScheme(
    primary = Purple500,
    secondary = Purple700,
    tertiary = Teal200
)

@Composable
fun MotionEmulatorTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val useDynamic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val colors = when {
        useDynamic && darkTheme -> dynamicDarkColorScheme(LocalContext.current)
        useDynamic && !darkTheme -> dynamicLightColorScheme(LocalContext.current)
        darkTheme -> DarkColorPalette
        else -> LightColorPalette
    }

    MaterialTheme(
        colorScheme = colors,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}