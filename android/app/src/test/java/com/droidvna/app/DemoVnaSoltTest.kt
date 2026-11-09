/*
 * DroidVNA - native Android client for LibreVNA / Harogic B60.
 * Copyright (C) 2026 The DroidVNA Authors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.droidvna.app

import com.droidvna.app.device.DemoVnaSource
import com.droidvna.app.model.CalStandard
import com.droidvna.app.model.Complex
import com.droidvna.app.model.IsolationMeasurement
import com.droidvna.app.model.OnePortStandards
import com.droidvna.app.model.SoltMath
import com.droidvna.app.model.SweepConfig
import com.droidvna.app.model.SweepResult
import com.droidvna.app.model.TwoPortStandard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * End-to-end check of the demo device: drive it exactly like the calibration screen does
 * (prepare a standard, take one sweep), solve the 12-term model, then verify that a real DUT
 * sweep corrects back to the ideal S-matrix of the synthetic filter.
 *
 * `start(..., standby = true)` keeps the background loop off, so `initiate()` produces exactly
 * one deterministic sweep per standard.
 */
class DemoVnaSoltTest {

    private val config = SweepConfig(
        startHz = 1_700_000_000L,
        stopHz = 2_700_000_000L,
        points = 21,
        ifbwHz = 10_000,
        twoPort = true,
        continuous = true,
    )

    @Test
    fun demoDeviceSupportsFullSolt() = runBlocking {
        val demo = DemoVnaSource()
        try {
            assertTrue(demo.start(config, standby = true))

            suspend fun capture(prepare: suspend () -> Unit): SweepResult {
                prepare()
                val next = async(Dispatchers.Default) { demo.sweeps.first() }
                delay(50)
                demo.initiate()
                return withTimeout(5_000) { next.await() }
            }

            val open1 = capture { demo.prepareCalStandard(1, CalStandard.OPEN) }
            val short1 = capture { demo.prepareCalStandard(1, CalStandard.SHORT) }
            val load1 = capture { demo.prepareCalStandard(1, CalStandard.LOAD) }
            val open2 = capture { demo.prepareCalStandard(2, CalStandard.OPEN) }
            val short2 = capture { demo.prepareCalStandard(2, CalStandard.SHORT) }
            val load2 = capture { demo.prepareCalStandard(2, CalStandard.LOAD) }
            val thru = capture { demo.prepareCalThru() }
            // The flags still describe the THRU, so the ideal standard can be sampled now.
            val grid = open1.freqHz
            val thruIdeal = Array(grid.size) { demo.idealDutAt(grid[it].toDouble()) }
            val iso = capture { demo.prepareCalIsolation() }

            fun standards(port: Int): OnePortStandards {
                val openSweep = if (port == 1) open1 else open2
                val shortSweep = if (port == 1) short1 else short2
                val loadSweep = if (port == 1) load1 else load2
                fun measured(sweep: SweepResult): Array<Complex> =
                    if (port == 1) sweep.s11 else sweep.s22
                // The demo's one-port standards really are ideal +1 / -1 / 0.
                return OnePortStandards(
                    measuredOpen = measured(openSweep),
                    measuredShort = measured(shortSweep),
                    measuredLoad = measured(loadSweep),
                    actualOpen = Array(grid.size) { Complex.ONE },
                    actualShort = Array(grid.size) { Complex(-1.0, 0.0) },
                    actualLoad = Array(grid.size) { Complex.ZERO },
                )
            }

            val cal = SoltMath.solve(
                grid,
                standards(1),
                standards(2),
                TwoPortStandard(
                    measured11 = thru.s11,
                    measured21 = thru.s21,
                    measured12 = thru.s12,
                    measured22 = thru.s22,
                    actual11 = Array(grid.size) { thruIdeal[it][0][0] },
                    actual21 = Array(grid.size) { thruIdeal[it][1][0] },
                    actual12 = Array(grid.size) { thruIdeal[it][0][1] },
                    actual22 = Array(grid.size) { thruIdeal[it][1][1] },
                ),
                IsolationMeasurement(measured21 = iso.s21, measured12 = iso.s12),
            )
            assertNotNull("demo mode must produce a solvable 12-term calibration", cal)
            assertTrue(cal!!.hasIsolation)

            // Now measure the synthetic filter DUT with the same error model.
            val rawDut = capture { demo.clearCalPreparation() }
            val corrected = SoltMath.correct(rawDut, cal)
            assertNotNull(corrected)
            val c = corrected!!

            // Sanity check the fixture itself: the raw (uncalibrated) S11 must be visibly off.
            val ideal = Array(grid.size) { demo.idealDutAt(grid[it].toDouble()) }
            val rawError = abs(rawDut.s11[10].re - ideal[10][0][0].re)
            assertTrue("raw data should carry the error model, got $rawError", rawError > 1e-3)

            for (i in grid.indices) {
                val tolerance = 0.01 // the demo adds receiver noise on purpose
                assertTrue(
                    "S11 mismatch at ${grid[i]}: ${ideal[i][0][0]} vs ${c.s11[i]}",
                    abs(c.s11[i].re - ideal[i][0][0].re) < tolerance &&
                        abs(c.s11[i].im - ideal[i][0][0].im) < tolerance,
                )
                assertTrue(
                    "S21 mismatch at ${grid[i]}: ${ideal[i][1][0]} vs ${c.s21[i]}",
                    abs(c.s21[i].re - ideal[i][1][0].re) < tolerance &&
                        abs(c.s21[i].im - ideal[i][1][0].im) < tolerance,
                )
                assertTrue(
                    "S22 mismatch at ${grid[i]}: ${ideal[i][1][1]} vs ${c.s22[i]}",
                    abs(c.s22[i].re - ideal[i][1][1].re) < tolerance &&
                        abs(c.s22[i].im - ideal[i][1][1].im) < tolerance,
                )
            }
        } finally {
            demo.close()
        }
    }
}
