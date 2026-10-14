package com.example.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class AppColors(
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val inputBg: Color,
    val border: Color,
    val navyHeader: Color,
    val primaryBlue: Color,
    val primaryBlueDark: Color,
    val primaryBlueLight: Color,
    val secondaryBlueContainer: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val success: Color,
    val successBg: Color,
    val error: Color,
    val errorBg: Color,
    val warning: Color,
    val warningBg: Color,
    val isDark: Boolean
)

val LightAppColors = AppColors(
    background = SleekLightBackground,
    surface = SleekLightSurface,
    surfaceVariant = SleekLightSurfaceVariant,
    inputBg = SleekLightInputBg,
    border = SleekLightBorder,
    navyHeader = SleekLightNavyHeader,
    primaryBlue = SleekLightPrimaryBlue,
    primaryBlueDark = SleekLightPrimaryBlueDark,
    primaryBlueLight = SleekLightPrimaryBlueLight,
    secondaryBlueContainer = SleekLightSecondaryBlueContainer,
    textPrimary = SleekLightTextPrimary,
    textSecondary = SleekLightTextSecondary,
    textMuted = SleekLightTextMuted,
    success = SleekLightSuccess,
    successBg = SleekLightSuccessBg,
    error = SleekLightError,
    errorBg = SleekLightErrorBg,
    warning = SleekLightWarning,
    warningBg = SleekLightWarningBg,
    isDark = false
)

val DarkAppColors = AppColors(
    background = SleekDarkBackground,
    surface = SleekDarkSurface,
    surfaceVariant = SleekDarkSurfaceVariant,
    inputBg = SleekDarkInputBg,
    border = SleekDarkBorder,
    navyHeader = SleekDarkNavyHeader,
    primaryBlue = SleekDarkPrimaryBlue,
    primaryBlueDark = SleekDarkPrimaryBlueDark,
    primaryBlueLight = SleekDarkPrimaryBlueLight,
    secondaryBlueContainer = SleekDarkSecondaryBlueContainer,
    textPrimary = SleekDarkTextPrimary,
    textSecondary = SleekDarkTextSecondary,
    textMuted = SleekDarkTextMuted,
    success = SleekDarkSuccess,
    successBg = SleekDarkSuccessBg,
    error = SleekDarkError,
    errorBg = SleekDarkErrorBg,
    warning = SleekDarkWarning,
    warningBg = SleekDarkWarningBg,
    isDark = true
)

val LocalAppColors = staticCompositionLocalOf { LightAppColors }

object AppTheme {
    val colors: AppColors
        @Composable
        @ReadOnlyComposable
        get() = LocalAppColors.current
}
