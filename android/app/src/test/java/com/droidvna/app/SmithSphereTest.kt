/*
 * DroidVNA - native Android client for LibreVNA / Harogic B60.
 * Copyright (C) 2026 The DroidVNA Authors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.droidvna.app

import com.droidvna.app.model.Complex
import com.droidvna.app.ui.chart.SmithReadout
import com.droidvna.app.ui.chart.SmithSphere
import com.droidvna.app.ui.chart.formatSmithReadout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the Smith sphere mapping: the hemisphere split (passive below the equator, active above),
 * the poles, and the invariants the renderer relies on (every Γ lands on the unit sphere, and
 * rotating the sphere never changes a point's distance from the centre).
 */
class SmithSphereTest {

    private fun unit(gamma: Complex): FloatArray {
        val p = SmithSphere.toUnitSphere(gamma)
        assertNotNull("Γ=$gamma must map onto the sphere", p)
        return p!!
    }

    @Test
    fun originIsTheSouthPole() {
        val p = unit(Complex(0.0, 0.0))
        assertEquals(0f, p[0], 1e-6f)
        assertEquals(0f, p[1], 1e-6f)
        assertEquals(-1f, p[2], 1e-6f)
    }

    @Test
    fun unitCircleIsTheEquator() {
        for (angleDeg in 0 until 360 step 15) {
            val angle = Math.toRadians(angleDeg.toDouble())
            val p = unit(Complex(kotlin.math.cos(angle), kotlin.math.sin(angle)))
            assertEquals("|Γ|=1 must sit on the equator (angle $angleDeg)", 0f, p[2], 1e-5f)
            assertEquals(1f, kotlin.math.sqrt(p[0] * p[0] + p[1] * p[1]), 1e-5f)
        }
    }

    @Test
    fun passiveRegionIsTheSouthernHemisphere() {
        val passive = unit(Complex(0.5, 0.0))
        val active = unit(Complex(2.0, 0.0))
        assertTrue("|Γ|<1 must be below the equator", passive[2] < 0f)
        assertTrue("|Γ|>1 must be above the equator", active[2] > 0f)
        // The mapping is monotonic in |Γ|: more reflection climbs further north.
        assertTrue(unit(Complex(4.0, 0.0))[2] > active[2])
    }

    @Test
    fun everyPointLandsOnTheUnitSphere() {
        for (re in listOf(-3.0, -1.0, -0.3, 0.0, 0.3, 1.0, 3.0)) {
            for (im in listOf(-3.0, -1.0, -0.3, 0.0, 0.3, 1.0, 3.0)) {
                val p = unit(Complex(re, im))
                val radius = kotlin.math.sqrt(p[0] * p[0] + p[1] * p[1] + p[2] * p[2])
                assertEquals("Γ=$re+j$im must stay on the sphere", 1f, radius, 1e-4f)
            }
        }
    }

    @Test
    fun nonFiniteGammaHasNoPositionOnTheSphere() {
        assertNull(SmithSphere.toUnitSphere(Complex(Double.NaN, 0.0)))
        assertNull(SmithSphere.toUnitSphere(Complex(0.0, Double.POSITIVE_INFINITY)))
    }

    @Test
    fun latitudeForMagnitudeRoundTripsWithTheEquator() {
        assertEquals(0f, SmithSphere.latitudeForMagnitude(1f), 1e-5f)
        assertTrue(SmithSphere.latitudeForMagnitude(0.5f) < 0f)
        assertTrue(SmithSphere.latitudeForMagnitude(2f) > 0f)
        // tan((lat + π/2)/2) must invert back to the magnitude it was built from.
        for (magnitude in listOf(0.2f, 0.5f, 1f, 2f, 4f)) {
            val lat = SmithSphere.latitudeForMagnitude(magnitude)
            val back = kotlin.math.tan((lat + Math.PI.toFloat() / 2f) / 2f)
            assertEquals(magnitude.toDouble(), back.toDouble(), 1e-4)
        }
    }

