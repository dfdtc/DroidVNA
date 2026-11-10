/*
 * DroidVNA - native Android client for LibreVNA / Harogic B60.
 * Copyright (C) 2026 The DroidVNA Authors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.droidvna.app.model.analysis

import com.droidvna.app.model.CalMath
import com.droidvna.app.model.Complex
import org.apache.commons.math3.complex.Complex as CmComplex
import org.apache.commons.math3.transform.DftNormalization
import org.apache.commons.math3.transform.FastFourierTransformer
import org.apache.commons.math3.transform.TransformType
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min

/** Windowing applied to the frequency-domain trace before the inverse transform. */
enum class DtfWindow(val label: String) {
    RECTANGULAR("矩形"),
    HANN("Hann"),
    HAMMING("Hamming"),
    BLACKMAN("Blackman"),
}

/** Parameters for one distance-to-fault calculation. */
data class DtfConfig(
    val window: DtfWindow = DtfWindow.HANN,
    /** Fraction of the speed of light in the cable (0 < VF <= 1), e.g. 0.66 for solid PE. */
    val velocityFactor: Double = 0.66,
    /** Zero-padding factor. Higher values interpolate the distance axis more densely. */
    val zeroPadding: Int = 4,
    /** Upper distance shown and searched, or null for the full unambiguous range. */
    val stopDistanceM: Double? = null,
    /** Approximate one-way cable loss in dB/m, compensated so far faults stay visible. */
    val lossDbPerM: Double = 0.0,
    /** Peaks below this level (relative to the strongest reflection) are not reported. */
    val peakThresholdDb: Double = -20.0,
    val maxFaults: Int = 8,
)

/** One point of the distance-domain trace. */
data class DtfSample(
    val distanceM: Double,
    /** Magnitude relative to the strongest reflection, in dB. */
    val magnitudeDb: Double,
)

/** A detected reflection: a fault or a connector/launch reflection. */
data class DtfFault(
    val distanceM: Double,
    /** Two-way delay converted to nanoseconds. */
    val timeNs: Double,
    /** Magnitude relative to the strongest reflection, in dB. */
    val magnitudeDb: Double,
)

/** Complete result of a distance-to-fault analysis. */
data class DtfResult(
    /** Down-sampled trace for drawing (peak-preserving, at most ~1200 points). */
    val samples: List<DtfSample>,
    val faults: List<DtfFault>,
    /** Far edge of the usable distance window (full range minus the zero-delay wrap guard). */
    val maxDistanceM: Double,
    /** Right edge of the requested display/search window. */
    val displayStopM: Double,
    /** Two-way range resolution, c * VF / (2 * span). */
    val resolutionM: Double,
    val spanHz: Double,
    val velocityFactor: Double,
    val fftSize: Int,
    val sourcePoints: Int,
    val window: DtfWindow,
    val peakThresholdDb: Double,
    /** True when a few resolution cells at the far edge were excluded as a zero-delay wrap. */
    val wrappedEdgeTrimmed: Boolean,
)

/**
 * Distance to fault (DTF) from a frequency-domain reflection measurement.
 *
 * The algorithm is the standard band-limited pulse response:
 *
 * 1. resample the reflection coefficient onto a uniform frequency grid (IFFT requires it),
 * 2. apply a window to control sidelobes,
 * 3. zero-pad and run an inverse FFT (Apache Commons Math, no hand-rolled transform),
 * 4. scale delay to distance with `d = c * VF * t / 2`,
 * 5. find local maxima above a threshold and report the strongest reflections.
 *
 * The unambiguous distance is `c * VF / (2 * df)`, the resolution is `c * VF / (2 * span)`.
 */
object Dtf {

    private const val SPEED_OF_LIGHT = 299_792_458.0
    private const val MAX_FFT_SIZE = 65_536
    private const val MAX_DISPLAY_SAMPLES = 1200

