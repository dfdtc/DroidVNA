/*
 * DroidVNA - native Android client for LibreVNA / Harogic B60.
 * Copyright (C) 2026 The DroidVNA Authors
 * SPDX-License-Identifier: GPL-3.0-only
 *
 * Based in part on LibreVNA-GUI (https://github.com/jankae/LibreVNA),
 * Copyright (C) Jan Käberich and contributors, GPL-3.0.
 */

package com.droidvna.app.model

import org.json.JSONArray
import org.json.JSONObject

enum class CalStandardKind { OPEN, SHORT, LOAD, THROUGH }

/** One standard definition from a LibreVNA .cal calibration kit (or from a LibreCAL module). */
data class CalKitStandard(
    val id: String,
    val name: String,
    val kind: CalStandardKind?,
    val freqHz: LongArray,
    val s11: Array<Complex>,
    val s12: Array<Complex>,
    val s21: Array<Complex>,
    val s22: Array<Complex>,
    val model: CalStandardModel? = null,
) {
    fun s11At(frequency: Double): Complex = when {
        model != null -> model.s11At(frequency)
        freqHz.isEmpty() -> Complex.NAN
        else -> CalMath.interpolate(freqHz, s11, frequency)
    }

    fun s21At(frequency: Double): Complex = when {
        model != null -> model.s21At(frequency)
        freqHz.isEmpty() || s21.isEmpty() -> Complex.NAN
        else -> CalMath.interpolate(freqHz, s21, frequency)
    }

    fun s12At(frequency: Double): Complex = when {
        model != null -> model.s12At(frequency)
        freqHz.isEmpty() || s12.isEmpty() -> Complex.NAN
        else -> CalMath.interpolate(freqHz, s12, frequency)
    }

    fun s11AtGrid(frequencies: LongArray): Array<Complex>? {
        if (model != null) return Array(frequencies.size) { model.s11At(frequencies[it].toDouble()) }
        if (freqHz.size < 2 || s11.size != freqHz.size) return null
        return Array(frequencies.size) { CalMath.interpolate(freqHz, s11, frequencies[it].toDouble()) }
    }

    fun s21AtGrid(frequencies: LongArray): Array<Complex>? {
        if (model != null) return Array(frequencies.size) { model.s21At(frequencies[it].toDouble()) }
        if (freqHz.size < 2 || s21.size != freqHz.size) return null
        return Array(frequencies.size) { CalMath.interpolate(freqHz, s21, frequencies[it].toDouble()) }
    }

    fun s12AtGrid(frequencies: LongArray): Array<Complex>? {
        if (model != null) return Array(frequencies.size) { model.s12At(frequencies[it].toDouble()) }
        if (freqHz.size < 2 || s12.size != freqHz.size) return null
        return Array(frequencies.size) { CalMath.interpolate(freqHz, s12, frequencies[it].toDouble()) }
    }
}

data class CalibrationImportResult(
    val calibration: CalibrationSet,
    val summary: String,
    val warnings: List<String>,
    val device: String = "",
)

/**
 * Imports the calibration result format written by LibreVNA-GUI (.cal, JSON format 3).
 *
 * The desktop file stores the measured calibration standards plus the calibration kit
 * definitions, not pre-computed error terms. This importer reconstructs the part of the
 * calibration the mobile app supports today: 1-port SOL for each port and THRU
 * normalization between the measured ports. A full 12-term SOLT correction is intentionally
 * not claimed; the summary says what was imported.
 */
object CalibrationFileImporter {
    private const val EXPECTED_FORMAT = 3

