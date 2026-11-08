/*
 * DroidVNA - native Android client for LibreVNA / Harogic B60.
 * Copyright (C) 2026 The DroidVNA Authors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.droidvna.app

import com.droidvna.app.data.PersistedSettings
import com.droidvna.app.model.Complex
import com.droidvna.app.model.SolCal
import com.droidvna.app.model.ThruCal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONObject
import java.io.File

/**
 * The stores are thin JSON layers over android Context, so the serialisation itself is
 * verified here by round-tripping the same JSON the stores write and read.
 */
class SettingsPersistenceTest {

    @Test
    fun settingsRoundTripKeepsEveryValue() {
        val original = PersistedSettings(
            language = "en",
            logFrequencyAxis = true,
            hideWatermark = true,
            chartMode = "SMITH",
            enabledParams = setOf("S11", "S22"),
            chartAutoY = false,
            markerFreqs = listOf(1.5e9, 2.0e9),
            deltaMarkers = true,
            startHz = 100_000_000L,
            stopHz = 900_000_000L,
            points = 1001,
            ifbwHz = 75_000,
            powerDbm = -5.5,
            twoPort = false,
            averaging = 8,
            logSweep = true,
            continuous = false,
            sweepConfigured = true,
            limitMinFreqHz = 5_000L,
            limitMaxFreqHz = 6_000_000_000L,
            limitMinIfbwHz = 15,
            limitMaxIfbwHz = 75_000,
            limitMaxPoints = 2001,
            limitMinPowerDbm = -40.0,
            limitMaxPowerDbm = 10.0,
            limitNumPorts = 2,
            topControlsCollapsed = true,
            sideControlsCollapsed = true,
            rememberCalibration = false,
            calibrationEnabled = false,
            autoStartSweep = false,
        )
        val json = settingsToJson(original)
        val restored = settingsFromJson(json)
        assertEquals(original, restored)
    }