    fun analyze(
        freqHz: LongArray,
        values: Array<Complex>,
        config: DtfConfig,
    ): DtfResult? {
        if (freqHz.size != values.size || freqHz.size < 8) return null

        val velocityFactor = config.velocityFactor.takeIf { it.isFinite() }?.coerceIn(0.01, 1.0) ?: return null
        val lossDbPerM = config.lossDbPerM.takeIf { it.isFinite() }?.coerceIn(0.0, 100.0) ?: 0.0
        val threshold = config.peakThresholdDb.takeIf { it.isFinite() }?.coerceIn(-200.0, 0.0) ?: -20.0

        // Sorted, finite samples. A partially swept trace simply ends where the NaNs start.
        val points = ArrayList<Pair<Double, Complex>>(freqHz.size)
        for (i in freqHz.indices) {
            if (values[i].isFinite()) points.add(freqHz[i].toDouble() to values[i])
        }
        if (points.size < 8) return null
        points.sortBy { it.first }

        val startHz = points.first().first
        val stopHz = points.last().first
        val spanHz = stopHz - startHz
        if (!(spanHz > 0.0)) return null

        val sourcePoints = points.size
        val stepHz = spanHz / (sourcePoints - 1)
        if (!(stepHz > 0.0)) return null

        val sourceFreq = LongArray(sourcePoints) { points[it].first.toLong() }
        val sourceValues = Array(sourcePoints) { points[it].second }

        // The inverse FFT needs uniform frequency spacing; a log sweep would otherwise smear
        // every reflection. Already-uniform data is used as-is to avoid interpolation loss.
        val uniformValues = if (isUniform(sourceFreq, startHz, stepHz)) {
            sourceValues
        } else {
            Array(sourcePoints) { k ->
                CalMath.interpolate(sourceFreq, sourceValues, startHz + stepHz * k)
            }
        }

        val padding = config.zeroPadding.coerceIn(1, 16)
        val fftSize = nextPowerOfTwo(sourcePoints * padding).coerceAtMost(MAX_FFT_SIZE)
        if (fftSize < sourcePoints) return null

        // Zero-padded, windowed spectrum. Commons Math's INVERSE transform uses e^{+j...},
        // so a causal reflection exp(-j*2*pi*f*tau) peaks at a positive delay.
        val spectrum = Array(fftSize) { CmComplex.ZERO }
        for (k in 0 until sourcePoints) {
            val value = uniformValues[k]
            if (!value.isFinite()) return null
            val window = windowValue(k, sourcePoints, config.window)
            spectrum[k] = CmComplex(value.re * window, value.im * window)
        }

        val timeDomain = FastFourierTransformer(DftNormalization.STANDARD)
            .transform(spectrum, TransformType.INVERSE)

        val magnitudes = DoubleArray(fftSize) { timeDomain[it].abs() }

        // Delay sample spacing: the padded output covers 0..1/df with fftSize points.
        val timeStepSeconds = 1.0 / (fftSize * stepHz)
        val distanceStep = SPEED_OF_LIGHT * velocityFactor * timeStepSeconds / 2.0
        if (!(distanceStep > 0.0)) return null

        val rawMaxDistance = distanceStep * (fftSize - 1)
        val requestedStop = config.stopDistanceM
            ?.takeIf { it.isFinite() && it > 0.0 }
            ?.coerceAtLeast(distanceStep)
            ?.coerceAtMost(rawMaxDistance)
            ?: rawMaxDistance
        val rawLastIndex = min(fftSize - 1, floor(requestedStop / distanceStep).toInt())
        if (rawLastIndex < 1) return null

        val resolutionM = SPEED_OF_LIGHT * velocityFactor / (2.0 * spanHz)

        // The inverse transform is circular: a reflection at zero delay wraps around to the
        // last bins of the record. When the full range is shown, trim a few resolution cells
        // at the far end so that wrap is not reported as a fault at the maximum distance. A
        // real fault that close to the edge is indistinguishable from the zero-delay alias.
        val edgeGuard = min(3.0 * resolutionM, rawMaxDistance * 0.2)
        val guardDistance = if (requestedStop >= rawMaxDistance - edgeGuard) edgeGuard else 0.0
        val lastIndex = if (guardDistance > 0.0) {
            (rawLastIndex - ceil(guardDistance / distanceStep).toInt()).coerceAtLeast(1)
        } else {
            rawLastIndex
        }

        // Normalise against the strongest reflection inside the usable window, so the plotted
        // trace and the fault table share the same 0 dB reference.
        var peakMagnitude = 0.0
        for (index in 0..lastIndex) {
            if (magnitudes[index] > peakMagnitude) peakMagnitude = magnitudes[index]
        }
        if (!(peakMagnitude > 0.0) || !peakMagnitude.isFinite()) return null

        fun distanceAt(index: Int): Double = index * distanceStep

        fun relativeDbAt(index: Int): Double {
            val magnitude = magnitudes[index]
            val relative = 20.0 * log10((magnitude / peakMagnitude).coerceAtLeast(1e-300))
            // One-way loss coefficient -> round-trip loss is 2 * loss * distance.
            return relative + 2.0 * lossDbPerM * distanceAt(index)
        }

        val levels = DoubleArray(lastIndex + 1) { relativeDbAt(it) }

        val faults = findFaults(
            levels = levels,
            distanceStep = distanceStep,
            velocityFactor = velocityFactor,
            thresholdDb = threshold,
            minSeparationM = max(distanceStep, resolutionM / 4.0),
            maxFaults = config.maxFaults.coerceIn(1, 32),
        )

        val samples = decimateForDisplay(levels, distanceStep, lastIndex)

        return DtfResult(
            samples = samples,
            faults = faults,
            maxDistanceM = distanceAt(lastIndex),
            displayStopM = distanceAt(lastIndex),
            resolutionM = resolutionM,
            spanHz = spanHz,
            velocityFactor = velocityFactor,
            fftSize = fftSize,
            sourcePoints = sourcePoints,
            window = config.window,
            peakThresholdDb = threshold,
            wrappedEdgeTrimmed = guardDistance > 0.0,
        )
    }