    fun parse(text: String, sourceKey: String = ""): CalibrationImportResult {
        val root = try {
            JSONObject(text.removePrefix("﻿"))
        } catch (e: Exception) {
            throw IllegalArgumentException("不是有效的 JSON .cal 文件")
        }
        val format = root.optInt("format", -1)
        if (format != -1 && format != EXPECTED_FORMAT) {
            throw IllegalArgumentException("不支持的 .cal 格式：$format（仅支持 format 3）")
        }
        if (format == -1 && !root.has("version")) {
            throw IllegalArgumentException("文件缺少 format 3 字段")
        }
        val kit = root.optJSONObject("calkit")
            ?: throw IllegalArgumentException("缺少 calkit（校准套件定义）")
        val standardsJson = kit.optJSONArray("standards")
            ?: throw IllegalArgumentException("calkit 中没有 standards")
        val standards = ArrayList<CalKitStandard>()
        for (i in 0 until standardsJson.length()) {
            val obj = standardsJson.optJSONObject(i) ?: continue
            standards.add(parseStandard(obj))
        }
        val byId = standards.associateBy { it.id }

        val measurementsJson = root.optJSONArray("measurements") ?: JSONArray()
        val onePorts = ArrayList<OnePortMeasurement>()
        val twoPorts = ArrayList<TwoPortMeasurement>()
        for (i in 0 until measurementsJson.length()) {
            val obj = measurementsJson.optJSONObject(i) ?: continue
            val type = obj.optString("type", "")
            val kind = kindFromString(type) ?: continue
            val data = obj.optJSONObject("data") ?: continue
            val standardId = data.opt("standard")?.toString()?.trim('"') ?: ""
            val points = data.optJSONArray("points") ?: continue
            if (points.length() < 2) continue
            if (kind == CalStandardKind.THROUGH || data.has("port1")) {
                twoPorts.add(parseTwoPort(data, kind, standardId, points))
            } else {
                val port = data.optInt("port", 0)
                if (port < 1) continue
                onePorts.add(parseOnePort(port, kind, standardId, points))
            }
        }

        val sol = ArrayList<SolCal>()
        val thrus = ArrayList<ThruCal>()
        val warnings = ArrayList<String>()

        for (port in 1..2) {
            val open = onePorts.firstOrNull { it.port == port && it.kind == CalStandardKind.OPEN }
            val short = onePorts.firstOrNull { it.port == port && it.kind == CalStandardKind.SHORT }
            val load = onePorts.firstOrNull { it.port == port && it.kind == CalStandardKind.LOAD }
            if (open == null && short == null && load == null) continue
            if (open == null || short == null || load == null) {
                warnings.add("端口 $port 缺少 Open/Short/Load 中的一项，未生成 SOL")
                continue
            }
            val stdOpen = byId[open.standardId]
            val stdShort = byId[short.standardId]
            val stdLoad = byId[load.standardId]
            if (stdOpen == null || stdShort == null || stdLoad == null) {
                warnings.add("端口 $port 的标准件定义缺失，未生成 SOL")
                continue
            }
            val grid = open.freqHz
            if (grid.size < 2) continue
            fun measuredAt(m: OnePortMeasurement): Array<Complex> =
                Array(grid.size) { CalMath.interpolate(m.freqHz, m.measured, grid[it].toDouble()) }
            fun actualAt(std: CalKitStandard): Array<Complex>? = std.s11AtGrid(grid)
            val openActual = actualAt(stdOpen)
            val shortActual = actualAt(stdShort)
            val loadActual = actualAt(stdLoad)
            if (openActual == null || shortActual == null || loadActual == null) {
                warnings.add("端口 $port 的标准件没有可用的 Touchstone 数据，未生成 SOL")
                continue
            }
            val cal = CalMath.solveSol(
                grid,
                measuredAt(open),
                measuredAt(short),
                measuredAt(load),
                openActual,
                shortActual,
                loadActual,
                port,
            )
            if (cal == null) {
                warnings.add("端口 $port 的 SOL 方程无解（标准件过近或数据异常）")
            } else {
                sol.add(cal)
            }
        }

        val thruMeas = twoPorts.firstOrNull { it.kind == CalStandardKind.THROUGH && it.port1 == 1 && it.port2 == 2 }
            ?: twoPorts.firstOrNull { it.kind == CalStandardKind.THROUGH }
        if (thruMeas != null) {
            val std = byId[thruMeas.standardId]
            val grid = thruMeas.freqHz
            val a21 = std?.s21AtGrid(grid)
            val a12 = std?.s12AtGrid(grid)
            if (std == null || a21 == null || a12 == null) {
                warnings.add("THRU 标准件定义缺失，跳过 THRU 归一化")
            } else {
                val actual21 = if (thruMeas.reversed) a12 else a21
                val actual12 = if (thruMeas.reversed) a21 else a12
                val n = grid.size
                val thru21 = Array(n) { i ->
                    val a = actual21[i]
                    val m = thruMeas.s21[i]
                    if (a.isFinite() && a.abs() > 1e-12 && m.isFinite()) m / a else m
                }
                val thru12 = Array(n) { i ->
                    val a = actual12[i]
                    val m = thruMeas.s12[i]
                    if (a.isFinite() && a.abs() > 1e-12 && m.isFinite()) m / a else m
                }
                if (thruMeas.port1 == 1 && thruMeas.port2 == 2) {
                    thrus.add(ThruCal(1, 2, grid.copyOf(), thru21))
                    thrus.add(ThruCal(2, 1, grid.copyOf(), thru12))
                } else {
                    thrus.add(ThruCal(thruMeas.port1, thruMeas.port2, grid.copyOf(), thru21))
                    thrus.add(ThruCal(thruMeas.port2, thruMeas.port1, grid.copyOf(), thru12))
                }
            }
        }

        if (sol.isEmpty() && thrus.isEmpty()) {
            val detail = warnings.joinToString("；").ifBlank { "没有可用的 Open/Short/Load/Through 测量" }
            throw IllegalArgumentException("未能从 .cal 中恢复校准数据：$detail")
        }
        val type = root.optString("type", "?").ifBlank { "?" }
        val ports = root.optJSONArray("ports")?.let { arr ->
            (0 until arr.length()).joinToString(",") { arr.optInt(it).toString() }
        } ?: "?"
        val summary = "PC .cal · $type · 端口 $ports · SOL ${sol.size} · THRU ${thrus.size / 2}"
        return CalibrationImportResult(
            calibration = CalibrationSet(sol, thrus, sourceKey),
            summary = summary,
            warnings = warnings,
            device = root.optString("device", ""),
        )
    }

