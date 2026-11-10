/*
 * DroidVNA - native Android client for LibreVNA / Harogic B60.
 * Copyright (C) 2026 The DroidVNA Authors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.droidvna.app.model.analysis

import com.droidvna.app.model.Complex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.math.sin

class DtfTest {

    private val speedOfLight = 299_792_458.0

    /** Linear frequency grid with an exactly integral step. */
    private fun linearFrequencies(count: Int = 401, startHz: Long = 1_000_000_000L, stopHz: Long = 2_000_000_000L): LongArray =
        LongArray(count) { startHz + (stopHz - startHz) * it / (count - 1) }

    private fun reflection(
        freqHz: LongArray,
        reflections: List<Pair<Double, Double>>,
    ): Array<Complex> = Array(freqHz.size) { i ->
        var re = 0.0
        var im = 0.0
        for ((delaySeconds, amplitude) in reflections) {
            val angle = -2.0 * PI * freqHz[i] * delaySeconds
            re += amplitude * cos(angle)
            im += amplitude * sin(angle)
        }
        Complex(re, im)
    }

    private fun distanceFor(delaySeconds: Double, velocityFactor: Double = 1.0): Double =
        speedOfLight * velocityFactor * delaySeconds / 2.0

    @Test
    fun findsFaultDistancesOfSyntheticReflections() {
        val freq = linearFrequencies()
        val values = reflection(
            freq,
            listOf(
                20e-9 to 1.0,
                60e-9 to 0.5,
            ),
        )
        val result = Dtf.analyze(
            freq,
            values,
            DtfConfig(
                window = DtfWindow.HANN,
                velocityFactor = 1.0,
                zeroPadding = 4,
                stopDistanceM = 15.0,
                peakThresholdDb = -30.0,
            ),
        )
        assertNotNull(result)
        result!!
        val expectedOne = distanceFor(20e-9)
        val expectedTwo = distanceFor(60e-9)
        val strongest = result.faults.maxByOrNull { it.magnitudeDb }
        assertNotNull(strongest)
        assertEquals(expectedOne, strongest!!.distanceM, 0.3)
        assertTrue(
            "second reflection not found: " + result.faults.map { it.distanceM },
            result.faults.any { abs(it.distanceM - expectedTwo) < 0.3 },
        )
        assertEquals(result.faults.sortedBy { it.distanceM }, result.faults)
        assertTrue(result.faults.isNotEmpty())
        assertTrue(result.resolutionM > 0.0)
        assertTrue(result.fftSize >= freq.size)
        assertEquals(1.0, result.velocityFactor, 1e-9)
    }

    @Test
    fun velocityFactorScalesTheDistanceAxis() {
        val freq = linearFrequencies()
        val values = reflection(freq, listOf(20e-9 to 1.0))
        val result = Dtf.analyze(
            freq,
            values,
            DtfConfig(window = DtfWindow.HANN, velocityFactor = 0.66, stopDistanceM = 10.0),
        )
        assertNotNull(result)
        val strongest = result!!.faults.maxByOrNull { it.magnitudeDb }!!
        assertEquals(distanceFor(20e-9, 0.66), strongest.distanceM, 0.2)
        assertEquals(0.66, result.velocityFactor, 1e-9)
    }

    @Test
    fun logFrequencySweepIsResampledBeforeTheInverseTransform() {
        val count = 401
        val start = 1_000_000_000.0
        val stop = 2_000_000_000.0
        val ratio = stop / start
        val freq = LongArray(count) {
            (start * ratio.pow(it.toDouble() / (count - 1))).roundToLong()
        }
        val values = reflection(freq, listOf(20e-9 to 1.0))
        val result = Dtf.analyze(
            freq,
            values,
            DtfConfig(window = DtfWindow.HAMMING, velocityFactor = 1.0, stopDistanceM = 10.0),
        )
        assertNotNull(result)
        val strongest = result!!.faults.maxByOrNull { it.magnitudeDb }!!
        assertEquals(distanceFor(20e-9), strongest.distanceM, 0.5)
    }

    @Test
    fun tooFewOrInvalidSamplesReturnNull() {
        val freq = LongArray(4) { 1_000_000_000L + it * 1_000_000L }
        val values = Array(4) { Complex.ONE }
        assertNull(Dtf.analyze(freq, values, DtfConfig()))
        assertNull(
            Dtf.analyze(
                linearFrequencies(),
                Array(401) { Complex.NAN },
                DtfConfig(),
            ),
        )
    }

    @Test
    fun peakThresholdLimitsTheFaultTable() {
        val freq = linearFrequencies()
        val values = reflection(
            freq,
            listOf(
                20e-9 to 1.0,
                60e-9 to 0.01, // -40 dB
            ),
        )
        val strict = Dtf.analyze(
            freq,
            values,
            DtfConfig(window = DtfWindow.HANN, velocityFactor = 1.0, stopDistanceM = 15.0, peakThresholdDb = -20.0),
        )
        assertNotNull(strict)
        assertTrue(strict!!.faults.all { it.magnitudeDb >= -20.0 })
        assertTrue(strict.faults.none { abs(it.distanceM - distanceFor(60e-9)) < 0.3 })
    }

    @Test
    fun zeroDelayReflectionIsNotDuplicatedAtTheFarEdge() {
        val freq = linearFrequencies()
        val values = reflection(freq, listOf(0.0 to 1.0))
        val result = Dtf.analyze(
            freq,
            values,
            DtfConfig(window = DtfWindow.HANN, velocityFactor = 1.0),
        )
        assertNotNull(result)
        result!!
        assertEquals(1, result.faults.size)
        assertTrue(result.wrappedEdgeTrimmed)
        assertTrue(abs(result.faults[0].distanceM) < 0.3)
        assertTrue(
            "a wrap-around duplicate must be trimmed",
            result.faults.all { it.distanceM < result.maxDistanceM - result.resolutionM * 0.5 },
        )
    }

    @Test
    fun displayStopTruncatesTheResult() {
        val freq = linearFrequencies()
        val values = reflection(freq, listOf(20e-9 to 1.0, 60e-9 to 0.5))
        val result = Dtf.analyze(
            freq,
            values,
            DtfConfig(window = DtfWindow.HANN, velocityFactor = 1.0, stopDistanceM = 5.0),
        )
        assertNotNull(result)
        assertTrue(result!!.displayStopM <= 5.0 + 1e-9)
        assertFalse(result.wrappedEdgeTrimmed)
        assertTrue(result.samples.all { it.distanceM <= result.displayStopM + 1e-9 })
        assertTrue(result.faults.all { it.distanceM <= result.displayStopM + 1e-9 })
    }
}