    private fun isUniform(freqHz: LongArray, startHz: Double, stepHz: Double): Boolean {
        val tolerance = stepHz * 1e-6
        for (i in freqHz.indices) {
            if (abs(freqHz[i].toDouble() - (startHz + stepHz * i)) > tolerance) return false
        }
        return true
    }

    private fun windowValue(index: Int, size: Int, window: DtfWindow): Double {
        if (size <= 1) return 1.0
        val angle = 2.0 * PI * index / (size - 1)
        return when (window) {
            DtfWindow.RECTANGULAR -> 1.0
            DtfWindow.HANN -> 0.5 - 0.5 * cos(angle)
            DtfWindow.HAMMING -> 0.54 - 0.46 * cos(angle)
            DtfWindow.BLACKMAN -> 0.42 - 0.5 * cos(angle) + 0.08 * cos(2.0 * angle)
        }
    }

    private fun nextPowerOfTwo(value: Int): Int {
        var result = 1
        while (result < value) result = result shl 1
        return result
    }

    private fun findFaults(
        levels: DoubleArray,
        distanceStep: Double,
        velocityFactor: Double,
        thresholdDb: Double,
        minSeparationM: Double,
        maxFaults: Int,
    ): List<DtfFault> {
        if (levels.size < 2) return emptyList()
        val last = levels.lastIndex
        val candidates = ArrayList<DtfFault>()

        for (index in levels.indices) {
            val level = levels[index]
            if (!level.isFinite() || level < thresholdDb) continue
            val left = if (index > 0) levels[index - 1] else Double.NEGATIVE_INFINITY
            val right = if (index < last) levels[index + 1] else Double.NEGATIVE_INFINITY
            val isPeak = when {
                index == 0 -> level >= right
                // The right edge is either the unambiguous-range limit or a user-cut window;
                // a rising/falling boundary there is not a reflection peak on its own.
                index == last -> false
                else -> level >= left && level > right
            }
            if (!isPeak) continue

            val y0 = if (index > 0) levels[index - 1] else level
            val y2 = if (index < last) levels[index + 1] else level
            val denominator = y0 - 2.0 * level + y2
            val delta = if (abs(denominator) > 1e-9) {
                (0.5 * (y0 - y2) / denominator).coerceIn(-0.5, 0.5)
            } else {
                0.0
            }
            val refinedDistance = ((index + delta) * distanceStep).coerceAtLeast(0.0)
            val refinedLevel = level + 0.5 * (y2 - y0) * delta + 0.5 * denominator * delta * delta
            candidates.add(
                DtfFault(
                    distanceM = refinedDistance,
                    timeNs = 2.0 * refinedDistance / (SPEED_OF_LIGHT * velocityFactor) * 1e9,
                    magnitudeDb = refinedLevel,
                )
            )
        }

        // Non-maximum suppression: zero padding creates a dense grid, so two adjacent bins can
        // both look like peaks on the same main lobe. Keep the strongest first.
        val accepted = ArrayList<DtfFault>(maxFaults)
        for (candidate in candidates.sortedByDescending { it.magnitudeDb }) {
            if (accepted.any { abs(it.distanceM - candidate.distanceM) < minSeparationM }) continue
            accepted.add(candidate)
            if (accepted.size >= maxFaults) break
        }
        return accepted.sortedBy { it.distanceM }
    }

