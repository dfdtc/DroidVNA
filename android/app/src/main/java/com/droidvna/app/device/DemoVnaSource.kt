/*
 * DroidVNA - native Android client for LibreVNA / Harogic B60.
 * Copyright (C) 2026 The DroidVNA Authors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.droidvna.app.device

import com.droidvna.app.model.CalStandard
import com.droidvna.app.model.Complex
import com.droidvna.app.model.SoltErrorModel
import com.droidvna.app.model.SoltTerms
import com.droidvna.app.model.SweepConfig
import com.droidvna.app.model.SweepResult
import com.droidvna.app.model.VnaDeviceInfo
import com.droidvna.app.model.VnaStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A synthetic LibreVNA with a 1.75-2.65 GHz band-pass DUT and a realistic 12-term error model.
 *
 * The raw sweeps are produced by [SoltErrorModel], the exact inverse of the correction in
 * `SoltMath`, so a full SOLT calibration can be run and verified end-to-end without hardware.
 */
class DemoVnaSource : VnaSource {
    override val info = VnaDeviceInfo(
        protocolVersion = 14,
        firmware = "1.6.0-demo",
        hardwareVersion = 0x01,
        hardwareRevision = 'B',
        hardwareName = "LibreVNA V1",
        minFreqHz = 5_000L,
        maxFreqHz = 6_000_000_000L,
        minIfbwHz = 1,
        maxIfbwHz = 200_000,
        maxPoints = 1001,
        minPowerDbm = -40.0,
        maxPowerDbm = 10.0,
        numPorts = 2,
        maxDwellUs = 65535,
        isDemo = true,
        serial = "DEMO-0001",
        usbName = "Demo",
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _status = MutableStateFlow<VnaStatus?>(null)
    private val _sweeps = MutableSharedFlow<SweepResult>(extraBufferCapacity = 4)
    private val _logs = MutableSharedFlow<String>(extraBufferCapacity = 16)
    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 4)
    override val status: StateFlow<VnaStatus?> = _status
    override val sweeps: SharedFlow<SweepResult> = _sweeps
    override val logs: SharedFlow<String> = _logs
    override val errors: SharedFlow<String> = _errors

    private var loopJob: Job? = null
    private var currentConfig = SweepConfig()

    @Volatile private var calPort = 0
    @Volatile private var calKind: CalStandard? = null
    @Volatile private var calThru = false
    @Volatile private var calIsolation = false

    override suspend fun start(config: SweepConfig, standby: Boolean): Boolean {
        currentConfig = config
        loopJob?.cancel()
        loopJob = null
        _status.value = VnaStatus(
            sourceLocked = true,
            loLocked = true,
            adcOverload = false,
            unlevel = false,
            temperatureC = 39.5,
            extRefInUse = false,
            extRefAvailable = false,
            fpgaConfigured = true,
        )
        if (!standby) {
            loopJob = scope.launch {
                delay(120)
                while (isActive) {
                    val started = System.nanoTime()
                    val sweep = generate(currentConfig)
                    _sweeps.emit(sweep)
                    _status.value = VnaStatus(
                        sourceLocked = true,
                        loLocked = true,
                        adcOverload = false,
                        unlevel = false,
                        temperatureC = 39.5 + 0.8 * sin(System.currentTimeMillis() / 20000.0),
                        extRefInUse = false,
                        extRefAvailable = false,
                        fpgaConfigured = true,
                    )
                    val targetMs = sweepDurationMs(currentConfig)
                    val elapsedMs = (System.nanoTime() - started) / 1_000_000
                    delay((targetMs - elapsedMs).coerceAtLeast(30))
                }
            }
        }
        _logs.tryEmit("Demo device started · " + config.points + " points")
        return true
    }

    override suspend fun initiate(): Boolean {
        delay(160)
        _sweeps.emit(generate(currentConfig))
        return true
    }

    override suspend fun stop() {
        loopJob?.cancel()
        loopJob = null
    }

    override fun close() {
        loopJob?.cancel()
        scope.cancel()
    }

    override suspend fun prepareCalStandard(port: Int, kind: CalStandard) {
        calPort = port
        calKind = kind
        calThru = false
        calIsolation = false
    }

    override suspend fun prepareCalIsolation() {
        calPort = 0
        calKind = null
        calThru = false
        calIsolation = true
    }

    override suspend fun prepareCalThru() {
        calPort = 0
        calKind = null
        calThru = true
        calIsolation = false
    }

    override suspend fun clearCalPreparation() {
        calPort = 0
        calKind = null
        calThru = false
        calIsolation = false
    }

    private fun sweepDurationMs(config: SweepConfig): Long {
        val bwFactor = sqrt(10_000.0 / config.ifbwHz.coerceAtLeast(1))
        val stages = if (config.twoPort) 2.0 else 1.0
        val result = 40.0 + config.points * 0.42 * bwFactor * stages
        return result.toLong().coerceIn(80L, 2500L)
    }

