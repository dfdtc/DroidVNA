/*
 * DroidVNA - native Android client for LibreVNA / Harogic B60.
 * Copyright (C) 2026 The DroidVNA Authors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.droidvna.app

import com.droidvna.app.model.CalMath
import com.droidvna.app.model.Complex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class CalibrationTest {
    @Test
    fun solRecoversErrorTermsAndCorrectsDut() {
        val frequencies = longArrayOf(100_000_000L, 500_000_000L, 1_000_000_000L, 2_400_000_000L)
        val e00 = Complex(0.02, 0.01)
        val e11 = Complex(0.04, -0.03)
        val prod = Complex(0.90, 0.10)

        fun measured(gamma: Complex): Complex = e00 + prod * gamma / (Complex.ONE - e11 * gamma)

        val open = Array(frequencies.size) { measured(Complex.ONE) }
        val short = Array(frequencies.size) { measured(Complex(-1.0, 0.0)) }
        val load = Array(frequencies.size) { measured(Complex.ZERO) }

        val cal = CalMath.solveSol(frequencies, open, short, load)
        assertNotNull(cal)
        val c = cal!!
        for (i in frequencies.indices) {
            assertEquals(e00.re, c.e00[i].re, 1e-9)
            assertEquals(e00.im, c.e00[i].im, 1e-9)
            assertEquals(e11.re, c.e11[i].re, 1e-9)
            assertEquals(e11.im, c.e11[i].im, 1e-9)
            assertEquals(prod.re, c.e01e10[i].re, 1e-9)
            assertEquals(prod.im, c.e01e10[i].im, 1e-9)
        }
        val actual = Complex(0.35, -0.2)
        val corrected = CalMath.correctReflection(measured(actual), e00, e11, prod)
        assertEquals(actual.re, corrected.re, 1e-9)
        assertEquals(actual.im, corrected.im, 1e-9)
    }

    @Test
    fun delayedStandardsRotate() {
        val f = 1_000_000_000L
        val gamma = CalMath.standardGamma(f, CalMath.SolStandard.OPEN, 100.0)
        val expected = -2.0 * PI * f * 100e-12
        assertEquals(cos(expected), gamma.re, 1e-9)
        assertEquals(sin(expected), gamma.im, 1e-9)
        val short = CalMath.standardGamma(f, CalMath.SolStandard.SHORT, 0.0)
        assertEquals(-1.0, short.re, 1e-12)
    }
}