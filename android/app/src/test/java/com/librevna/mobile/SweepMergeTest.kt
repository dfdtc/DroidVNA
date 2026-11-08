/*
 * LibreVNA Mobile - native Android client for LibreVNA / Harogic B60.
 * Copyright (C) 2026 The LibreVNA Mobile Authors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.librevna.mobile

import com.librevna.mobile.model.Complex
import com.librevna.mobile.model.SweepMerge
import com.librevna.mobile.model.SweepResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SweepMergeTest {
    @Test
    fun mergeKeepsUnseenPointsAndOverwritesSeenOnes() {
        val base = sweep(doubleArrayOf(1.0, 2.0, 3.0, 4.0), isPartial = false)
        val update = sweep(
            doubleArrayOf(9.0, Double.NaN, Double.NaN, Double.NaN),
            isPartial = true,
            measuredPoints = 1,
        )
        val merged = SweepMerge.merge(base, update)
        assertTrue(merged.isPartial)
        assertEquals(1, merged.measuredPoints)
        assertEquals(9.0, merged.s11[0].re, 0.0)
        assertEquals(2.0, merged.s11[1].re, 0.0)
        assertEquals(3.0, merged.s11[2].re, 0.0)
        assertEquals(4.0, merged.s11[3].re, 0.0)
    }

    @Test
    fun mergeWithoutBaseReturnsUpdate() {
        val update = sweep(
            doubleArrayOf(9.0, Double.NaN),
            isPartial = true,
            measuredPoints = 1,
        )
        val merged = SweepMerge.merge(null, update)
        assertEquals(9.0, merged.s11[0].re, 0.0)
        assertTrue(merged.s11[1].re.isNaN())
        assertEquals(1, merged.measuredPoints)
    }

    private fun sweep(
        s11: DoubleArray,
        isPartial: Boolean,
        measuredPoints: Int = -1,
    ): SweepResult {
        val n = s11.size
        return SweepResult(
            freqHz = LongArray(n) { 1_000_000L + it * 1_000_000L },
            s11 = Array(n) { Complex(s11[it], 0.0) },
            s21 = Array(n) { Complex.NAN },
            s12 = Array(n) { Complex.NAN },
            s22 = Array(n) { Complex.NAN },
            cdbm = -10.0,
            isPartial = isPartial,
            measuredPoints = measuredPoints,
        )
    }
}