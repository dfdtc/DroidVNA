/*
 * DroidVNA - native Android client for LibreVNA / Harogic B60.
 * Copyright (C) 2026 The DroidVNA Authors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.droidvna.app

import com.droidvna.app.model.CalibrationEngine
import com.droidvna.app.model.CalibrationFileImporter
import com.droidvna.app.model.Complex
import com.droidvna.app.model.SweepResult
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalibrationFileImporterTest {
    private val frequencies = longArrayOf(1_000_000L, 1_000_000_000L, 2_000_000_000L)

    @Test
    fun importsTwoPortSolAndThrough() {
        val json = sampleCal().toString()
        val result = CalibrationFileImporter.parse(json, "TEST")
        assertEquals(2, result.calibration.sol.size)
        assertEquals(2, result.calibration.thrus.size)
        assertTrue(result.summary.contains("SOL 2"))
        assertTrue(result.summary.contains("THRU 1"))

        val sol1 = result.calibration.solFor(1)!!
        assertEquals(1, sol1.port)
        assertEquals(0.0, sol1.e00[1].abs(), 1e-6)
        assertEquals(0.0, sol1.e11[1].abs(), 1e-6)
        assertEquals(1.0, sol1.e01e10[1].abs(), 1e-6)

        val raw = SweepResult(
            freqHz = frequencies.copyOf(),
            s11 = Array(3) { Complex(0.5, 0.0) },
            s21 = Array(3) { Complex(0.8, 0.0) },
            s12 = Array(3) { Complex(0.8, 0.0) },
            s22 = Array(3) { Complex(-0.5, 0.0) },
            cdbm = -10.0,
        )
        val corrected = CalibrationEngine.apply(raw, result.calibration)
        assertEquals(0.5, corrected.s11[1].re, 1e-6)
        assertEquals(-0.5, corrected.s22[1].re, 1e-6)
        assertEquals(0.9, corrected.s21[1].re, 1e-6)
        assertEquals(0.9, corrected.s12[1].re, 1e-6)
    }

    @Test
    fun importsParametricCalkitWithoutTouchstone() {
        val result = CalibrationFileImporter.parse(sampleParametricCal().toString(), "TEST")
        assertEquals(2, result.calibration.sol.size)
        assertEquals(2, result.calibration.thrus.size)
        assertTrue(result.warnings.none { it.contains("Touchstone") })
    }

    @Test
    fun rejectsFileWithoutCalkit() {
        val bad = JSONObject().put("format", 3).toString()
        try {
            CalibrationFileImporter.parse(bad, "TEST")
            throw AssertionError("expected failure")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("calkit"))
        }
    }

    private fun sampleCal(): JSONObject {
        val root = JSONObject()
        root.put("format", 3)
        root.put("type", "SOLT")
        root.put("ports", JSONArray().put(1).put(2))
        root.put("device", "TEST")
        val standards = JSONArray()
        standards.put(onePortStandard(11, "Port 1", "C0", Complex.ONE))
        standards.put(onePortStandard(12, "Port 1", "L0", Complex(-1.0, 0.0)))
        standards.put(onePortStandard(13, "Port 1", "Cfirst", Complex.ZERO))
        standards.put(onePortStandard(21, "Port 2", "C0", Complex.ONE))
        standards.put(onePortStandard(22, "Port 2", "L0", Complex(-1.0, 0.0)))
        standards.put(onePortStandard(23, "Port 2", "Cfirst", Complex.ZERO))
        standards.put(throughStandard(14, "Port 1 to 2", Complex(0.9, 0.0)))
        root.put("calkit", JSONObject().put("standards", standards))

        val measurements = JSONArray()
        measurements.put(onePortMeasurement("Open", 1, 11, Complex.ONE))
        measurements.put(onePortMeasurement("Short", 1, 12, Complex(-1.0, 0.0)))
        measurements.put(onePortMeasurement("Load", 1, 13, Complex.ZERO))
        measurements.put(onePortMeasurement("Open", 2, 21, Complex.ONE))
        measurements.put(onePortMeasurement("Short", 2, 22, Complex(-1.0, 0.0)))
        measurements.put(onePortMeasurement("Load", 2, 23, Complex.ZERO))
        measurements.put(throughMeasurement(14, Complex(0.8, 0.0)))
        root.put("measurements", measurements)
        return root
    }

    private fun sampleParametricCal(): JSONObject {
        val root = JSONObject()
        root.put("format", 3)
        root.put("type", "SOLT")
        root.put("ports", JSONArray().put(1).put(2))
        val standards = JSONArray()
        standards.put(parametricStandard(101, "ECal Port 1 Open", "C0", 0.0))
        standards.put(parametricStandard(102, "ECal Port 1 Short", "L0", 0.0))
        standards.put(parametricStandard(103, "ECal Port 1 Load", "Cfirst", 0.0))
        standards.put(parametricStandard(201, "ECal Port 2 Open", "C0", 0.0))
        standards.put(parametricStandard(202, "ECal Port 2 Short", "L0", 0.0))
        standards.put(parametricStandard(203, "ECal Port 2 Load", "Cfirst", 0.0))
        val through = JSONObject()
        val throughParams = JSONObject()
        throughParams.put("id", 301)
        throughParams.put("name", "Insertable Thru Standard")
        throughParams.put("Z0", 50.0)
        throughParams.put("delay", 0.0)
        throughParams.put("loss", 0.0)
        through.put("params", throughParams)
        standards.put(through)
        root.put("calkit", JSONObject().put("standards", standards))

        val measurements = JSONArray()
        measurements.put(onePortMeasurement("Open", 1, 101, Complex(0.9, 0.0)))
        measurements.put(onePortMeasurement("Short", 1, 102, Complex(-0.9, 0.0)))
        measurements.put(onePortMeasurement("Load", 1, 103, Complex.ZERO))
        measurements.put(onePortMeasurement("Open", 2, 201, Complex(0.9, 0.0)))
        measurements.put(onePortMeasurement("Short", 2, 202, Complex(-0.9, 0.0)))
        measurements.put(onePortMeasurement("Load", 2, 203, Complex.ZERO))
        measurements.put(throughMeasurement(301, Complex(0.8, 0.0)))
        root.put("measurements", measurements)
        return root
    }

    private fun parametricStandard(id: Long, name: String, param: String, value: Double): JSONObject {
        val params = JSONObject()
        params.put("id", id)
        params.put("name", name)
        params.put(param, value)
        params.put("Z0", 50.0)
        params.put("delay", 0.0)
        params.put("loss", 0.0)
        return JSONObject().put("params", params)
    }

    private fun onePortStandard(id: Long, name: String, param: String, s11: Complex): JSONObject {
        val params = JSONObject()
        params.put("id", id)
        params.put("name", name)
        params.put(param, 0.0)
        params.put("touchstone", touchstone(List(3) { listOf(s11) }))
        return JSONObject().put("params", params)
    }

    private fun throughStandard(id: Long, name: String, s21: Complex): JSONObject {
        val params = JSONObject()
        params.put("id", id)
        params.put("name", name)
        params.put("Z0", 50.0)
        params.put("touchstone", touchstone(List(3) { listOf(Complex.ZERO, s21, s21, Complex.ZERO) }))
        return JSONObject().put("params", params)
    }

    private fun touchstone(points: List<List<Complex>>): JSONObject {
        val datapoints = JSONArray()
        for ((index, values) in points.withIndex()) {
            val point = JSONObject()
            point.put("frequency", frequencies[index].toDouble())
            val sp = JSONArray()
            for (v in values) {
                sp.put(JSONObject().put("real", v.re).put("imag", v.im))
            }
            point.put("Sparams", sp)
            datapoints.put(point)
        }
        return JSONObject().put("datapoints", datapoints)
    }

    private fun onePortMeasurement(type: String, port: Int, standard: Long, value: Complex): JSONObject {
        val data = JSONObject()
        data.put("port", port)
        data.put("standard", standard)
        data.put("timestamp", 0)
        val points = JSONArray()
        for (f in frequencies) {
            points.put(JSONObject().put("frequency", f.toDouble()).put("real", value.re).put("imag", value.im))
        }
        data.put("points", points)
        return JSONObject().put("type", type).put("data", data)
    }

    private fun throughMeasurement(standard: Long, measured: Complex): JSONObject {
        val data = JSONObject()
        data.put("port1", 1)
        data.put("port2", 2)
        data.put("standard", standard)
        data.put("reverseStandard", false)
        data.put("timestamp", 0)
        val points = JSONArray()
        for (f in frequencies) {
            val sp = JSONObject()
            sp.put("m11_real", 0.0).put("m11_imag", 0.0)
            sp.put("m12_real", measured.re).put("m12_imag", measured.im)
            sp.put("m21_real", measured.re).put("m21_imag", measured.im)
            sp.put("m22_real", 0.0).put("m22_imag", 0.0)
            points.put(JSONObject().put("frequency", f.toDouble()).put("Sparam", sp))
        }
        data.put("points", points)
        return JSONObject().put("type", "Through").put("data", data)
    }
}