/*
 * DroidVNA - native Android client for LibreVNA / Harogic B60.
 * Copyright (C) 2026 The DroidVNA Authors
 * SPDX-License-Identifier: GPL-3.0-only
 *
 * Based in part on LibreVNA-GUI (https://github.com/jankae/LibreVNA),
 * Copyright (C) Jan Käberich and contributors, GPL-3.0.
 */

package com.droidvna.app.model

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

data class SolCal(
    val port: Int,
    val freqHz: LongArray,
    val e00: Array<Complex>,
    val e11: Array<Complex>,
    val e01e10: Array<Complex>,
    val createdAt: Long = System.currentTimeMillis(),
) {
    val size: Int get() = freqHz.size
}

data class ThruCal(
    val fromPort: Int,
    val toPort: Int,
    val freqHz: LongArray,
    val thru: Array<Complex>,
    val createdAt: Long = System.currentTimeMillis(),
)

data class CalibrationSet(
    val sol: List<SolCal> = emptyList(),
    val thrus: List<ThruCal> = emptyList(),
    /** Serial (or demo id) of the device this calibration was measured on. */
    val sourceKey: String = "",
    /**
     * Full 12-term two-port correction. When present it takes precedence over [sol] and [thrus]:
     * applying a 1-port SOL on top of a 12-term model would correct the reflections twice.
     */
    val solt: SoltCal? = null,
) {
    val isEmpty: Boolean get() = sol.isEmpty() && thrus.isEmpty() && solt == null

    fun solFor(port: Int): SolCal? = sol.firstOrNull { it.port == port }
    fun thruFor(from: Int, to: Int): ThruCal? = thrus.firstOrNull { it.fromPort == from && it.toPort == to }

    fun withSol(cal: SolCal): CalibrationSet = copy(sol = sol.filter { it.port != cal.port } + cal)
    fun withThru(cal: ThruCal): CalibrationSet =
        copy(thrus = thrus.filterNot { it.fromPort == cal.fromPort && it.toPort == cal.toPort } + cal)

    fun withSolt(cal: SoltCal): CalibrationSet = copy(solt = cal)
    fun withoutSolt(): CalibrationSet = copy(solt = null)

    /**
     * The frequency band in which every correction that will actually be applied is defined.
     *
     * A sweep point outside this band is left uncorrected (the engine skips it), so the UI uses
     * this to tell the user when the sweep reaches beyond the calibration. The 12-term model
     * wins over the 1-port subset, matching [CalibrationEngine.apply]. For the legacy subset the
     * *intersection* is reported, because that is the band where all of its pieces apply.
     */
    fun calibratedRange(): FreqRange? {
        solt?.let { cal ->
            if (cal.size < 2) return null
            return FreqRange(cal.freqHz.first(), cal.freqHz.last())
        }
        var start = Long.MIN_VALUE
        var end = Long.MAX_VALUE
        var found = false
        for (cal in sol) {
            if (cal.size < 2) continue
            found = true
            start = maxOf(start, cal.freqHz.first())
            end = minOf(end, cal.freqHz.last())
        }
        for (cal in thrus) {
            if (cal.freqHz.size < 2) continue
            found = true
            start = maxOf(start, cal.freqHz.first())
            end = minOf(end, cal.freqHz.last())
        }
        if (!found || start >= end) return null
        return FreqRange(start, end)
    }
}

/** An inclusive frequency interval in Hz. */
data class FreqRange(val startHz: Long, val endHz: Long) {
    /** True when the sweep interval is fully inside this calibrated band. */
    fun covers(start: Long, stop: Long): Boolean {
        val lo = minOf(start, stop)
        val hi = maxOf(start, stop)
        return lo >= startHz && hi <= endHz
    }
}

/** Complex interpolation over a calibration frequency grid. */
object CalMath {
    fun interpolate(freq: LongArray, values: Array<Complex>, f: Double): Complex {
        if (freq.isEmpty() || values.isEmpty()) return Complex.NAN
        if (values.size != freq.size) return Complex.NAN
        if (freq.size == 1 || f <= freq[0]) return values[0]
        if (f >= freq[freq.size - 1]) return values[values.size - 1]
        var lo = 0
        var hi = freq.size - 1
        while (hi - lo > 1) {
            val mid = (lo + hi) / 2
            if (freq[mid].toDouble() <= f) lo = mid else hi = mid
        }
        val f0 = freq[lo].toDouble()
        val f1 = freq[hi].toDouble()
        val t = if (f1 == f0) 0.0 else (f - f0) / (f1 - f0)
        return values[lo] * (1.0 - t) + values[hi] * t
    }

    /** Applies the 1-port SOL error model to a measured reflection coefficient. */
    fun correctReflection(measured: Complex, e00: Complex, e11: Complex, e01e10: Complex): Complex {
        val d = measured - e00
        val denominator = e01e10 + e11 * d
        if (denominator.abs() < 1e-18) return measured
        return d / denominator
    }

    fun standardGamma(freqHz: Long, kind: SolStandard, delayPs: Double): Complex {
        val delay = delayPs * 1e-12
        val theta = -2.0 * PI * freqHz.toDouble() * delay
        val rotation = Complex(cos(theta), sin(theta))
        return when (kind) {
            SolStandard.OPEN -> rotation
            SolStandard.SHORT -> rotation * -1.0
            SolStandard.LOAD -> Complex.ZERO
        }
    }

