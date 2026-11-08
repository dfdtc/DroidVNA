/*
 * LibreVNA Mobile - native Android client for LibreVNA / Harogic B60.
 * Copyright (C) 2026 The LibreVNA Mobile Authors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.librevna.mobile.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

val VnaBackground = Color(0xFF0B1116)
val VnaSurface = Color(0xFF121B22)
val VnaSurfaceElevated = Color(0xFF19242D)
val VnaPrimary = Color(0xFF31D6C4)
val VnaSecondary = Color(0xFFFFB74D)
val VnaError = Color(0xFFFF6B6B)
val VnaOutline = Color(0xFF2B3A45)
val VnaText = Color(0xFFE6EEF3)
val VnaTextMuted = Color(0xFF8AA0AE)

private val VnaColors = darkColorScheme(
    primary = VnaPrimary,
    onPrimary = Color(0xFF00201C),
    primaryContainer = Color(0xFF0E4B45),
    onPrimaryContainer = Color(0xFFB8FFF5),
    secondary = VnaSecondary,
    onSecondary = Color(0xFF2B1A00),
    error = VnaError,
    background = VnaBackground,
    onBackground = VnaText,
    surface = VnaSurface,
    onSurface = VnaText,
    surfaceVariant = VnaSurfaceElevated,
    onSurfaceVariant = VnaTextMuted,
    outline = VnaOutline,
)

@Composable
fun LibreVnaTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
        }
    }
    MaterialTheme(colorScheme = VnaColors, content = content)
}