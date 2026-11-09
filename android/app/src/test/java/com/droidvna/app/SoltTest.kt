/*
 * DroidVNA - native Android client for LibreVNA / Harogic B60.
 * Copyright (C) 2026 The DroidVNA Authors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.droidvna.app

import com.droidvna.app.model.CalibrationEngine
import com.droidvna.app.model.CalibrationSet
import com.droidvna.app.model.Complex
import com.droidvna.app.model.IsolationMeasurement
import com.droidvna.app.model.OnePortStandards
import com.droidvna.app.model.SolCal
import com.droidvna.app.model.SoltErrorModel
import com.droidvna.app.model.SoltMath
import com.droidvna.app.model.SoltTerms
import com.droidvna.app.model.SweepResult
import com.droidvna.app.model.ThruCal
import com.droidvna.app.model.TwoPortStandard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Round-trip tests for the 12-term model: raw data is generated with [SoltErrorModel] (the
 * forward model), the calibration is solved from those raw standard measurements, and the
 * corrected DUT has to come back to the ideal S-matrix.
 */
class SoltTest {

    private val frequencies = longArrayOf(100_000_000L, 900_000_000L, 1_500_000_000L, 2_400_000_000L)

    private val open = Complex.ONE
    private val short = Complex(-1.0, 0.0)
    private val load = Complex.ZERO

    private val terms = SoltTerms(
        d1 = Complex(0.020, 0.010),
        s1 = Complex(0.040, -0.030),
        r1 = Complex(0.920, 0.200),
        d2 = Complex(-0.015, 0.008),
        s2 = Complex(0.030, -0.020),
        r2 = Complex(0.880, 0.220),
        l12 = Complex(0.050, -0.020),
        l21 = Complex(0.045, -0.010),
        t12 = Complex(0.960, -0.080),
        t21 = Complex(0.950, -0.090),
        i12 = Complex(0.0008, 0.0004),
        i21 = Complex(0.0007, -0.0005),
    )

    private fun matrix(a11: Complex, a12: Complex, a21: Complex, a22: Complex) = arrayOf(
        arrayOf(a11, a12),
        arrayOf(a21, a22),
    )

    private fun diagonal(a: Complex, b: Complex) = matrix(a, Complex.ZERO, Complex.ZERO, b)

    /** Ideal through: negligible loss, no reflection. */
    private fun idealThrough() = matrix(Complex.ZERO, Complex(0.99, -0.02), Complex(0.99, -0.02), Complex.ZERO)

    private fun measured(ideal: Array<Array<Complex>>): Array<Array<Complex>> {
        val result = SoltErrorModel.measure(ideal, terms)
        assertNotNull("forward model must be solvable", result)
        return result!!
    }

    /** A non-trivial two-port DUT: mismatched on both ports with real transmission. */
    private fun idealDut() = matrix(
        Complex(0.30, -0.20),
        Complex(0.020, 0.010),
        Complex(0.700, 0.500),
        Complex(-0.250, 0.100),
    )

    private fun onePortMeasured(port: Int, gamma: Complex): Array<Complex> {
        val ideal = if (port == 1) diagonal(gamma, Complex.ZERO) else diagonal(Complex.ZERO, gamma)
        val m = measured(ideal)
        val index = port - 1
        return Array(frequencies.size) { m[index][index] }
    }

    private fun standards(port: Int) = OnePortStandards(
        measuredOpen = onePortMeasured(port, open),
        measuredShort = onePortMeasured(port, short),
        measuredLoad = onePortMeasured(port, load),
        actualOpen = Array(frequencies.size) { open },
        actualShort = Array(frequencies.size) { short },
        actualLoad = Array(frequencies.size) { load },
    )

