/*
 * DroidVNA - native Android client for LibreVNA / Harogic B60.
 * Copyright (C) 2026 The DroidVNA Authors
 * SPDX-License-Identifier: GPL-3.0-only
 *
 * The error-term model, the SOLT solve and the correction follow LibreVNA-GUI
 * (Calibration/calibration.cpp: Calibration::Point, computeOSL, computeSOLT, correctMeasurement),
 * Copyright (C) Jan Käberich and contributors, GPL-3.0.
 */

package com.droidvna.app.model

/** Directivity / source match / reflection tracking of one port (the classic 1-port SOL box). */
data class PortErrorTerms(
    /** e00, directivity. */
    val directivity: Array<Complex>,
    /** e11, source match. */
    val sourceMatch: Array<Complex>,
    /** e01*e10, reflection tracking (a product: the two factors are not separable here). */
    val reflectionTracking: Array<Complex>,
) {
    val size: Int get() = directivity.size
}

/**
 * The raw through measurement together with the ideal standard that was connected.
 *
 * It is kept inside [SoltCal] because the transmission tracking is derived from
 * `(measured S21 - isolation)`, so the isolation terms can be recomputed later (for example
 * after a dedicated isolation measurement with mechanical loads) without redoing the whole
 * calibration. The load match does not depend on the isolation.
 */
data class SoltThroughSnapshot(
    val measured11: Array<Complex>,
    val measured21: Array<Complex>,
    val measured12: Array<Complex>,
    val measured22: Array<Complex>,
    val actual11: Array<Complex>,
    val actual21: Array<Complex>,
    val actual12: Array<Complex>,
    val actual22: Array<Complex>,
) {
    val size: Int get() = measured11.size

    fun isConsistent(): Boolean =
        size > 0 &&
            measured21.size == size && measured12.size == size && measured22.size == size &&
            actual11.size == size && actual21.size == size &&
            actual12.size == size && actual22.size == size
}

/**
 * Full 12-term (SOLT) error model of a two-port VNA.
 *
 * The six error boxes of the classic 12-term model are stored as
 * - directivity / source match / reflection tracking per port (the three reflection terms), and
 * - receiver match, transmission tracking and isolation per direction (the remaining terms).
 *
 * Index convention follows LibreVNA: `12` means "port 1 is the source, port 2 is the receiver".
 */
data class SoltCal(
    val freqHz: LongArray,
    val port1: PortErrorTerms,
    val port2: PortErrorTerms,
    /** Receiver match at port 2 while port 1 is the source (the classic "load match"). */
    val loadMatch12: Array<Complex>,
    /** Receiver match at port 1 while port 2 is the source. */
    val loadMatch21: Array<Complex>,
    val transmissionTracking12: Array<Complex>,
    val transmissionTracking21: Array<Complex>,
    val isolation12: Array<Complex>,
    val isolation21: Array<Complex>,
    /** False when the isolation measurement was skipped (isolation then counts as zero). */
    val hasIsolation: Boolean = false,
    /** Raw through data, kept so the isolation terms can be replaced later. */
    val through: SoltThroughSnapshot? = null,
    val createdAt: Long = System.currentTimeMillis(),
) {
    val size: Int get() = freqHz.size

    /**
     * Every coefficient array must match the frequency grid.
     *
     * [SoltMath.correct] indexes the arrays directly when the grid matches the sweep, so a
     * truncated or corrupted snapshot would otherwise read out of bounds.
     */
    fun isConsistent(): Boolean = size > 0 &&
        port1.size == size && port2.size == size &&
        loadMatch12.size == size && loadMatch21.size == size &&
        transmissionTracking12.size == size && transmissionTracking21.size == size &&
        isolation12.size == size && isolation21.size == size
}

/** The measured and the ideal S-parameters of one one-port standard set. */
data class OnePortStandards(
    val measuredOpen: Array<Complex>,
    val measuredShort: Array<Complex>,
    val measuredLoad: Array<Complex>,
    val actualOpen: Array<Complex>,
    val actualShort: Array<Complex>,
    val actualLoad: Array<Complex>,
) {
    val size: Int get() = measuredOpen.size
}

/** The measured and the ideal S-parameters of a through connection. */
data class TwoPortStandard(
    val measured11: Array<Complex>,
    val measured21: Array<Complex>,
    val measured12: Array<Complex>,
    val measured22: Array<Complex>,
    val actual11: Array<Complex>,
    val actual21: Array<Complex>,
    val actual12: Array<Complex>,
    val actual22: Array<Complex>,
) {
    fun toSnapshot(): SoltThroughSnapshot = SoltThroughSnapshot(
        measured11 = measured11.copyOf(),
        measured21 = measured21.copyOf(),
        measured12 = measured12.copyOf(),
        measured22 = measured22.copyOf(),
        actual11 = actual11.copyOf(),
        actual21 = actual21.copyOf(),
        actual12 = actual12.copyOf(),
        actual22 = actual22.copyOf(),
    )
}