    private data class OnePortMeasurement(
        val port: Int,
        val kind: CalStandardKind,
        val standardId: String,
        val freqHz: LongArray,
        val measured: Array<Complex>,
    )

    private data class TwoPortMeasurement(
        val port1: Int,
        val port2: Int,
        val kind: CalStandardKind,
        val standardId: String,
        val reversed: Boolean,
        val freqHz: LongArray,
        val s11: Array<Complex>,
        val s12: Array<Complex>,
        val s21: Array<Complex>,
        val s22: Array<Complex>,
    )

    private fun parseStandard(obj: JSONObject): CalKitStandard {
        val params = obj.optJSONObject("params") ?: obj
        // ids are unsigned 64-bit and can exceed Long.MAX_VALUE; keep the JSON representation
        // as a string so a through standard and its measurement still match.
        val id = params.opt("id")?.toString()?.trim('"') ?: ""
        val name = params.optString("name", "Standard")
        val points = params.optJSONObject("touchstone")?.optJSONArray("datapoints") ?: JSONArray()
        val entries = ArrayList<Pair<Long, Array<Complex>>>()
        for (i in 0 until points.length()) {
            val point = points.optJSONObject(i) ?: continue
            val freq = point.optDouble("frequency", 0.0)
            if (freq <= 0.0) continue
            val sp = point.optJSONArray("Sparams") ?: continue
            val values = ArrayList<Complex>()
            for (j in 0 until sp.length()) {
                val c = sp.optJSONObject(j) ?: continue
                values.add(Complex(c.optDouble("real", 0.0), c.optDouble("imag", 0.0)))
            }
            if (values.size != 1 && values.size != 4) continue
            entries.add(freq.toLong() to values.toTypedArray())
        }
        entries.sortBy { it.first }
        val freq = LongArray(entries.size) { entries[it].first }
        val n = entries.size
        val s11 = if (n == 0) emptyArray() else Array(n) { entries[it].second[0] }
        val s12 = if (n == 0) emptyArray() else Array(n) { entries[it].second.getOrNull(1) ?: Complex.NAN }
        val s21 = if (n == 0) emptyArray() else Array(n) { entries[it].second.getOrNull(2) ?: Complex.NAN }
        val s22 = if (n == 0) emptyArray() else Array(n) { entries[it].second.getOrNull(3) ?: Complex.NAN }
        val kind = inferKind(params, name, n > 0 && entries[0].second.size == 4)
        val model = if (n == 0 && kind != null) modelFromParams(params, kind) else null
        return CalKitStandard(id, name, kind, freq, s11, s12, s21, s22, model)
    }

