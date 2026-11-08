/*
 * DroidVNA - native Android client for LibreVNA / Harogic B60.
 * Copyright (C) 2026 The DroidVNA Authors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.droidvna.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Stores [PersistedSettings] as JSON in the app's private storage.
 *
 * Writes are atomic (temp file + rename) so a crash or a killed process can never leave a
 * half-written settings file behind. Unreadable files fall back to defaults instead of
 * failing the app start.
 */
class SettingsStore(context: Context) {
    private val file = File(context.filesDir, "settings.json")
    private val tempFile = File(context.filesDir, "settings.json.tmp")

    @Synchronized
    fun load(): PersistedSettings {
        if (!file.exists()) return PersistedSettings()
        return try {
            val root = JSONObject(file.readText())
            val defaults = PersistedSettings()
            val params = root.optJSONArray("enabledParams")?.let { array ->
                buildSet {
                    for (i in 0 until array.length()) {
                        val name = array.optString(i, "")
                        if (name.isNotBlank()) add(name)
                    }
                }
            }
            val markers = root.optJSONArray("markerFreqs")?.let { array ->
                buildList {
                    for (i in 0 until array.length()) {
                        val value = array.optDouble(i, Double.NaN)
                        if (value.isFinite() && value > 0.0) add(value)
                    }
                }
            }
            PersistedSettings(
                language = root.optString("language", "").ifBlank { null },
                logFrequencyAxis = root.optBoolean("logFrequencyAxis", defaults.logFrequencyAxis),
                hideWatermark = root.optBoolean("hideWatermark", defaults.hideWatermark),
                chartMode = root.optString("chartMode", defaults.chartMode),
                enabledParams = params?.takeIf { it.isNotEmpty() } ?: defaults.enabledParams,
                chartAutoY = root.optBoolean("chartAutoY", defaults.chartAutoY),
                markerFreqs = markers ?: defaults.markerFreqs,
                deltaMarkers = root.optBoolean("deltaMarkers", defaults.deltaMarkers),
                startHz = root.optLong("startHz", defaults.startHz),
                stopHz = root.optLong("stopHz", defaults.stopHz),
                points = root.optInt("points", defaults.points),
                ifbwHz = root.optInt("ifbwHz", defaults.ifbwHz),
                powerDbm = root.optDouble("powerDbm", defaults.powerDbm),
                twoPort = root.optBoolean("twoPort", defaults.twoPort),
                averaging = root.optInt("averaging", defaults.averaging),
                logSweep = root.optBoolean("logSweep", defaults.logSweep),
                continuous = root.optBoolean("continuous", defaults.continuous),
                sweepConfigured = root.optBoolean("sweepConfigured", defaults.sweepConfigured),
                limitMinFreqHz = root.optLongOrNull("limitMinFreqHz"),
                limitMaxFreqHz = root.optLongOrNull("limitMaxFreqHz"),
                limitMinIfbwHz = root.optIntOrNull("limitMinIfbwHz"),
                limitMaxIfbwHz = root.optIntOrNull("limitMaxIfbwHz"),
                limitMaxPoints = root.optIntOrNull("limitMaxPoints"),
                limitMinPowerDbm = root.optDoubleOrNull("limitMinPowerDbm"),
                limitMaxPowerDbm = root.optDoubleOrNull("limitMaxPowerDbm"),
                limitNumPorts = root.optIntOrNull("limitNumPorts"),
                topControlsCollapsed = root.optBoolean("topControlsCollapsed", defaults.topControlsCollapsed),
                sideControlsCollapsed = root.optBoolean("sideControlsCollapsed", defaults.sideControlsCollapsed),
                rememberCalibration = root.optBoolean("rememberCalibration", defaults.rememberCalibration),
                calibrationEnabled = root.optBoolean("calibrationEnabled", defaults.calibrationEnabled),
                autoStartSweep = root.optBoolean("autoStartSweep", defaults.autoStartSweep),
            )
        } catch (_: Exception) {
            PersistedSettings()
        }
    }

    @Synchronized
    fun save(settings: PersistedSettings) {
        try {
            val root = JSONObject()
            root.put("version", 2)
            root.put("language", settings.language ?: "")
            root.put("logFrequencyAxis", settings.logFrequencyAxis)
            root.put("hideWatermark", settings.hideWatermark)
            root.put("chartMode", settings.chartMode)
            root.put("enabledParams", JSONArray(settings.enabledParams.toList()))
            root.put("chartAutoY", settings.chartAutoY)
            root.put("markerFreqs", JSONArray(settings.markerFreqs))
            root.put("deltaMarkers", settings.deltaMarkers)
            root.put("startHz", settings.startHz)
            root.put("stopHz", settings.stopHz)
            root.put("points", settings.points)
            root.put("ifbwHz", settings.ifbwHz)
            root.put("powerDbm", settings.powerDbm)
            root.put("twoPort", settings.twoPort)
            root.put("averaging", settings.averaging)
            root.put("logSweep", settings.logSweep)
            root.put("continuous", settings.continuous)
            root.put("sweepConfigured", settings.sweepConfigured)
            settings.limitMinFreqHz?.let { root.put("limitMinFreqHz", it) }
            settings.limitMaxFreqHz?.let { root.put("limitMaxFreqHz", it) }
            settings.limitMinIfbwHz?.let { root.put("limitMinIfbwHz", it) }
            settings.limitMaxIfbwHz?.let { root.put("limitMaxIfbwHz", it) }
            settings.limitMaxPoints?.let { root.put("limitMaxPoints", it) }
            settings.limitMinPowerDbm?.let { root.put("limitMinPowerDbm", it) }
            settings.limitMaxPowerDbm?.let { root.put("limitMaxPowerDbm", it) }
            settings.limitNumPorts?.let { root.put("limitNumPorts", it) }
            root.put("topControlsCollapsed", settings.topControlsCollapsed)
            root.put("sideControlsCollapsed", settings.sideControlsCollapsed)
            root.put("rememberCalibration", settings.rememberCalibration)
            root.put("calibrationEnabled", settings.calibrationEnabled)
            root.put("autoStartSweep", settings.autoStartSweep)
            tempFile.writeText(root.toString())
            if (!tempFile.renameTo(file)) {
                file.writeText(root.toString())
                tempFile.delete()
            }
        } catch (_: Exception) {
            runCatching { tempFile.delete() }
        }
    }

    @Synchronized
    fun clear() {
        runCatching { file.delete() }
        runCatching { tempFile.delete() }
    }

    private fun JSONObject.optLongOrNull(key: String): Long? =
        if (has(key)) optLong(key) else null

    private fun JSONObject.optIntOrNull(key: String): Int? =
        if (has(key)) optInt(key) else null

    private fun JSONObject.optDoubleOrNull(key: String): Double? =
        if (has(key)) optDouble(key) else null
}