/** Crosstalk measured with every port terminated. */
data class IsolationMeasurement(
    val measured21: Array<Complex>,
    val measured12: Array<Complex>,
)

/**
 * The 12-term error terms of a single frequency point. Grouped so the per-point work in
 * [SoltMath.correct] and [SoltErrorModel.measure] reads the same way the formulas do.
 */
data class SoltTerms(
    val d1: Complex,
    val s1: Complex,
    val r1: Complex,
    val d2: Complex,
    val s2: Complex,
    val r2: Complex,
    val l12: Complex,
    val l21: Complex,
    val t12: Complex,
    val t21: Complex,
    val i12: Complex,
    val i21: Complex,
)

object SoltMath {

    /** Below this magnitude a denominator is treated as singular. */
    private const val EPS = 1e-15

    /**
     * Loose physical sanity bound for the reflection terms.
     *
     * Directivity and source match are reflection coefficients of a passive error box, so in
     * practice they are well below 1. When the standards are nearly indistinguishable the
     * equations stop separating them and the solution blows up even though the determinant is
     * not exactly zero; this bound rejects those cases instead of applying garbage.
     */
    private const val MAX_REFLECTION_TERM = 10.0

    /**
     * Solves the three reflection error terms of one port from Open/Short/Load.
     *
     * Upstream LibreVNA computes these in closed form (`Calibration::computeOSL`); using the
     * same expressions keeps mobile and desktop coefficients comparable and avoids solving a
     * linear system per frequency point.
     */
    fun portTerms(
        measuredOpen: Array<Complex>,
        measuredShort: Array<Complex>,
        measuredLoad: Array<Complex>,
        actualOpen: Array<Complex>,
        actualShort: Array<Complex>,
        actualLoad: Array<Complex>,
    ): PortErrorTerms? {
        val n = measuredOpen.size
        if (n == 0) return null
        if (measuredShort.size != n || measuredLoad.size != n ||
            actualOpen.size != n || actualShort.size != n || actualLoad.size != n
        ) {
            return null
        }
        val directivity = arrayOfNulls<Complex>(n)
        val sourceMatch = arrayOfNulls<Complex>(n)
        val tracking = arrayOfNulls<Complex>(n)
        for (i in 0 until n) {
            val om = measuredOpen[i]
            val sm = measuredShort[i]
            val lm = measuredLoad[i]
            val oc = actualOpen[i]
            val sc = actualShort[i]
            val lc = actualLoad[i]
            if (!om.isFinite() || !sm.isFinite() || !lm.isFinite() ||
                !oc.isFinite() || !sc.isFinite() || !lc.isFinite()
            ) {
                return null
            }
            val denom = lc * oc * (om - lm) + lc * sc * (lm - sm) + oc * sc * (sm - om)
            if (!denom.isFinite() || denom.abs() < EPS) return null

            val d = (lc * om * (sm * (oc - sc) + lm * sc) - lc * oc * lm * sm + oc * lm * sc * (sm - om)) / denom
            val s = (lc * (om - sm) + oc * (sm - lm) + sc * (lm - om)) / denom
            val delta = (lc * lm * (om - sm) + oc * om * (sm - lm) + sc * sm * (lm - om)) / denom
            val r = d * s - delta

            if (!d.isFinite() || !s.isFinite() || !r.isFinite()) return null
            // Nearly-identical standards keep the determinant alive while making the solution
            // explode; reject that instead of silently applying it.
            if (d.abs() > MAX_REFLECTION_TERM || s.abs() > MAX_REFLECTION_TERM) return null
            directivity[i] = d
            sourceMatch[i] = s
            tracking[i] = r
        }
        @Suppress("UNCHECKED_CAST")
        return PortErrorTerms(
            directivity = directivity as Array<Complex>,
            sourceMatch = sourceMatch as Array<Complex>,
            reflectionTracking = tracking as Array<Complex>,
        )
    }

