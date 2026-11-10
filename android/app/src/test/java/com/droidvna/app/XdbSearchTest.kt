/*
 * DroidVNA - native Android client for LibreVNA / Harogic B60.
 * Copyright (C) 2026 The DroidVNA Authors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.droidvna.app.model.analysis

import com.droidvna.app.model.Complex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.pow

class XdbSearchTest {

    private fun frequencies(count: Int, stepHz: Long = 1_000_000L): LongArray =
        LongArray(count) { stepHz * (it + 1) }

    private fun levels(vararg db: Double): Array<Complex> =
        Array(db.size) { Complex(10.0.pow(db[it] / 20.0), 0.0) }

    @Test
    fun bandwidthSearchFindsBothEdges() {
        val freq = frequencies(11)
        val values = levels(-30.0, -20.0, -10.0, -3.0, 0.0, -3.0, -10.0, -20.0, -30.0, -40.0, -50.0)
        val reference = XdbSearch.peakCursor(freq, values)
        assertNotNull(reference)
        assertEquals(4, reference!!.index)
        assertEquals(5_000_000.0, reference.frequencyHz, 1e-6)
        assertEquals(0.0, reference.levelDb, 1e-6)

        val result = XdbSearch.search(freq, values, reference, 3.0, XdbSide.BOTH)
        assertNotNull(result)
        result!!
        assertEquals(4_000_000.0, result.lower!!.frequencyHz, 1e-6)
        assertEquals(6_000_000.0, result.upper!!.frequencyHz, 1e-6)
        assertEquals(2_000_000.0, result.bandwidthHz!!, 1e-6)
        assertEquals(5_000_000.0, result.centerHz!!, 1e-6)
        assertEquals(2, result.foundCount)
    }

    @Test
    fun sideRestrictionOnlyWalksOneDirection() {
        val freq = frequencies(11)
        val values = levels(-30.0, -20.0, -10.0, -3.0, 0.0, -3.0, -10.0, -20.0, -30.0, -40.0, -50.0)
        val reference = XdbSearch.peakCursor(freq, values)!!

        val left = XdbSearch.search(freq, values, reference, 3.0, XdbSide.LEFT)!!
        assertEquals(4_000_000.0, left.lower!!.frequencyHz, 1e-6)
        assertNull(left.upper)
        assertNull(left.bandwidthHz)

        val right = XdbSearch.search(freq, values, reference, 3.0, XdbSide.RIGHT)!!
        assertNull(right.lower)
        assertEquals(6_000_000.0, right.upper!!.frequencyHz, 1e-6)
    }

    @Test
    fun crossingIsInterpolatedBetweenSamples() {
        val freq = longArrayOf(1_000_000L, 2_000_000L, 3_000_000L)
        val values = levels(-6.0, 0.0, -6.0)
        val reference = XdbSearch.peakCursor(freq, values)!!
        val result = XdbSearch.search(freq, values, reference, 3.0, XdbSide.BOTH)!!
        assertEquals(1_500_000.0, result.lower!!.frequencyHz, 1e-6)
        assertEquals(2_500_000.0, result.upper!!.frequencyHz, 1e-6)
        assertEquals(1_000_000.0, result.bandwidthHz!!, 1e-6)
    }

    @Test
    fun customCursorUsesNearestFinitePoint() {
        val freq = frequencies(5)
        val values = levels(-20.0, -10.0, 0.0, -10.0, -20.0)
        val cursor = XdbSearch.cursorAt(freq, values, 3_800_000.0)
        assertNotNull(cursor)
        assertEquals(3, cursor!!.index)
        assertEquals(4_000_000.0, cursor.frequencyHz, 1e-6)
        assertEquals(-10.0, cursor.levelDb, 1e-6)
    }

    @Test
    fun unfinishedSweepDoesNotInventAnEdge() {
        val freq = frequencies(11)
        val values = levels(-30.0, -20.0, -10.0, -3.0, 0.0, -3.0, -10.0, -20.0, -30.0, -40.0, -50.0)
        // Points to the right of the reference have not arrived yet.
        for (i in 5 until values.size) values[i] = Complex.NAN
        val reference = XdbSearch.peakCursor(freq, values)!!
        val result = XdbSearch.search(freq, values, reference, 3.0, XdbSide.BOTH)!!
        assertEquals(4_000_000.0, result.lower!!.frequencyHz, 1e-6)
        assertNull(result.upper)
    }

    @Test
    fun traceThatNeverDropsHasNoEdges() {
        val freq = frequencies(5)
        // Only 1 dB of ripple: a -3 dB search never crosses the target on either side.
        val values = levels(-11.0, -10.5, -10.0, -10.5, -11.0)
        val reference = XdbSearch.peakCursor(freq, values)!!
        val result = XdbSearch.search(freq, values, reference, 3.0, XdbSide.BOTH)!!
        assertEquals(0, result.foundCount)
    }

    @Test
    fun invalidDropReturnsNull() {
        val freq = frequencies(5)
        val values = levels(0.0, -1.0, -2.0, -3.0, -4.0)
        val reference = XdbSearch.peakCursor(freq, values)!!
        assertNull(XdbSearch.search(freq, values, reference, 0.0, XdbSide.BOTH))
        assertNull(XdbSearch.search(freq, values, reference, -3.0, XdbSide.BOTH))
    }
}
