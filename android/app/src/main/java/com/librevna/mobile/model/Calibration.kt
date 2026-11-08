/*
 * LibreVNA Mobile - native Android client for LibreVNA / Harogic B60.
 * Copyright (C) 2026 The LibreVNA Mobile Authors
 * SPDX-License-Identifier: GPL-3.0-only
 *
 * Based in part on LibreVNA-GUI (https://github.com/jankae/LibreVNA),
 * Copyright (C) Jan Käberich and contributors, GPL-3.0.
 */

package com.librevna.mobile.model

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
) {
    val isEmpty: Boolean get() = sol.isEmpty() && thrus.isEmpty()

    fun solFor(port: Int): SolCal? = sol.firstOrNull { it.port == port }
    fun thruFor(from: Int, to: Int): ThruCal? = thrus.firstOrNull { it.fromPort == from && it.toPort == to }

    fun withSol(cal: SolCal): CalibrationSet = copy(sol = sol.filter { it.port != cal.port } + cal)
    fun withThru(cal: ThruCal): CalibrationSet =
        copy(thrus = thrus.filterNot { it.fromPort == cal.fromPort && it.toPort == cal.toPort } + cal)
}

/** Complex interpolation over a calibration frequency grid. */
object CalMath {
    fun interpolate(freq: LongArray, values: Array<Complex>, f: Double): Complex {
        if (freq.isEmpty() || values.isEmpty()) return Complex.NAN
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
     * Solves for e00, e11 and e01e10 from three measured standards using the linear form
     * Gamma_m = e00 + B*Gamma_a + e11*Gamma_m*Gamma_a, with B = e01e10 - e00*e11.
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
     * SOL solver with arbitrary, frequency-dependent standard definitions. This is used both by
     * the E-Cal workflow and by imported LibreVNA-GUI .cal files, where Open/Short/Load are not
     * ideal and may have individual loss/delay/parasitic responses.
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
        if (open.size != freqHz.size || short.size != freqHz.size || load.size != freqHz.size ||
            openActual.size != freqHz.size || shortActual.size != freqHz.size || loadActual.size != freqHz.size
        ) {
            return null
        }
        val e00 = arrayOfNulls<Complex>(freqHz.size)
        val e11 = arrayOfNulls<Complex>(freqHz.size)
        val prod = arrayOfNulls<Complex>(freqHz.size)
        for (i in freqHz.indices) {
            val ga = arrayOf(openActual[i], shortActual[i], loadActual[i])
            val gm = arrayOf(open[i], short[i], load[i])
            if (ga.any { !it.isFinite() } || gm.any { !it.isFinite() }) return null
            // 6x7 augmented matrix: unknowns are Re/Im of e00, B and e11.
            val aug = Array(6) { DoubleArray(7) }
            for (r in 0 until 3) {
                val g = ga[r]
                val m = gm[r]
                aug[2 * r][0] = 1.0
                aug[2 * r][2] = g.re
                aug[2 * r][4] = (m * g).re
                aug[2 * r][6] = m.re

                aug[2 * r + 1][1] = 1.0
                aug[2 * r + 1][3] = g.re
                aug[2 * r + 1][5] = (m * g).re
                aug[2 * r + 1][6] = m.im

                aug[2 * r][1] = 0.0
                aug[2 * r][3] = -g.im
                aug[2 * r][5] = -(m * g).im
                aug[2 * r + 1][0] = 0.0
                aug[2 * r + 1][2] = g.im
                aug[2 * r + 1][4] = (m * g).im
            }
            val solution = solveReal(aug) ?: return null
            val a0 = Complex(solution[0], solution[1])
            val c = Complex(solution[4], solution[5])
            val b = Complex(solution[2], solution[3])
            e00[i] = a0
            e11[i] = c
            prod[i] = b + a0 * c
        }
        @Suppress("UNCHECKED_CAST")
        return SolCal(
            port = port,
            freqHz = freqHz.copyOf(),
            e00 = e00 as Array<Complex>,
            e11 = e11 as Array<Complex>,
            e01e10 = prod as Array<Complex>,
        )
    }

    private fun solveReal(aug: Array<DoubleArray>): DoubleArray? {
        val n = 6
        for (col in 0 until n) {
            var pivot = col
            for (row in col + 1 until n) if (kotlin.math.abs(aug[row][col]) > kotlin.math.abs(aug[pivot][col])) pivot = row
            if (kotlin.math.abs(aug[pivot][col]) < 1e-14) return null
            if (pivot != col) {
                val tmp = aug[col]; aug[col] = aug[pivot]; aug[pivot] = tmp
            }
            val p = aug[col][col]
            for (j in col..n) aug[col][j] /= p
            for (row in 0 until n) {
                if (row == col) continue
                val factor = aug[row][col]
                if (factor == 0.0) continue
                for (j in col..n) aug[row][j] -= factor * aug[col][j]
            }
        }
        return DoubleArray(n) { aug[it][n] }
    }

    enum class SolStandard { OPEN, SHORT, LOAD }
}

object CalibrationEngine {
    fun apply(raw: SweepResult, cal: CalibrationSet): SweepResult {
        if (cal.isEmpty) return raw
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