package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF818CF8),         // Vibrant indigo/violet (iOS/M3 fusion)
    secondary = Color(0xFF34D399),       // Mint emerald green
    tertiary = Color(0xFFFB7185),         // Rose red
    background = Color(0xFF080810),       // Deep Space Black
    surface = Color(0xFF121222),          // Translucent Dark Slate
    surfaceVariant = Color(0xFF1C1C30),   // Lighter space slate
    onPrimary = Color(0xFFFFFFFF),
    onSecondary = Color(0xFF0F172A),
    onTertiary = Color(0xFFFFFFFF),
    onBackground = Color(0xFFF8FAFC),
    onSurface = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF94A3B8),
    primaryContainer = Color(0xFF312E81),
    onPrimaryContainer = Color(0xFFE0E7FF),
    secondaryContainer = Color(0xFF065F46),
    onSecondaryContainer = Color(0xFFD1FAE5)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF4F46E5),         // Vivid active indigo
    secondary = Color(0xFF10B981),       // Active green
    tertiary = Color(0xFFF43F5E),         // Dynamic rose red
    background = Color(0xFFF4F6FA),       // Light Ice Screen
    surface = Color(0xFFFFFFFF),          // Pure iOS White glass
    surfaceVariant = Color(0xFFE2E8F0),   // Light separator slate
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF0F172A),
    onSurface = Color(0xFF1E293B),
    onSurfaceVariant = Color(0xFF64748B),
    primaryContainer = Color(0xFFE0E7FF),
    onPrimaryContainer = Color(0xFF312E81),
    secondaryContainer = Color(0xFFD1FAE5),
    onSecondaryContainer = Color(0xFF065F46)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
