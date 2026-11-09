/*
 * DroidVNA - native Android client for LibreVNA / Harogic B60.
 * Copyright (C) 2026 The DroidVNA Authors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.droidvna.app

import com.droidvna.app.model.CalStandardModel
import com.droidvna.app.model.CalibrationFileImporter
import com.droidvna.app.model.Complex
import com.droidvna.app.model.IsolationMeasurement
import com.droidvna.app.model.OnePortStandards
import com.droidvna.app.model.SoltCal
import com.droidvna.app.model.SoltMath
import com.droidvna.app.model.SweepResult
import com.droidvna.app.model.TwoPortStandard
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Guards around the happy path: mismatched grids, truncated snapshots, degenerate standards and
 * the DC edge of the standard models.
 */
class CalibrationRobustnessTest {

    private fun assertComplex(expected: Complex, actual: Complex, tolerance: Double = 1e-12) {
        assertTrue(
            "expected $expected but was $actual",
            abs(expected.re - actual.re) < tolerance && abs(expected.im - actual.im) < tolerance,
        )
    }

    // ------------------------------------------------------------------ standard models at DC

    @Test
    fun standardModelsStayFiniteAtDc() {
        // A parametric kit with real parasitics would divide by zero at 0 Hz without the guards.
        val through = CalStandardModel.Through(z0 = 50.0, delayPs = 0.0, loss = 0.5)
        assertComplex(Complex.ONE, through.s21At(0.0))
        assertComplex(Complex.ZERO, through.s11At(0.0))

        val open = CalStandardModel.Open(50.0, 0.0, 0.0, c0 = 50.0, c1 = 0.0, c2 = 0.0, c3 = 0.0)
        assertComplex(Complex.ONE, open.s11At(0.0))

        val load = CalStandardModel.Load(
            z0 = 50.0,
            delayPs = 0.0,
            loss = 0.0,
            resistance = 50.0,
            cFirst = true,
            cParallel = 1e-13,
            lSeries = 0.0,
        )
        val loadDc = load.s11At(0.0)
        assertTrue("load at DC must be finite, was $loadDc", loadDc.isFinite())
        assertComplex(Complex.ZERO, loadDc, 1e-12)

        // Sanity: the same models still produce normal values above DC.
        assertTrue(through.s21At(1e9).isFinite())
        assertTrue(open.s11At(1e9).isFinite())
        assertTrue(load.s11At(1e9).isFinite())
    }

    // ------------------------------------------------------------------ single-point calibrations

    @Test
    fun solveRejectsASingleFrequencyPoint() {
        val one = longArrayOf(1_000_000_000L)
        fun arr(c: Complex) = arrayOf(c)
        val standards = OnePortStandards(
            measuredOpen = arr(Complex.ONE),
            measuredShort = arr(Complex(-1.0, 0.0)),
            measuredLoad = arr(Complex.ZERO),
            actualOpen = arr(Complex.ONE),
            actualShort = arr(Complex(-1.0, 0.0)),
            actualLoad = arr(Complex.ZERO),
        )
        val thru = TwoPortStandard(
            measured11 = arr(Complex.ZERO), measured21 = arr(Complex.ONE),
            measured12 = arr(Complex.ONE), measured22 = arr(Complex.ZERO),
            actual11 = arr(Complex.ZERO), actual21 = arr(Complex.ONE),
            actual12 = arr(Complex.ONE), actual22 = arr(Complex.ZERO),
        )
        assertNull(
            "a one-point calibration cannot be interpolated",
            SoltMath.solve(one, standards, standards, thru),
        )
    }

    // ------------------------------------------------------------------ truncated snapshots

