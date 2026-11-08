/*
 * DroidVNA - native Android client for LibreVNA / Harogic B60.
 * Copyright (C) 2026 The DroidVNA Authors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.droidvna.app.data

import android.content.Context
import com.droidvna.app.model.CalibrationSet
import com.droidvna.app.model.Complex
import com.droidvna.app.model.SolCal
import com.droidvna.app.model.ThruCal
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * One persisted calibration together with what it was measured with and how it was
 * obtained. The metadata is shown in the app settings and in the calibration wizard.
 */
// Plain data holders: Array fields are compared by identity, which is fine here because
// instances are only ever created by parsing and handed to the UI as a whole.
data class StoredCalibration(
    val calibration: CalibrationSet = CalibrationSet(),
    /** Serial (or "demo") of the device the calibration belongs to. */
    val deviceKey: String = "",
    /** How the calibration was created: "ECAL", "SOL", "THRU", "IMPORT", "MIXED", "NONE". */
    val source: String = "NONE",
    /** RFC 3339 timestamp of the last update, empty when unknown. */
    val updatedAt: String = "",
    /** Coefficient set name for E-Cal calibrations. */
    val detail: String = "",
)

/**
 * Persists the calibration in the app's private storage.
 *
 * The file is a timestamped snapshot ("last good calibration"): every time a calibration is
 * changed it is rewritten atomically, and on the next launch it is loaded automatically so
 * a measurement session can continue without re-running SOL/THRU or E-Cal.
 */
class CalibrationStore(context: Context) {
    private val file = File(context.filesDir, "calibration.json")
    private val tempFile = File(context.filesDir, "calibration.json.tmp")

    @Synchronized
    fun loadFile(): StoredCalibration {
        if (!file.exists()) return StoredCalibration()
        return try {
            val root = JSONObject(file.readText())
            val sol = ArrayList<SolCal>()
            val solArray = root.optJSONArray("sol") ?: JSONArray()
            for (i in 0 until solArray.length()) {
                val obj = solArray.getJSONObject(i)
                sol.add(
                    SolCal(
                        port = obj.getInt("port"),
                        freqHz = longArray(obj.getJSONArray("freq")),
                        e00 = complexArray(obj.getJSONArray("e00")),
                        e11 = complexArray(obj.getJSONArray("e11")),
                        e01e10 = complexArray(obj.getJSONArray("e01e10")),
                        createdAt = obj.optLong("createdAt", 0L),
                    )
                )
            }
            val thrus = ArrayList<ThruCal>()
            val thruArray = root.optJSONArray("thrus") ?: JSONArray()
            for (i in 0 until thruArray.length()) {
                val obj = thruArray.getJSONObject(i)
                thrus.add(
                    ThruCal(
                        fromPort = obj.getInt("from"),
                        toPort = obj.getInt("to"),
                        freqHz = longArray(obj.getJSONArray("freq")),
                        thru = complexArray(obj.getJSONArray("thru")),
                        createdAt = obj.optLong("createdAt", 0L),
                    )
                )
            }
            val set = CalibrationSet(sol, thrus, root.optString("sourceKey", ""))
            StoredCalibration(
                calibration = set,
                deviceKey = set.sourceKey,
                source = if (set.isEmpty) "NONE" else root.optString("calSource", "MIXED").ifBlank { "MIXED" },
                updatedAt = root.optString("updatedAt", ""),
                detail = root.optString("calDetail", ""),
            )
        } catch (_: Exception) {
            StoredCalibration()
        }
    }

    /** Previous behaviour: the coefficients only. */
    @Synchronized
    fun load(): CalibrationSet = loadFile().calibration

    @Synchronized
    fun save(set: CalibrationSet) {
        val previous = loadFile()
        saveFile(
            StoredCalibration(
                calibration = set,
                deviceKey = set.sourceKey,
                source = previous.source.takeIf { previous.deviceKey == set.sourceKey } ?: "MIXED",
                updatedAt = previous.updatedAt,
                detail = previous.detail.takeIf { previous.deviceKey == set.sourceKey } ?: "",
            )
        )
    }

    @Synchronized
    fun saveFile(record: StoredCalibration) {
        try {
            val root = JSONObject()
            val set = record.calibration
            val solArray = JSONArray()
            for (cal in set.sol) {
                val obj = JSONObject()
                obj.put("port", cal.port)
                obj.put("freq", longArray(cal.freqHz))
                obj.put("e00", complexArray(cal.e00))
                obj.put("e11", complexArray(cal.e11))
                obj.put("e01e10", complexArray(cal.e01e10))
                obj.put("createdAt", cal.createdAt)
                solArray.put(obj)
            }
            val thruArray = JSONArray()
            for (cal in set.thrus) {
                val obj = JSONObject()
                obj.put("from", cal.fromPort)
                obj.put("to", cal.toPort)
                obj.put("freq", longArray(cal.freqHz))
                obj.put("thru", complexArray(cal.thru))
                obj.put("createdAt", cal.createdAt)
                thruArray.put(obj)
            }
            root.put("sol", solArray)
            root.put("thrus", thruArray)
            root.put("sourceKey", set.sourceKey)
            root.put("calSource", if (set.isEmpty) "NONE" else record.source)
            root.put("calDetail", record.detail)
            root.put("updatedAt", record.updatedAt.ifBlank { nowStamp() })
            root.put("version", 2)
            tempFile.writeText(root.toString())
            if (!tempFile.renameTo(file)) {
                file.writeText(root.toString())
                tempFile.delete()
            }
        } catch (_: Exception) {
            runCatching { tempFile.delete() }
        }
    }

    /** "Forget" removes the remembered calibration from disk. */
    @Synchronized
    fun clear() {
        runCatching { file.delete() }
        runCatching { tempFile.delete() }
    }

    @Synchronized
    fun summary(): StoredCalibration = loadFile()

    private fun nowStamp(): String =
        java.time.OffsetDateTime.now(java.time.ZoneId.systemDefault())
            .withNano(0)
            .toString()

    private fun longArray(array: LongArray): JSONArray {
        val out = JSONArray()
        for (v in array) out.put(v)
        return out
    }

    private fun complexArray(array: Array<Complex>): JSONArray {
        val out = JSONArray()
        for (v in array) {
            val pair = JSONArray()
            pair.put(if (v.re.isFinite()) v.re else 0.0)
            pair.put(if (v.im.isFinite()) v.im else 0.0)
            out.put(pair)
        }
        return out
    }

    private fun longArray(array: JSONArray): LongArray = LongArray(array.length()) { array.getLong(it) }

    private fun complexArray(array: JSONArray): Array<Complex> = Array(array.length()) {
        val pair = array.getJSONArray(it)
        Complex(pair.getDouble(0), pair.getDouble(1))
    }
}
