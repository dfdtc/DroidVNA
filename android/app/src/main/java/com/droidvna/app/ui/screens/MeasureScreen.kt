/*
 * DroidVNA - native Android client for LibreVNA / Harogic B60.
 * Copyright (C) 2026 The DroidVNA Authors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.droidvna.app.ui.screens

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
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import com.droidvna.app.ui.text.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.droidvna.app.MainViewModel
import com.droidvna.app.device.ConnectionState
import com.droidvna.app.device.SourceKind
import com.droidvna.app.model.CalibrationSet
import com.droidvna.app.model.Marker
import com.droidvna.app.model.SParam
import com.droidvna.app.model.SweepConfig
import com.droidvna.app.model.SweepResult
import com.droidvna.app.model.VnaStatus
import com.droidvna.app.model.formatDb
import com.droidvna.app.model.formatFrequency
import com.droidvna.app.model.formatIfbw
import com.droidvna.app.AppScreen
import com.droidvna.app.ui.AppOverflowMenu
import com.droidvna.app.ui.chart.ChartMode
import com.droidvna.app.ui.chart.VnaChart
import com.droidvna.app.ui.chart.color
import com.droidvna.app.ui.components.ChoiceChip
import com.droidvna.app.ui.components.CollapseToggle
import com.droidvna.app.ui.components.ConnectionPill
import com.droidvna.app.ui.components.GoodGreen
import com.droidvna.app.ui.components.KeyValueRow
import com.droidvna.app.ui.components.Metric
import com.droidvna.app.ui.components.OutlineButton
import com.droidvna.app.ui.components.ParamChip
import com.droidvna.app.ui.components.SectionCard
import com.droidvna.app.ui.components.StatusDot
import com.droidvna.app.ui.components.SweepPulse
import com.droidvna.app.ui.text.tr
import com.droidvna.app.ui.theme.VnaError
import com.droidvna.app.ui.theme.VnaOutline
import com.droidvna.app.ui.theme.VnaPrimary
import com.droidvna.app.ui.theme.VnaSecondary
import com.droidvna.app.ui.theme.VnaSurface
import com.droidvna.app.ui.theme.VnaSurfaceElevated
import com.droidvna.app.ui.theme.VnaText
import com.droidvna.app.ui.theme.VnaTextMuted
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
    val calibrationOn by vm.calibrationEnabled.collectAsStateWithLifecycle()
    val status by vm.repo.status.collectAsStateWithLifecycle()
    val topCollapsed by vm.topControlsCollapsed.collectAsStateWithLifecycle()
    val sideCollapsed by vm.sideControlsCollapsed.collectAsStateWithLifecycle()
    val notice by vm.restoreNotice.collectAsStateWithLifecycle()
    val hideWatermark by vm.hideWatermark.collectAsStateWithLifecycle()
    val focusedMarker by vm.focusedMarkerId.collectAsStateWithLifecycle()
    // The trace slider always steers a real marker: an explicit selection, else the first one.
    val focusedMarkerId = focusedMarker.takeIf { id -> markers.any { it.id == id } } ?: markers.firstOrNull()?.id
    val configuration = LocalConfiguration.current
    // A phone in landscape has room for a vertical strip of acquisition controls on the left
    // and a wider chart on the right, so it is treated like the tablet layout.
    val sideBySide = wide || (configuration.screenWidthDp >= 600 && configuration.screenWidthDp > configuration.screenHeightDp)
    val sideWidth = when {
        configuration.screenWidthDp >= 1100 -> 320.dp
        configuration.screenWidthDp >= 900 -> 280.dp
        else -> 236.dp
    }
    // On a phone held in landscape the acquisition strip belongs on the left edge, where the
    // chart keeps the rest of the width; a tablet keeps its wide card column on the right.
    val sidebarFirst = !wide
    val calibrated = calibration.isEmpty.not() && calibrationOn

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
            Column(modifier = Modifier.fillMaxSize()) {
                RestoreNotice(notice, onDismiss = vm::clearRestoreNotice)
                if (sideBySide) {
                    val chartColumn: @Composable () -> Unit = {
                        Column(Modifier.weight(1f).fillMaxHeight()) {
                            if (warningVisible(status)) {
                                WarningStrip(status)
                                Spacer(Modifier.height(6.dp))
                            }
                            MeasureHeader(
                                vm = vm,
                                connection = conn,
                                config = config,
                                enabled = enabled,
                                mode = mode,
                                autoY = autoY,
                                collapsed = topCollapsed,
                            )
                            Spacer(Modifier.height(6.dp))
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
                                calibrated = calibrated,
                                sweepActive = running || singleSweep,
                                singleSweep = singleSweep,
                                autoY = autoY,
                                onAutoYChange = { vm.chartAutoY.value = it },
                                logFrequencyAxis = logFrequencyAxis,
                                focusedMarkerId = focusedMarkerId,
                                onMoveFocusedMarker = { vm.moveFocusedMarker(it) },
                                sliderLabel = if (mode == ChartMode.SMITH) tr("迹线频率") else tr("频率"),
                                showWatermark = !hideWatermark,
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                            )
                            if (markers.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                MarkerBar(vm, sweep, markers, enabled, mode, focusedMarkerId)
                            }
                        }
                    }
                    val sideColumn: @Composable () -> Unit = {
                        if (sideCollapsed) {
                            CollapsedSideStrip(
                                onExpand = vm::toggleSideControls,
                                modifier = Modifier.width(46.dp),
                            ) {
                                VerticalControlItem("运行") { vm.toggleRunning() }
                                VerticalControlItem("单次") { vm.singleSweep() }
                                VerticalControlItem("扫描") { vm.showSweepSheet.value = true }
                                VerticalControlItem("校准") { vm.navigate(com.droidvna.app.AppScreen.CALIBRATION) }
                                VerticalControlItem("导出") { vm.showExportSheet.value = true }
                                VerticalControlItem("设备") { vm.navigate(com.droidvna.app.AppScreen.DEVICE) }
                            }
                        } else {
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
                                calibrationOn = calibrationOn,
                                status = status,
                                markerCount = markers.size,
                                full = wide,
                                modifier = Modifier.width(sideWidth).fillMaxHeight(),
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (sidebarFirst) {
                            sideColumn()
                            chartColumn()
                        } else {
                            chartColumn()
                            sideColumn()
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (warningVisible(status)) WarningStrip(status)
                        MeasureHeader(
                            vm = vm,
                            connection = conn,
                            config = config,
                            enabled = enabled,
                            mode = mode,
                            autoY = autoY,
                            collapsed = topCollapsed,
                        )
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
                            calibrated = calibrated,
                            sweepActive = running || singleSweep,
                            singleSweep = singleSweep,
                            autoY = autoY,
                            onAutoYChange = { vm.chartAutoY.value = it },
                            logFrequencyAxis = logFrequencyAxis,
                            focusedMarkerId = focusedMarkerId,
                            onMoveFocusedMarker = { vm.moveFocusedMarker(it) },
                            sliderLabel = if (mode == ChartMode.SMITH) tr("迹线频率") else tr("频率"),
                            showWatermark = !hideWatermark,
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                        )
                        if (markers.isNotEmpty()) MarkerBar(vm, sweep, markers, enabled, mode, focusedMarkerId)
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
}

private fun warningVisible(status: VnaStatus?): Boolean = status != null && (
    status.adcOverload == true || status.unlevel == true ||
        status.sourceLocked == false || status.loLocked == false
    )

@Composable
private fun VerticalControlItem(label: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier.clickable { onClick() }.padding(vertical = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, color = VnaText, fontSize = 10.5.sp, maxLines = 1)
    }
}

/** Narrow vertical strip shown when the side card column is hidden in landscape. */
@Composable
private fun CollapsedSideStrip(
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
    shortcuts: @Composable () -> Unit,
) {
    Surface(
        color = VnaSurface,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.fillMaxHeight(),
    ) {
        Column(
            modifier = Modifier.fillMaxHeight().verticalScroll(rememberScrollState()).padding(vertical = 8.dp, horizontal = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CollapseToggle(
                expanded = false,
                contentDescription = "展开采集控件",
                onClick = onExpand,
            )
            shortcuts()
        }
    }
}

/** One-line banner telling the user that the previous calibration was restored. */
@Composable
private fun RestoreNotice(notice: String?, onDismiss: () -> Unit) {
    if (notice == null) return
    Surface(
        color = VnaPrimary.copy(alpha = 0.10f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = notice,
                color = VnaPrimary,
                fontSize = 11.5.sp,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Close, contentDescription = "关闭", tint = VnaPrimary, modifier = Modifier.size(15.dp))
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

/**
 * The only control row on the measure screen: trace/mode chips on the left, connection state
 * and the single overflow menu on the right. No app bar sits above it, so the chart keeps the
 * rest of the height. When collapsed it shrinks to the marker shortcuts.
 */
@Composable
private fun MeasureHeader(
    vm: MainViewModel,
    connection: ConnectionState,
    config: SweepConfig,
    enabled: Set<SParam>,
    mode: ChartMode,
    autoY: Boolean,
    collapsed: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (collapsed) {
            Row(
                modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlineButton("＋ 标记", onClick = vm::addMarkerAtCenter)
                OutlineButton("峰值", onClick = vm::peakMarker)
                OutlineButton("谷值", onClick = vm::valleyMarker)
                OutlineButton("清除", onClick = vm::clearMarkers)
            }
        } else {
            Row(
                modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
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
                    ChoiceChip(if (autoY) "Y 自动" else "Y 手动", autoY) { vm.chartAutoY.value = !autoY }
                }
                Spacer(Modifier.width(4.dp))
            }
        }
        Spacer(Modifier.width(4.dp))
        ConnectionPill(
            state = connection,
            compact = LocalConfiguration.current.screenWidthDp < 560,
        ) { vm.navigate(AppScreen.DEVICE) }
        Spacer(Modifier.width(4.dp))
        // The collapse chevron stays outside the scrolling chip strip so it never scrolls away.
        CollapseToggle(
            expanded = !collapsed,
            contentDescription = if (collapsed) "展开迹线控件" else "收起迹线控件",
            onClick = vm::toggleTopControls,
        )
        AppOverflowMenu(vm)
    }
}


@Composable
private fun MarkerBar(
    vm: MainViewModel,
    sweep: SweepResult?,
    markers: List<Marker>,
    enabled: Set<SParam>,
    mode: ChartMode,
    focusedMarkerId: Int?,
) {
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
                        focused = marker.id == focusedMarkerId,
                        onFocus = { vm.selectMarker(marker.id) },
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
    focused: Boolean,
    onFocus: () -> Unit,
    onDelete: () -> Unit,
) {
    val index = sweep?.nearestIndex(marker.freqHz) ?: 0
    val frequency = sweep?.freqHz?.getOrNull(index)?.toDouble() ?: marker.freqHz
    Surface(
        color = if (focused) VnaSecondary.copy(alpha = 0.10f) else VnaSurfaceElevated,
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(
            if (focused) 1.5.dp else 1.dp,
            if (focused) VnaSecondary else VnaOutline,
        ),
        modifier = Modifier.clickable { onFocus() },
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

/**
 * Vertical control column used on tablets and on phones held in landscape. The acquisition
 * card comes first because this column is the primary control strip in landscape.
 */
@Composable
private fun MeasureSidebar(
    vm: MainViewModel,
    config: SweepConfig,
    info: com.droidvna.app.model.VnaDeviceInfo?,
    connection: ConnectionState,
    running: Boolean,
    singleSweep: Boolean,
    rate: Double,
    averagingProgress: Double,
    calibration: CalibrationSet,
    calibrationOn: Boolean,
    status: VnaStatus?,
    markerCount: Int,
    full: Boolean,
    modifier: Modifier = Modifier,
) {
    val enabled by vm.enabledParams.collectAsStateWithLifecycle()
    val chartMode by vm.chartMode.collectAsStateWithLifecycle()
    Column(modifier = modifier) {
        // Acquisition controls stay pinned; the informational cards below scroll.
        SectionCard(
            title = "采集",
            trailing = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SweepPulse(active = running || singleSweep, singleSweep = singleSweep, showLabel = full)
                    CollapseToggle(
                        expanded = true,
                        contentDescription = "收起采集控件",
                        onClick = vm::toggleSideControls,
                    )
                }
            },
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = vm::toggleRunning,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (running) VnaSurfaceElevated else VnaPrimary,
                        contentColor = if (running) VnaText else Color(0xFF00201C),
                    ),
                    shape = RoundedCornerShape(15.dp),
                    modifier = Modifier.weight(1f).height(if (full) 58.dp else 52.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp),
                ) {
                    Icon(if (running) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (running) "暂停扫描" else "开始扫描", fontSize = if (full) 15.sp else 13.5.sp, fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = vm::singleSweep,
                    shape = RoundedCornerShape(15.dp),
                    modifier = Modifier.height(if (full) 58.dp else 52.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp),
                ) {
                    Text("单次", fontSize = 13.sp)
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
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
            Spacer(Modifier.height(8.dp))
            val stat = status
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatusDot(stat?.sourceLocked, "源锁定")
                StatusDot(stat?.loLocked, "LO 锁定")
                StatusDot(stat?.adcOverload?.not(), "ADC")
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SweepCard(vm, config)
            TraceCard(vm, config, enabled, chartMode, markerCount)
            CalibrationCard(vm, calibration, calibrationOn)
            ExportCard(vm)
            if (info != null) {
                Text(
                    info.displayName + " · FW " + info.firmware + " · " + info.protocolVersion,
                    color = VnaTextMuted,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                )
            }
            ConnectionPill(connection) { vm.navigate(com.droidvna.app.AppScreen.DEVICE) }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun TraceCard(vm: MainViewModel, config: SweepConfig, enabled: Set<SParam>, chartMode: ChartMode, markerCount: Int) {
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
}

@Composable
private fun SweepCard(vm: MainViewModel, config: SweepConfig) {
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
}

@Composable
private fun CalibrationCard(vm: MainViewModel, calibration: CalibrationSet, calibrationOn: Boolean) {
    SectionCard(
        title = "校准",
        trailing = {
            val label = when {
                calibration.isEmpty -> "未校准"
                calibrationOn -> "已应用"
                else -> "已停用"
            }
            Text(
                label,
                color = when {
                    calibration.isEmpty || !calibrationOn -> VnaTextMuted
                    else -> GoodGreen
                },
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
        } else {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceChip(if (calibrationOn) "校准已启用" else "校准已停用", calibrationOn) {
                    vm.setCalibrationEnabled(!calibrationOn)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = { vm.navigate(com.droidvna.app.AppScreen.CALIBRATION) },
            shape = RoundedCornerShape(13.dp),
            colors = ButtonDefaults.buttonColors(containerColor = VnaSurfaceElevated, contentColor = VnaText),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.Science, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("打开校准向导", fontSize = 13.sp)
        }
    }
}

@Composable
private fun ExportCard(vm: MainViewModel) {
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
                Icon(Icons.AutoMirrored.Filled.ShowChart, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("导出数据", fontSize = 13.sp)
            }
            OutlinedButton(onClick = { vm.navigate(com.droidvna.app.AppScreen.DEVICE) }) {
                Text("设备", fontSize = 13.sp)
            }
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
            ControlItem("校准") { vm.navigate(com.droidvna.app.AppScreen.CALIBRATION) }
            ControlItem("导出") { vm.showExportSheet.value = true }
            ControlItem("设备") { vm.navigate(com.droidvna.app.AppScreen.DEVICE) }
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
            Text("DroidVNA", color = VnaPrimary, fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
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
                        val known = com.droidvna.app.usb.UsbVnaManager.isSupportedVna(device)
                        val granted = vm.usb.hasPermission(device)
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(6.dp).clip(CircleShape).background(if (known) GoodGreen else VnaTextMuted))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                com.droidvna.app.usb.UsbVnaManager.describe(device),
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
                Text(com.droidvna.app.usb.UsbVnaManager.deviceLabel(device), color = VnaText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
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