    /**
     * Computes the full 12-term error model from a two-port SOLT calibration.
     *
     * [freqHz] is the common frequency grid; every array of the inputs must be aligned to it.
     * Returns null when the standards are degenerate (open and short too similar, a zero
     * through transmission, ...), so the caller can report a useful message instead of
     * silently applying a broken calibration.
     */
    fun solve(
        freqHz: LongArray,
        port1: OnePortStandards,
        port2: OnePortStandards,
        thru: TwoPortStandard,
        isolation: IsolationMeasurement? = null,
    ): SoltCal? {
        val n = freqHz.size
        // At least two points: a one-point "calibration" cannot be interpolated and the rest of
        // the app (CalibrationSet.calibratedRange, SolCal/ThruCal handling) assumes >= 2 as well.
        if (n < 2) return null
        if (port1.size != n || port2.size != n) return null
        if (thru.measured11.size != n || thru.measured21.size != n ||
            thru.measured12.size != n || thru.measured22.size != n ||
            thru.actual11.size != n || thru.actual21.size != n ||
            thru.actual12.size != n || thru.actual22.size != n
        ) {
            return null
        }
        if (!isolationFits(isolation, n)) return null

        val terms1 = portTerms(
            port1.measuredOpen, port1.measuredShort, port1.measuredLoad,
            port1.actualOpen, port1.actualShort, port1.actualLoad,
        ) ?: return null
        val terms2 = portTerms(
            port2.measuredOpen, port2.measuredShort, port2.measuredLoad,
            port2.actualOpen, port2.actualShort, port2.actualLoad,
        ) ?: return null

        return solveFromTerms(freqHz, terms1, terms2, thru.toSnapshot(), isolation)
    }

    /**
     * Recomputes the through terms for a different isolation measurement.
     *
     * Load match and the reflection terms do not depend on the isolation, but the transmission
     * tracking does: upstream derives it from `(measured S21 - isolation)`. Keeping the raw
     * through measurement in [SoltCal.through] therefore makes an isolation-only recalibration
     * exact — no need to redo Open/Short/Load or the through.
     *
     * Pass null to remove the isolation correction (treat crosstalk as zero).
     */
    fun replaceIsolation(cal: SoltCal, isolation: IsolationMeasurement?): SoltCal? {
        val through = cal.through ?: return null
        if (!isolationFits(isolation, cal.size)) return null
        val updated = solveFromTerms(cal.freqHz, cal.port1, cal.port2, through, isolation) ?: return null
        return updated
    }

    private fun isolationFits(isolation: IsolationMeasurement?, n: Int): Boolean = when {
        isolation == null -> true
        isolation.measured21.size != n || isolation.measured12.size != n -> false
        else -> true
    }

    private fun solveFromTerms(
        freqHz: LongArray,
        terms1: PortErrorTerms,
        terms2: PortErrorTerms,
        through: SoltThroughSnapshot,
        isolation: IsolationMeasurement?,
    ): SoltCal? {
        val n = freqHz.size
        if (n < 2) return null
        if (terms1.size != n || terms2.size != n) return null
        if (!through.isConsistent() || through.size != n) return null

        val l12 = arrayOfNulls<Complex>(n)
        val l21 = arrayOfNulls<Complex>(n)
        val t12 = arrayOfNulls<Complex>(n)
        val t21 = arrayOfNulls<Complex>(n)
        val i12 = arrayOfNulls<Complex>(n)
        val i21 = arrayOfNulls<Complex>(n)

        for (i in 0 until n) {
            val iso12 = isolation?.measured21?.get(i) ?: Complex.ZERO
            val iso21 = isolation?.measured12?.get(i) ?: Complex.ZERO
            if (!iso12.isFinite() || !iso21.isFinite()) return null

            val terms = throughTermsAt(
                i,
                terms1.directivity[i], terms1.sourceMatch[i], terms1.reflectionTracking[i],
                terms2.directivity[i], terms2.sourceMatch[i], terms2.reflectionTracking[i],
                through, iso12, iso21,
            ) ?: return null

            l12[i] = terms.l12
            t12[i] = terms.t12
            l21[i] = terms.l21
            t21[i] = terms.t21
            i12[i] = iso12
            i21[i] = iso21
        }

        @Suppress("UNCHECKED_CAST")
        return SoltCal(
            freqHz = freqHz.copyOf(),
            port1 = terms1,
            port2 = terms2,
            loadMatch12 = l12 as Array<Complex>,
            loadMatch21 = l21 as Array<Complex>,
            transmissionTracking12 = t12 as Array<Complex>,
            transmissionTracking21 = t21 as Array<Complex>,
            isolation12 = i12 as Array<Complex>,
            isolation21 = i21 as Array<Complex>,
            hasIsolation = isolation != null,
            through = through,
        )
    }

    private class ThroughTerms(
        val l12: Complex,
        val t12: Complex,
        val l21: Complex,
        val t21: Complex,
    )