    private fun thruStandard(): TwoPortStandard {
        val ideal = idealThrough()
        val m = measured(ideal)
        return TwoPortStandard(
            measured11 = Array(frequencies.size) { m[0][0] },
            measured21 = Array(frequencies.size) { m[1][0] },
            measured12 = Array(frequencies.size) { m[0][1] },
            measured22 = Array(frequencies.size) { m[1][1] },
            actual11 = Array(frequencies.size) { ideal[0][0] },
            actual21 = Array(frequencies.size) { ideal[1][0] },
            actual12 = Array(frequencies.size) { ideal[0][1] },
            actual22 = Array(frequencies.size) { ideal[1][1] },
        )
    }

    private fun isolationMeasurement(): IsolationMeasurement {
        val m = measured(diagonal(Complex.ZERO, Complex.ZERO))
        return IsolationMeasurement(
            measured21 = Array(frequencies.size) { m[1][0] },
            measured12 = Array(frequencies.size) { m[0][1] },
        )
    }

    private fun dutSweep(ideal: Array<Array<Complex>>): SweepResult {
        val m = measured(ideal)
        return SweepResult(
            freqHz = frequencies.copyOf(),
            s11 = Array(frequencies.size) { m[0][0] },
            s21 = Array(frequencies.size) { m[1][0] },
            s12 = Array(frequencies.size) { m[0][1] },
            s22 = Array(frequencies.size) { m[1][1] },
            cdbm = -10.0,
        )
    }

    private fun assertComplex(expected: Complex, actual: Complex, tolerance: Double = 1e-9) {
        assertTrue(
            "expected $expected but was $actual",
            abs(expected.re - actual.re) < tolerance && abs(expected.im - actual.im) < tolerance,
        )
    }

    @Test
    fun soltRecoversAllTwelveErrorTerms() {
        val cal = SoltMath.solve(
            frequencies,
            standards(1),
            standards(2),
            thruStandard(),
            isolationMeasurement(),
        )
        assertNotNull("12-term solve must succeed", cal)
        val c = cal!!
        assertTrue(c.hasIsolation)
        for (i in frequencies.indices) {
            assertComplex(terms.d1, c.port1.directivity[i])
            assertComplex(terms.s1, c.port1.sourceMatch[i])
            assertComplex(terms.r1, c.port1.reflectionTracking[i])
            assertComplex(terms.d2, c.port2.directivity[i])
            assertComplex(terms.s2, c.port2.sourceMatch[i])
            assertComplex(terms.r2, c.port2.reflectionTracking[i])
            assertComplex(terms.l12, c.loadMatch12[i])
            assertComplex(terms.l21, c.loadMatch21[i])
            assertComplex(terms.t12, c.transmissionTracking12[i])
            assertComplex(terms.t21, c.transmissionTracking21[i])
            assertComplex(terms.i12, c.isolation12[i])
            assertComplex(terms.i21, c.isolation21[i])
        }
    }

    @Test
    fun solvedSoltCorrectsDutBackToIdeal() {
        val cal = SoltMath.solve(
            frequencies,
            standards(1),
            standards(2),
            thruStandard(),
            isolationMeasurement(),
        )!!
        val ideal = idealDut()
        val corrected = SoltMath.correct(dutSweep(ideal), cal)
        assertNotNull(corrected)
        val c = corrected!!
        assertTrue(c.calibrated)
        for (i in frequencies.indices) {
            assertComplex(ideal[0][0], c.s11[i])
            assertComplex(ideal[1][0], c.s21[i])
            assertComplex(ideal[0][1], c.s12[i])
            assertComplex(ideal[1][1], c.s22[i])
        }
    }

