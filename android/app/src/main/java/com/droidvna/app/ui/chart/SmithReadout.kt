/*
 * DroidVNA - native Android client for LibreVNA / Harogic B60.
 * Copyright (C) 2026 The DroidVNA Authors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.droidvna.app.ui.chart

import com.droidvna.app.model.Complex
import java.util.Locale

/**
 * The second value a Smith readout prints next to |Γ|.
 *
 * |Γ| alone cannot tell two points of the same circle apart, so every Smith readout carries one
 * more number. An operator reading a *position* wants the imaginary part of Γ (whether the point
 * sits above or below the real axis); a phase measurement wants the angle. Both describe the same
 * point, so this is a display preference rather than a separate chart mode.
 */
enum class SmithReadout(val label: String) {
    IMAGINARY("虚部"),
    PHASE("角度"),
}

/** Formats the extra Smith value for [readout], with an explicit sign. */
fun formatSmithReadout(value: Complex, readout: SmithReadout): String = when (readout) {
    SmithReadout.IMAGINARY -> String.format(Locale.US, " Im %+.3f", value.im)
    SmithReadout.PHASE -> String.format(Locale.US, " ∠%.1f°", value.phaseDeg())
}
