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
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline
)

private val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    secondary = LightSecondary,
    onSecondary = LightOnSecondary,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline
)

@Composable
fun MyApplicationTheme(
  themeMode: String = "SYSTEM",
  amoledDark: Boolean = false,
  highContrast: Boolean = false,
  fontScale: Float = 1.0f,
  // Custom design themes are the primary visual identity
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val darkTheme = when (themeMode) {
    "LIGHT" -> false
    "DARK" -> true
    else -> isSystemInDarkTheme()
  }

  var colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  if (darkTheme && amoledDark) {
    colorScheme = colorScheme.copy(
      background = Color.Black,
      surface = Color.Black,
      surfaceVariant = Color(0xFF121212),
      onBackground = Color.White,
      onSurface = Color.White
    )
  }

  if (highContrast) {
    colorScheme = colorScheme.copy(
      primary = if (darkTheme) Color(0xFFF3E5F5) else Color(0xFF21005D),
      secondary = if (darkTheme) Color(0xFFE8EAF6) else Color(0xFF1A237E)
    )
  }

  // Real-time responsive typography scaling
  val scaledTypography = Typography.copy(
    displayLarge = Typography.displayLarge.copy(fontSize = Typography.displayLarge.fontSize * fontScale, lineHeight = Typography.displayLarge.lineHeight * fontScale),
    displayMedium = Typography.displayMedium.copy(fontSize = Typography.displayMedium.fontSize * fontScale, lineHeight = Typography.displayMedium.lineHeight * fontScale),
    displaySmall = Typography.displaySmall.copy(fontSize = Typography.displaySmall.fontSize * fontScale, lineHeight = Typography.displaySmall.lineHeight * fontScale),
    headlineLarge = Typography.headlineLarge.copy(fontSize = Typography.headlineLarge.fontSize * fontScale, lineHeight = Typography.headlineLarge.lineHeight * fontScale),
    headlineMedium = Typography.headlineMedium.copy(fontSize = Typography.headlineMedium.fontSize * fontScale, lineHeight = Typography.headlineMedium.lineHeight * fontScale),
    headlineSmall = Typography.headlineSmall.copy(fontSize = Typography.headlineSmall.fontSize * fontScale, lineHeight = Typography.headlineSmall.lineHeight * fontScale),
    titleLarge = Typography.titleLarge.copy(fontSize = Typography.titleLarge.fontSize * fontScale, lineHeight = Typography.titleLarge.lineHeight * fontScale),
    titleMedium = Typography.titleMedium.copy(fontSize = Typography.titleMedium.fontSize * fontScale, lineHeight = Typography.titleMedium.lineHeight * fontScale),
    titleSmall = Typography.titleSmall.copy(fontSize = Typography.titleSmall.fontSize * fontScale, lineHeight = Typography.titleSmall.lineHeight * fontScale),
    bodyLarge = Typography.bodyLarge.copy(fontSize = Typography.bodyLarge.fontSize * fontScale, lineHeight = Typography.bodyLarge.lineHeight * fontScale),
    bodyMedium = Typography.bodyMedium.copy(fontSize = Typography.bodyMedium.fontSize * fontScale, lineHeight = Typography.bodyMedium.lineHeight * fontScale),
    bodySmall = Typography.bodySmall.copy(fontSize = Typography.bodySmall.fontSize * fontScale, lineHeight = Typography.bodySmall.lineHeight * fontScale),
    labelLarge = Typography.labelLarge.copy(fontSize = Typography.labelLarge.fontSize * fontScale, lineHeight = Typography.labelLarge.lineHeight * fontScale),
    labelMedium = Typography.labelMedium.copy(fontSize = Typography.labelMedium.fontSize * fontScale, lineHeight = Typography.labelMedium.lineHeight * fontScale),
    labelSmall = Typography.labelSmall.copy(fontSize = Typography.labelSmall.fontSize * fontScale, lineHeight = Typography.labelSmall.lineHeight * fontScale)
  )

  MaterialTheme(colorScheme = colorScheme, typography = scaledTypography, shapes = Shapes, content = content)
}