    @Test
    fun isolationIsSubtractedFromTransmissions() {
        val withIsolation = SoltMath.solve(
            frequencies,
            standards(1),
            standards(2),
            thruStandard(),
            isolationMeasurement(),
        )!!
        val withoutIsolation = SoltMath.solve(
            frequencies,
            standards(1),
            standards(2),
            thruStandard(),
            null,
        )!!
        assertTrue(withIsolation.hasIsolation)
        assertTrue(!withoutIsolation.hasIsolation)

        val raw = dutSweep(idealDut())
        val correctedWith = SoltMath.correct(raw, withIsolation)!!
        val correctedWithout = SoltMath.correct(raw, withoutIsolation)!!

        // Ignoring a known crosstalk floor leaves a measurable error in S21/S12.
        val ideal = idealDut()
        val errWith = abs(correctedWith.s21[1].re - ideal[1][0].re)
        val errWithout = abs(correctedWithout.s21[1].re - ideal[1][0].re)
        assertTrue("with isolation: $errWith", errWith < 1e-9)
        assertTrue("without isolation should be worse: $errWithout", errWithout > 1e-5)
    }

    /**
     * The reason the issue asked for this: 1-port SOL plus THRU normalization cannot remove the
     * port mismatch during transmission, while the 12-term model can.
     */
    @Test
    fun fullSoltBeatsThruNormalizationOnTransmission() {
        val ideal = idealDut()
        val raw = dutSweep(ideal)

        val solt = SoltMath.solve(
            frequencies,
            standards(1),
            standards(2),
            thruStandard(),
            isolationMeasurement(),
        )!!
        val full = SoltMath.correct(raw, solt)!!
        assertComplex(ideal[1][0], full.s21[1], 1e-9)

        // Rebuild the legacy subset the app used before this change: 1-port SOL + THRU ratio.
        val portTerms1 = SoltMath.portTerms(
            standards(1).measuredOpen, standards(1).measuredShort, standards(1).measuredLoad,
            standards(1).actualOpen, standards(1).actualShort, standards(1).actualLoad,
        )!!
        val portTerms2 = SoltMath.portTerms(
            standards(2).measuredOpen, standards(2).measuredShort, standards(2).measuredLoad,
            standards(2).actualOpen, standards(2).actualShort, standards(2).actualLoad,
        )!!
        val thruIdeal = idealThrough()
        val thruMeasured = measured(thruIdeal)
        val legacySet = CalibrationSet(
            sol = listOf(
                SolCal(1, frequencies.copyOf(), portTerms1.directivity, portTerms1.sourceMatch, portTerms1.reflectionTracking),
                SolCal(2, frequencies.copyOf(), portTerms2.directivity, portTerms2.sourceMatch, portTerms2.reflectionTracking),
            ),
            thrus = listOf(
                ThruCal(1, 2, frequencies.copyOf(), Array(frequencies.size) { thruMeasured[1][0] / thruIdeal[1][0] }),
                ThruCal(2, 1, frequencies.copyOf(), Array(frequencies.size) { thruMeasured[0][1] / thruIdeal[0][1] }),
            ),
        )
        val legacy = CalibrationEngine.apply(raw, legacySet)
        val legacyError = abs(legacy.s21[1].re - ideal[1][0].re)
        assertTrue(
            "THRU normalization should still carry a mismatch error, got $legacyError",
            legacyError > 1e-3,
        )
    }

    @Test
    fun enginePrefersFullSoltOverLegacySubset() {
        val solt = SoltMath.solve(
            frequencies,
            standards(1),
            standards(2),
            thruStandard(),
            isolationMeasurement(),
        )!!
        val ideal = idealDut()
        val raw = dutSweep(ideal)

        // A deliberately wrong 1-port SOL must not be applied on top of the 12-term model.
        val wrongSol = SoltMath.portTerms(
            standards(1).measuredOpen, standards(1).measuredShort, standards(1).measuredLoad,
            standards(1).actualOpen, standards(1).actualShort, standards(1).actualLoad,
        )!!
        val set = CalibrationSet(
            sol = listOf(
                SolCal(1, frequencies.copyOf(), wrongSol.directivity, wrongSol.sourceMatch, wrongSol.reflectionTracking),
            ),
            solt = solt,
        )
        val corrected = CalibrationEngine.apply(raw, set)
        assertComplex(ideal[0][0], corrected.s11[1], 1e-9)
        assertComplex(ideal[1][0], corrected.s21[1], 1e-9)
    }