    /**
     * Down-samples the trace for the chart while keeping the tallest sample of every bin, so a
     * narrow fault never disappears between two drawn points.
     */
    private fun decimateForDisplay(
        levels: DoubleArray,
        distanceStep: Double,
        lastIndex: Int,
    ): List<DtfSample> {
        val sourceCount = lastIndex + 1
        val targetCount = min(sourceCount, MAX_DISPLAY_SAMPLES)
        val samples = ArrayList<DtfSample>(targetCount + 2)
        samples.add(DtfSample(0.0, levels[0]))
        for (bin in 0 until targetCount) {
            val start = bin * sourceCount / targetCount
            val end = ((bin + 1) * sourceCount / targetCount).coerceAtMost(sourceCount)
            var best = start
            for (index in start until end) {
                if (levels[index] > levels[best]) best = index
            }
            val sample = DtfSample(best * distanceStep, levels[best])
            if (sample.distanceM > samples.last().distanceM) samples.add(sample)
        }
        val finalDistance = lastIndex * distanceStep
        if (finalDistance > samples.last().distanceM) {
            samples.add(DtfSample(finalDistance, levels[lastIndex]))
        }
        return samples
    }
}

/** Human-readable distance with a unit chosen for its magnitude. */
fun formatDistance(meters: Double): String {
    if (!meters.isFinite()) return "—"
    val magnitude = abs(meters)
    if (magnitude < 1e-9) return "0 m"
    return when {
        magnitude >= 1.0 -> String.format("%.3f m", meters)
        magnitude >= 0.01 -> String.format("%.1f cm", meters * 100.0)
        magnitude >= 1e-4 -> String.format("%.2f mm", meters * 1000.0)
        magnitude >= 1e-7 -> String.format("%.1f µm", meters * 1e6)
        else -> String.format("%.0f nm", meters * 1e9)
    }
}

/** Compact distance suitable for axis tick labels. */
fun formatDistanceShort(meters: Double): String {
    if (!meters.isFinite()) return "—"
    val magnitude = abs(meters)
    if (magnitude < 1e-9) return "0 m"
    return when {
        magnitude >= 1.0 -> String.format("%.2f m", meters)
        magnitude >= 0.01 -> String.format("%.1f cm", meters * 100.0)
        magnitude >= 1e-4 -> String.format("%.1f mm", meters * 1000.0)
        else -> String.format("%.0f µm", meters * 1e6)
    }
}
