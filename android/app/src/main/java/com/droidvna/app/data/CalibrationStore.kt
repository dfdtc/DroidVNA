/*
 * DroidVNA - native Android client for LibreVNA / Harogic B60.
 * Copyright (C) 2026 The DroidVNA Authors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.droidvna.app.data

import android.content.Context
import com.droidvna.app.model.CalibrationSet
import com.droidvna.app.model.Complex
import com.droidvna.app.model.PortErrorTerms
import com.droidvna.app.model.SolCal
import com.droidvna.app.model.SoltCal
import com.droidvna.app.model.SoltThroughSnapshot
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
    /** How the calibration was created: "ECAL", "SOLT", "SOL", "THRU", "IMPORT", "MIXED", "NONE". */
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
 * a measurement session can continue without re-running SOL/SOLT or E-Cal.
 *
 * File versions:
 *  - 1/2: `sol` (1-port) and `thrus` (THRU normalization) only.
 *  - 3:   adds `solt`, the full 12-term two-port model. Older files still load unchanged.
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
            val solt = root.optJSONObject("solt")?.let { parseSolt(it) }
            val set = CalibrationSet(sol, thrus, root.optString("sourceKey", ""), solt)
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
            set.solt?.let { root.put("solt", writeSolt(it)) }
            root.put("sourceKey", set.sourceKey)
            root.put("calSource", if (set.isEmpty) "NONE" else record.source)
            root.put("calDetail", record.detail)
            root.put("updatedAt", record.updatedAt.ifBlank { nowStamp() })
            root.put("version", 3)
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

    private fun writeSolt(cal: SoltCal): JSONObject {
        val obj = JSONObject()
        obj.put("freq", longArray(cal.freqHz))
        obj.put("p1", writeTerms(cal.port1))
        obj.put("p2", writeTerms(cal.port2))
        obj.put("l12", complexArray(cal.loadMatch12))
        obj.put("l21", complexArray(cal.loadMatch21))
        obj.put("t12", complexArray(cal.transmissionTracking12))
        obj.put("t21", complexArray(cal.transmissionTracking21))
        obj.put("i12", complexArray(cal.isolation12))
        obj.put("i21", complexArray(cal.isolation21))
        obj.put("hasIsolation", cal.hasIsolation)
        cal.through?.let { obj.put("through", writeThrough(it)) }
        obj.put("createdAt", cal.createdAt)
        return obj
    }

    private fun writeThrough(snapshot: SoltThroughSnapshot): JSONObject = JSONObject()
        .put("m11", complexArray(snapshot.measured11))
        .put("m21", complexArray(snapshot.measured21))
        .put("m12", complexArray(snapshot.measured12))
        .put("m22", complexArray(snapshot.measured22))
        .put("a11", complexArray(snapshot.actual11))
        .put("a21", complexArray(snapshot.actual21))
        .put("a12", complexArray(snapshot.actual12))
        .put("a22", complexArray(snapshot.actual22))

    private fun readThrough(obj: JSONObject): SoltThroughSnapshot? = try {
        val snapshot = SoltThroughSnapshot(
            measured11 = complexArray(obj.getJSONArray("m11")),
            measured21 = complexArray(obj.getJSONArray("m21")),
            measured12 = complexArray(obj.getJSONArray("m12")),
            measured22 = complexArray(obj.getJSONArray("m22")),
            actual11 = complexArray(obj.getJSONArray("a11")),
            actual21 = complexArray(obj.getJSONArray("a21")),
            actual12 = complexArray(obj.getJSONArray("a12")),
            actual22 = complexArray(obj.getJSONArray("a22")),
        )
        if (snapshot.isConsistent()) snapshot else null
    } catch (_: Exception) {
        null
    }

    private fun writeTerms(terms: PortErrorTerms): JSONObject = JSONObject()
        .put("d", complexArray(terms.directivity))
        .put("sm", complexArray(terms.sourceMatch))
        .put("rt", complexArray(terms.reflectionTracking))

    private fun parseSolt(obj: JSONObject): SoltCal? = try {
        val freq = longArray(obj.getJSONArray("freq"))
        val p1 = readTerms(obj.getJSONObject("p1"))
        val p2 = readTerms(obj.getJSONObject("p2"))
        val parsed = SoltCal(
            freqHz = freq,
            port1 = p1,
            port2 = p2,
            loadMatch12 = complexArray(obj.getJSONArray("l12")),
            loadMatch21 = complexArray(obj.getJSONArray("l21")),
            transmissionTracking12 = complexArray(obj.getJSONArray("t12")),
            transmissionTracking21 = complexArray(obj.getJSONArray("t21")),
            isolation12 = complexArray(obj.getJSONArray("i12")),
            isolation21 = complexArray(obj.getJSONArray("i21")),
            hasIsolation = obj.optBoolean("hasIsolation", false),
            through = obj.optJSONObject("through")?.let { readThrough(it) },
            createdAt = obj.optLong("createdAt", 0L),
        )
        // A truncated file must not reach the correction path; drop the whole model instead.
        if (parsed.isConsistent()) parsed else null
    } catch (_: Exception) {
        null
    }

    private fun readTerms(obj: JSONObject): PortErrorTerms = PortErrorTerms(
        directivity = complexArray(obj.getJSONArray("d")),
        sourceMatch = complexArray(obj.getJSONArray("sm")),
        reflectionTracking = complexArray(obj.getJSONArray("rt")),
    )

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
