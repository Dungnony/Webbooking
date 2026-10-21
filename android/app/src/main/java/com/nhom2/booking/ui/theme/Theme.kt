package com.nhom2.booking.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Same palette as the web app.
val BookingBlue = Color(0xFF006CE4)
val BookingDarkBlue = Color(0xFF003B95)
val BookingYellow = Color(0xFFFEBB02)
val SuccessGreen = Color(0xFF2E7D32)
val SuccessGreenContainer = Color(0xFFE8F5E9)

private val LightColors = lightColorScheme(
    primary = BookingBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEBF3FF),
    onPrimaryContainer = BookingDarkBlue,
    secondary = BookingDarkBlue,
    onSecondary = Color.White,
    tertiary = BookingYellow,
    onTertiary = Color(0xFF1A1A1A),
    background = Color.White,
    surface = Color.White,
    onSurface = Color(0xFF1A1A1A),
    onSurfaceVariant = Color(0xFF595959),
)

@Composable
fun BookingTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = LightColors, content = content)
}
