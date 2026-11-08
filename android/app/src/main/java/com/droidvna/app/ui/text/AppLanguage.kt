/*
 * DroidVNA - native Android client for LibreVNA / Harogic B60.
 * Copyright (C) 2026 The DroidVNA Authors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.droidvna.app.ui.text

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import java.util.Locale

/** UI language selected in App settings. */
enum class AppLanguage(val key: String, val label: String) {
    SYSTEM("system", "跟随系统"),
    CHINESE("zh", "中文"),
    ENGLISH("en", "English");

    companion object {
        fun fromKey(key: String?): AppLanguage = entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}

/** Resolve SYSTEM against the device locale. */
fun AppLanguage.resolved(): AppLanguage {
    if (this != AppLanguage.SYSTEM) return this
    return if (Locale.getDefault().language.startsWith("zh")) AppLanguage.CHINESE else AppLanguage.ENGLISH
}

val LocalAppLanguage = compositionLocalOf { AppLanguage.CHINESE }

/** Translate UI text for the current composition. */
@Composable
fun tr(text: String): String = translate(text, LocalAppLanguage.current)

/** Translate UI text. Unknown strings are returned unchanged (or partially translated). */
fun translate(text: String, language: AppLanguage): String {
    if (language != AppLanguage.ENGLISH || text.isEmpty()) return text
    EnglishStrings.exact[text]?.let { return it }
    // Device logs and other very long blocks are technical data, not UI copy.
    if (text.length > 600) return text
    return EnglishStrings.translateWords(text)
}