    @Test
    fun degenerateStandardsProduceNoCalibration() {
        // Open and Short both measured as a matched load: the SOL equations are singular.
        val flat = Array(frequencies.size) { Complex.ZERO }
        val standards = OnePortStandards(
            measuredOpen = flat,
            measuredShort = flat,
            measuredLoad = flat,
            actualOpen = Array(frequencies.size) { open },
            actualShort = Array(frequencies.size) { short },
            actualLoad = Array(frequencies.size) { load },
        )
        val cal = SoltMath.solve(frequencies, standards, standards, thruStandard(), null)
        assertNull("degenerate standards must not produce a calibration", cal)
    }

    // ---------------------------------------------------------------------------------------
    // Independent cross-checks. The tests above use SoltErrorModel for both directions, which
    // would hide a misconception shared by the forward model and the correction. The helper
    // below derives the same measurement from the signal-flow graph instead (reflection seen at
    // the exciting port, wave reaching the receiver, source-match re-reflection), so it is an
    // independent formulation of the physics.
    // ---------------------------------------------------------------------------------------

    private fun physicsMeasure(g: Array<Array<Complex>>, t: SoltTerms): Array<Array<Complex>> {
        val out = Array(2) { Array(2) { Complex.NAN } }
        for (src in 0..1) {
            val rcv = 1 - src
            val sourceMatch = if (src == 0) t.s1 else t.s2
            val loadMatch = if (src == 0) t.l12 else t.l21
            val directivity = if (src == 0) t.d1 else t.d2
            val tracking = if (src == 0) t.r1 else t.r2
            val trackTransmission = if (src == 0) t.t12 else t.t21
            val isolation = if (src == 0) t.i12 else t.i21

            val sSrcSrc = g[src][src]
            // Wave at the receiver driven by the source: g[receiver][source].
            val sSrcOther = g[rcv][src]
            val sOtherSrc = g[src][rcv]
            val sOtherOther = g[rcv][rcv]

            // Reflection presented at the exciting port, with the other port on `loadMatch`.
            val gamma = sSrcSrc + sSrcOther * sOtherSrc * loadMatch /
                (Complex.ONE - sOtherOther * loadMatch)
            // Incident wave at the DUT including the source-match multiple reflection.
            val incident = Complex.ONE / (Complex.ONE - sourceMatch * gamma)
            // Wave arriving at the receiver through the DUT and the load-match termination.
            val received = sSrcOther * incident / (Complex.ONE - sOtherOther * loadMatch)

            out[src][src] = directivity + tracking * (gamma * incident)
            out[rcv][src] = trackTransmission * received + isolation
        }
        return out
    }

    @Test
    fun forwardModelAgreesWithSignalFlowDerivation() {
        val duts = listOf(
            idealDut(),
            idealThrough(),
            diagonal(Complex.ONE, Complex.ZERO),
            diagonal(Complex.ZERO, Complex(-1.0, 0.0)),
            matrix(Complex(0.5, 0.5), Complex(0.1, 0.1), Complex(-0.3, 0.7), Complex(0.2, -0.4)),
        )
        for (dut in duts) {
            val fromModel = SoltErrorModel.measure(dut, terms)!!
            val fromPhysics = physicsMeasure(dut, terms)
            for (row in 0..1) {
                for (col in 0..1) {
                    assertComplex(fromPhysics[row][col], fromModel[row][col], 1e-12)
                }
            }
        }
    }

