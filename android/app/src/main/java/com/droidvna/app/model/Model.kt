/*
 * DroidVNA - native Android client for LibreVNA / Harogic B60.
 * Copyright (C) 2026 The DroidVNA Authors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.droidvna.app.model

import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min

/** A complex number with the small set of operations the VNA maths needs. */
data class Complex(val re: Double, val im: Double) {
    companion object {
        val ZERO = Complex(0.0, 0.0)
        val ONE = Complex(1.0, 0.0)
        val NAN = Complex(Double.NaN, Double.NaN)
    }

    operator fun plus(other: Complex) = Complex(re + other.re, im + other.im)
    operator fun minus(other: Complex) = Complex(re - other.re, im - other.im)
    operator fun times(other: Complex) = Complex(re * other.re - im * other.im, re * other.im + im * other.re)
    operator fun times(scale: Double) = Complex(re * scale, im * scale)
    operator fun div(other: Complex): Complex {
        val d = other.re * other.re + other.im * other.im
        return Complex((re * other.re + im * other.im) / d, (im * other.re - re * other.im) / d)
    }

    fun abs(): Double = hypot(re, im)
    fun phaseDeg(): Double = Math.toDegrees(atan2(im, re))
    fun isFinite(): Boolean = re.isFinite() && im.isFinite()
    fun magDb(): Double = 20.0 * log10(max(abs(), 1e-12))
    fun vswr(): Double {
        val m = min(abs(), 0.999999)
        return (1.0 + m) / (1.0 - m)
    }
}

/** The four S-parameters of a two-port sweep. */
enum class SParam(val label: String, val outPort: Int, val inPort: Int) {
    S11("S11", 1, 1),
    S21("S21", 2, 1),
    S12("S12", 1, 2),
    S22("S22", 2, 2);

    val needsTwoPort: Boolean get() = inPort == 2
}

class SweepResult(
    val freqHz: LongArray,
    val s11: Array<Complex>,
    val s21: Array<Complex>,
    val s12: Array<Complex>,
    val s22: Array<Complex>,
    val cdbm: Double,
    val calibrated: Boolean = false,
    val completedAtNanos: Long = System.nanoTime(),
    val sweepDurationNanos: Long = 0L,
    /**
     * True for an in-progress snapshot emitted while the device is still sweeping.
     * Points that have not arrived yet are NaN; such a result must not be counted,
     * averaged, calibrated into the stored data, or exported.
     */
    val isPartial: Boolean = false,
    /** Number of points already delivered by the current sweep; -1 when unknown. */
    val measuredPoints: Int = -1,
) {
    val size: Int get() = freqHz.size

    fun param(p: SParam): Array<Complex> = when (p) {
        SParam.S11 -> s11
        SParam.S21 -> s21
        SParam.S12 -> s12
        SParam.S22 -> s22
    }

    fun valueAt(p: SParam, index: Int): Complex {
        val arr = param(p)
        return if (index in arr.indices) arr[index] else Complex.NAN
    }

    /** Index of the point closest to [frequency]. */
    fun nearestIndex(frequency: Double): Int {
        if (freqHz.isEmpty()) return 0
        var lo = 0
        var hi = freqHz.size - 1
        while (lo < hi) {
            val mid = (lo + hi) / 2
            if (freqHz[mid] < frequency) lo = mid + 1 else hi = mid
        }
        if (lo > 0 && kotlin.math.abs(freqHz[lo - 1] - frequency) < kotlin.math.abs(freqHz[lo] - frequency)) return lo - 1
        return lo
    }
}

/**
 * Rolling display update for continuous sweeps.
 *
 * A slow sweep is delivered point by point. Instead of replacing the chart with a partial
 * trace (which looks empty to the right of the write head), keep the previous trace and
 * overwrite each point as soon as the new sweep reaches it. Missing points retain their last
 * known value, so the chart never blanks between sweeps.
 */
object SweepMerge {
    fun merge(base: SweepResult?, update: SweepResult): SweepResult {
        if (base == null || base.size != update.size) return update
        fun mergeArray(old: Array<Complex>, new: Array<Complex>): Array<Complex> {
            val out = old.copyOf()
            for (i in new.indices) {
                if (new[i].isFinite()) out[i] = new[i]
            }
            return out
        }
        return SweepResult(
            freqHz = update.freqHz.copyOf(),
            s11 = mergeArray(base.s11, update.s11),
            s21 = mergeArray(base.s21, update.s21),
            s12 = mergeArray(base.s12, update.s12),
            s22 = mergeArray(base.s22, update.s22),
            cdbm = update.cdbm,
            calibrated = false,
            completedAtNanos = update.completedAtNanos,
            sweepDurationNanos = update.sweepDurationNanos,
            isPartial = update.isPartial,
            measuredPoints = update.measuredPoints,
        )
    }
}

