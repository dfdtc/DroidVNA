/*
 * LibreVNA Mobile - native Android client for LibreVNA / Harogic B60.
 * Copyright (C) 2026 The LibreVNA Mobile Authors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.librevna.mobile.device

import com.librevna.mobile.model.CalStandard
import com.librevna.mobile.model.Complex
import com.librevna.mobile.model.SweepConfig
import com.librevna.mobile.model.SweepResult
import com.librevna.mobile.model.VnaDeviceInfo
import com.librevna.mobile.model.VnaStatus
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
 * A synthetic LibreVNA with a 1.75-2.65 GHz band-pass DUT and a plausible systematic error
 * model. It makes the whole UI (including SOL calibration) usable without hardware.
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
    }

    override suspend fun prepareCalThru() {
        calPort = 0
        calKind = null
        calThru = true
    }

    override suspend fun clearCalPreparation() {
        calPort = 0
        calKind = null
        calThru = false
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
            val dut = if (calKind != null) standardDut(f, calPort, calKind!!) else if (calThru) thruDut(f) else filterDut(f)
            var m11 = dut[0]
            var m21 = dut[1]
            var m12 = dut[2]
            var m22 = dut[3]

            // 1-port SOL error model on both ports.
            val prod1 = Complex(0.92, 0.20)
            val e001 = Complex(0.020 + 0.004 * sin(2 * PI * f / 1.5e9), 0.012)
            val e111 = Complex(0.035, -0.045)
            val prod2 = Complex(0.90, 0.24)
            val e002 = Complex(0.016 + 0.003 * cos(2 * PI * f / 1.2e9), -0.010)
            val e112 = Complex(0.040, -0.030)
            m11 = applyError(m11, e001, e111, prod1)
            m22 = applyError(m22, e002, e112, prod2)
            m21 = m21 * Complex(0.975, -0.08) + Complex(0.0007, 0.0004)
            m12 = m12 * Complex(0.968, -0.10) + Complex(0.0006, -0.0005)

            s11[i] = m11 + Complex(random.nextGaussian() * noiseScale, random.nextGaussian() * noiseScale)
            s21[i] = m21 + Complex(random.nextGaussian() * noiseScale, random.nextGaussian() * noiseScale)
            s12[i] = m12 + Complex(random.nextGaussian() * noiseScale, random.nextGaussian() * noiseScale)
            s22[i] = m22 + Complex(random.nextGaussian() * noiseScale, random.nextGaussian() * noiseScale)
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

    private fun applyError(gamma: Complex, e00: Complex, e11: Complex, prod: Complex): Complex {
        val denom = Complex.ONE - e11 * gamma
        if (denom.abs() < 1e-12) return e00
        return e00 + prod * gamma / denom
    }

    /** Ideal band-pass filter. Index order: S11, S21, S12, S22. */
    private fun filterDut(f: Double): Array<Complex> {
        val g = f / 1e9
        val edge = 0.065
        val pass = sigmoid((f - 1.75e9) / (edge * 1e9)) * sigmoid((2.65e9 - f) / (edge * 1e9))
        val ripple = 1.0 - 0.035 * cos(2 * PI * (f - 2.2e9) / 0.22e9)
        val s21mag = (0.006 + 0.94 * pass * ripple).coerceIn(0.0001, 1.0)
        val s11mag = (0.86 - 0.72 * pass).coerceIn(0.01, 0.999)
        val phase21 = -2 * PI * f * 1.55e-9 * (0.35 + 0.65 * pass) + 0.06 * sin(g * 7.0)
        val phase11 = PI + 2 * PI * f * 0.24e-9 + 0.55 * sin(2 * PI * f / 1.1e9)
        val s21 = polar(s21mag, phase21)
        return arrayOf(
            polar(s11mag, phase11),
            s21,
            s21 * Complex(0.985, -0.04),
            polar(s11mag * 0.92, phase11 + 0.35),
        )
    }

    private fun standardDut(f: Double, port: Int, kind: CalStandard): Array<Complex> {
        val gamma = when (kind) {
            CalStandard.OPEN -> Complex.ONE
            CalStandard.SHORT -> Complex(-1.0, 0.0)
            CalStandard.LOAD -> Complex.ZERO
        }
        return if (port == 2) {
            arrayOf(Complex.ZERO, polar(0.0008, 0.0), polar(0.0008, 0.0), gamma)
        } else {
            arrayOf(gamma, polar(0.0008, 0.0), polar(0.0008, 0.0), Complex.ZERO)
        }
    }

    private fun thruDut(f: Double): Array<Complex> {
        val loss = 1.0 - 0.002 * (f / 1e9)
        return arrayOf(Complex.ZERO, polar(loss, -2 * PI * f * 0.12e-9), polar(loss, -2 * PI * f * 0.12e-9), Complex.ZERO)
    }

    private fun polar(mag: Double, phase: Double) = Complex(mag * cos(phase), mag * sin(phase))

    private fun sigmoid(x: Double): Double = 1.0 / (1.0 + exp(-x))
}