package com.example.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

// Light Colors
val SleekLightBackground = Color(0xFFF4F6FB)
val SleekLightSurface = Color(0xFFFFFFFF)
val SleekLightSurfaceVariant = Color(0xFFEEF2F8)
val SleekLightInputBg = Color(0xFFF0F4FA)
val SleekLightBorder = Color(0xFFE2E8F0)

val SleekLightNavyHeader = Color(0xFF0F172A)
val SleekLightPrimaryBlue = Color(0xFF1D4ED8)
val SleekLightPrimaryBlueDark = Color(0xFF1E40AF)
val SleekLightPrimaryBlueLight = Color(0xFF3B82F6)
val SleekLightSecondaryBlueContainer = Color(0xFFDBEAFE)

val SleekLightTextPrimary = Color(0xFF0F172A)
val SleekLightTextSecondary = Color(0xFF475569)
val SleekLightTextMuted = Color(0xFF94A3B8)

val SleekLightSuccess = Color(0xFF059669)
val SleekLightSuccessBg = Color(0xFFD1FAE5)
val SleekLightError = Color(0xFFDC2626)
val SleekLightErrorBg = Color(0xFFFEE2E2)
val SleekLightWarning = Color(0xFFD97706)
val SleekLightWarningBg = Color(0xFFFEF3C7)

// Dark Colors
val SleekDarkBackground = Color(0xFF0F172A)
val SleekDarkSurface = Color(0xFF1E293B)
val SleekDarkSurfaceVariant = Color(0xFF334155)
val SleekDarkInputBg = Color(0xFF1E293B)
val SleekDarkBorder = Color(0xFF334155)

val SleekDarkNavyHeader = Color(0xFFF8FAFC)
val SleekDarkPrimaryBlue = Color(0xFF3B82F6)
val SleekDarkPrimaryBlueDark = Color(0xFF60A5FA)
val SleekDarkPrimaryBlueLight = Color(0xFF93C5FD)
val SleekDarkSecondaryBlueContainer = Color(0xFF1E3A8A)

val SleekDarkTextPrimary = Color(0xFFF8FAFC)
val SleekDarkTextSecondary = Color(0xFFCBD5E1)
val SleekDarkTextMuted = Color(0xFF64748B)

val SleekDarkSuccess = Color(0xFF10B981)
val SleekDarkSuccessBg = Color(0xFF064E3B)
val SleekDarkError = Color(0xFFEF4444)
val SleekDarkErrorBg = Color(0xFF450A0A)
val SleekDarkWarning = Color(0xFFF59E0B)
val SleekDarkWarningBg = Color(0xFF451A03)

// Backward-compatible dynamic accessors resolving to current active theme palette
val SleekBackground: Color
    @Composable
    @ReadOnlyComposable
    get() = AppTheme.colors.background

val SleekSurface: Color
    @Composable
    @ReadOnlyComposable
    get() = AppTheme.colors.surface

val SleekSurfaceVariant: Color
    @Composable
    @ReadOnlyComposable
    get() = AppTheme.colors.surfaceVariant

val SleekInputBg: Color
    @Composable
    @ReadOnlyComposable
    get() = AppTheme.colors.inputBg

val SleekBorder: Color
    @Composable
    @ReadOnlyComposable
    get() = AppTheme.colors.border

val SleekNavyHeader: Color
    @Composable
    @ReadOnlyComposable
    get() = AppTheme.colors.navyHeader

val SleekPrimaryBlue: Color
    @Composable
    @ReadOnlyComposable
    get() = AppTheme.colors.primaryBlue

val SleekPrimaryBlueDark: Color
    @Composable
    @ReadOnlyComposable
    get() = AppTheme.colors.primaryBlueDark

val SleekPrimaryBlueLight: Color
    @Composable
    @ReadOnlyComposable
    get() = AppTheme.colors.primaryBlueLight

val SleekSecondaryBlueContainer: Color
    @Composable
    @ReadOnlyComposable
    get() = AppTheme.colors.secondaryBlueContainer

val SleekTextPrimary: Color
    @Composable
    @ReadOnlyComposable
    get() = AppTheme.colors.textPrimary

val SleekTextSecondary: Color
    @Composable
    @ReadOnlyComposable
    get() = AppTheme.colors.textSecondary

val SleekTextMuted: Color
    @Composable
    @ReadOnlyComposable
    get() = AppTheme.colors.textMuted

val SleekSuccess: Color
    @Composable
    @ReadOnlyComposable
    get() = AppTheme.colors.success

val SleekSuccessBg: Color
    @Composable
    @ReadOnlyComposable
    get() = AppTheme.colors.successBg

val SleekError: Color
    @Composable
    @ReadOnlyComposable
    get() = AppTheme.colors.error

val SleekErrorBg: Color
    @Composable
    @ReadOnlyComposable
    get() = AppTheme.colors.errorBg

val SleekWarning: Color
    @Composable
    @ReadOnlyComposable
    get() = AppTheme.colors.warning

val SleekWarningBg: Color
    @Composable
    @ReadOnlyComposable
    get() = AppTheme.colors.warningBg