    private fun generate(config: SweepConfig): SweepResult {
        val n = config.points
        val frequencies = config.frequencyArray()
        val s11 = Array(n) { Complex.NAN }
        val s21 = Array(n) { Complex.NAN }
        val s12 = Array(n) { Complex.NAN }
        val s22 = Array(n) { Complex.NAN }
        val random = java.util.Random(System.nanoTime())
        val noiseScale = 0.00045 * sqrt(config.ifbwHz / 10_000.0)

        for (i in 0 until n) {
            val f = frequencies[i].toDouble()
            val ideal = actualDut(f)
            val measured = SoltErrorModel.measure(ideal, errorTerms(f))
            if (measured == null) {
                s11[i] = Complex.NAN
                s21[i] = Complex.NAN
                s12[i] = Complex.NAN
                s22[i] = Complex.NAN
                continue
            }
            fun noisy(v: Complex): Complex =
                v + Complex(random.nextGaussian() * noiseScale, random.nextGaussian() * noiseScale)
            s11[i] = noisy(measured[0][0])
            s21[i] = noisy(measured[1][0])
            s12[i] = noisy(measured[0][1])
            s22[i] = noisy(measured[1][1])
        }
        if (!config.twoPort) {
            for (i in 0 until n) {
                s12[i] = Complex.NAN
                s22[i] = Complex.NAN
            }
        }
        _logs.tryEmit(String.format("Demo sweep %d pts · %.1f ms", n, sweepDurationMs(config).toDouble()))
        return SweepResult(
            freqHz = frequencies,
            s11 = s11,
            s21 = s21,
            s12 = s12,
            s22 = s22,
            cdbm = config.powerDbm,
        )
    }

    /**
     * The system error boxes of the synthetic device. Deliberately non-zero in every term so a
     * SOLT calibration has something to find; the values roughly match a real LibreVNA.
     */
    private fun errorTerms(f: Double): SoltTerms = SoltTerms(
        d1 = Complex(0.020 + 0.004 * sin(2 * PI * f / 1.5e9), 0.012),
        s1 = Complex(0.035, -0.045),
        r1 = Complex(0.92, 0.20),
        d2 = Complex(0.016 + 0.003 * cos(2 * PI * f / 1.2e9), -0.010),
        s2 = Complex(0.040, -0.030),
        r2 = Complex(0.90, 0.24),
        l12 = Complex(0.030, -0.020),
        l21 = Complex(0.028, -0.018),
        t12 = Complex(0.975, -0.08),
        t21 = Complex(0.968, -0.10),
        i12 = Complex(0.0007, 0.0004),
        i21 = Complex(0.0006, -0.0005),
    )

    /** The ideal S-matrix `[receiver][source]` seen by the VNA for the current setup. */
    private fun actualDut(f: Double): Array<Array<Complex>> = when {
        calKind != null -> onePortStandard(f, calPort, calKind!!)
        calThru -> thru(f)
        calIsolation -> Array(2) { Array(2) { Complex.ZERO } }
        else -> filterDut(f)
    }

    /**
     * Test hook: the ideal (error-free) S-matrix the current preparation state produces at
     * [frequency]. Lets the demo-mode integration test compare corrected data against truth.
     */
    internal fun idealDutAt(frequency: Double): Array<Array<Complex>> = actualDut(frequency)

    /**
     * A one-port standard with the other port terminated in a matched load, which is what the
     * 1-port SOL error model assumes (the full two-port model then reduces to it exactly).
     */
    private fun onePortStandard(f: Double, port: Int, kind: CalStandard): Array<Array<Complex>> {
        val gamma = when (kind) {
            CalStandard.OPEN -> Complex.ONE
            CalStandard.SHORT -> Complex(-1.0, 0.0)
            CalStandard.LOAD -> Complex.ZERO
        }
        val matrix = Array(2) { Array(2) { Complex.ZERO } }
        matrix[port - 1][port - 1] = gamma
        return matrix
    }

    private fun thru(f: Double): Array<Array<Complex>> {
        val loss = 1.0 - 0.002 * (f / 1e9)
        val transmission = polar(loss, -2 * PI * f * 0.12e-9)
        return arrayOf(
            arrayOf(Complex.ZERO, transmission),
            arrayOf(transmission, Complex.ZERO),
        )
    }

    /** Ideal band-pass filter, `[receiver][source]`. */
    private fun filterDut(f: Double): Array<Array<Complex>> {
        val g = f / 1e9
        val edge = 0.065
        val pass = sigmoid((f - 1.75e9) / (edge * 1e9)) * sigmoid((2.65e9 - f) / (edge * 1e9))
        val ripple = 1.0 - 0.035 * cos(2 * PI * (f - 2.2e9) / 0.22e9)
        val s21mag = (0.006 + 0.94 * pass * ripple).coerceIn(0.0001, 1.0)
        val s11mag = (0.86 - 0.72 * pass).coerceIn(0.01, 0.999)
        val phase21 = -2 * PI * f * 1.55e-9 * (0.35 + 0.65 * pass) + 0.06 * sin(g * 7.0)
        val phase11 = PI + 2 * PI * f * 0.24e-9 + 0.55 * sin(2 * PI * f / 1.1e9)
        val s21 = polar(s21mag, phase21)
        val s11 = polar(s11mag, phase11)
        val s22 = polar(s11mag * 0.92, phase11 + 0.35)
        val s12 = s21 * Complex(0.985, -0.04)
        return arrayOf(
            arrayOf(s11, s12),
            arrayOf(s21, s22),
        )
    }

    private fun polar(mag: Double, phase: Double) = Complex(mag * cos(phase), mag * sin(phase))

    private fun sigmoid(x: Double): Double = 1.0 / (1.0 + exp(-x))
}