    private fun modelFromParams(params: JSONObject, kind: CalStandardKind): CalStandardModel? {
        val z0 = params.optDouble("Z0", 50.0)
        val delay = params.optDouble("delay", 0.0)
        val loss = params.optDouble("loss", 0.0)
        return when (kind) {
            CalStandardKind.OPEN -> CalStandardModel.Open(
                z0, delay, loss,
                params.optDouble("C0", 0.0),
                params.optDouble("C1", 0.0),
                params.optDouble("C2", 0.0),
                params.optDouble("C3", 0.0),
            )
            CalStandardKind.SHORT -> CalStandardModel.Short(
                z0, delay, loss,
                params.optDouble("L0", 0.0),
                params.optDouble("L1", 0.0),
                params.optDouble("L2", 0.0),
                params.optDouble("L3", 0.0),
            )
            CalStandardKind.LOAD -> CalStandardModel.Load(
                z0, delay, loss,
                params.optDouble("resistance", 50.0),
                params.optBoolean("Cfirst", true),
                params.optDouble("Cparallel", 0.0),
                params.optDouble("Lseries", 0.0),
            )
            CalStandardKind.THROUGH -> CalStandardModel.Through(z0, delay, loss)
        }
    }

    private fun parseOnePort(
        port: Int,
        kind: CalStandardKind,
        standardId: String,
        points: JSONArray,
    ): OnePortMeasurement {
        val entries = ArrayList<Pair<Long, Complex>>()
        for (i in 0 until points.length()) {
            val p = points.optJSONObject(i) ?: continue
            val f = p.optDouble("frequency", 0.0)
            if (f <= 0.0) continue
            entries.add(f.toLong() to Complex(p.optDouble("real", 0.0), p.optDouble("imag", 0.0)))
        }
        entries.sortBy { it.first }
        return OnePortMeasurement(
            port,
            kind,
            standardId,
            LongArray(entries.size) { entries[it].first },
            Array(entries.size) { entries[it].second },
        )
    }

    private fun parseTwoPort(
        data: JSONObject,
        kind: CalStandardKind,
        standardId: String,
        points: JSONArray,
    ): TwoPortMeasurement {
        val port1 = data.optInt("port1", 1)
        val port2 = data.optInt("port2", 2)
        val reversed = data.optBoolean("reverseStandard", false)
        data class Row(val f: Long, val s11: Complex, val s12: Complex, val s21: Complex, val s22: Complex)
        val rows = ArrayList<Row>()
        for (i in 0 until points.length()) {
            val p = points.optJSONObject(i) ?: continue
            val f = p.optDouble("frequency", 0.0)
            if (f <= 0.0) continue
            val sp = p.optJSONObject("Sparam") ?: continue
            rows.add(
                Row(
                    f.toLong(),
                    Complex(sp.optDouble("m11_real", 0.0), sp.optDouble("m11_imag", 0.0)),
                    Complex(sp.optDouble("m12_real", 0.0), sp.optDouble("m12_imag", 0.0)),
                    Complex(sp.optDouble("m21_real", 0.0), sp.optDouble("m21_imag", 0.0)),
                    Complex(sp.optDouble("m22_real", 0.0), sp.optDouble("m22_imag", 0.0)),
                )
            )
        }
        rows.sortBy { it.f }
        return TwoPortMeasurement(
            port1, port2, kind, standardId, reversed,
            LongArray(rows.size) { rows[it].f },
            Array(rows.size) { rows[it].s11 },
            Array(rows.size) { rows[it].s12 },
            Array(rows.size) { rows[it].s21 },
            Array(rows.size) { rows[it].s22 },
        )
    }

    private fun inferKind(params: JSONObject, name: String, isTwoPort: Boolean): CalStandardKind? = when {
        params.has("C0") || params.has("C1") || params.has("C2") || params.has("C3") -> CalStandardKind.OPEN
        params.has("L0") || params.has("L1") || params.has("L2") || params.has("L3") -> CalStandardKind.SHORT
        params.has("Cfirst") || params.has("Cparallel") || params.has("resistance") -> CalStandardKind.LOAD
        isTwoPort || name.contains(" to ", ignoreCase = true) ||
            name.contains("through", ignoreCase = true) || name.contains("thru", ignoreCase = true) ||
            (params.has("Z0") && params.has("delay") && params.has("loss")) -> CalStandardKind.THROUGH
        else -> null
    }

    private fun kindFromString(value: String): CalStandardKind? = when (value.trim().lowercase()) {
        "open" -> CalStandardKind.OPEN
        "short" -> CalStandardKind.SHORT
        "load", "slidingload" -> CalStandardKind.LOAD
        "through", "thru" -> CalStandardKind.THROUGH
        else -> null
    }
}