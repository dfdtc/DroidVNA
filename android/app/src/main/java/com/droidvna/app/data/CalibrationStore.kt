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

/** Persists the calibration in the app's private storage. */
class CalibrationStore(context: Context) {
    private val file = File(context.filesDir, "calibration.json")

    @Synchronized
    fun load(): CalibrationSet {
        if (!file.exists()) return CalibrationSet()
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
            CalibrationSet(sol, thrus, root.optString("sourceKey", ""))
        } catch (e: Exception) {
            CalibrationSet()
        }
    }

    @Synchronized
    fun save(set: CalibrationSet) {
        try {
            val root = JSONObject()
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
            root.put("version", 1)
            file.writeText(root.toString())
        } catch (_: Exception) {
        }
    }

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