/*
 * DroidVNA - native Android client for LibreVNA / Harogic B60.
 * Copyright (C) 2026 The DroidVNA Authors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.droidvna.app.ui.chart

import com.droidvna.app.model.Complex
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.sin

/**
 * Mathematics of the Smith sphere, kept free of Compose so it can be unit-tested.
 *
 * The mapping is the Riemann sphere: a reflection coefficient Γ = u + jv becomes
 *
 *     x = 2u / (1 + |Γ|²),  y = 2v / (1 + |Γ|²),  z = (|Γ|² − 1) / (|Γ|² + 1)
 *
 * so Γ = 0 sits on the south pole (0, 0, −1), |Γ| = 1 on the equator and |Γ| → ∞ on the north pole.
 * The southern hemisphere is therefore the passive region (|Γ| < 1) and the northern hemisphere the
 * active one (|Γ| > 1) — which is what makes the sphere useful for amplifier work, where a device
 * with gain pushes S11/S22 outside the unit circle that the flat Smith chart cannot draw.
 */
internal object SmithSphere {

    /** Point on the unit sphere for [gamma], or null when Γ is not a finite number. */
    fun toUnitSphere(gamma: Complex): FloatArray? {
        if (!gamma.isFinite()) return null
        val u = gamma.re.toFloat()
        val v = gamma.im.toFloat()
        val m = u * u + v * v
        if (!m.isFinite()) return null
        val d = 1f + m
        if (d <= 0f || !d.isFinite()) return null
        return floatArrayOf(2f * u / d, 2f * v / d, (m - 1f) / d)
    }

    /** Latitude of a constant-|Γ| parallel (radians): |Γ| = tan((lat + π/2) / 2), inverted. */
    fun latitudeForMagnitude(magnitude: Float): Float =
        (2.0 * atan(magnitude.coerceAtLeast(0f).toDouble()) - PI / 2.0).toFloat()

    /** Unit-sphere point at [latitude] / [longitude], both in radians. */
    fun point(latitude: Float, longitude: Float): FloatArray {
        val ring = cos(latitude)
        return floatArrayOf(ring * cos(longitude), ring * sin(longitude), sin(latitude))
    }

    /**
     * Turns the sphere by [yaw] (about the vertical axis) then [pitch] (about the horizontal axis)
     * and projects the result orthographically onto the `xy` plane.
     *
     * Returns `[x, y, depth]` in unit-sphere units, x to the right and y up. [depth] is positive on
     * the near side of the sphere and negative on the far side, which the renderer uses to dim the
     * hidden half instead of drawing it like the visible one.
     */
    fun project(x: Float, y: Float, z: Float, yaw: Float, pitch: Float): FloatArray {
        val yawCos = cos(yaw)
        val yawSin = sin(yaw)
        val rx = x * yawCos - y * yawSin
        val ry = x * yawSin + y * yawCos
        val pitchCos = cos(pitch)
        val pitchSin = sin(pitch)
        val py = ry * pitchCos - z * pitchSin
        val pz = ry * pitchSin + z * pitchCos
        return floatArrayOf(rx, py, pz)
    }
}
