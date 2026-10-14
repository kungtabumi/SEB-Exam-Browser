package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

private val SleekLightColorScheme = lightColorScheme(
    primary = LightAppColors.primaryBlue,
    onPrimary = LightAppColors.surface,
    primaryContainer = LightAppColors.secondaryBlueContainer,
    onPrimaryContainer = LightAppColors.navyHeader,
    secondary = LightAppColors.navyHeader,
    onSecondary = LightAppColors.surface,
    background = LightAppColors.background,
    onBackground = LightAppColors.textPrimary,
    surface = LightAppColors.surface,
    onSurface = LightAppColors.textPrimary,
    surfaceVariant = LightAppColors.surfaceVariant,
    onSurfaceVariant = LightAppColors.textSecondary,
    outline = LightAppColors.border,
    error = LightAppColors.error,
    errorContainer = LightAppColors.errorBg
)

@Composable
fun MyApplicationTheme(
    themeMode: ThemeMode = ThemeMode.LIGHT,
    content: @Composable () -> Unit,
) {
    // Mode gelap dihapus - aplikasi selalu menggunakan tema terang (Light Mode) yang konsisten dan kontras tinggi
    val appColors = LightAppColors
    val colorScheme = SleekLightColorScheme

    CompositionLocalProvider(LocalAppColors provides appColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
