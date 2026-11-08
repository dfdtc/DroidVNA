/*
 * DroidVNA - native Android client for LibreVNA / Harogic B60.
 * Copyright (C) 2026 The DroidVNA Authors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.droidvna.app.export

import com.droidvna.app.model.Complex
import com.droidvna.app.model.SweepResult
import java.util.Locale

object Touchstone {
    fun s1p(result: SweepResult, name: String = "DroidVNA"): String {
        val sb = StringBuilder()
        sb.append("! ").append(name).append("\n")
        sb.append("! Exported by DroidVNA\n")
        sb.append("# Hz S RI R 50\n")
        for (i in result.freqHz.indices) {
            val v = result.s11[i]
            sb.append(String.format(Locale.US, "%d %.9f %.9f\n", result.freqHz[i], safe(v.re), safe(v.im)))
        }
        return sb.toString()
    }

    fun s2p(result: SweepResult, name: String = "DroidVNA"): String {
        val sb = StringBuilder()
        sb.append("! ").append(name).append("\n")
        sb.append("! Exported by DroidVNA\n")
        if (result.s12.all { !it.isFinite() } && result.s22.all { !it.isFinite() }) {
            sb.append("! S12/S22 not measured (1-port sweep)\n")
        }
        sb.append("# Hz S RI R 50\n")
        for (i in result.freqHz.indices) {
            sb.append(result.freqHz[i])
            appendComplex(sb, result.s11[i])
            appendComplex(sb, result.s21[i])
            appendComplex(sb, result.s12[i])
            appendComplex(sb, result.s22[i])
            sb.append("\n")
        }
        return sb.toString()
    }

    fun csv(result: SweepResult): String {
        val sb = StringBuilder()
        sb.append("frequency_hz,s11_db,s11_phase_deg,s21_db,s21_phase_deg,s12_db,s12_phase_deg,s22_db,s22_phase_deg\n")
        for (i in result.freqHz.indices) {
            sb.append(result.freqHz[i])
            appendMagPhase(sb, result.s11[i])
            appendMagPhase(sb, result.s21[i])
            appendMagPhase(sb, result.s12[i])
            appendMagPhase(sb, result.s22[i])
            sb.append("\n")
        }
        return sb.toString()
    }

    private fun appendComplex(sb: StringBuilder, c: Complex) {
        sb.append(String.format(Locale.US, " %.9f %.9f", safe(c.re), safe(c.im)))
    }

    private fun appendMagPhase(sb: StringBuilder, c: Complex) {
        if (!c.isFinite()) {
            sb.append(",,,,")
        } else {
            sb.append(String.format(Locale.US, ",%.4f,%.4f", c.magDb(), c.phaseDeg()))
        }
    }

    private fun safe(value: Double): Double = if (value.isFinite()) value else 0.0
}