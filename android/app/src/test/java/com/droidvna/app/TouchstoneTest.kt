/*
 * DroidVNA - native Android client for LibreVNA / Harogic B60.
 * Copyright (C) 2026 The DroidVNA Authors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.droidvna.app

import com.droidvna.app.export.Touchstone
import com.droidvna.app.model.Complex
import com.droidvna.app.model.SweepResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TouchstoneTest {
    private fun result(): SweepResult {
        val n = 3
        return SweepResult(
            freqHz = longArrayOf(1_000_000L, 2_000_000L, 3_000_000L),
            s11 = Array(n) { Complex(0.1 * it, 0.0) },
            s21 = Array(n) { Complex(0.9, 0.01) },
            s12 = Array(n) { Complex(0.8, 0.02) },
            s22 = Array(n) { Complex(0.2, 0.0) },
            cdbm = -10.0,
        )
    }

    @Test
    fun s2pHasHeaderAndFourParameters() {
        val text = Touchstone.s2p(result(), "test")
        val lines = text.lines().filter { it.isNotBlank() && !it.startsWith("!") }
        assertEquals("# Hz S RI R 50", lines[0])
        assertEquals(4, lines.size)
        assertEquals(9, lines[1].trim().split(Regex("\\s+")).size)
    }

    @Test
    fun csvContainsAllRows() {
        val text = Touchstone.csv(result())
        assertEquals(4, text.lines().filter { it.isNotBlank() }.size)
        assertTrue(text.contains("frequency_hz"))
    }
}