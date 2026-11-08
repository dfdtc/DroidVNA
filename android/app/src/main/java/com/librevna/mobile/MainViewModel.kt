/*
 * LibreVNA Mobile - native Android client for LibreVNA / Harogic B60.
 * Copyright (C) 2026 The LibreVNA Mobile Authors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.librevna.mobile

import android.app.Application
import android.hardware.usb.UsbDevice
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.librevna.mobile.device.ConnectionState
import com.librevna.mobile.device.SourceKind
import com.librevna.mobile.device.VnaRepository
import com.librevna.mobile.export.ExportManager
import com.librevna.mobile.export.ExportPayload
import com.librevna.mobile.export.Touchstone
import com.librevna.mobile.model.CalKitStandard
import com.librevna.mobile.model.CalMath
import com.librevna.mobile.model.CalStandard
import com.librevna.mobile.model.CalStandardKind
import com.librevna.mobile.model.CalibrationFileImporter
import com.librevna.mobile.model.CalibrationSet
import com.librevna.mobile.model.Complex
import com.librevna.mobile.model.SParam
import com.librevna.mobile.model.SolCal
import com.librevna.mobile.model.ThruCal
import com.librevna.mobile.model.SweepConfig
import com.librevna.mobile.model.SweepResult
import com.librevna.mobile.model.Marker
import com.librevna.mobile.ui.chart.ChartMode
import com.librevna.mobile.usb.EcalDevice
import com.librevna.mobile.usb.EcalSnapshot
import com.librevna.mobile.usb.EcalStandard
import com.librevna.mobile.usb.UsbVnaManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min

enum class AppScreen { MEASURE, DEVICE, CALIBRATION }

enum class ExportFormat(val label: String, val extension: String, val mime: String) {
    S2P("Touchstone .s2p (2-port)", "s2p", "application/octet-stream"),
    S1P("Touchstone .s1p (port 1)", "s1p", "application/octet-stream"),
    CSV("CSV (dB / phase)", "csv", "text/csv"),
}

data class CalDraft(
    val p1: Map<CalStandard, SweepResult> = emptyMap(),
    val p2: Map<CalStandard, SweepResult> = emptyMap(),
    val thru: SweepResult? = null,
) {
    fun captures(port: Int): Map<CalStandard, SweepResult> = if (port == 1) p1 else p2
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as LibreVnaApplication
    val repo: VnaRepository = app.repository
    val usb: UsbVnaManager = app.usbManager

    val screen = MutableStateFlow(AppScreen.MEASURE)
    val showSweepSheet = MutableStateFlow(false)
    val showExportSheet = MutableStateFlow(false)
    val showAbout = MutableStateFlow(false)
    val chartMode = MutableStateFlow(ChartMode.DB)
    val enabledParams = MutableStateFlow(setOf(SParam.S11, SParam.S21))
    val markers = MutableStateFlow<List<Marker>>(emptyList())
    val deltaMarkers = MutableStateFlow(false)
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 8)

    val calPort = MutableStateFlow(1)
    val calDraft = MutableStateFlow(CalDraft())
    val calBusy = MutableStateFlow(false)
    val calMessage = MutableStateFlow<String?>(null)
    val calOpenDelayPs = MutableStateFlow("0")
    val calShortDelayPs = MutableStateFlow("0")

    val ecalConnected = MutableStateFlow(false)
    val ecalInfo = MutableStateFlow<EcalSnapshot?>(null)
    val ecalCoefficientSets = MutableStateFlow<List<String>>(emptyList())
    val ecalSelectedSet = MutableStateFlow("")
    val ecalCoefficientsLoaded = MutableStateFlow(false)
    val ecalBusy = MutableStateFlow(false)
    val ecalProgress = MutableStateFlow(0.0)
    val ecalMessage = MutableStateFlow<String?>(null)
    val ecalAvailable = MutableStateFlow(0)

    private var ecal: EcalDevice? = null
    private var ecalStandards: Map<String, CalKitStandard> = emptyMap()

    private var nextMarkerId = 1

    init {
        viewModelScope.launch {
            usb.attached.collect { device ->
                messages.emit("检测到 " + UsbVnaManager.deviceLabel(device) + "，点击连接")
            }
        }
        viewModelScope.launch {
            repo.connection.collect { state ->
                if (state is ConnectionState.Connected) {
                    showSweepSheet.value = false
                    showExportSheet.value = false
                }
            }
        }
        viewModelScope.launch {
            usb.allDevices.collect { list ->
                ecalAvailable.value = list.count { UsbVnaManager.isEcal(it) }
                if (ecalConnected.value && list.none { UsbVnaManager.isEcal(it) }) {
                    // The E-Cal module was unplugged; drop the stale handle.
                    runCatching { ecal?.close() }
                    ecal = null
                    ecalConnected.value = false
                    ecalInfo.value = null
                    ecalCoefficientsLoaded.value = false
                    ecalStandards = emptyMap()
                    ecalMessage.value = "电子校准件已断开"
                }
            }
        }
    }

    fun message(text: String) {
        viewModelScope.launch { messages.emit(text) }
    }

    fun navigate(target: AppScreen) {
        screen.value = target
        if (target == AppScreen.CALIBRATION) calMessage.value = null
    }

    fun connect(device: UsbDevice) {
        calDraft.value = CalDraft()
        calMessage.value = null
        repo.connectUsb(device)
    }

    fun demo() {
        calDraft.value = CalDraft()
        calMessage.value = null
        repo.startDemo()
    }

    fun disconnect() {
        repo.disconnect()
    }

    fun toggleRunning() {
        repo.toggleRunning()
    }

    fun singleSweep() {
        repo.requestSingleSweep()
    }

    fun applyConfig(config: SweepConfig) {
        repo.setConfig(config)
        showSweepSheet.value = false
    }

    fun toggleParam(param: SParam) {
        enabledParams.update { current ->
            val next = if (current.contains(param)) current - param else current + param
            if (next.isEmpty()) current else next
        }
    }

    fun addMarkerAtCenter() {
        val sweep = repo.sweep.value ?: run {
            message("还没有扫描数据")
            return
        }
        val freq = (sweep.freqHz.first() + sweep.freqHz.last()) / 2.0
        addMarker(freq)
    }

    fun addMarker(freqHz: Double) {
        val marker = Marker(nextMarkerId++, freqHz)
        markers.update { it + marker }
    }

    fun moveMarker(id: Int, freqHz: Double) {
        markers.update { list -> list.map { if (it.id == id) it.copy(freqHz = freqHz) else it } }
    }

    fun removeMarker(id: Int) {
        markers.update { list -> list.filterNot { it.id == id } }
    }

    fun clearMarkers() {
        markers.value = emptyList()
        nextMarkerId = 1
    }

    fun peakMarker() {
        val sweep = repo.sweep.value ?: return
        val reflectionMode = chartMode.value == ChartMode.VSWR || chartMode.value == ChartMode.SMITH
        val param = when {
            reflectionMode && enabledParams.value.contains(SParam.S11) -> SParam.S11
            !reflectionMode && enabledParams.value.contains(SParam.S21) -> SParam.S21
            else -> enabledParams.value.firstOrNull() ?: SParam.S11
        }
        val values = sweep.param(param)
        var best = -1
        var bestValue = Double.NEGATIVE_INFINITY
        for (i in values.indices) {
            val c = values[i]
            if (!c.isFinite()) continue
            val db = c.magDb()
            if (db > bestValue) {
                bestValue = db
                best = i
            }
        }
        if (best < 0) return
        val freq = sweep.freqHz[best].toDouble()
        val existing = markers.value.firstOrNull()
        if (existing == null) addMarker(freq) else moveMarker(existing.id, freq)
    }

    fun valleyMarker() {
        val sweep = repo.sweep.value ?: return
        val param = when {
            enabledParams.value.contains(SParam.S11) -> SParam.S11
            else -> enabledParams.value.firstOrNull() ?: SParam.S11
        }
        val values = sweep.param(param)
        var best = -1
        var bestValue = Double.POSITIVE_INFINITY
        for (i in values.indices) {
            val c = values[i]
            if (!c.isFinite()) continue
            val db = c.magDb()
            if (db < bestValue) {
                bestValue = db
                best = i
            }
        }
        if (best < 0) return
        val freq = sweep.freqHz[best].toDouble()
        val existing = markers.value.firstOrNull()
        if (existing == null) addMarker(freq) else moveMarker(existing.id, freq)
    }

    /** Import a LibreVNA-GUI .cal file and reconstruct 1-port SOL + THRU for this device. */
    fun importCal(uri: Uri) {
        if (calBusy.value) return
        if (repo.connection.value !is ConnectionState.Connected || repo.sourceKind.value != SourceKind.USB) {
            calMessage.value = "请先连接 USB VNA，再导入 .cal（校准会按当前设备序列号保存）"
            return
        }
        calBusy.value = true
        calMessage.value = null
        viewModelScope.launch {
            try {
                val text = withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openInputStream(uri)
                        ?.bufferedReader()
                        ?.use { it.readText() }
                        ?: throw IllegalStateException("无法打开文件")
                }
                val result = withContext(Dispatchers.Default) {
                    CalibrationFileImporter.parse(text)
                }
                repo.setCalibration(result.calibration)
                calDraft.value = CalDraft()
                val warnings = ArrayList(result.warnings)
                val serial = repo.info.value?.serial
                if (result.device.isNotBlank() && serial != null && result.device != serial) {
                    warnings.add(".cal 记录的是 " + result.device + "，当前设备是 " + serial + "，精度可能受影响")
                }
                val warning = if (warnings.isEmpty()) "" else "；" + warnings.joinToString("；")
                calMessage.value = "已导入 " + result.summary + warning
            } catch (e: Exception) {
                calMessage.value = "导入失败：" + (e.message ?: e.javaClass.simpleName)
            } finally {
                calBusy.value = false
            }
        }
    }

    fun connectEcal() {
        if (ecalConnected.value || ecalBusy.value) return
        val device = usb.allDevices.value.firstOrNull { UsbVnaManager.isEcal(it) }
        if (device == null) {
            ecalMessage.value = "没有发现 LibreCAL / ECB-60（1209:4122）"
            return
        }
        ecalBusy.value = true
        ecalMessage.value = null
        viewModelScope.launch {
            var opened: EcalDevice? = null
            try {
                val granted = if (usb.hasPermission(device)) {
                    true
                } else {
                    withTimeoutOrNull(60_000) { usb.requestPermission(device).await() } ?: false
                }
                if (!granted) throw IllegalStateException("USB 权限被拒绝")
                opened = withContext(Dispatchers.IO) { EcalDevice.open(getApplication(), device) }
                val snapshot = withContext(Dispatchers.IO) { opened.refreshInfo() }
                if (snapshot.ports <= 0) throw IllegalStateException("设备没有报告有效端口数")
                ecal?.close()
                ecal = opened
                ecalInfo.value = snapshot
                ecalCoefficientSets.value = snapshot.coefficientSets
                ecalSelectedSet.value = snapshot.coefficientSets.firstOrNull() ?: ""
                ecalCoefficientsLoaded.value = false
                ecalStandards = emptyMap()
                ecalConnected.value = true
                ecalMessage.value = "已连接 " + snapshot.idn.trim().ifBlank { "电子校准件" } +
                    " · FW " + snapshot.firmware + " · " + snapshot.ports + " 端口" +
                    if (snapshot.coefficientSets.isEmpty()) " · 无系数集" else " · 系数集 " + snapshot.coefficientSets.joinToString(",")
            } catch (e: Exception) {
                runCatching { opened?.close() }
                ecal?.close()
                ecal = null
                ecalConnected.value = false
                ecalInfo.value = null
                ecalMessage.value = "连接电子校准件失败：" + (e.message ?: e.javaClass.simpleName)
            } finally {
                ecalBusy.value = false
            }
        }
    }

    fun disconnectEcal() {
        val dev = ecal
        ecal = null
        ecalConnected.value = false
        ecalInfo.value = null
        ecalCoefficientSets.value = emptyList()
        ecalSelectedSet.value = ""
        ecalCoefficientsLoaded.value = false
        ecalStandards = emptyMap()
        ecalProgress.value = 0.0
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { repo.stopScanning() }
            runCatching { dev?.setAllNone(2) }
            runCatching { dev?.close() }
        }
    }

    fun selectEcalCoefficientSet(name: String) {
        if (name == ecalSelectedSet.value) return
        ecalSelectedSet.value = name
        ecalCoefficientsLoaded.value = false
        ecalStandards = emptyMap()
    }

    fun loadEcalCoefficients() {
        val dev = ecal ?: run {
            ecalMessage.value = "请先连接电子校准件"
            return
        }
        if (ecalBusy.value) return
        val set = ecalSelectedSet.value.ifBlank { ecalCoefficientSets.value.firstOrNull() ?: "" }
        if (set.isBlank()) {
            ecalMessage.value = "设备没有可用系数集"
            return
        }
        val ports = min(ecalInfo.value?.ports ?: 2, 2)
        ecalBusy.value = true
        ecalCoefficientsLoaded.value = false
        ecalProgress.value = 0.0
        ecalMessage.value = "正在加载系数 " + set + "…"
        viewModelScope.launch {
            try {
                val loaded = withContext(Dispatchers.IO) {
                    dev.readCoefficientSet(set, ports) { progress -> ecalProgress.value = progress }
                }
                ecalStandards = loaded
                ecalCoefficientsLoaded.value = true
                ecalProgress.value = 1.0
                ecalMessage.value = "已加载 " + loaded.size + " 组系数（" + set + "）"
            } catch (e: Exception) {
                ecalCoefficientsLoaded.value = false
                ecalStandards = emptyMap()
                ecalMessage.value = "加载系数失败：" + (e.message ?: e.javaClass.simpleName)
            } finally {
                ecalBusy.value = false
            }
        }
    }

    fun refreshEcalStatus() {
        val dev = ecal ?: return
        if (ecalBusy.value) return
        viewModelScope.launch {
            val temperature = withContext(Dispatchers.IO) { runCatching { dev.temperature() }.getOrNull() }
            val stable = withContext(Dispatchers.IO) { runCatching { dev.stable() }.getOrNull() }
            ecalInfo.value = ecalInfo.value?.copy(temperatureC = temperature, stable = stable)
        }
    }

    fun startEcalCalibration() {
        val dev = ecal ?: run {
            ecalMessage.value = "请先连接电子校准件"
            return
        }
        if (calBusy.value || ecalBusy.value) return
        val vnaInfo = repo.info.value
        if (repo.connection.value !is ConnectionState.Connected || repo.sourceKind.value != SourceKind.USB || vnaInfo == null) {
            ecalMessage.value = "请先连接 USB VNA 再开始电子校准"
            return
        }
        if (!ecalCoefficientsLoaded.value) {
            ecalMessage.value = "请先加载电子校准件系数"
            return
        }
        val vnaPorts = min(vnaInfo.numPorts, 2)
        val calPorts = min(ecalInfo.value?.ports ?: 0, vnaPorts)
        if (calPorts < 1) {
            ecalMessage.value = "没有可用的校准端口"
            return
        }
        val required = ArrayList<String>()
        for (p in 1..calPorts) {
            required.add("P" + p + "_OPEN")
            required.add("P" + p + "_SHORT")
            required.add("P" + p + "_LOAD")
        }
        if (calPorts >= 2) required.add("P12_THROUGH")
        if (!ecalStandards.keys.containsAll(required)) {
            ecalMessage.value = "系数不完整，缺少：" + required.filterNot { ecalStandards.containsKey(it) }.joinToString(",")
            return
        }

        val wasRunning = repo.running.value
        calBusy.value = true
        ecalBusy.value = true
        ecalProgress.value = 0.0
        calMessage.value = null
        ecalMessage.value = "正在执行电子校准…"
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val sols = ArrayList<SolCal>()
                val thrus = ArrayList<ThruCal>()
                val total = calPorts * 3 + if (calPorts >= 2) 1 else 0
                var done = 0
                fun step() {
                    done++
                    ecalProgress.value = done.toDouble() / total
                }
                try {
                    val stable = runCatching { dev.stable() }.getOrNull()
                    if (stable == false) {
                        ecalMessage.value = "电子校准件正在升温，等待温度稳定…"
                        var waited = 0
                        while (waited < 90_000) {
                            delay(2000)
                            waited += 2000
                            if (runCatching { dev.stable() }.getOrNull() == true) break
                        }
                        if (runCatching { dev.stable() }.getOrNull() != true) {
                            ecalMessage.value = "温度未完全稳定，继续校准"
                        }
                    }
                    for (p in 1..calPorts) {
                        val captures = LinkedHashMap<CalStandard, com.librevna.mobile.model.SweepResult>()
                        for (kind in listOf(CalStandard.OPEN, CalStandard.SHORT, CalStandard.LOAD)) {
                            repo.stopScanning()
                            if (!dev.setStandard(p, kind.toEcalStandard())) {
                                throw IllegalStateException("切换到 $kind 失败")
                            }
                            delay(50)
                            val sweep = repo.captureStandard(p, kind)
                                ?: throw IllegalStateException("端口 " + p + " " + kind.label + " 没有扫描数据")
                            captures[kind] = sweep
                            step()
                        }
                        val open = captures[CalStandard.OPEN]!!
                        val short = captures[CalStandard.SHORT]!!
                        val load = captures[CalStandard.LOAD]!!
                        val grid = open.freqHz
                        if (grid.size < 2) throw IllegalStateException("捕获点数不足")
                        fun measured(sweep: com.librevna.mobile.model.SweepResult): Array<Complex> {
                            val arr = if (p == 1) sweep.s11 else sweep.s22
                            return Array(grid.size) { i ->
                                if (sweep.freqHz.size == grid.size && sweep.freqHz[i] == grid[i]) {
                                    arr[i]
                                } else {
                                    CalMath.interpolate(sweep.freqHz, arr, grid[i].toDouble())
                                }
                            }
                        }
                        fun actual(key: String, selector: (CalKitStandard) -> Array<Complex>): Array<Complex> {
                            val std = ecalStandards[key] ?: throw IllegalStateException("缺少系数 " + key)
                            val arr = selector(std)
                            if (std.freqHz.size < 2 || arr.size != std.freqHz.size) {
                                throw IllegalStateException("系数 " + key + " 没有有效数据")
                            }
                            return Array(grid.size) { i -> CalMath.interpolate(std.freqHz, arr, grid[i].toDouble()) }
                        }
                        val oa = actual("P" + p + "_OPEN") { it.s11 }
                        val sa = actual("P" + p + "_SHORT") { it.s11 }
                        val la = actual("P" + p + "_LOAD") { it.s11 }
                        val cal = CalMath.solveSol(
                            grid,
                            measured(open),
                            measured(short),
                            measured(load),
                            oa,
                            sa,
                            la,
                            p,
                        ) ?: throw IllegalStateException("端口 " + p + " SOL 计算无解")
                        sols.add(cal)
                    }

                    if (calPorts >= 2) {
                        repo.stopScanning()
                        if (!dev.setStandard(1, EcalStandard.THROUGH, 2)) {
                            throw IllegalStateException("切换到 THROUGH 失败")
                        }
                        delay(50)
                        val thru = repo.captureThru()
                            ?: throw IllegalStateException("THRU 没有扫描数据")
                        val std = ecalStandards["P12_THROUGH"]
                            ?: throw IllegalStateException("缺少 P12_THROUGH 系数")
                        if (std.freqHz.size < 2 || std.s21.size != std.freqHz.size || std.s12.size != std.freqHz.size) {
                            throw IllegalStateException("P12_THROUGH 系数没有有效数据")
                        }
                        val grid = thru.freqHz
                        fun act(arr: Array<Complex>): Array<Complex> =
                            Array(grid.size) { i -> CalMath.interpolate(std.freqHz, arr, grid[i].toDouble()) }
                        val a21 = act(std.s21)
                        val a12 = act(std.s12)
                        val t21 = Array(grid.size) { i ->
                            val a = a21[i]
                            val m = thru.s21[i]
                            if (a.isFinite() && a.abs() > 1e-12 && m.isFinite()) m / a else m
                        }
                        val t12 = Array(grid.size) { i ->
                            val a = a12[i]
                            val m = thru.s12[i]
                            if (a.isFinite() && a.abs() > 1e-12 && m.isFinite()) m / a else m
                        }
                        thrus.add(ThruCal(1, 2, grid.copyOf(), t21))
                        thrus.add(ThruCal(2, 1, grid.copyOf(), t12))
                        step()
                    }

                    repo.setCalibration(CalibrationSet(sols, thrus))
                    calMessage.value = "电子校准完成：SOL " + sols.size + " 端口 · THRU " + thrus.size / 2 + " 组"
                    ecalMessage.value = calMessage.value
                } catch (e: Exception) {
                    val msg = "电子校准失败：" + (e.message ?: e.javaClass.simpleName)
                    calMessage.value = msg
                    ecalMessage.value = msg
                } finally {
                    // Stop the VNA before opening/closing E-Cal relays.
                    runCatching { repo.stopScanning() }
                    runCatching { dev.setAllNone(calPorts) }
                    if (wasRunning) runCatching { repo.resume() }
                    calBusy.value = false
                    ecalBusy.value = false
                }
            }
        }
    }

    private fun CalStandard.toEcalStandard(): EcalStandard = when (this) {
        CalStandard.OPEN -> EcalStandard.OPEN
        CalStandard.SHORT -> EcalStandard.SHORT
        CalStandard.LOAD -> EcalStandard.LOAD
    }

    fun captureStandard(port: Int, standard: CalStandard) {
        if (calBusy.value) return
        calBusy.value = true
        calMessage.value = null
        viewModelScope.launch {
            try {
                val raw = repo.captureStandard(port, standard)
                if (raw == null) {
                    calMessage.value = "没有收到扫描数据，请确认设备正在扫描"
                } else {
                    calDraft.update { draft ->
                        if (port == 1) draft.copy(p1 = draft.p1 + (standard to raw))
                        else draft.copy(p2 = draft.p2 + (standard to raw))
                    }
                    calMessage.value = standard.label + " 已捕获（端口 " + port + "）"
                }
            } catch (e: Exception) {
                calMessage.value = "捕获失败：" + (e.message ?: "unknown")
            } finally {
                calBusy.value = false
            }
        }
    }

    fun captureThru() {
        if (calBusy.value) return
        calBusy.value = true
        calMessage.value = null
        viewModelScope.launch {
            try {
                val raw = repo.captureThru()
                if (raw == null) {
                    calMessage.value = "没有收到扫描数据，请确认设备正在扫描"
                } else {
                    calDraft.update { it.copy(thru = raw) }
                    calMessage.value = "THRU 已捕获"
                }
            } catch (e: Exception) {
                calMessage.value = "捕获失败：" + (e.message ?: "unknown")
            } finally {
                calBusy.value = false
            }
        }
    }

    fun computeSol(port: Int) {
        val draft = calDraft.value
        val open = draft.captures(port)[CalStandard.OPEN]
        val short = draft.captures(port)[CalStandard.SHORT]
        val load = draft.captures(port)[CalStandard.LOAD]
        if (open == null || short == null || load == null) {
            calMessage.value = "请先捕获 Open / Short / Load"
            return
        }
        if (open.size != short.size || open.size != load.size || open.size < 2) {
            calMessage.value = "三次捕获的点数不一致，请保持扫描设置不变后重做"
            return
        }
        val err = repo.applySol(
            port = port,
            open = open,
            short = short,
            load = load,
            openDelayPs = calOpenDelayPs.value.toDoubleOrNull() ?: 0.0,
            shortDelayPs = calShortDelayPs.value.toDoubleOrNull() ?: 0.0,
        )
        calMessage.value = err ?: ("端口 " + port + " SOL 校准已应用")
    }

    fun computeThru() {
        val thru = calDraft.value.thru
        if (thru == null) {
            calMessage.value = "请先捕获 THRU"
            return
        }
        val err = repo.applyThru(thru)
        calMessage.value = err ?: "THRU 归一化已应用"
    }

    fun clearCalibration() {
        repo.clearCalibration()
        calDraft.value = CalDraft()
        calMessage.value = "校准已清除"
    }

    fun clearCalPort(port: Int) {
        repo.clearSol(port)
        calDraft.update { if (port == 1) it.copy(p1 = emptyMap()) else it.copy(p2 = emptyMap()) }
    }

    fun clearDraft() {
        calDraft.value = CalDraft()
        calMessage.value = "已清除捕获数据"
    }

    fun buildExport(format: ExportFormat): ExportPayload? {
        val current = repo.sweep.value
        // Never export an in-flight partial sweep: its trailing points are still NaN.
        val sweep = (if (current != null && !current.isPartial) current else repo.lastCompleteSweep.value) ?: run {
            message("还没有可导出的扫描数据")
            return null
        }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val suffix = if (sweep.calibrated) "_cal" else "_raw"
        val name = "librevna_" + stamp + suffix + "." + format.extension
        val content = when (format) {
            ExportFormat.S2P -> Touchstone.s2p(sweep, name)
            ExportFormat.S1P -> Touchstone.s1p(sweep, name)
            ExportFormat.CSV -> Touchstone.csv(sweep)
        }
        return ExportPayload(name, format.mime, content.toByteArray())
    }

    fun saveExport(format: ExportFormat) {
        val payload = buildExport(format) ?: return
        viewModelScope.launch {
            try {
                val location = withContext(Dispatchers.IO) {
                    ExportManager.saveToDownloads(getApplication(), payload)
                }
                showExportSheet.value = false
                message("已保存：" + location)
            } catch (e: Exception) {
                message("保存失败：" + (e.message ?: "unknown"))
            }
        }
    }

    fun shareExport(format: ExportFormat) {
        val payload = buildExport(format) ?: return
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { ExportManager.share(getApplication(), payload) }
                showExportSheet.value = false
            } catch (e: Exception) {
                message("分享失败：" + (e.message ?: "unknown"))
            }
        }
    }

    fun markerValue(marker: Marker, param: SParam): Double? {
        val sweep = repo.sweep.value ?: return null
        val index = sweep.nearestIndex(marker.freqHz)
        val c = sweep.valueAt(param, index)
        return if (c.isFinite()) c.magDb() else null
    }

    fun deltaValue(markerA: Marker, markerB: Marker, param: SParam): Double? {
        val a = markerValue(markerA, param) ?: return null
        val b = markerValue(markerB, param) ?: return null
        return a - b
    }

    fun markerFrequency(marker: Marker): Double {
        val sweep = repo.sweep.value ?: return marker.freqHz
        val index = sweep.nearestIndex(marker.freqHz)
        return sweep.freqHz.getOrNull(index)?.toDouble() ?: marker.freqHz
    }

    fun formatMarkerFrequency(marker: Marker): String {
        return com.librevna.mobile.model.formatFrequency(markerFrequency(marker), 4)
    }

    override fun onCleared() {
        runCatching { ecal?.close() }
        ecal = null
        super.onCleared()
    }
}