/*
 * DroidVNA - native Android client for LibreVNA / Harogic B60.
 * Copyright (C) 2026 The DroidVNA Authors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.droidvna.app.model

/**
 * Helpers for the LibreCAL / ECB-60 coefficient stream.
 *
 * The device returns frequency in GHz and, for a through coefficient, four complex values in
 * the device order S11, S21, S12, S22. Internally we keep the more usual S11, S12, S21, S22
 * order, so the middle two values are swapped here.
 */
object EcalCoefficients {
    fun parseLine(line: String): Pair<Long, List<Complex>>? {
        val parts = line.trim().split(Regex("\\s+"))
        if (parts.size < 3) return null
        val ghz = parts[0].toDoubleOrNull() ?: return null
        if (ghz <= 0.0) return null
        val values = ArrayList<Complex>()
        var i = 1
        while (i + 1 < parts.size) {
            val re = parts[i].toDoubleOrNull() ?: return null
            val im = parts[i + 1].toDoubleOrNull() ?: return null
            values.add(Complex(re, im))
            i += 2
        }
        if (values.size != 1 && values.size != 4) return null
        return (ghz * 1e9).toLong() to values
    }

    fun standard(
        name: String,
        kind: CalStandardKind,
        points: List<Pair<Long, List<Complex>>>,
    ): CalKitStandard {
        val sorted = points.sortedBy { it.first }
        val n = sorted.size
        val freq = LongArray(n) { sorted[it].first }
        val s11 = if (n == 0) emptyArray() else Array(n) { sorted[it].second.getOrElse(0) { Complex.NAN } }
        // Device order is S11, S21, S12, S22; expose S11, S12, S21, S22.
        val s12 = if (n == 0) emptyArray() else Array(n) { sorted[it].second.getOrElse(2) { Complex.NAN } }
        val s21 = if (n == 0) emptyArray() else Array(n) { sorted[it].second.getOrElse(1) { Complex.NAN } }
        val s22 = if (n == 0) emptyArray() else Array(n) { sorted[it].second.getOrElse(3) { Complex.NAN } }
        return CalKitStandard(
            id = name,
            name = name,
            kind = kind,
            freqHz = freq,
            s11 = s11,
            s12 = s12,
            s21 = s21,
            s22 = s22,
        )
    }
}