    @Test
    fun rotationKeepsTheSphereRoundAndPutsThePolesUpAndDown() {
        // Orthographic projection of a rigid rotation: no point may leave the silhouette circle,
        // and viewed edge-on (pitch -90°) the poles sit at the top and bottom of the disc.
        val samples = SmithSphere.point(0.4f, 1.1f)
        for (yaw in listOf(0f, 0.7f, -2.1f)) {
            for (pitch in listOf(0f, 0.5f, -0.9f)) {
                val p = SmithSphere.project(samples[0], samples[1], samples[2], yaw, pitch)
                val radius = kotlin.math.sqrt(p[0] * p[0] + p[1] * p[1])
                assertTrue("projection must stay inside the disc (yaw=$yaw pitch=$pitch)", radius <= 1.0001f)
            }
        }
        val edgeOn = (-Math.PI / 2).toFloat()
        val north = SmithSphere.project(0f, 0f, 1f, 0f, edgeOn)
        assertEquals("north pole must stay on the vertical axis", 0f, north[0], 1e-6f)
        assertEquals("north pole must be at the top", 1f, north[1], 1e-6f)
        val south = SmithSphere.project(0f, 0f, -1f, 0f, edgeOn)
        assertEquals("south pole must be at the bottom", -1f, south[1], 1e-6f)
        // Looking along the y axis the poles land on the silhouette, so their depth is zero.
        assertEquals(0f, north[2], 1e-6f)
    }

    @Test
    fun farSideHasNegativeDepth() {
        // With no rotation the near half is +z, so a point behind the sphere projects with depth < 0.
        val back = SmithSphere.project(0f, 0f, -1f, 0f, 0f)
        assertTrue(back[2] < 0f)
        val front = SmithSphere.project(0f, 0f, 1f, 0f, 0f)
        assertTrue(front[2] > 0f)
    }

    @Test
    fun tumblingPastThePoleBringsThePassiveHemisphereToTheFront() {
        // Regression: the pitch used to be clamped to ±77°, so the southern (passive, |Γ| < 1)
        // hemisphere could only ever be looked at from below. A full-turn pitch has to be able to
        // put the south pole in the centre facing the viewer.
        val halfTurn = Math.PI.toFloat()
        val southFront = SmithSphere.project(0f, 0f, -1f, 0f, -halfTurn)
        assertEquals("south pole must project to the centre", 0f, southFront[0], 1e-6f)
        assertEquals(0f, southFront[1], 1e-6f)
        assertTrue("south pole must face the viewer", southFront[2] > 0.99f)
        val northBehind = SmithSphere.project(0f, 0f, 1f, 0f, -halfTurn)
        assertTrue("north pole must be on the far side", northBehind[2] < -0.99f)

        // The same fold must work in the other direction, and reach it through an edge-on view.
        val otherSouth = SmithSphere.project(0f, 0f, -1f, 0f, halfTurn)
        assertTrue(otherSouth[2] > 0.99f)
        val edgeOn = SmithSphere.project(0f, 0f, -1f, 0f, (-Math.PI / 2).toFloat())
        assertEquals(0f, edgeOn[2], 1e-6f)
        // Past the half turn the rotation keeps going instead of sticking at the pole: at -270° the
        // sphere is edge-on again, so the same pole now sits on the rim and not in the centre.
        val beyond = SmithSphere.project(0f, 0f, -1f, 0f, (-1.5 * Math.PI).toFloat())
        assertEquals(0f, beyond[2], 1e-6f)
        assertEquals(1f, beyond[1], 1e-6f)
    }

    @Test
    fun imaginaryReadoutKeepsTheSignSoTheSideOfTheAxisIsVisible() {
        assertEquals(" Im -0.350", formatSmithReadout(Complex(0.2, -0.35), SmithReadout.IMAGINARY))
        assertEquals(" Im +0.350", formatSmithReadout(Complex(0.2, 0.35), SmithReadout.IMAGINARY))
    }

    @Test
    fun phaseReadoutPrintsDegrees() {
        // Γ = 0.5 - j0.5 sits on the -45° line of the Smith chart.
        assertEquals(" ∠-45.0°", formatSmithReadout(Complex(0.5, -0.5), SmithReadout.PHASE))
        assertEquals(" ∠0.0°", formatSmithReadout(Complex(0.5, 0.0), SmithReadout.PHASE))
    }
}