data class SweepConfig(
    val startHz: Long = 500_000_000L,
    val stopHz: Long = 3_000_000_000L,
    val points: Int = 401,
    val ifbwHz: Int = 10_000,
    val powerDbm: Double = -10.0,
    val twoPort: Boolean = true,
    val averaging: Int = 1,
    val logSweep: Boolean = false,
    val continuous: Boolean = true,
) {
    val spanHz: Long get() = stopHz - startHz

    fun frequencyAt(index: Int): Long {
        if (points <= 1) return startHz
        if (logSweep && startHz > 0) {
            val ratio = stopHz.toDouble() / startHz.toDouble()
            return (startHz * Math.pow(ratio, index.toDouble() / (points - 1))).toLong()
        }
        return startHz + (spanHz * index) / (points - 1)
    }

    fun frequencyArray(): LongArray = LongArray(points) { frequencyAt(it) }

    /** Keep the configuration inside the capabilities reported by the device. */
    fun clamped(info: VnaDeviceInfo?): SweepConfig {
        if (info == null) return this
        var s = startHz.coerceIn(info.minFreqHz, info.maxFreqHz)
        var e = stopHz.coerceIn(info.minFreqHz, info.maxFreqHz)
        if (e <= s) {
            val mid = (info.minFreqHz + info.maxFreqHz) / 2
            s = (mid - 100_000_000L).coerceAtLeast(info.minFreqHz)
            e = (mid + 100_000_000L).coerceAtMost(info.maxFreqHz)
            if (e <= s) e = (s + 1_000_000L).coerceAtMost(info.maxFreqHz)
        }
        val p = points.coerceIn(11, min(info.maxPoints, 2001))
        val bw = ifbwHz.coerceIn(max(info.minIfbwHz, 1), info.maxIfbwHz)
        val pwr = powerDbm.coerceIn(info.minPowerDbm, info.maxPowerDbm)
        return copy(
            startHz = s,
            stopHz = e,
            points = p,
            ifbwHz = bw,
            powerDbm = pwr,
            twoPort = twoPort && info.numPorts >= 2,
            averaging = averaging.coerceIn(1, 64),
        )
    }

    companion object {
        val PRESETS = listOf(
            Triple("10M – 6G", 10_000_000L, 6_000_000_000L),
            Triple("100M – 1G", 100_000_000L, 1_000_000_000L),
            Triple("500M – 3G", 500_000_000L, 3_000_000_000L),
            Triple("1.7G – 2.7G", 1_700_000_000L, 2_700_000_000L),
            Triple("2.4G – 2.5G", 2_400_000_000L, 2_500_000_000L),
        )
    }
}

data class VnaDeviceInfo(
    val protocolVersion: Int,
    val firmware: String,
    val hardwareVersion: Int,
    val hardwareRevision: Char,
    val hardwareName: String,
    val minFreqHz: Long,
    val maxFreqHz: Long,
    val minIfbwHz: Int,
    val maxIfbwHz: Int,
    val maxPoints: Int,
    val minPowerDbm: Double,
    val maxPowerDbm: Double,
    val numPorts: Int,
    val maxDwellUs: Int,
    val isDemo: Boolean = false,
    val serial: String = "",
    val usbName: String = "",
) {
    val displayName: String get() = if (isDemo) "Demo device" else hardwareName
}

data class VnaStatus(
    val sourceLocked: Boolean? = null,
    val loLocked: Boolean? = null,
    val adcOverload: Boolean? = null,
    val unlevel: Boolean? = null,
    val temperatureC: Double? = null,
    val extRefInUse: Boolean? = null,
    val secondaryTemperatureC: Double? = null,
    val supplyVoltage: Double? = null,
    /** Supply current in mA (the `VD0` status reports it that way). */
    val supplyCurrent: Double? = null,
    /** Whether an external reference is available at all (status bit 0). */
    val extRefAvailable: Boolean? = null,
    /**
     * Whether the device's FPGA is configured (status bit 2). On the VNB-60 this is the quickest
     * "the device is alive and did not fall back to Idle" indicator when a sweep produces no data.
     */
    val fpgaConfigured: Boolean? = null,
    val updatedAtNanos: Long = System.nanoTime(),
)

/** Format a frequency using the usual SI prefixes. */
fun formatFrequency(hz: Double, decimals: Int = 4): String {
    val a = kotlin.math.abs(hz)
    val fmt = "%." + decimals + "f"
    return when {
        a >= 1e9 -> String.format(fmt + " GHz", hz / 1e9)
        a >= 1e6 -> String.format(fmt + " MHz", hz / 1e6)
        a >= 1e3 -> String.format(fmt + " kHz", hz / 1e3)
        else -> String.format(fmt + " Hz", hz)
    }
}

fun formatFrequencyShort(hz: Double): String {
    val a = kotlin.math.abs(hz)
    return when {
        a >= 1e9 -> String.format("%.3fG", hz / 1e9)
        a >= 1e6 -> String.format("%.1fM", hz / 1e6)
        a >= 1e3 -> String.format("%.1fk", hz / 1e3)
        else -> String.format("%.0f", hz)
    }
}

fun formatDb(db: Double, decimals: Int = 1): String {
    if (!db.isFinite()) return "—"
    return String.format("%." + decimals + "f dB", db)
}

fun formatIfbw(hz: Int): String = if (hz >= 1000) String.format("%.1f kHz", hz / 1000.0) else String.format("%d Hz", hz)