    @Test
    fun truncatedCalibrationIsRejectedInsteadOfCrashing() {
        val frequencies = longArrayOf(1_000_000_000L, 2_000_000_000L)
        val cal = wellFormedCalibration(frequencies)
        assertTrue(cal.isConsistent())

        val raw = SweepResult(
            freqHz = frequencies.copyOf(),
            s11 = Array(2) { Complex(0.3, 0.0) },
            s21 = Array(2) { Complex(0.7, 0.0) },
            s12 = Array(2) { Complex(0.7, 0.0) },
            s22 = Array(2) { Complex(-0.3, 0.0) },
            cdbm = -10.0,
        )
        assertNotNull("the well-formed model must correct", SoltMath.correct(raw, cal))

        // Simulate a truncated persisted array (freq has 2 entries, coefficients only 1).
        val broken: SoltCal = cal.copy(loadMatch12 = arrayOf(cal.loadMatch12[0]))
        assertFalse(broken.isConsistent())
        assertNull("must not index past the truncated array", SoltMath.correct(raw, broken))
    }

    private fun wellFormedCalibration(frequencies: LongArray): SoltCal {
        val n = frequencies.size
        fun arr(c: Complex) = Array(n) { c }
        val standards = OnePortStandards(
            measuredOpen = arr(Complex(0.9, 0.05)),
            measuredShort = arr(Complex(-0.85, 0.02)),
            measuredLoad = arr(Complex(0.02, 0.0)),
            actualOpen = arr(Complex.ONE),
            actualShort = arr(Complex(-1.0, 0.0)),
            actualLoad = arr(Complex.ZERO),
        )
        val thru = TwoPortStandard(
            measured11 = arr(Complex(0.02, 0.0)), measured21 = arr(Complex(0.8, -0.1)),
            measured12 = arr(Complex(0.8, -0.1)), measured22 = arr(Complex(0.02, 0.0)),
            actual11 = arr(Complex.ZERO), actual21 = arr(Complex.ONE),
            actual12 = arr(Complex.ONE), actual22 = arr(Complex.ZERO),
        )
        val isolation = IsolationMeasurement(
            measured21 = arr(Complex.ZERO),
            measured12 = arr(Complex.ZERO),
        )
        return SoltMath.solve(frequencies, standards, standards, thru, isolation)!!
    }

    // ------------------------------------------------------------------ degenerate standards

    @Test
    fun nearlyIdenticalStandardsAreRejected() {
        val f = longArrayOf(1_000_000_000L, 2_000_000_000L)
        // Open and Short almost identical: the determinant survives but the solution explodes.
        val open = Array(f.size) { Complex(0.5, 0.0) }
        val short = Array(f.size) { Complex(0.5 - 1e-6, 0.0) }
        val load = Array(f.size) { Complex.ZERO }
        val actualOpen = Array(f.size) { Complex.ONE }
        val actualShort = Array(f.size) { Complex(-1.0, 0.0) }
        val actualLoad = Array(f.size) { Complex.ZERO }
        assertNull(
            "a blown-up source match must not become a calibration",
            SoltMath.portTerms(open, short, load, actualOpen, actualShort, actualLoad),
        )
    }

    @Test
    fun reasonableStandardsStillSolve() {
        val f = longArrayOf(1_000_000_000L, 2_000_000_000L)
        val open = Array(f.size) { Complex(0.9, 0.05) }
        val short = Array(f.size) { Complex(-0.85, 0.02) }
        val load = Array(f.size) { Complex(0.02, 0.0) }
        val actualOpen = Array(f.size) { Complex.ONE }
        val actualShort = Array(f.size) { Complex(-1.0, 0.0) }
        val actualLoad = Array(f.size) { Complex.ZERO }
        assertNotNull(SoltMath.portTerms(open, short, load, actualOpen, actualShort, actualLoad))
    }

    // ------------------------------------------------------------------ importer coverage

    @Test
    fun importerRefusesFullSoltWhenThroughMissesTheSolBand() {
        // SOL on both ports spans 1..2 GHz, but the THRU only spans 1.5..2 GHz. Resampling it onto
        // the wider grid would clamp 1.0-1.5 GHz to the endpoint value, so the importer must
        // decline the full model and warn instead.
        val solGrid = longArrayOf(1_000_000_000L, 1_500_000_000L, 2_000_000_000L)
        val thruGrid = longArrayOf(1_500_000_000L, 2_000_000_000L)
        val json = sampleCal(solGrid, thruGrid)
        val result = CalibrationFileImporter.parse(json.toString(), "TEST")

        assertNull("a partially covered through must not yield a full SOLT", result.calibration.solt)
        assertTrue(
            "expected a coverage warning, got ${result.warnings}",
            result.warnings.any { it.contains("没有覆盖") },
        )
        // The 1-port subset is still usable as a fallback.
        assertEquals(2, result.calibration.sol.size)
    }

