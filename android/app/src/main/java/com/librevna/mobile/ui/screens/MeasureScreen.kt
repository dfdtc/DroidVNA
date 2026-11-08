/*
 * LibreVNA Mobile - native Android client for LibreVNA / Harogic B60.
 * Copyright (C) 2026 The LibreVNA Mobile Authors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.librevna.mobile.ui.screens

import android.hardware.usb.UsbDevice
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import com.librevna.mobile.ui.text.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.librevna.mobile.MainViewModel
import com.librevna.mobile.device.ConnectionState
import com.librevna.mobile.device.SourceKind
import com.librevna.mobile.model.CalibrationSet
import com.librevna.mobile.model.Marker
import com.librevna.mobile.model.SParam
import com.librevna.mobile.model.SweepConfig
import com.librevna.mobile.model.SweepResult
import com.librevna.mobile.model.VnaStatus
import com.librevna.mobile.model.formatDb
import com.librevna.mobile.model.formatFrequency
import com.librevna.mobile.model.formatIfbw
import com.librevna.mobile.ui.chart.ChartMode
import com.librevna.mobile.ui.chart.VnaChart
import com.librevna.mobile.ui.chart.color
import com.librevna.mobile.ui.components.ChoiceChip
import com.librevna.mobile.ui.components.ConnectionPill
import com.librevna.mobile.ui.components.GoodGreen
import com.librevna.mobile.ui.components.KeyValueRow
import com.librevna.mobile.ui.components.Metric
import com.librevna.mobile.ui.components.OutlineButton
import com.librevna.mobile.ui.components.ParamChip
import com.librevna.mobile.ui.components.SectionCard
import com.librevna.mobile.ui.components.SectionLabel
import com.librevna.mobile.ui.components.StatusDot
import com.librevna.mobile.ui.components.SweepPulse
import com.librevna.mobile.ui.text.tr
import com.librevna.mobile.ui.theme.VnaError
import com.librevna.mobile.ui.theme.VnaOutline
import com.librevna.mobile.ui.theme.VnaPrimary
import com.librevna.mobile.ui.theme.VnaSecondary
import com.librevna.mobile.ui.theme.VnaSurface
import com.librevna.mobile.ui.theme.VnaSurfaceElevated
import com.librevna.mobile.ui.theme.VnaText
import com.librevna.mobile.ui.theme.VnaTextMuted
import kotlin.math.roundToInt

@Composable
fun MeasureScreen(vm: MainViewModel, wide: Boolean) {
    val connection by vm.repo.connection.collectAsStateWithLifecycle()
    val sweep by vm.repo.sweep.collectAsStateWithLifecycle()
    val config by vm.repo.config.collectAsStateWithLifecycle()
    val info by vm.repo.info.collectAsStateWithLifecycle()
    val enabled by vm.enabledParams.collectAsStateWithLifecycle()
    val mode by vm.chartMode.collectAsStateWithLifecycle()
    val autoY by vm.chartAutoY.collectAsStateWithLifecycle()
    val logFrequencyAxis by vm.logFrequencyAxis.collectAsStateWithLifecycle()
    val markers by vm.markers.collectAsStateWithLifecycle()
    val running by vm.repo.running.collectAsStateWithLifecycle()
    val singleSweep by vm.repo.singleSweep.collectAsStateWithLifecycle()
    val rate by vm.repo.sweepRate.collectAsStateWithLifecycle()
    val averagingProgress by vm.repo.averagingProgress.collectAsStateWithLifecycle()
    val calibration by vm.repo.calibration.collectAsStateWithLifecycle()
    val status by vm.repo.status.collectAsStateWithLifecycle()
    val connecting = connection is ConnectionState.Connecting

    when (val conn = connection) {
        is ConnectionState.Disconnected, is ConnectionState.Error -> {
            ConnectPanel(
                vm = vm,
                error = (conn as? ConnectionState.Error)?.message,
            )
        }
        is ConnectionState.Connecting -> {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator(color = VnaPrimary)
                Spacer(Modifier.height(16.dp))
                Text("正在连接…", color = VnaText, fontSize = 16.sp)
                Spacer(Modifier.height(8.dp))
                Text(conn.target, color = VnaTextMuted, fontSize = 12.sp)
            }
        }
        is ConnectionState.Connected -> {
            if (wide) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Column(Modifier.weight(1f).fillMaxHeight()) {
                        WarningStrip(status)
                        TraceModeRow(vm, config, enabled, mode, autoY, { vm.chartAutoY.value = !autoY }, { vm.showTraceSettings.value = true })
                        Spacer(Modifier.height(8.dp))
                        VnaChart(
                            result = sweep,
                            config = config,
                            enabled = enabled,
                            mode = mode,
                            markers = markers,
                            onMoveMarker = vm::moveMarker,
                            sweepRate = rate,
                            averaging = config.averaging,
                            averagingProgress = averagingProgress.toFloat(),
                            calibrated = calibration.isEmpty.not(),
                            sweepActive = running || singleSweep,
                            singleSweep = singleSweep,
                            autoY = autoY,
                            onAutoYChange = { vm.chartAutoY.value = it },
                            logFrequencyAxis = logFrequencyAxis,
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                        )
                        if (markers.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            MarkerBar(vm, sweep, markers, enabled, mode)
                        }
                    }
                    MeasureSidebar(
                        vm = vm,
                        config = config,
                        info = info,
                        connection = conn,
                        running = running,
                        singleSweep = singleSweep,
                        rate = rate,
                        averagingProgress = averagingProgress,
                        calibration = calibration,
                        status = status,
                        markerCount = markers.size,
                        modifier = Modifier.width(370.dp).fillMaxHeight(),
                    )
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    WarningStrip(status)
                    TraceModeRow(vm, config, enabled, mode, autoY, { vm.chartAutoY.value = !autoY }, { vm.showTraceSettings.value = true })
                    VnaChart(
                        result = sweep,
                        config = config,
                        enabled = enabled,
                        mode = mode,
                        markers = markers,
                        onMoveMarker = vm::moveMarker,
                        sweepRate = rate,
                        averaging = config.averaging,
                        averagingProgress = averagingProgress.toFloat(),
                        calibrated = calibration.isEmpty.not(),
                        sweepActive = running || singleSweep,
                        singleSweep = singleSweep,
                        autoY = autoY,
                        onAutoYChange = { vm.chartAutoY.value = it },
                        logFrequencyAxis = logFrequencyAxis,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    )
                    if (markers.isNotEmpty()) MarkerBar(vm, sweep, markers, enabled, mode)
                    ControlBar(
                        vm = vm,
                        running = running,
                        config = config,
                    )
                }
            }
        }
    }
}

@Composable
private fun WarningStrip(status: VnaStatus?) {
    val message = when {
        status == null -> null
        status.adcOverload == true -> "ADC 过载：降低发射功率或增加衰减"
        status.unlevel == true -> "源未稳幅（unlevel）：当前频率/功率下输出不可靠"
        status.sourceLocked == false || status.loLocked == false -> "PLL 未锁定：检查参考时钟"
        else -> null
    }
    if (message != null) {
        Surface(
            color = VnaError.copy(alpha = 0.14f),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = VnaError, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(message, color = VnaError, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun TraceModeRow(
    vm: MainViewModel,
    config: SweepConfig,
    enabled: Set<SParam>,
    mode: ChartMode,
    autoY: Boolean,
    onToggleAutoY: () -> Unit,
    onOpenTraceSettings: () -> Unit,
) {
    Column {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (p in SParam.entries) {
                val transmission = p == SParam.S21 || p == SParam.S12
                val available = (config.twoPort || !p.needsTwoPort) &&
                    !((mode == ChartMode.VSWR || mode == ChartMode.SMITH) && transmission)
                ParamChip(
                    param = p,
                    selected = enabled.contains(p),
                    enabled = available,
                    onClick = { vm.toggleParam(p) },
                )
            }
            Spacer(Modifier.width(6.dp))
            Box(Modifier.width(1.dp).height(22.dp).background(VnaOutline))
            Spacer(Modifier.width(6.dp))
            for (m in ChartMode.entries) {
                ChoiceChip(m.label, mode == m) { vm.chartMode.value = m }
            }
            if (mode != ChartMode.SMITH) {
                Spacer(Modifier.width(6.dp))
                Box(Modifier.width(1.dp).height(22.dp).background(VnaOutline))
                Spacer(Modifier.width(6.dp))
                ChoiceChip(if (autoY) "Y 自动" else "Y 手动", autoY) { onToggleAutoY() }
            }
            IconButton(onClick = onOpenTraceSettings, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = tr("迹线设置"),
                    tint = VnaTextMuted,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun MarkerBar(vm: MainViewModel, sweep: SweepResult?, markers: List<Marker>, enabled: Set<SParam>, mode: ChartMode) {
    val delta by vm.deltaMarkers.collectAsStateWithLifecycle()
    Surface(
        color = VnaSurface,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                for (marker in markers) {
                    MarkerCard(
                        vm = vm,
                        sweep = sweep,
                        marker = marker,
                        enabled = enabled,
                        mode = mode,
                        onDelete = { vm.removeMarker(marker.id) },
                    )
                }
                Surface(
                    color = VnaSurfaceElevated,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.clickable { vm.addMarkerAtCenter() },
                ) {
                    Row(
                        Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = VnaPrimary, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("标记", color = VnaPrimary, fontSize = 12.sp)
                    }
                }
                OutlineButton("峰值", onClick = vm::peakMarker)
                OutlineButton("谷值", onClick = vm::valleyMarker)
                ChoiceChip("Δ", delta) { vm.deltaMarkers.value = !delta }
            }
            if (delta && markers.size >= 2 && sweep != null) {
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(
                        "Δ M" + markers[1].id + "−M" + markers[0].id,
                        color = VnaSecondary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                    for (p in SParam.entries) {
                        if (!enabled.contains(p)) continue
                        if ((mode == ChartMode.VSWR || mode == ChartMode.SMITH) && (p == SParam.S21 || p == SParam.S12)) continue
                        val a = markerMetric(sweep, markers[0], p, mode)
                        val b = markerMetric(sweep, markers[1], p, mode)
                        if (a != null && b != null) {
                            val unit = when (mode) {
                                ChartMode.DB -> " dB"
                                ChartMode.PHASE -> "°"
                                ChartMode.VSWR -> ""
                                ChartMode.SMITH -> " |Γ|"
                            }
                            val value = if (mode == ChartMode.DB) formatDb(b - a) else String.format("%.3f", b - a) + unit
                            Text(
                                p.label + " " + value,
                                color = p.color,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun markerMetric(sweep: SweepResult?, marker: Marker, param: SParam, mode: ChartMode): Double? {
    if (sweep == null) return null
    val index = sweep.nearestIndex(marker.freqHz)
    val c = sweep.valueAt(param, index)
    if (!c.isFinite()) return null
    return when (mode) {
        ChartMode.DB -> c.magDb()
        ChartMode.PHASE -> c.phaseDeg()
        ChartMode.VSWR -> c.vswr()
        ChartMode.SMITH -> c.abs()
    }
}

@Composable
private fun MarkerCard(
    vm: MainViewModel,
    sweep: SweepResult?,
    marker: Marker,
    enabled: Set<SParam>,
    mode: ChartMode,
    onDelete: () -> Unit,
) {
    val index = sweep?.nearestIndex(marker.freqHz) ?: 0
    val frequency = sweep?.freqHz?.getOrNull(index)?.toDouble() ?: marker.freqHz
    Surface(
        color = VnaSurfaceElevated,
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, VnaOutline),
    ) {
        Column(Modifier.padding(start = 9.dp, end = 4.dp, top = 6.dp, bottom = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("M" + marker.id, color = VnaSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(6.dp))
                Text(formatFrequency(frequency, 4), color = VnaText, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "删除标记", tint = VnaTextMuted, modifier = Modifier.size(13.dp))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                for (p in SParam.entries) {
                    if (!enabled.contains(p) || sweep == null) continue
                    if ((mode == ChartMode.VSWR || mode == ChartMode.SMITH) && (p == SParam.S21 || p == SParam.S12)) continue
                    val c = sweep.valueAt(p, index)
                    if (!c.isFinite()) continue
                    val text = when (mode) {
                        ChartMode.SMITH -> p.label + " |Γ|" + String.format("%.3f", c.abs())
                        ChartMode.PHASE -> p.label + " " + String.format("%.0f°", c.phaseDeg())
                        ChartMode.VSWR -> p.label + " " + String.format("%.2f", c.vswr())
                        ChartMode.DB -> p.label + " " + String.format("%.1f dB", c.magDb())
                    }
                    Text(text, color = p.color, fontSize = 10.5.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

@Composable
private fun MeasureSidebar(
    vm: MainViewModel,
    config: SweepConfig,
    info: com.librevna.mobile.model.VnaDeviceInfo?,
    connection: ConnectionState,
    running: Boolean,
    singleSweep: Boolean,
    rate: Double,
    averagingProgress: Double,
    calibration: CalibrationSet,
    status: VnaStatus?,
    markerCount: Int,
    modifier: Modifier = Modifier,
) {
    val enabled by vm.enabledParams.collectAsStateWithLifecycle()
    val chartMode by vm.chartMode.collectAsStateWithLifecycle()
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SectionCard(
            title = "采集",
            trailing = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SweepPulse(active = running || singleSweep, singleSweep = singleSweep)
                    ConnectionPill(connection) { vm.navigate(com.librevna.mobile.AppScreen.DEVICE) }
                }
            },
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = vm::toggleRunning,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (running) VnaSurfaceElevated else VnaPrimary,
                        contentColor = if (running) VnaText else Color(0xFF00201C),
                    ),
                    shape = RoundedCornerShape(15.dp),
                    modifier = Modifier.weight(1f).height(58.dp),
                ) {
                    Icon(if (running) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (running) "暂停扫描" else "开始扫描", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = vm::singleSweep,
                    shape = RoundedCornerShape(15.dp),
                    modifier = Modifier.height(58.dp),
                ) {
                    Text("单次", fontSize = 14.sp)
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Metric("速率", String.format("%.1f/s", rate))
                Metric("点数", config.points.toString())
                Metric(
                    "平均",
                    if (config.averaging > 1) config.averaging.toString() + "×" else "关",
                    valueColor = if (config.averaging > 1) VnaSecondary else VnaText,
                )
            }
            if (config.averaging > 1) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { averagingProgress.toFloat() },
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                    color = VnaSecondary,
                    trackColor = VnaSurfaceElevated,
                )
            }
            Spacer(Modifier.height(10.dp))
            val stat = status
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                StatusDot(stat?.sourceLocked, "源锁定")
                StatusDot(stat?.loLocked, "LO 锁定")
                StatusDot(stat?.adcOverload?.not(), "ADC")
            }
        }

        SectionCard(
            title = "扫描设置",
            trailing = {
                OutlineButton("编辑", onClick = { vm.showSweepSheet.value = true })
            },
        ) {
            KeyValueRow("起始", formatFrequency(config.startHz.toDouble(), 4))
            KeyValueRow("终止", formatFrequency(config.stopHz.toDouble(), 4))
            KeyValueRow("中频带宽", formatIfbw(config.ifbwHz))
            KeyValueRow("功率", String.format("%.1f dBm", config.powerDbm))
            KeyValueRow("端口", if (config.twoPort) "2-port（S11/S21/S12/S22）" else "1-port（S11/S21）")
            KeyValueRow("平均", config.averaging.toString() + "×")
        }

        SectionCard(
            title = "迹线",
            trailing = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(markerCount.toString() + " 个标记", color = VnaTextMuted, fontSize = 11.sp)
                    IconButton(
                        onClick = { vm.showTraceSettings.value = true },
                        modifier = Modifier.size(30.dp),
                    ) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = tr("迹线设置"),
                            tint = VnaTextMuted,
                            modifier = Modifier.size(17.dp),
                        )
                    }
                }
            },
        ) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                for (p in SParam.entries) {
                    val transmission = p == SParam.S21 || p == SParam.S12
                    val available = (config.twoPort || !p.needsTwoPort) &&
                        !((chartMode == ChartMode.VSWR || chartMode == ChartMode.SMITH) && transmission)
                    ParamChip(p, enabled.contains(p), available) {
                        vm.toggleParam(p)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlineButton("＋ 标记", onClick = vm::addMarkerAtCenter)
                OutlineButton("峰值", onClick = vm::peakMarker)
                OutlineButton("谷值", onClick = vm::valleyMarker)
                OutlineButton("清除", onClick = vm::clearMarkers)
            }
        }

        SectionCard(
            title = "校准",
            trailing = {
                Text(
                    if (calibration.isEmpty) "未校准" else "已应用",
                    color = if (calibration.isEmpty) VnaTextMuted else GoodGreen,
                    fontSize = 11.sp,
                )
            },
        ) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (calibration.solFor(1) != null) ChoiceChip("P1 SOL", true) {}
                if (calibration.solFor(2) != null) ChoiceChip("P2 SOL", true) {}
                if (calibration.thrus.isNotEmpty()) ChoiceChip("THRU", true) {}
            }
            if (calibration.isEmpty) {
                Text(
                    "1 端口 SOL 与 THRU 归一化可显著改善测量精度。",
                    color = VnaTextMuted,
                    fontSize = 11.5.sp,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = { vm.navigate(com.librevna.mobile.AppScreen.CALIBRATION) },
                shape = RoundedCornerShape(13.dp),
                colors = ButtonDefaults.buttonColors(containerColor = VnaSurfaceElevated, contentColor = VnaText),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Science, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("打开校准向导", fontSize = 13.sp)
            }
        }

        SectionCard(title = "导出") {
            Text(
                "当前迹线可保存为 Touchstone .s2p/.s1p 或 CSV，方便在 PC 上继续分析。",
                color = VnaTextMuted,
                fontSize = 11.5.sp,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { vm.showExportSheet.value = true },
                    shape = RoundedCornerShape(13.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = VnaPrimary, contentColor = Color(0xFF00201C)),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.ShowChart, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("导出数据", fontSize = 13.sp)
                }
                OutlinedButton(onClick = { vm.navigate(com.librevna.mobile.AppScreen.DEVICE) }) {
                    Text("设备", fontSize = 13.sp)
                }
            }
        }

        if (info != null) {
            Text(
                info.displayName + " · FW " + info.firmware + " · " + info.protocolVersion,
                color = VnaTextMuted,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun ControlBar(vm: MainViewModel, running: Boolean, config: SweepConfig) {
    Surface(color = VnaSurface, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = vm::toggleRunning,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (running) VnaSurfaceElevated else VnaPrimary,
                    contentColor = if (running) VnaText else Color(0xFF00201C),
                ),
                modifier = Modifier.size(58.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
            ) {
                Icon(if (running) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(26.dp))
            }
            ControlItem("单次") { vm.singleSweep() }
            ControlItem("扫描") { vm.showSweepSheet.value = true }
            ControlItem("校准") { vm.navigate(com.librevna.mobile.AppScreen.CALIBRATION) }
            ControlItem("导出") { vm.showExportSheet.value = true }
            ControlItem("设备") { vm.navigate(com.librevna.mobile.AppScreen.DEVICE) }
        }
    }
}

@Composable
private fun ControlItem(label: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier.clickable { onClick() }.padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, color = VnaText, fontSize = 12.sp)
    }
}

@Composable
private fun ConnectPanel(vm: MainViewModel, error: String?) {
    val devices by vm.usb.devices.collectAsStateWithLifecycle()
    val allDevices by vm.usb.allDevices.collectAsStateWithLifecycle()
    val connectBusy by vm.repo.connectBusy.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier.padding(20.dp).fillMaxWidth().widthIn(max = 760.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Spacer(Modifier.height(10.dp))
            Text("LibreVNA", color = VnaPrimary, fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Text("把矢量网络分析仪装进口袋", color = VnaText, fontSize = 17.sp)
            Text(
                "通过 USB-C OTG 直接连接 LibreVNA / Harogic B60，无需 PC 或共享内核容器。完整支持双端口扫描、触摸图表、标记、1 端口 SOL 校准与 Touchstone 导出。",
                color = VnaTextMuted,
                fontSize = 13.sp,
            )
            if (error != null) {
                Surface(color = VnaError.copy(alpha = 0.12f), shape = RoundedCornerShape(12.dp)) {
                    Text(error, color = VnaError, fontSize = 12.5.sp, modifier = Modifier.padding(12.dp))
                }
            }

            SectionCard(title = "连接 USB VNA") {
                if (devices.isEmpty()) {
                    Text("未检测到 USB VNA（LibreVNA / Harogic B60）。", color = VnaText, fontSize = 14.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "请确认：\n• 使用支持数据传输的 USB-C OTG 转接头\n• LibreVNA / Harogic B60 已上电并连接\n• 固件协议版本为 14（旧固件可能不兼容）\n• 在系统弹窗中允许此应用访问 USB 设备",
                        color = VnaTextMuted,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                    )
                } else {
                    for (device in devices) {
                        DeviceRow(vm, device)
                        Spacer(Modifier.height(8.dp))
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { devices.firstOrNull()?.let { vm.connect(it) } },
                        enabled = devices.isNotEmpty() && !connectBusy,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = VnaPrimary, contentColor = Color(0xFF00201C)),
                    ) {
                        Icon(Icons.Default.Usb, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (connectBusy) "连接中…" else "连接 USB", fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = { vm.usb.refresh() },
                        shape = RoundedCornerShape(14.dp),
                    ) { Text("重新扫描") }
                }
            }

            SectionCard(title = "没有硬件？先试试演示模式") {
                Text(
                    "演示模式生成一个 1.75–2.65 GHz 带通滤波器的合成测量，带有真实的系统误差，可完整走一遍 SOL 校准流程。",
                    color = VnaTextMuted,
                    fontSize = 12.5.sp,
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = vm::demo,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = VnaSecondary, contentColor = Color(0xFF2B1A00)),
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("启动演示设备", fontWeight = FontWeight.Bold)
                }
            }

            if (androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < 700) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FeatureCard("触摸图表", "双指缩放、单指读数、拖动标记")
                    FeatureCard("SOL 校准", "Open / Short / Load 与 THRU 归一化")
                    FeatureCard("离线演示", "无需硬件即可体验完整流程")
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FeatureCard("触摸图表", "双指缩放、单指读数、拖动标记", Modifier.weight(1f))
                    FeatureCard("SOL 校准", "Open / Short / Load 与 THRU 归一化", Modifier.weight(1f))
                    FeatureCard("离线演示", "无需硬件即可体验完整流程", Modifier.weight(1f))
                }
            }

            if (allDevices.isNotEmpty()) {
                SectionCard(title = "USB 诊断") {
                    Text("已枚举的 USB 设备（用于排查 OTG/权限问题）", color = VnaTextMuted, fontSize = 11.sp)
                    Spacer(Modifier.height(8.dp))
                    for (device in allDevices) {
                        val known = com.librevna.mobile.usb.UsbVnaManager.isSupportedVna(device)
                        val granted = vm.usb.hasPermission(device)
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(6.dp).clip(CircleShape).background(if (known) GoodGreen else VnaTextMuted))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                com.librevna.mobile.usb.UsbVnaManager.describe(device),
                                color = VnaText,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                if (granted) "已授权" else "未授权",
                                color = if (granted) GoodGreen else VnaTextMuted,
                                fontSize = 10.sp,
                            )
                        }
                    }
                }
            }
            Text(
                "提示：USB 调试与安装权限由系统控制；应用本身只需要 USB Host 访问权限。",
                color = VnaTextMuted,
                fontSize = 10.5.sp,
                modifier = Modifier.padding(bottom = 20.dp),
            )
        }
    }
}

@Composable
private fun FeatureCard(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Surface(color = VnaSurface, shape = RoundedCornerShape(16.dp), modifier = modifier) {
        Column(Modifier.padding(14.dp)) {
            Text(title, color = VnaPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, color = VnaTextMuted, fontSize = 10.5.sp, lineHeight = 15.sp)
        }
    }
}

@Composable
private fun DeviceRow(vm: MainViewModel, device: UsbDevice) {
    val granted = vm.usb.hasPermission(device)
    Surface(color = VnaSurfaceElevated, shape = RoundedCornerShape(14.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Usb, contentDescription = null, tint = GoodGreen)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(com.librevna.mobile.usb.UsbVnaManager.deviceLabel(device), color = VnaText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    String.format("%04X:%04X · %s", device.vendorId, device.productId, device.deviceName),
                    color = VnaTextMuted,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
            Text(
                if (granted) "USB 已授权" else "需要授权",
                color = if (granted) GoodGreen else VnaSecondary,
                fontSize = 10.5.sp,
            )
            Spacer(Modifier.width(10.dp))
            Button(
                onClick = { vm.connect(device) },
                shape = RoundedCornerShape(11.dp),
                colors = ButtonDefaults.buttonColors(containerColor = VnaPrimary, contentColor = Color(0xFF00201C)),
            ) { Text("连接", fontSize = 12.sp) }
        }
    }
}