/*
 * DroidVNA - native Android client for LibreVNA / Harogic B60.
 * Copyright (C) 2026 The DroidVNA Authors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.droidvna.app.model

import org.apache.commons.math3.complex.Complex as CmComplex
import org.apache.commons.math3.linear.FieldLUDecomposition
import org.apache.commons.math3.linear.FieldMatrix
import org.apache.commons.math3.linear.MatrixUtils

/**
 * Complex matrix helpers built on Apache Commons Math.
 *
 * The 12-term SOLT correction is the multi-port formulation `S = B * A^-1`; the calibration
 * solve itself is closed-form (see [SoltMath]), so a matrix library is only needed for the
 * inversion and for the demo/test forward model. Rather than hand-rolling LU decomposition,
 * this adapter converts the app's [Complex] to Commons Math's `Complex` field and lets
 * [FieldLUDecomposition] do the linear algebra.
 *
 * Matrices are row-major `Array<Array<Complex>>`; all operations are O(n^3) with n = 2.
 */
object ComplexMatrix {

    fun identity(n: Int): Array<Array<Complex>> =
        Array(n) { r -> Array(n) { c -> if (r == c) Complex.ONE else Complex.ZERO } }

    /** `a - b`, or null when the shapes do not match. */
    fun subtract(a: Array<Array<Complex>>, b: Array<Array<Complex>>): Array<Array<Complex>>? {
        if (!sameShape(a, b)) return null
        val result = toMatrix(a).subtract(toMatrix(b))
        return fromMatrix(result)
    }

    /** `a * b`, or null when the shapes do not match. */
    fun multiply(a: Array<Array<Complex>>, b: Array<Array<Complex>>): Array<Array<Complex>>? {
        if (a.isEmpty() || b.isEmpty() || a[0].size != b.size) return null
        val result = toMatrix(a).multiply(toMatrix(b))
        return fromMatrix(result)
    }

    /** Matrix inverse, or null when the matrix is singular (or contains non-finite values). */
    fun inverse(a: Array<Array<Complex>>): Array<Array<Complex>>? {
        if (a.isEmpty() || a.size != a[0].size) return null
        if (a.any { row -> row.any { !it.isFinite() } }) return null
        return try {
            val solver = FieldLUDecomposition(toMatrix(a)).solver
            if (!solver.isNonSingular) null else fromMatrix(solver.inverse)
        } catch (_: Exception) {
            null
        }
    }

    /** `b * a^-1`, or null when `a` is singular or the shapes do not match. */
    fun rightMultiplyByInverse(
        b: Array<Array<Complex>>,
        a: Array<Array<Complex>>,
    ): Array<Array<Complex>>? {
        if (a.isEmpty() || b.isEmpty()) return null
        if (a.size != a[0].size || b.size != a.size || b[0].size != a.size) return null
        if (a.any { row -> row.any { !it.isFinite() } } || b.any { row -> row.any { !it.isFinite() } }) {
            return null
        }
        return try {
            val solver = FieldLUDecomposition(toMatrix(a)).solver
            if (!solver.isNonSingular) {
                null
            } else {
                // Straightforward and unambiguous: invert, then multiply. Both matrices are 2x2,
                // so the extra inverse costs nothing and there is no transpose trick to get wrong.
                fromMatrix(toMatrix(b).multiply(solver.inverse))
            }
        } catch (_: Exception) {
            null
        }
    }

    /** Solves `a * x = b` for x, or null when `a` is singular. */
    fun solve(a: Array<Array<Complex>>, b: Array<Complex>): Array<Complex>? {
        if (a.isEmpty() || a.size != a[0].size || a.size != b.size) return null
        if (a.any { row -> row.any { !it.isFinite() } } || b.any { !it.isFinite() }) return null
        return try {
            val solver = FieldLUDecomposition(toMatrix(a)).solver
            if (!solver.isNonSingular) {
                null
            } else {
                val x = solver.solve(MatrixUtils.createFieldVector(Array(b.size) { toMath(b[it]) }))
                Array(b.size) { fromMath(x.getEntry(it)) }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun sameShape(a: Array<Array<Complex>>, b: Array<Array<Complex>>): Boolean =
        a.isNotEmpty() && b.isNotEmpty() &&
            a.size == b.size && a[0].size == b[0].size &&
            a.all { it.size == a[0].size } && b.all { it.size == b[0].size }

    private fun toMath(value: Complex): CmComplex = CmComplex(value.re, value.im)

    private fun fromMath(value: CmComplex): Complex = Complex(value.real, value.imaginary)

    private fun toMatrix(rows: Array<Array<Complex>>): FieldMatrix<CmComplex> =
        MatrixUtils.createFieldMatrix(Array(rows.size) { r -> Array(rows[r].size) { c -> toMath(rows[r][c]) } })

    private fun fromMatrix(matrix: FieldMatrix<CmComplex>): Array<Array<Complex>> =
        Array(matrix.rowDimension) { r ->
            Array(matrix.columnDimension) { c -> fromMath(matrix.getEntry(r, c)) }
        }
}