    /** Upstream `Calibration::computeSOLT` for one frequency point. */
    private fun throughTermsAt(
        index: Int,
        d1: Complex,
        s1: Complex,
        r1: Complex,
        d2: Complex,
        s2: Complex,
        r2: Complex,
        through: SoltThroughSnapshot,
        iso12: Complex,
        iso21: Complex,
    ): ThroughTerms? {
        val a11 = through.actual11[index]
        val a21 = through.actual21[index]
        val a12 = through.actual12[index]
        val a22 = through.actual22[index]
        val m11 = through.measured11[index]
        val m21 = through.measured21[index]
        val m12 = through.measured12[index]
        val m22 = through.measured22[index]
        if (!a11.isFinite() || !a21.isFinite() || !a12.isFinite() || !a22.isFinite()) return null
        if (!m11.isFinite() || !m21.isFinite() || !m12.isFinite() || !m22.isFinite()) return null

        // deltaS is invariant when the through is used in the reverse direction.
        val deltaS = a11 * a22 - a21 * a12

        // Forward direction: port 1 drives, port 2 receives.
        val denForward = (m11 - d1) * (a22 - s1 * deltaS) - deltaS * r1
        if (!denForward.isFinite() || denForward.abs() < EPS) return null
        val loadForward = ((m11 - d1) * (Complex.ONE - s1 * a11) - a11 * r1) / denForward
        if (!loadForward.isFinite() || a21.abs() < EPS) return null
        val trackForward = (m21 - iso12) *
            (Complex.ONE - s1 * a11 - loadForward * a22 + s1 * loadForward * deltaS) / a21
        if (!trackForward.isFinite()) return null

        // Reverse direction: port 2 drives, port 1 receives. The ideal through is mirrored,
        // so a11 <-> a22 and a21 <-> a12 (same as Sparam::swapPorts upstream).
        val denReverse = (m22 - d2) * (a11 - s2 * deltaS) - deltaS * r2
        if (!denReverse.isFinite() || denReverse.abs() < EPS) return null
        val loadReverse = ((m22 - d2) * (Complex.ONE - s2 * a22) - a22 * r2) / denReverse
        if (!loadReverse.isFinite() || a12.abs() < EPS) return null
        val trackReverse = (m12 - iso21) *
            (Complex.ONE - s2 * a22 - loadReverse * a11 + s2 * loadReverse * deltaS) / a12
        if (!trackReverse.isFinite()) return null

        return ThroughTerms(loadForward, trackForward, loadReverse, trackReverse)
    }

    /**
     * Applies the 12-term correction to one sweep, using the multi-port formulation
     * `S = B * A^-1` from "Multi-Port Calibration Techniques for Differential Parameter
     * Measurements with Network Analyzers" (the same reference LibreVNA-GUI cites).
     *
     * Points outside the calibration range are left untouched; the UI warns about that
     * (see `CalibrationSet.calibratedRange`).
     */
    fun correct(raw: SweepResult, cal: SoltCal): SweepResult? {
        val n = raw.size
        if (n == 0 || cal.size < 1) return null
        // Guard against a truncated persisted snapshot: the exact-grid path indexes the term
        // arrays directly, so their lengths have to be validated here as well.
        if (!cal.isConsistent()) return null
        val exact = cal.freqHz.size == n && (0 until n).all { cal.freqHz[it] == raw.freqHz[it] }

        val s11 = raw.s11.copyOf()
        val s21 = raw.s21.copyOf()
        val s12 = raw.s12.copyOf()
        val s22 = raw.s22.copyOf()
        var applied = false

        for (i in 0 until n) {
            val f = raw.freqHz[i].toDouble()
            if (f < cal.freqHz.first() || f > cal.freqHz.last()) continue

            fun at(values: Array<Complex>): Complex =
                if (exact) values[i] else CalMath.interpolate(cal.freqHz, values, f)

            val d1 = at(cal.port1.directivity)
            val s1 = at(cal.port1.sourceMatch)
            val r1 = at(cal.port1.reflectionTracking)
            val d2 = at(cal.port2.directivity)
            val s2 = at(cal.port2.sourceMatch)
            val r2 = at(cal.port2.reflectionTracking)
            val l12 = at(cal.loadMatch12)
            val l21 = at(cal.loadMatch21)
            val t12 = at(cal.transmissionTracking12)
            val t21 = at(cal.transmissionTracking21)
            val i12 = at(cal.isolation12)
            val i21 = at(cal.isolation21)

            if (r1.abs() < EPS || r2.abs() < EPS || t12.abs() < EPS || t21.abs() < EPS) continue

            val m11 = raw.s11[i]
            val m21 = raw.s21[i]
            val m12 = raw.s12[i]
            val m22 = raw.s22[i]
            if (!m11.isFinite() || !m21.isFinite() || !m12.isFinite() || !m22.isFinite()) continue

            // Isolation is removed from the transmission measurements first.
            val v21 = m21 - i12
            val v12 = m12 - i21

            // a(i,j) / b(i,j): row = receiving port, column = exciting port.
            val a = Array(2) { Array(2) { Complex.ZERO } }
            val b = Array(2) { Array(2) { Complex.ZERO } }

            a[0][0] = Complex.ONE + s1 / r1 * (m11 - d1)
            b[0][0] = (m11 - d1) / r1
            a[1][1] = Complex.ONE + s2 / r2 * (m22 - d2)
            b[1][1] = (m22 - d2) / r2
            a[1][0] = l12 * v21 / t12
            b[1][0] = v21 / t12
            a[0][1] = l21 * v12 / t21
            b[0][1] = v12 / t21

            val corrected = ComplexMatrix.rightMultiplyByInverse(b, a) ?: continue
            val c11 = corrected[0][0]
            val c21 = corrected[1][0]
            val c12 = corrected[0][1]
            val c22 = corrected[1][1]
            if (!c11.isFinite() || !c21.isFinite() || !c12.isFinite() || !c22.isFinite()) continue

            s11[i] = c11
            s21[i] = c21
            s12[i] = c12
            s22[i] = c22
            applied = true
        }

        if (!applied) return null
        return SweepResult(
            freqHz = raw.freqHz.copyOf(),
            s11 = s11,
            s21 = s21,
            s12 = s12,
            s22 = s22,
            cdbm = raw.cdbm,
            calibrated = true,
            completedAtNanos = raw.completedAtNanos,
            sweepDurationNanos = raw.sweepDurationNanos,
            isPartial = raw.isPartial,
            measuredPoints = raw.measuredPoints,
        )
    }
}