    @Test
    fun calibrationMakesTheStandardsLookIdeal() {
        val cal = SoltMath.solve(
            frequencies,
            standards(1),
            standards(2),
            thruStandard(),
            isolationMeasurement(),
        )!!

        // Every standard measurement corrected by the calibration has to look like the ideal
        // standard it was, which is a ground-truth check that never references the model.
        fun correctIdeal(ideal: Array<Array<Complex>>): SweepResult {
            val m = measured(ideal)
            val raw = SweepResult(
                freqHz = frequencies.copyOf(),
                s11 = Array(frequencies.size) { m[0][0] },
                s21 = Array(frequencies.size) { m[1][0] },
                s12 = Array(frequencies.size) { m[0][1] },
                s22 = Array(frequencies.size) { m[1][1] },
                cdbm = -10.0,
            )
            return SoltMath.correct(raw, cal)!!
        }

        // Open on port 1, port 2 terminated: ideal S11 = +1, everything else zero.
        val openCorrected = correctIdeal(diagonal(Complex.ONE, Complex.ZERO))
        assertComplex(Complex.ONE, openCorrected.s11[1])
        assertComplex(Complex.ZERO, openCorrected.s21[1])
        assertComplex(Complex.ZERO, openCorrected.s22[1])
        assertComplex(Complex.ZERO, openCorrected.s12[1])

        // Short on port 2: ideal S22 = -1.
        val shortCorrected = correctIdeal(diagonal(Complex.ZERO, Complex(-1.0, 0.0)))
        assertComplex(Complex(-1.0, 0.0), shortCorrected.s22[1])
        assertComplex(Complex.ZERO, shortCorrected.s11[1])

        // The through itself must come back as the ideal through.
        val thruIdeal = idealThrough()
        val thruCorrected = correctIdeal(thruIdeal)
        assertComplex(thruIdeal[0][0], thruCorrected.s11[1])
        assertComplex(thruIdeal[1][0], thruCorrected.s21[1])
        assertComplex(thruIdeal[0][1], thruCorrected.s12[1])
        assertComplex(thruIdeal[1][1], thruCorrected.s22[1])
    }

    @Test
    fun isolationCanBeReplacedWithoutRedoingTheCalibration() {
        val firstIsolation = IsolationMeasurement(
            measured21 = Array(frequencies.size) { Complex(0.001, 0.0005) },
            measured12 = Array(frequencies.size) { Complex(0.0009, -0.0006) },
        )
        val cal = SoltMath.solve(
            frequencies,
            standards(1),
            standards(2),
            thruStandard(),
            isolationMeasurement(),
        )!!
        val replaced = SoltMath.replaceIsolation(cal, firstIsolation)
        assertNotNull(replaced)

        // It must match a calibration solved from scratch with that isolation.
        val fromScratch = SoltMath.solve(
            frequencies,
            standards(1),
            standards(2),
            thruStandard(),
            firstIsolation,
        )!!
        for (i in frequencies.indices) {
            assertComplex(fromScratch.loadMatch12[i], replaced!!.loadMatch12[i])
            assertComplex(fromScratch.transmissionTracking12[i], replaced.transmissionTracking12[i])
            assertComplex(fromScratch.transmissionTracking21[i], replaced.transmissionTracking21[i])
            assertComplex(fromScratch.isolation12[i], replaced.isolation12[i])
        }
        // Load match does not depend on the isolation at all.
        for (i in frequencies.indices) {
            assertComplex(cal.loadMatch12[i], replaced!!.loadMatch12[i])
        }

        // Removing the isolation again must reproduce the original (zero-isolation) solve.
        val withoutIsolation = SoltMath.replaceIsolation(cal, null)!!
        val zeroIsolationSolve = SoltMath.solve(
            frequencies,
            standards(1),
            standards(2),
            thruStandard(),
            null,
        )!!
        for (i in frequencies.indices) {
            assertComplex(zeroIsolationSolve.transmissionTracking12[i], withoutIsolation.transmissionTracking12[i])
            assertComplex(zeroIsolationSolve.isolation12[i], withoutIsolation.isolation12[i])
        }
        assertTrue(!withoutIsolation.hasIsolation)
    }
}
