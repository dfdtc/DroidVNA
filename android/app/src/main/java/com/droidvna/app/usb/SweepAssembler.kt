/*
 * DroidVNA - native Android client for LibreVNA / Harogic B60.
 * Copyright (C) 2026 The DroidVNA Authors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.droidvna.app.usb

import com.droidvna.app.model.Complex
import com.droidvna.app.model.SParam
import com.droidvna.app.model.SweepConfig
import com.droidvna.app.model.SweepResult

/**
 * Reassembles one sweep from the per-point datapoint frames the device streams.
 * Each point arrives once per excited port (stage); every frame carries the two receiver
 * values and the reference receiver value.
 */
class SweepAssembler(
    private val config: SweepConfig,
    private val onSweep: (SweepResult) -> Unit,
    /** In-progress snapshots for a slow sweep; fast sweeps only emit the final result. */
    private val onPartial: ((SweepResult) -> Unit)? = null,
) {
    private val n = config.points
    private val freq = LongArray(n)
    private val data = Array(4) { Array(n) { Complex.NAN } }
    private val seen = IntArray(n)
    private val expectedMask = if (config.twoPort) 0x3 else 0x1
    private var started = false
    private var lastPoint = -1
    private var readyPoints = 0
    private var cdbm = 0.0
    private var sweepStartNanos = 0L
    private var lastPartialAt = 0L

    fun debugInfo(): String = "started=" + started + " ready=" + readyPoints + "/" + n + " last=" + lastPoint + " seen0=" + seen.getOrNull(0)

    fun reset() {
        started = false
        lastPoint = -1
        readyPoints = 0
        lastPartialAt = 0
        seen.fill(0)
        freq.fill(0L)
        for (arr in data) arr.fill(Complex.NAN)
    }

    /** @return true when the datapoint contained at least one usable receiver stage. */
    fun accept(dp: Datapoint): Boolean {
        val point = dp.pointNum
        if (point !in 0 until n) return false
        if (point == 0) {
            // A new sweep begins with point 0/stage 0. Do not reset when point 0/stage 1
            // (same point, second stimulus) arrives.
            if (started && lastPoint != 0) reset()
            started = true
            if (lastPoint != 0) sweepStartNanos = System.nanoTime()
        } else if (!started) {
            started = true
            sweepStartNanos = System.nanoTime()
        }
        if (dp.frequencyHz > 0) freq[point] = dp.frequencyHz
        cdbm = dp.cdbm / 100.0

        // A datapoint normally carries every stage for this point (the firmware accumulates all
        // stimulus stages and sends one packet per point). Group by stage so both S11/S21 and
        // S12/S22 can be extracted from the same packet.
        var anyStage = false
        for ((stage, values) in dp.values.groupBy { it.stage }) {
            if (stage > 1) continue
            if (stage == 1 && !config.twoPort) continue
            var reference: Complex? = null
            var port1: Complex? = null
            var port2: Complex? = null
            for (v in values) {
                if ((v.mask and 0x10) != 0) {
                    reference = v.complex()
                } else if ((v.mask and 0x01) != 0) {
                    port1 = v.complex()
                } else if ((v.mask and 0x02) != 0) {
                    port2 = v.complex()
                }
            }
            val ref = reference
            if (ref == null || !ref.isFinite() || ref.abs() <= 1e-15) continue
            if (stage == 0) {
                port1?.let { data[SParam.S11.ordinal][point] = it / ref }
                port2?.let { data[SParam.S21.ordinal][point] = it / ref }
            } else {
                port1?.let { data[SParam.S12.ordinal][point] = it / ref }
                port2?.let { data[SParam.S22.ordinal][point] = it / ref }
            }
            val bit = 1 shl stage
            val before = seen[point]
            if ((before and bit) == 0) {
                seen[point] = before or bit
                if (seen[point] == expectedMask) readyPoints++
            }
            anyStage = true
        }
        if (!anyStage) return false
        lastPoint = point
        if (readyPoints == n) {
            emit()
            reset()
            return true
        }
        val partial = onPartial ?: return true
        val now = System.nanoTime()
        // Do not emit a one-point snapshot for sweeps that finish quickly: it would
        // replace a just-completed trace and make the chart flicker at high sweep rates.
        if (now - sweepStartNanos < PARTIAL_MIN_AGE_NANOS) return true
        if (now - lastPartialAt < PARTIAL_INTERVAL_NANOS) return true
        lastPartialAt = now
        partial(snapshot(partial = true))
        return true
    }

    private fun emit() {
        onSweep(snapshot(partial = false))
    }

    private fun snapshot(partial: Boolean): SweepResult {
        val frequencies = LongArray(n) { if (freq[it] > 0) freq[it] else config.frequencyAt(it) }
        val now = System.nanoTime()
        return SweepResult(
            freqHz = frequencies,
            s11 = data[0].copyOf(),
            s21 = data[1].copyOf(),
            s12 = data[2].copyOf(),
            s22 = data[3].copyOf(),
            cdbm = cdbm,
            completedAtNanos = now,
            sweepDurationNanos = (now - sweepStartNanos).coerceAtLeast(0L),
            isPartial = partial,
            measuredPoints = if (partial) countMeasured() else n,
        )
    }

    private fun countMeasured(): Int {
        var count = 0
        for (i in 0 until n) {
            if (data[0][i].isFinite() || data[1][i].isFinite() ||
                data[2][i].isFinite() || data[3][i].isFinite()
            ) {
                count++
            }
        }
        return count
    }

    private companion object {
        /** A sweep shorter than this never produces partials. */
        const val PARTIAL_MIN_AGE_NANOS = 250_000_000L
        const val PARTIAL_INTERVAL_NANOS = 60_000_000L
    }
}