/**
 * Forward model of the 12-term error box: turns an ideal S-matrix into what the VNA would
 * measure. The production correction in [SoltMath.correct] is the exact inverse of this, so
 * the demo device and the unit tests can generate physically consistent raw data.
 *
 * Derivation (per exciting port i): with `a`/`b` the incident/reflected waves, `b = Sa * a` and
 * the error box gives `b(i,i) = (M(i,i) - D_i) / R_i`, `b(j,i) = M(j,i) / T[i][j]` together with
 * `a(i,i) = 1 + S_i * b(i,i)` and `a(j,i) = L[i][j] * b(j,i)`. Note that both off-diagonal
 * relations share the row *and* the column, so the load match forms a diagonal matrix acting on
 * the wave vector of that excitation. Eliminating `a` yields `(I - Sa * Q_i) * b_i = Sa[:,i]`.
 */
object SoltErrorModel {

    /** Returns the measured S-matrix `M[receiver][source]`, or null when the model is singular. */
    fun measure(actual: Array<Array<Complex>>, terms: SoltTerms): Array<Array<Complex>>? {
        if (actual.size != 2 || actual.any { it.size != 2 }) return null
        if (actual.any { row -> row.any { !it.isFinite() } }) return null
        val out = Array(2) { Array(2) { Complex.NAN } }
        for (src in 0..1) {
            val rcv = 1 - src
            val sourceMatch = if (src == 0) terms.s1 else terms.s2
            val loadMatch = if (src == 0) terms.l12 else terms.l21

            // Q_i is diagonal: the exciting port sees the source match, the other port sees the
            // receiver match (the classic "load match") terminating it.
            val q = Array(2) { Array(2) { Complex.ZERO } }
            q[src][src] = sourceMatch
            q[rcv][rcv] = loadMatch

            val gq = ComplexMatrix.multiply(actual, q) ?: return null
            val m = ComplexMatrix.subtract(ComplexMatrix.identity(2), gq) ?: return null
            val b = ComplexMatrix.solve(m, arrayOf(actual[0][src], actual[1][src])) ?: return null

            val directivity = if (src == 0) terms.d1 else terms.d2
            val tracking = if (src == 0) terms.r1 else terms.r2
            val trackTransmission = if (src == 0) terms.t12 else terms.t21
            val isolation = if (src == 0) terms.i12 else terms.i21

            out[src][src] = directivity + tracking * b[src]
            out[rcv][src] = trackTransmission * b[rcv] + isolation
            if (!out[src][src].isFinite() || !out[rcv][src].isFinite()) return null
        }
        return out
    }
}
