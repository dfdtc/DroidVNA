/*
 * DroidVNA - native Android client for LibreVNA / Harogic B60.
 * Copyright (C) 2026 The DroidVNA Authors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.droidvna.app

import com.droidvna.app.model.SweepConfig
import com.droidvna.app.model.SweepTiming
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The capture timeout used to be a fixed 8 s, which cannot cover a low-IFBW sweep: E-Cal then
 * reported "port … has no scan data" on a healthy device. These tests pin the scaling.
 */
class SweepTimingTest {
    @Test
    fun slowLowIfbwSweepsGetTimeoutsWellBeyondEightSeconds() {
        // 2001 points at 15 Hz IFBW: minutes per sweep.
        assertTrue(SweepTiming.estimateSweepMs(2001, 15) > 180_000)
        assertTrue(SweepTiming.captureTimeoutMs(2001, 15) > 300_000)
        // Even 401 points at 15 Hz is far beyond a single 8 s window.
        assertTrue(SweepTiming.estimateSweepMs(401, 15) > 8_000)
        assertTrue(SweepTiming.captureTimeoutMs(401, 15) >= 16_000)
    }

    @Test
    fun fastSweepsKeepTheEightSecondFloor() {
        assertEquals(SweepTiming.MIN_CAPTURE_TIMEOUT_MS, SweepTiming.captureTimeoutMs(401, 75_000))
        assertEquals(SweepTiming.MIN_CAPTURE_TIMEOUT_MS, SweepTiming.captureTimeoutMs(SweepConfig()))
        // 401 points at 3 kHz finishes in well under a second on a VNB-60.
        assertTrue(SweepTiming.estimateSweepMs(401, 3_000) < 2_000)
    }

    @Test
    fun timeoutIsCappedAndGrowsWithSlowerSettings() {
        assertEquals(SweepTiming.MAX_CAPTURE_TIMEOUT_MS, SweepTiming.captureTimeoutMs(2001, 1))
        assertTrue(
            SweepTiming.captureTimeoutMs(401, 1_000) >
                SweepTiming.captureTimeoutMs(401, 10_000),
        )
        // Malformed IFBW values must not crash or produce an unbounded wait.
        assertTrue(SweepTiming.captureTimeoutMs(401, 0) > 0)
        assertTrue(SweepTiming.captureTimeoutMs(0, 0) > 0)
    }

    @Test
    fun onlySuspiciouslyFastSweepsAreWaitedForTwice() {
        // A sweep that arrives within a second may be a leftover from before the switch.
        assertTrue(SweepTiming.needsSecondSweep(0))
        assertTrue(SweepTiming.needsSecondSweep(999))
        // A multi-minute sweep is certainly fresh: waiting for a second one would double the
        // capture time of an E-Cal step for no benefit.
        assertTrue(!SweepTiming.needsSecondSweep(SweepTiming.SETTLE_FAST_MS))
        assertTrue(!SweepTiming.needsSecondSweep(180_000))
    }

    @Test
    fun aMeasuredSweepDurationWinsOverTheFormula() {
        // Real VNB-60 behaviour: 2001 points at 100 Hz with a log sweep take far longer than
        // 1/IFBW predicts. A completed sweep on the current grid must raise the timeout.
        val formula = SweepTiming.captureTimeoutMs(2001, 100)
        val observed = 130_000_000_000L // 130 s
        val measured = SweepTiming.captureTimeoutMs(2001, 100, observed)
        assertTrue(measured > formula)
        assertTrue(measured >= 260_000)
        // A stale observation from a faster grid can only shrink to the formula, never below it.
        assertEquals(formula, SweepTiming.captureTimeoutMs(2001, 100, 1_000_000L))
        // The cap still applies.
        assertEquals(
            SweepTiming.MAX_CAPTURE_TIMEOUT_MS,
            SweepTiming.captureTimeoutMs(2001, 100, 3_600_000_000_000L),
        )
    }
}