    /**
     * Solves for e00, e11 and e01e10 from three measured standards, using the ideal
     * Open (+1) / Short (-1) / Load (0) definitions with optional port delays.
     */
    fun solveSol(
        freqHz: LongArray,
        open: Array<Complex>,
        short: Array<Complex>,
        load: Array<Complex>,
        openDelayPs: Double = 0.0,
        shortDelayPs: Double = 0.0,
    ): SolCal? {
        if (freqHz.isEmpty()) return null
        val openActual = Array(freqHz.size) { standardGamma(freqHz[it], SolStandard.OPEN, openDelayPs) }
        val shortActual = Array(freqHz.size) { standardGamma(freqHz[it], SolStandard.SHORT, shortDelayPs) }
        val loadActual = Array(freqHz.size) { standardGamma(freqHz[it], SolStandard.LOAD, 0.0) }
        return solveSol(freqHz, open, short, load, openActual, shortActual, loadActual, 1)
    }

    /**
     * SOL solver with arbitrary, frequency-dependent standard definitions. Used by the 1-port
     * workflow, by the E-Cal workflow and by imported LibreVNA-GUI .cal files, where
     * Open/Short/Load are not ideal and may have individual loss/delay/parasitic responses.
     */
    fun solveSol(
        freqHz: LongArray,
        open: Array<Complex>,
        short: Array<Complex>,
        load: Array<Complex>,
        openActual: Array<Complex>,
        shortActual: Array<Complex>,
        loadActual: Array<Complex>,
        port: Int = 1,
    ): SolCal? {
        if (freqHz.isEmpty()) return null
        val terms = SoltMath.portTerms(open, short, load, openActual, shortActual, loadActual) ?: return null
        if (terms.size != freqHz.size) return null
        return SolCal(
            port = port,
            freqHz = freqHz.copyOf(),
            e00 = terms.directivity,
            e11 = terms.sourceMatch,
            e01e10 = terms.reflectionTracking,
        )
    }

    enum class SolStandard { OPEN, SHORT, LOAD }
}

object CalibrationEngine {
    /**
     * Applies the active calibration. A full 12-term SOLT model wins over the 1-port SOL +
     * THRU normalization subset, because both would otherwise correct the same reflections.
     */
    fun apply(raw: SweepResult, cal: CalibrationSet): SweepResult {
        if (cal.isEmpty) return raw
        val solt = cal.solt
        if (solt != null) {
            val corrected = SoltMath.correct(raw, solt)
            if (corrected != null) return corrected
            // Fall through to the 1-port subset only when the 12-term model could not be
            // applied at all (for example a sweep completely outside its frequency range).
        }
        return applyLegacy(raw, cal)
    }

    private fun applyLegacy(raw: SweepResult, cal: CalibrationSet): SweepResult {
        val n = raw.size
        val s11 = raw.s11.copyOf()
        val s21 = raw.s21.copyOf()
        val s12 = raw.s12.copyOf()
        val s22 = raw.s22.copyOf()
        var applied = false

        val sol1 = cal.solFor(1)
        if (sol1 != null) {
            applied = true
            for (i in 0 until n) {
                val f = raw.freqHz[i].toDouble()
                if (sol1.size < 2 || f < sol1.freqHz.first() || f > sol1.freqHz.last()) continue
                s11[i] = CalMath.correctReflection(
                    raw.s11[i],
                    CalMath.interpolate(sol1.freqHz, sol1.e00, f),
                    CalMath.interpolate(sol1.freqHz, sol1.e11, f),
                    CalMath.interpolate(sol1.freqHz, sol1.e01e10, f),
                )
            }
        }
        val sol2 = cal.solFor(2)
        if (sol2 != null) {
            applied = true
            for (i in 0 until n) {
                val f = raw.freqHz[i].toDouble()
                if (sol2.size < 2 || f < sol2.freqHz.first() || f > sol2.freqHz.last()) continue
                s22[i] = CalMath.correctReflection(
                    raw.s22[i],
                    CalMath.interpolate(sol2.freqHz, sol2.e00, f),
                    CalMath.interpolate(sol2.freqHz, sol2.e11, f),
                    CalMath.interpolate(sol2.freqHz, sol2.e01e10, f),
                )
            }
        }
        val thru12 = cal.thruFor(1, 2)
        if (thru12 != null) {
            applied = true
            for (i in 0 until n) {
                val f = raw.freqHz[i].toDouble()
                if (thru12.freqHz.size < 2 || f < thru12.freqHz.first() || f > thru12.freqHz.last()) continue
                val t = CalMath.interpolate(thru12.freqHz, thru12.thru, f)
                if (t.isFinite() && t.abs() > 1e-9) s21[i] = raw.s21[i] / t
            }
        }
        val thru21 = cal.thruFor(2, 1)
        if (thru21 != null) {
            applied = true
            for (i in 0 until n) {
                val f = raw.freqHz[i].toDouble()
                if (thru21.freqHz.size < 2 || f < thru21.freqHz.first() || f > thru21.freqHz.last()) continue
                val t = CalMath.interpolate(thru21.freqHz, thru21.thru, f)
                if (t.isFinite() && t.abs() > 1e-9) s12[i] = raw.s12[i] / t
            }
        }
        if (!applied) return raw
        return SweepResult(
            freqHz = raw.freqHz.copyOf(),
            s11 = s11, s21 = s21, s12 = s12, s22 = s22,
            cdbm = raw.cdbm,
            calibrated = true,
            completedAtNanos = raw.completedAtNanos,
            sweepDurationNanos = raw.sweepDurationNanos,
            isPartial = raw.isPartial,
            measuredPoints = raw.measuredPoints,
        )
    }
}
