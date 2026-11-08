/*
 * LibreVNA Mobile - native Android client for LibreVNA / Harogic B60.
 * Copyright (C) 2026 The LibreVNA Mobile Authors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.librevna.mobile

import com.librevna.mobile.model.CalStandardKind
import com.librevna.mobile.model.EcalCoefficients
import org.junit.Assert.assertEquals
import org.junit.Test

class EcalCoefficientsTest {
    @Test
    fun mapsDeviceThroughOrderToSparams() {
        val parsed = EcalCoefficients.parseLine(
            "0.0001 0.1 0.2 0.9 0.3 0.8 0.4 0.7 0.5"
        )!!
        assertEquals(100_000L, parsed.first)
        val standard = EcalCoefficients.standard(
            "P12_THROUGH",
            CalStandardKind.THROUGH,
            listOf(parsed),
        )
        assertEquals(0.1, standard.s11[0].re, 1e-12)
        assertEquals(0.2, standard.s11[0].im, 1e-12)
        assertEquals(0.9, standard.s21[0].re, 1e-12) // device order S11 S21 S12 S22
        assertEquals(0.3, standard.s21[0].im, 1e-12)
        assertEquals(0.8, standard.s12[0].re, 1e-12)
        assertEquals(0.4, standard.s12[0].im, 1e-12)
        assertEquals(0.7, standard.s22[0].re, 1e-12)
    }

    @Test
    fun parsesOnePortCoefficient() {
        val parsed = EcalCoefficients.parseLine("1.5 0.25 -0.5")!!
        assertEquals(1_500_000_000L, parsed.first)
        val standard = EcalCoefficients.standard("P1_OPEN", CalStandardKind.OPEN, listOf(parsed))
        assertEquals(0.25, standard.s11[0].re, 1e-12)
        assertEquals(-0.5, standard.s11[0].im, 1e-12)
    }
}