/*
 * DroidVNA - native Android client for LibreVNA / Harogic B60.
 * Copyright (C) 2026 The DroidVNA Authors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.droidvna.app.model

import kotlin.math.max

/**
 * Sweep-duration model used to size calibration capture timeouts.
 *
 * The device integrates for roughly one IFBW period per point, plus a fixed per-point overhead.
 * Measured on a VNB-60 (FW 1.8.6): 401 points at 3 kHz IFBW complete in ~0.33 s (≈ 0.83 ms per
 * point) and 501 points at 10 kHz in ~190 ms.
 *
 * A fixed timeout cannot cover a low-IFBW configuration: 2001 points at 15 Hz need minutes per
 * sweep, so the old fixed 8 s capture timeout failed on a perfectly healthy device with
 * "port … has no scan data".
 */
object SweepTiming {
    /** Fixed per-point cost observed on the VNB-60, in milliseconds. */
    const val PER_POINT_OVERHEAD_MS = 0.5

    /**
     * Conservative multiplier on the ideal 1/IFBW integration time. The firmware needs a few
     * IFBW periods per point (measured factor ≈ 2.5–3.8), so the estimate deliberately errs on
     * the long side. It only sizes a timeout; it never delays a capture that completes earlier.
     */
    const val IFBW_FACTOR = 3.0

    /** A capture never gives up before this, so a fast sweep keeps the old behaviour. */
    const val MIN_CAPTURE_TIMEOUT_MS = 8_000L

    /** Upper bound so a wrong IFBW cannot produce an unbounded wait. */
    const val MAX_CAPTURE_TIMEOUT_MS = 20 * 60_000L

    /** Estimated wall-clock duration of one complete sweep, in milliseconds. */
    fun estimateSweepMs(points: Int, ifbwHz: Int): Long {
        val perPointMs = IFBW_FACTOR * 1000.0 / max(ifbwHz, 1) + PER_POINT_OVERHEAD_MS
        return (max(points, 1) * perPointMs).toLong() + 500
    }

    fun estimateSweepMs(config: SweepConfig): Long = estimateSweepMs(config.points, config.ifbwHz)

    /**
     * How long to wait for one complete sweep: two sweep periods plus a margin, clamped to
     * [MIN_CAPTURE_TIMEOUT_MS]..[MAX_CAPTURE_TIMEOUT_MS].
     *
     * [observedSweepNanos] is the duration of a real completed sweep on the current grid. The
     * firmware's per-point cost is not a pure function of IFBW (log sweeps and low IFBW add
     * several tens of milliseconds per point), so a measured duration always wins over the
     * formula.
     */
    fun captureTimeoutMs(points: Int, ifbwHz: Int, observedSweepNanos: Long = 0L): Long {
        val estimated = estimateSweepMs(points, ifbwHz)
        val observed = if (observedSweepNanos > 0) observedSweepNanos / 1_000_000 else 0L
        return (max(estimated, observed) * 2 + 5_000)
            .coerceIn(MIN_CAPTURE_TIMEOUT_MS, MAX_CAPTURE_TIMEOUT_MS)
    }

    fun captureTimeoutMs(config: SweepConfig, observedSweepNanos: Long = 0L): Long =
        captureTimeoutMs(config.points, config.ifbwHz, observedSweepNanos)

    /**
     * A sweep that completes this soon after the wait started may have been in flight from
     * before the standard switch, so one more is awaited. Anything slower is already fresh, and
     * waiting twice would double the time of a multi-minute sweep.
     */
    const val SETTLE_FAST_MS = 1_000L

    fun needsSecondSweep(elapsedMs: Long): Boolean = elapsedMs < SETTLE_FAST_MS
}