    private fun sampleCal(solGrid: LongArray, thruGrid: LongArray): JSONObject {
        val standards = JSONArray()
        standards.put(parametric(11, "Port 1", "C0"))
        standards.put(parametric(12, "Port 1", "L0"))
        standards.put(parametric(13, "Port 1", "Cfirst"))
        standards.put(parametric(21, "Port 2", "C0"))
        standards.put(parametric(22, "Port 2", "L0"))
        standards.put(parametric(23, "Port 2", "Cfirst"))
        standards.put(throughStandard(14))

        val measurements = JSONArray()
        measurements.put(onePortMeasurement("Open", 1, 11, solGrid, Complex(0.9, 0.0)))
        measurements.put(onePortMeasurement("Short", 1, 12, solGrid, Complex(-0.85, 0.0)))
        measurements.put(onePortMeasurement("Load", 1, 13, solGrid, Complex(0.02, 0.0)))
        measurements.put(onePortMeasurement("Open", 2, 21, solGrid, Complex(0.9, 0.0)))
        measurements.put(onePortMeasurement("Short", 2, 22, solGrid, Complex(-0.85, 0.0)))
        measurements.put(onePortMeasurement("Load", 2, 23, solGrid, Complex(0.02, 0.0)))
        measurements.put(throughMeasurement(14, thruGrid))

        return JSONObject()
            .put("format", 3)
            .put("type", "SOLT")
            .put("ports", JSONArray().put(1).put(2))
            .put("calkit", JSONObject().put("standards", standards))
            .put("measurements", measurements)
    }

    private fun parametric(id: Long, name: String, param: String): JSONObject {
        val params = JSONObject()
        params.put("id", id)
        params.put("name", name)
        params.put(param, 0.0)
        params.put("Z0", 50.0)
        params.put("delay", 0.0)
        params.put("loss", 0.0)
        return JSONObject().put("params", params)
    }

    private fun throughStandard(id: Long): JSONObject {
        val params = JSONObject()
        params.put("id", id)
        params.put("name", "Port 1 to 2")
        params.put("Z0", 50.0)
        params.put("delay", 0.0)
        params.put("loss", 0.0)
        return JSONObject().put("params", params)
    }

    private fun onePortMeasurement(
        type: String,
        port: Int,
        standard: Long,
        grid: LongArray,
        value: Complex,
    ): JSONObject {
        val data = JSONObject()
        data.put("port", port)
        data.put("standard", standard)
        data.put("timestamp", 0)
        val points = JSONArray()
        for (f in grid) {
            points.put(
                JSONObject()
                    .put("frequency", f.toDouble())
                    .put("real", value.re)
                    .put("imag", value.im)
            )
        }
        data.put("points", points)
        return JSONObject().put("type", type).put("data", data)
    }

    private fun throughMeasurement(standard: Long, grid: LongArray): JSONObject {
        val data = JSONObject()
        data.put("port1", 1)
        data.put("port2", 2)
        data.put("standard", standard)
        data.put("reverseStandard", false)
        data.put("timestamp", 0)
        val points = JSONArray()
        for (f in grid) {
            val sp = JSONObject()
            sp.put("m11_real", 0.02).put("m11_imag", 0.0)
            sp.put("m12_real", 0.8).put("m12_imag", -0.1)
            sp.put("m21_real", 0.8).put("m21_imag", -0.1)
            sp.put("m22_real", 0.02).put("m22_imag", 0.0)
            points.put(JSONObject().put("frequency", f.toDouble()).put("Sparam", sp))
        }
        data.put("points", points)
        return JSONObject().put("type", "Through").put("data", data)
    }
}
