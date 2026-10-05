package com.stickerya.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Brand = Color(0xFF6C3BFF)
val BrandPink = Color(0xFFFF2D55)
val WhatsAppGreen = Color(0xFF25D366)
val WhatsAppBusiness = Color(0xFF128C7E)

private val Light = lightColorScheme(
    primary = Brand,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE9E1FF),
    onPrimaryContainer = Color(0xFF22005D),
    secondary = BrandPink,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFD9DE),
    onSecondaryContainer = Color(0xFF3F0010),
    tertiary = WhatsAppBusiness,
    background = Color(0xFFF7F5FF),
    surface = Color(0xFFF7F5FF),
    surfaceContainer = Color(0xFFEFEBFA),
    surfaceContainerLow = Color(0xFFF3F0FC),
    surfaceContainerHigh = Color(0xFFE9E5F5),
    surfaceContainerHighest = Color(0xFFE3DFF0),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFCDBDFF),
    onPrimary = Color(0xFF390094),
    primaryContainer = Color(0xFF5221E0),
    onPrimaryContainer = Color(0xFFE9E1FF),
    secondary = Color(0xFFFFB1BE),
    onSecondary = Color(0xFF660023),
    secondaryContainer = Color(0xFF8F0034),
    onSecondaryContainer = Color(0xFFFFD9DE),
    tertiary = Color(0xFF7FD8C6),
    background = Color(0xFF14111C),
    surface = Color(0xFF14111C),
    surfaceContainer = Color(0xFF211D2B),
    surfaceContainerLow = Color(0xFF1C1825),
    surfaceContainerHigh = Color(0xFF2B2736),
    surfaceContainerHighest = Color(0xFF363141),
)

@Composable
fun StickerYaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
