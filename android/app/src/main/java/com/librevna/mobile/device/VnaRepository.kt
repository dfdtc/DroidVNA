/*
 * LibreVNA Mobile - native Android client for LibreVNA / Harogic B60.
 * Copyright (C) 2026 The LibreVNA Mobile Authors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.librevna.mobile.device

import android.content.Context
import android.hardware.usb.UsbDevice
import com.librevna.mobile.data.CalibrationStore
import com.librevna.mobile.model.CalMath
import com.librevna.mobile.model.CalStandard
import com.librevna.mobile.model.CalibrationEngine
import com.librevna.mobile.model.CalibrationSet
import com.librevna.mobile.model.Complex
import com.librevna.mobile.model.SParam
import com.librevna.mobile.model.SolCal
import com.librevna.mobile.model.SweepConfig
import com.librevna.mobile.model.SweepMerge
import com.librevna.mobile.model.SweepResult
import com.librevna.mobile.model.ThruCal
import com.librevna.mobile.model.VnaDeviceInfo
import com.librevna.mobile.model.VnaStatus
import com.librevna.mobile.usb.UsbVnaManager
import com.librevna.mobile.usb.UsbVnaSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.sqrt

class VnaRepository(
    context: Context,
    private val usb: UsbVnaManager,
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val store = CalibrationStore(appContext)
    private val controlMutex = Mutex()

    val connection = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val info = MutableStateFlow<VnaDeviceInfo?>(null)
    val status = MutableStateFlow<VnaStatus?>(null)
    val config = MutableStateFlow(SweepConfig().clamped(null))
    val sweep = MutableStateFlow<SweepResult?>(null)
    /** The last fully assembled sweep; partial in-flight snapshots never land here. */
    val lastCompleteSweep = MutableStateFlow<SweepResult?>(null)
    val running = MutableStateFlow(false)
    val sweepRate = MutableStateFlow(0.0)
    val sweepCount = MutableStateFlow(0L)
    val averagingProgress = MutableStateFlow(1.0)
    val calibration = MutableStateFlow(store.load())
    val logs = MutableStateFlow<List<String>>(emptyList())
    val lastError = MutableStateFlow<String?>(null)
    val singleSweep = MutableStateFlow(false)
    val connectBusy = MutableStateFlow(false)
    val sourceKind = MutableStateFlow<SourceKind?>(null)

    private val rawSweeps = MutableSharedFlow<SweepResult>(extraBufferCapacity = 8)
    private var source: VnaSource? = null
    private var sourceJob: Job? = null
    private var lastSweepNanos = 0L
    private var lastLogAt = 0L
    private var connectedUsbName: String? = null
    private var calibrationKey: String = ""
    /** Last trace used for the rolling display (raw, before calibration). */
    private var displayRaw: SweepResult? = null
    private var displayGrid: String = ""

    private var accS11: Array<Complex>? = null
    private var accS21: Array<Complex>? = null
    private var accS12: Array<Complex>? = null
    private var accS22: Array<Complex>? = null
    private var accCounts: Array<IntArray>? = null
    private var accSweeps = 0

    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm:ss")

    init {
        addLog("LibreVNA Mobile ready. " + calibration.value.sol.size + " SOL cal(s) loaded.")
        scope.launch {
            usb.devices.collect { devices ->
                val name = connectedUsbName ?: return@collect
                val connected = connection.value
                if (connected is ConnectionState.Connected &&
                    sourceKind.value == SourceKind.USB &&
                    devices.none { it.deviceName == name }
                ) {
                    addLog("USB device detached: " + name)
                    lastError.value = "USB 设备已断开"
                    disconnect()
                }
            }
        }
    }

    val sourceInfo: VnaSource? get() = source

    fun addLog(message: String) {
        val line = LocalTime.now().format(timeFormat) + "  " + message
        logs.update { (it + line).takeLast(300) }
        lastLogAt = System.currentTimeMillis()
    }

    fun clearLog() {
        logs.value = emptyList()
    }

    fun setLastError(message: String?) {
        lastError.value = message
    }

    fun setConfig(newConfig: SweepConfig) {
        val previous = config.value
        val clamped = newConfig.clamped(info.value)
        val sameGrid = previous.startHz == clamped.startHz &&
            previous.stopHz == clamped.stopHz &&
            previous.points == clamped.points &&
            previous.logSweep == clamped.logSweep
        config.value = clamped
        if (!sameGrid) {
            clearDisplay()
            sweep.value = null
            lastCompleteSweep.value = null
        }
        singleSweep.value = false
        sweepRate.value = 0.0
        lastSweepNanos = 0L
        resetAveraging()
        val src = source ?: return
        scope.launch {
            val ok = controlMutex.withLock { src.start(clamped, standby = !clamped.continuous) }
            running.value = ok && clamped.continuous
            if (!ok) lastError.value = "设备拒绝了扫描设置"
        }
    }

    fun resume() {
        val src = source ?: return
        val cfg = config.value.copy(continuous = true)
        config.value = cfg
        singleSweep.value = false
        resetAveraging()
        scope.launch {
            val ok = controlMutex.withLock { src.start(cfg, standby = false) }
            running.value = ok
            if (!ok) lastError.value = "无法启动扫描"
        }
    }

    fun pause() {
        val src = source ?: return
        running.value = false
        singleSweep.value = false
        sweepRate.value = 0.0
        scope.launch { controlMutex.withLock { src.stop() } }
    }

    /** Suspend until the VNA is idle; used before switching E-Cal standards. */
    suspend fun stopScanning() {
        running.value = false
        singleSweep.value = false
        sweepRate.value = 0.0
        controlMutex.withLock { source?.stop() }
    }

    fun toggleRunning() {
        if (running.value) pause() else resume()
    }

    /** One sweep in standby mode; the device stops again afterwards. */
    fun requestSingleSweep() {
        val src = source ?: return
        scope.launch {
            val cfg = config.value.copy(continuous = false)
            config.value = cfg
            sweepRate.value = 0.0
            lastSweepNanos = 0L
            resetAveraging()
            singleSweep.value = true
            val ok = controlMutex.withLock {
                val started = src.start(cfg, standby = true)
                if (started) src.initiate() else false
            }
            if (!ok) {
                lastError.value = "无法启动单次扫描"
                singleSweep.value = false
            }
            running.value = false
        }
    }

    fun connectUsb(device: UsbDevice) {
        if (connectBusy.value) return
        connectBusy.value = true
        scope.launch {
            try {
                closeSource()
                info.value = null
                status.value = null
                sweep.value = null
                lastCompleteSweep.value = null
                clearDisplay()
                sourceKind.value = null
                connection.value = ConnectionState.Connecting("USB " + device.deviceName)
                addLog("USB device detected: " + UsbVnaManager.describe(device))
                if (!usb.hasPermission(device)) {
                    addLog("Requesting USB permission…")
                    val deferred = usb.requestPermission(device)
                    val granted = withTimeoutOrNull(60_000) { deferred.await() } ?: false
                    if (!granted) {
                        connection.value = ConnectionState.Error("USB 访问被拒绝。请在系统弹窗中允许访问该 USB 设备。")
                        addLog("USB permission denied")
                        return@launch
                    }
                }
                val src = withContext(Dispatchers.IO) { UsbVnaSource.open(appContext, device) }
                connectedUsbName = device.deviceName
                attachSource(src, SourceKind.USB, src.info.usbName)
                addLog("Connected: " + src.info.hardwareName + " · FW " + src.info.firmware + " · " + src.info.serial)
                if (src.info.protocolVersion != 14) {
                    addLog("Warning: device protocol " + src.info.protocolVersion + ", app expects 14")
                }
                var cfg = config.value.clamped(src.info)
                if (src.info.hardwareName == "Harogic B60") {
                    // VNB-60 firmware 1.8.6 is much more usable at its high IFBW; 10 kHz makes
                    // each point take seconds. Leave the point count modest for a quick first view.
                    cfg = cfg.copy(ifbwHz = minOf(75_000, src.info.maxIfbwHz))
                }
                config.value = cfg
                resetAveraging()
                val ok = controlMutex.withLock { src.start(cfg, standby = false) }
                running.value = ok
                if (!ok) lastError.value = "设备拒绝扫描设置"
                usb.refresh()
            } catch (e: Exception) {
                val msg = e.message ?: e.javaClass.simpleName
                connection.value = ConnectionState.Error("USB 连接失败: " + msg)
                lastError.value = msg
                addLog("USB connect failed: " + msg)
                closeSource()
            } finally {
                connectBusy.value = false
            }
        }
    }

    fun startDemo() {
        if (connectBusy.value) return
        connectedUsbName = null
        connectBusy.value = true
        scope.launch {
            try {
                closeSource()
                sweep.value = null
                lastCompleteSweep.value = null
                clearDisplay()
                val src = DemoVnaSource()
                attachSource(src, SourceKind.DEMO, "Demo DUT")
                val cfg = config.value.clamped(src.info)
                config.value = cfg
                resetAveraging()
                val ok = controlMutex.withLock { src.start(cfg, standby = false) }
                running.value = ok
                addLog("Demo mode: synthetic 1.75–2.65 GHz band-pass DUT")
            } catch (e: Exception) {
                connection.value = ConnectionState.Error("Demo 启动失败: " + (e.message ?: "unknown"))
            } finally {
                connectBusy.value = false
            }
        }
    }

    private fun attachSource(src: VnaSource, kind: SourceKind, label: String) {
        source = src
        info.value = src.info
        val key = calibrationKeyFor(src.info)
        calibrationKey = key
        if (calibration.value.sourceKey != key) {
            calibration.value = CalibrationSet(sourceKey = key)
            store.save(calibration.value)
        }
        sourceKind.value = kind
        connection.value = ConnectionState.Connected(kind, label)
        lastError.value = null
        sourceJob?.cancel()
        sourceJob = scope.launch {
            launch {
                src.sweeps.collect { raw -> handleRawSweep(raw) }
            }
            launch {
                src.status.collect { status.value = it }
            }
            launch {
                src.logs.collect { line -> addLog("DEV  " + line) }
            }
            launch {
                src.errors.collect { message ->
                    lastError.value = message
                    addLog("ERROR  " + message)
                }
            }
        }
    }

    private suspend fun closeSource() {
        sourceJob?.cancel()
        sourceJob = null
        val src = source
        source = null
        connectedUsbName = null
        if (src != null) {
            controlMutex.withLock { runCatching { src.close() } }
        }
        running.value = false
        singleSweep.value = false
        clearDisplay()
        resetAveraging()
    }

    fun disconnect() {
        if (connectBusy.value) return
        connectBusy.value = true
        scope.launch {
            closeSource()
            connection.value = ConnectionState.Disconnected
            info.value = null
            status.value = null
            sweep.value = null
            lastCompleteSweep.value = null
            clearDisplay()
            sourceKind.value = null
            connectedUsbName = null
            connectBusy.value = false
            addLog("Disconnected")
        }
    }

    private fun resetAveraging() {
        accS11 = null
        accS21 = null
        accS12 = null
        accS22 = null
        accCounts = null
        accSweeps = 0
        averagingProgress.value = 1.0
    }

    private fun handleRawSweep(raw: SweepResult) {
        // A late frame from the previous configuration must not pollute the rolling trace.
        if (!gridMatches(raw, config.value)) return
        if (raw.isPartial) {
            publishPartial(raw)
            return
        }
        displayRaw = raw
        displayGrid = gridKey(config.value)
        rawSweeps.tryEmit(raw)
        val cal = calibration.value
        val calibrated = if (cal.isEmpty) raw else CalibrationEngine.apply(raw, cal)
        val average = config.value.averaging.coerceAtLeast(1)
        if (average <= 1) {
            publish(calibrated)
            return
        }
        val n = calibrated.size
        if (accS11 == null || accS11!!.size != n) {
            accS11 = Array(n) { Complex.ZERO }
            accS21 = Array(n) { Complex.ZERO }
            accS12 = Array(n) { Complex.ZERO }
            accS22 = Array(n) { Complex.ZERO }
            accCounts = Array(4) { IntArray(n) }
            accSweeps = 0
        }
        accumulate(SParam.S11.ordinal, calibrated.s11)
        accumulate(SParam.S21.ordinal, calibrated.s21)
        accumulate(SParam.S12.ordinal, calibrated.s12)
        accumulate(SParam.S22.ordinal, calibrated.s22)
        accSweeps++
        val averaged = buildAverage(calibrated, n)
        averagingProgress.value = (accSweeps.toDouble() / average).coerceIn(0.0, 1.0)
        publish(averaged)
        if (accSweeps >= average) {
            resetAveraging()
        }
    }

    private fun accumulate(param: Int, values: Array<Complex>) {
        val arrays = arrayOf(accS11, accS21, accS12, accS22)
        val target = arrays[param] ?: return
        val counts = accCounts ?: return
        for (i in values.indices) {
            val v = values[i]
            if (v.isFinite()) {
                target[i] = target[i] + v
                counts[param][i]++
            }
        }
    }

    private fun buildAverage(base: SweepResult, n: Int): SweepResult {
        val counts = accCounts ?: return base
        fun average(param: Int, source: Array<Complex>?): Array<Complex> {
            val out = Array(n) { Complex.NAN }
            if (source == null) return out
            for (i in 0 until n) {
                val c = counts[param][i]
                if (c > 0) out[i] = source[i] * (1.0 / c)
            }
            return out
        }
        return SweepResult(
            freqHz = base.freqHz.copyOf(),
            s11 = average(0, accS11),
            s21 = average(1, accS21),
            s12 = average(2, accS12),
            s22 = average(3, accS22),
            cdbm = base.cdbm,
            calibrated = base.calibrated,
            completedAtNanos = base.completedAtNanos,
            sweepDurationNanos = base.sweepDurationNanos,
            measuredPoints = n,
        )
    }

    /**
     * Shows an in-flight sweep while it is being acquired. Partial snapshots deliberately do
     * not touch the sweep count, rate, averaging accumulator or single-sweep latch.
     */
    private fun publishPartial(raw: SweepResult) {
        // Averaging is only meaningful once a full sweep has arrived; showing partials would
        // make the trace jump between averaged and non-averaged data.
        if (config.value.averaging > 1) return
        val key = gridKey(config.value)
        val base = displayRaw?.takeIf { displayGrid == key }
        // Keep the previous trace for points the new sweep has not reached yet. The chart
        // therefore always shows a complete curve and new points overwrite old ones in place.
        val merged = SweepMerge.merge(base, raw)
        displayRaw = merged
        displayGrid = key
        val cal = calibration.value
        sweep.value = if (cal.isEmpty) merged else CalibrationEngine.apply(merged, cal)
    }

    private fun clearDisplay() {
        displayRaw = null
        displayGrid = ""
    }

    private fun gridKey(cfg: SweepConfig): String =
        cfg.startHz.toString() + ":" + cfg.stopHz + ":" + cfg.points + ":" + cfg.logSweep

    private fun gridMatches(raw: SweepResult, cfg: SweepConfig): Boolean {
        if (raw.size != cfg.points || raw.freqHz.isEmpty()) return false
        val span = (cfg.stopHz - cfg.startHz).coerceAtLeast(1L)
        val tolerance = (span / 100).coerceAtLeast(1_000L)
        val mid = raw.freqHz[raw.size / 2]
        return kotlin.math.abs(raw.freqHz.first() - cfg.startHz) <= tolerance &&
            kotlin.math.abs(raw.freqHz.last() - cfg.stopHz) <= tolerance &&
            kotlin.math.abs(mid - cfg.frequencyAt(raw.size / 2)) <= tolerance
    }

    private fun publish(result: SweepResult) {
        lastCompleteSweep.value = result
        sweep.value = result
        sweepCount.update { it + 1 }
        singleSweep.value = false
        val now = System.nanoTime()
        if (running.value) {
            if (lastSweepNanos != 0L) {
                val delta = (now - lastSweepNanos).toDouble()
                if (delta > 1_000_000) {
                    val instant = 1e9 / delta
                    sweepRate.value = sweepRate.value * 0.7 + instant * 0.3
                }
            }
        } else {
            sweepRate.value = 0.0
        }
        lastSweepNanos = now
    }

    suspend fun captureRawSweep(timeoutMs: Long = 6000): SweepResult? {
        if (source == null) return null
        return withTimeoutOrNull(timeoutMs) { rawSweeps.first() }
    }

    /** Wait for a second sweep so an in-flight sweep with the old standard is not captured. */
    private suspend fun captureSettledSweep(): SweepResult? {
        val first = captureRawSweep(8000) ?: return null
        val second = withTimeoutOrNull(8000) { rawSweeps.first() }
        return second ?: first
    }

    suspend fun captureStandard(port: Int, kind: CalStandard): SweepResult? {
        val src = source ?: return null
        if (port == 2 && !config.value.twoPort) {
            val cfg = config.value.copy(twoPort = true, continuous = true)
            config.value = cfg
            controlMutex.withLock { src.start(cfg, standby = false) }
            running.value = true
            delay(250)
        }
        if (!running.value) {
            controlMutex.withLock { src.start(config.value.copy(continuous = true), standby = false) }
            running.value = true
            delay(200)
        }
        src.prepareCalStandard(port, kind)
        addLog("Capturing " + kind.label + " on port " + port + "…")
        return try {
            captureSettledSweep()
        } finally {
            src.clearCalPreparation()
        }
    }

    suspend fun captureThru(): SweepResult? {
        val src = source ?: return null
        if (!config.value.twoPort) {
            val cfg = config.value.copy(twoPort = true, continuous = true)
            config.value = cfg
            controlMutex.withLock { src.start(cfg, standby = false) }
            running.value = true
            delay(250)
        }
        if (!running.value) {
            controlMutex.withLock { src.start(config.value.copy(continuous = true), standby = false) }
            running.value = true
            delay(200)
        }
        src.prepareCalThru()
        addLog("Capturing THRU…")
        return try {
            captureSettledSweep()
        } finally {
            src.clearCalPreparation()
        }
    }

    fun applySol(
        port: Int,
        open: SweepResult,
        short: SweepResult,
        load: SweepResult,
        openDelayPs: Double = 0.0,
        shortDelayPs: Double = 0.0,
    ): String? {
        val reflection = when (port) {
            1 -> open.s11 to (short.s11 to load.s11)
            else -> open.s22 to (short.s22 to load.s22)
        }
        val cal = CalMath.solveSol(
            freqHz = open.freqHz,
            open = reflection.first,
            short = reflection.second.first,
            load = reflection.second.second,
            openDelayPs = openDelayPs,
            shortDelayPs = shortDelayPs,
        ) ?: return "标准件过于相似，无法计算 SOL（检查连接）"
        val withPort = cal.copy(port = port)
        val set = calibration.value.withSol(withPort)
        setCalibration(set)
        addLog("SOL calibration applied to port " + port)
        return null
    }

    fun applyThru(raw: SweepResult): String? {
        val hasS21 = raw.s21.all { it.isFinite() }
        val hasS12 = raw.s12.all { it.isFinite() }
        if (!hasS21 && !hasS12) return "THRU 数据无效（需要双端口扫描）"
        var set = calibration.value
        if (hasS21) set = set.withThru(ThruCal(1, 2, raw.freqHz.copyOf(), raw.s21.copyOf()))
        if (hasS12) set = set.withThru(ThruCal(2, 1, raw.freqHz.copyOf(), raw.s12.copyOf()))
        setCalibration(set)
        addLog("THRU normalization applied")
        return null
    }

    fun setCalibration(set: CalibrationSet) {
        val keyed = set.copy(sourceKey = calibrationKey)
        calibration.value = keyed
        store.save(keyed)
        resetAveraging()
    }

    fun clearCalibration() {
        calibration.value = CalibrationSet(sourceKey = calibrationKey)
        store.save(calibration.value)
        resetAveraging()
        addLog("Calibration cleared")
    }

    private fun calibrationKeyFor(info: VnaDeviceInfo?): String {
        if (info == null) return ""
        if (info.isDemo) return "demo"
        return info.serial.ifBlank { info.hardwareName + ":" + info.firmware }
    }

    fun clearSol(port: Int) {
        setCalibration(calibration.value.copy(sol = calibration.value.sol.filterNot { it.port == port }))
    }

    fun clearThru() {
        setCalibration(calibration.value.copy(thrus = emptyList()))
    }

    fun shutdown() {
        runCatching { scope.launch { closeSource() } }
        scope.cancel()
    }
}