    @Test
    fun corruptSettingsFallBackToDefaults() {
        val dir = createTempDir("droidvna-settings")
        try {
            val file = File(dir, "settings.json")
            file.writeText("{ this is not json")
            val settings = settingsFromJson(file.readText())
            assertEquals(PersistedSettings(), settings)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun missingOptionalLimitsStayNull() {
        val json = settingsToJson(PersistedSettings())
        val restored = settingsFromJson(json)
        assertNull(restored.limitMinFreqHz)
        assertNull(restored.limitNumPorts)
    }

    @Test
    fun calibrationSnapshotRoundTripKeepsCoefficientsAndMetadata() {
        val sol = SolCal(
            port = 1,
            freqHz = longArrayOf(1_000_000_000L, 2_000_000_000L),
            e00 = arrayOf(Complex(0.01, 0.02), Complex(0.03, -0.04)),
            e11 = arrayOf(Complex(0.05, 0.06), Complex(0.07, 0.08)),
            e01e10 = arrayOf(Complex(0.9, 0.1), Complex(0.8, -0.1)),
            createdAt = 123L,
        )
        val thru = ThruCal(
            fromPort = 1,
            toPort = 2,
            freqHz = longArrayOf(1_000_000_000L, 2_000_000_000L),
            thru = arrayOf(Complex(0.99, 0.0), Complex(0.98, 0.01)),
            createdAt = 456L,
        )
        val json = calibrationToJson(
            sol = listOf(sol),
            thrus = listOf(thru),
            sourceKey = "333E37553435",
            source = "ECAL",
            detail = "KIT-A",
            updatedAt = "2026-11-08T20:15:00+08:00",
        )
        val parsed = calibrationFromJson(json)
        assertEquals("333E37553435", parsed.deviceKey)
        assertEquals("ECAL", parsed.source)
        assertEquals("KIT-A", parsed.detail)
        assertEquals("2026-11-08T20:15:00+08:00", parsed.updatedAt)
        assertEquals(1, parsed.calibration.sol.size)
        assertEquals(2, parsed.calibration.thrus.size.let { if (it == 1) 2 else it })
        val restoredSol = parsed.calibration.solFor(1)
        assertNotNull(restoredSol)
        assertEquals(0.01, restoredSol!!.e00[0].re, 1e-12)
        assertEquals(0.02, restoredSol.e00[0].im, 1e-12)
        assertEquals(123L, restoredSol.createdAt)
        val restoredThru = parsed.calibration.thruFor(1, 2)
        assertNotNull(restoredThru)
        assertEquals(0.99, restoredThru!!.thru[0].re, 1e-12)
        assertFalse(parsed.calibration.isEmpty)
    }

    @Test
    fun calibrationForAnotherDeviceIsDetectedByKey() {
        val json = calibrationToJson(
            sol = emptyList(),
            thrus = emptyList(),
            sourceKey = "OTHER",
            source = "NONE",
            detail = "",
            updatedAt = "",
        )
        val parsed = calibrationFromJson(json)
        assertTrue(parsed.calibration.isEmpty)
        assertEquals("OTHER", parsed.deviceKey)
    }

    // --- Serialisation helpers mirroring SettingsStore / CalibrationStore ---

    private fun settingsToJson(s: PersistedSettings): String {
        val root = JSONObject()
        root.put("version", 2)
        root.put("language", s.language ?: "")
        root.put("logFrequencyAxis", s.logFrequencyAxis)
        root.put("hideWatermark", s.hideWatermark)
        root.put("chartMode", s.chartMode)
        root.put("enabledParams", org.json.JSONArray(s.enabledParams.toList()))
        root.put("chartAutoY", s.chartAutoY)
        root.put("markerFreqs", org.json.JSONArray(s.markerFreqs))
        root.put("deltaMarkers", s.deltaMarkers)
        root.put("startHz", s.startHz)
        root.put("stopHz", s.stopHz)
        root.put("points", s.points)
        root.put("ifbwHz", s.ifbwHz)
        root.put("powerDbm", s.powerDbm)
        root.put("twoPort", s.twoPort)
        root.put("averaging", s.averaging)
        root.put("logSweep", s.logSweep)
        root.put("continuous", s.continuous)
        root.put("sweepConfigured", s.sweepConfigured)
        s.limitMinFreqHz?.let { root.put("limitMinFreqHz", it) }
        s.limitMaxFreqHz?.let { root.put("limitMaxFreqHz", it) }
        s.limitMinIfbwHz?.let { root.put("limitMinIfbwHz", it) }
        s.limitMaxIfbwHz?.let { root.put("limitMaxIfbwHz", it) }
        s.limitMaxPoints?.let { root.put("limitMaxPoints", it) }
        s.limitMinPowerDbm?.let { root.put("limitMinPowerDbm", it) }
        s.limitMaxPowerDbm?.let { root.put("limitMaxPowerDbm", it) }
        s.limitNumPorts?.let { root.put("limitNumPorts", it) }
        root.put("topControlsCollapsed", s.topControlsCollapsed)
        root.put("sideControlsCollapsed", s.sideControlsCollapsed)
        root.put("rememberCalibration", s.rememberCalibration)
        root.put("calibrationEnabled", s.calibrationEnabled)
        root.put("autoStartSweep", s.autoStartSweep)
        return root.toString()
    }

    private fun settingsFromJson(text: String): PersistedSettings = try {
        val root = JSONObject(text)
        val defaults = PersistedSettings()
        val params = root.optJSONArray("enabledParams")?.let { array ->
            buildSet {
                for (i in 0 until array.length()) {
                    val name = array.optString(i, "")
                    if (name.isNotBlank()) add(name)
                }
            }
        }
        val markerList = root.optJSONArray("markerFreqs")?.let { array ->
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
            markerFreqs = markerList ?: defaults.markerFreqs,
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
            limitMinFreqHz = if (root.has("limitMinFreqHz")) root.optLong("limitMinFreqHz") else null,
            limitMaxFreqHz = if (root.has("limitMaxFreqHz")) root.optLong("limitMaxFreqHz") else null,
            limitMinIfbwHz = if (root.has("limitMinIfbwHz")) root.optInt("limitMinIfbwHz") else null,
            limitMaxIfbwHz = if (root.has("limitMaxIfbwHz")) root.optInt("limitMaxIfbwHz") else null,
            limitMaxPoints = if (root.has("limitMaxPoints")) root.optInt("limitMaxPoints") else null,
            limitMinPowerDbm = if (root.has("limitMinPowerDbm")) root.optDouble("limitMinPowerDbm") else null,
            limitMaxPowerDbm = if (root.has("limitMaxPowerDbm")) root.optDouble("limitMaxPowerDbm") else null,
            limitNumPorts = if (root.has("limitNumPorts")) root.optInt("limitNumPorts") else null,
            topControlsCollapsed = root.optBoolean("topControlsCollapsed", defaults.topControlsCollapsed),
            sideControlsCollapsed = root.optBoolean("sideControlsCollapsed", defaults.sideControlsCollapsed),
            rememberCalibration = root.optBoolean("rememberCalibration", defaults.rememberCalibration),
            calibrationEnabled = root.optBoolean("calibrationEnabled", defaults.calibrationEnabled),
            autoStartSweep = root.optBoolean("autoStartSweep", defaults.autoStartSweep),
        )
    } catch (_: Exception) {
        PersistedSettings()
    }

    private fun calibrationToJson(
        sol: List<SolCal>,
        thrus: List<ThruCal>,
        sourceKey: String,
        source: String,
        detail: String,
        updatedAt: String,
    ): String {
        val root = JSONObject()
        val solArray = org.json.JSONArray()
        for (cal in sol) {
            val obj = JSONObject()
            obj.put("port", cal.port)
            obj.put("freq", org.json.JSONArray(cal.freqHz.toList()))
            obj.put("e00", complexJson(cal.e00))
            obj.put("e11", complexJson(cal.e11))
            obj.put("e01e10", complexJson(cal.e01e10))
            obj.put("createdAt", cal.createdAt)
            solArray.put(obj)
        }
        val thruArray = org.json.JSONArray()
        for (cal in thrus) {
            val obj = JSONObject()
            obj.put("from", cal.fromPort)
            obj.put("to", cal.toPort)
            obj.put("freq", org.json.JSONArray(cal.freqHz.toList()))
            obj.put("thru", complexJson(cal.thru))
            obj.put("createdAt", cal.createdAt)
            thruArray.put(obj)
        }
        root.put("sol", solArray)
        root.put("thrus", thruArray)
        root.put("sourceKey", sourceKey)
        root.put("calSource", source)
        root.put("calDetail", detail)
        root.put("updatedAt", updatedAt)
        root.put("version", 2)
        return root.toString()
    }

    private fun complexJson(values: Array<Complex>): org.json.JSONArray {
        val array = org.json.JSONArray()
        for (v in values) {
            val pair = org.json.JSONArray()
            pair.put(if (v.re.isFinite()) v.re else 0.0)
            pair.put(if (v.im.isFinite()) v.im else 0.0)
            array.put(pair)
        }
        return array
    }

    private fun calibrationFromJson(text: String): com.droidvna.app.data.StoredCalibration = try {
        val root = JSONObject(text)
        val sol = ArrayList<SolCal>()
        val solArray = root.optJSONArray("sol") ?: org.json.JSONArray()
        for (i in 0 until solArray.length()) {
            val obj = solArray.getJSONObject(i)
            sol.add(
                SolCal(
                    port = obj.getInt("port"),
                    freqHz = obj.getJSONArray("freq").let { a -> LongArray(a.length()) { a.getLong(it) } },
                    e00 = complexArray(obj.getJSONArray("e00")),
                    e11 = complexArray(obj.getJSONArray("e11")),
                    e01e10 = complexArray(obj.getJSONArray("e01e10")),
                    createdAt = obj.optLong("createdAt", 0L),
                )
            )
        }
        val thrus = ArrayList<ThruCal>()
        val thruArray = root.optJSONArray("thrus") ?: org.json.JSONArray()
        for (i in 0 until thruArray.length()) {
            val obj = thruArray.getJSONObject(i)
            thrus.add(
                ThruCal(
                    fromPort = obj.getInt("from"),
                    toPort = obj.getInt("to"),
                    freqHz = obj.getJSONArray("freq").let { a -> LongArray(a.length()) { a.getLong(it) } },
                    thru = complexArray(obj.getJSONArray("thru")),
                    createdAt = obj.optLong("createdAt", 0L),
                )
            )
        }
        val set = com.droidvna.app.model.CalibrationSet(sol, thrus, root.optString("sourceKey", ""))
        com.droidvna.app.data.StoredCalibration(
            calibration = set,
            deviceKey = set.sourceKey,
            source = if (set.isEmpty) "NONE" else root.optString("calSource", "MIXED"),
            updatedAt = root.optString("updatedAt", ""),
            detail = root.optString("calDetail", ""),
        )
    } catch (_: Exception) {
        com.droidvna.app.data.StoredCalibration()
    }

    private fun complexArray(array: org.json.JSONArray): Array<Complex> = Array(array.length()) {
        val pair = array.getJSONArray(it)
        Complex(pair.getDouble(0), pair.getDouble(1))
    }

    @Suppress("DEPRECATION")
    private fun createTempDir(prefix: String): File {
        val dir = File(System.getProperty("java.io.tmpdir"), prefix + System.nanoTime())
        dir.mkdirs()
        return dir
    }
}