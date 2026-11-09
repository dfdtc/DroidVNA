/*
 * DroidVNA - native Android client for LibreVNA / Harogic B60.
 * Copyright (C) 2026 The DroidVNA Authors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.droidvna.app

import com.droidvna.app.model.CalibrationSet
import com.droidvna.app.model.Complex
import com.droidvna.app.model.PortErrorTerms
import com.droidvna.app.model.SolCal
import com.droidvna.app.model.SoltCal
import com.droidvna.app.model.SoltMath
import com.droidvna.app.model.ThruCal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The measure screen warns when the sweep reaches outside the calibrated band; these tests pin
 * down which band is reported for each kind of calibration.
 */
class CalibrationCoverageTest {

    private val twoPoints = longArrayOf(1_000_000_000L, 2_000_000_000L)

    private fun arrayOf(constant: Complex, n: Int) = Array(n) { constant }

    private fun solt(freq: LongArray): SoltCal {
        val n = freq.size
        return SoltCal(
            freqHz = freq.copyOf(),
            port1 = PortErrorTerms(arrayOf(Complex.ZERO, n), arrayOf(Complex.ZERO, n), arrayOf(Complex.ONE, n)),
            port2 = PortErrorTerms(arrayOf(Complex.ZERO, n), arrayOf(Complex.ZERO, n), arrayOf(Complex.ONE, n)),
            loadMatch12 = arrayOf(Complex.ZERO, n),
            loadMatch21 = arrayOf(Complex.ZERO, n),
            transmissionTracking12 = arrayOf(Complex.ONE, n),
            transmissionTracking21 = arrayOf(Complex.ONE, n),
            isolation12 = arrayOf(Complex.ZERO, n),
            isolation21 = arrayOf(Complex.ZERO, n),
        )
    }

    private fun sol(port: Int, freq: LongArray) = SolCal(
        port = port,
        freqHz = freq.copyOf(),
        e00 = arrayOf(Complex.ZERO, freq.size),
        e11 = arrayOf(Complex.ZERO, freq.size),
        e01e10 = arrayOf(Complex.ONE, freq.size),
    )

    private fun thru(freq: LongArray) = ThruCal(
        fromPort = 1,
        toPort = 2,
        freqHz = freq.copyOf(),
        thru = arrayOf(Complex.ONE, freq.size),
    )

    @Test
    fun fullSoltReportsItsOwnGrid() {
        val range = CalibrationSet(solt = solt(twoPoints)).calibratedRange()
        assertNotNull(range)
        assertEquals(1_000_000_000L, range!!.startHz)
        assertEquals(2_000_000_000L, range.endHz)
    }

    @Test
    fun legacySubsetReportsTheIntersection() {
        val solRange = longArrayOf(100_000_000L, 3_000_000_000L)
        val thruRange = longArrayOf(500_000_000L, 2_000_000_000L)
        val range = CalibrationSet(
            sol = listOf(sol(1, solRange), sol(2, solRange)),
            thrus = listOf(thru(thruRange), thru(thruRange)),
        ).calibratedRange()
        assertNotNull(range)
        // Both SOL ports cover more than the THRU, so the THRU limits the fully calibrated band.
        assertEquals(500_000_000L, range!!.startHz)
        assertEquals(2_000_000_000L, range.endHz)
    }

    @Test
    fun emptyCalibrationHasNoRange() {
        assertNull(CalibrationSet().calibratedRange())
        // A single-point "calibration" corrects nothing, so it must not claim a band either.
        assertNull(CalibrationSet(sol = listOf(sol(1, longArrayOf(1_000_000_000L)))).calibratedRange())
    }

    @Test
    fun coversDetectsSweepsOutsideTheCalibratedBand() {
        val range = solt(twoPoints).let { CalibrationSet(solt = it).calibratedRange() }!!
        assertTrue(range.covers(1_000_000_000L, 2_000_000_000L))
        assertTrue(range.covers(1_200_000_000L, 1_800_000_000L))
        assertFalse(range.covers(500_000_000L, 2_000_000_000L))
        assertFalse(range.covers(1_000_000_000L, 2_500_000_000L))
        assertFalse(range.covers(100_000_000L, 6_000_000_000L))
        // A reversed sweep argument must not change the answer.
        assertTrue(range.covers(2_000_000_000L, 1_000_000_000L))
    }

    @Test
    fun isolationCannotBeReplacedWithoutThroughData() {
        // SoltCal built by hand has no through snapshot; replacing isolation must refuse rather
        // than silently produce an inconsistent model.
        val cal = solt(twoPoints)
        assertNull(SoltMath.replaceIsolation(cal, null))
    }

    @Test
    fun isolationReplacementChecksTheGridLength() {
        val cal = solt(twoPoints)
        val tooMany = com.droidvna.app.model.IsolationMeasurement(
            measured21 = arrayOf(Complex.ZERO, 3),
            measured12 = arrayOf(Complex.ZERO, 3),
        )
        // Still null because there is no through snapshot; the length check is the second guard.
        assertNull(SoltMath.replaceIsolation(cal, tooMany))
    }
}
