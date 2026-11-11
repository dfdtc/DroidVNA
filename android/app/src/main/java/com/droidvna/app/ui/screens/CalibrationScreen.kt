/*
 * DroidVNA - native Android client for LibreVNA / Harogic B60.
 * Copyright (C) 2026 The DroidVNA Authors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.droidvna.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import com.droidvna.app.ui.text.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.droidvna.app.ExportFormat
import com.droidvna.app.MainViewModel
import com.droidvna.app.device.ConnectionState
import com.droidvna.app.model.CalStandard
import com.droidvna.app.model.CalibrationSet
import com.droidvna.app.model.SweepResult
import com.droidvna.app.model.formatFrequency
import com.droidvna.app.ui.chart.color
import com.droidvna.app.ui.components.ChoiceChip
import com.droidvna.app.ui.components.GoodGreen
import com.droidvna.app.ui.components.OutlineButton
import com.droidvna.app.ui.components.SectionCard
import com.droidvna.app.ui.components.SectionLabel
import com.droidvna.app.ui.components.SettingToggleRow
import com.droidvna.app.ui.theme.VnaError
import com.droidvna.app.ui.theme.VnaOutline
import com.droidvna.app.ui.theme.VnaPrimary
import com.droidvna.app.ui.theme.VnaSecondary
import com.droidvna.app.ui.theme.VnaSurfaceElevated
import com.droidvna.app.ui.theme.VnaText
import com.droidvna.app.ui.theme.VnaTextMuted
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

@Composable
fun CalibrationScreen(vm: MainViewModel) {
    val port by vm.calPort.collectAsStateWithLifecycle()
    val draft by vm.calDraft.collectAsStateWithLifecycle()
    val busy by vm.calBusy.collectAsStateWithLifecycle()
    val message by vm.calMessage.collectAsStateWithLifecycle()
    val calibration by vm.repo.calibration.collectAsStateWithLifecycle()
    val info by vm.repo.info.collectAsStateWithLifecycle()
    val config by vm.repo.config.collectAsStateWithLifecycle()
    val connection by vm.repo.connection.collectAsStateWithLifecycle()
    val openDelay by vm.calOpenDelayPs.collectAsStateWithLifecycle()
    val shortDelay by vm.calShortDelayPs.collectAsStateWithLifecycle()
    val thruDelay by vm.calThruDelayPs.collectAsStateWithLifecycle()
    val ecalConnected by vm.ecalConnected.collectAsStateWithLifecycle()
    val ecalInfo by vm.ecalInfo.collectAsStateWithLifecycle()
    val ecalSets by vm.ecalCoefficientSets.collectAsStateWithLifecycle()
    val ecalSelected by vm.ecalSelectedSet.collectAsStateWithLifecycle()
    val ecalLoaded by vm.ecalCoefficientsLoaded.collectAsStateWithLifecycle()
    val ecalBusy by vm.ecalBusy.collectAsStateWithLifecycle()
    val ecalProgress by vm.ecalProgress.collectAsStateWithLifecycle()
    val ecalMessage by vm.ecalMessage.collectAsStateWithLifecycle()
    val ecalAvailable by vm.ecalAvailable.collectAsStateWithLifecycle()
    val ecalUseIsolation by vm.ecalUseIsolation.collectAsStateWithLifecycle()
    val savedCal by vm.savedCalibration.collectAsStateWithLifecycle()
    val calEnabled by vm.calibrationEnabled.collectAsStateWithLifecycle()
    val rememberCal by vm.rememberCalibration.collectAsStateWithLifecycle()

    val calFilePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importCal(uri)
    }

    LaunchedEffect(ecalConnected) {
        while (ecalConnected) {
            vm.refreshEcalStatus()
            delay(2500)
        }
    }

    val captures = draft.captures(port)
    val open = captures[CalStandard.OPEN]
    val short = captures[CalStandard.SHORT]
    val load = captures[CalStandard.LOAD]
    val canApply = open != null && short != null && load != null && !busy

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp),
    ) {
        item {
            SectionCard(title = ".cal 校准文件导入") {
                Text(
                    "导入电脑版 LibreVNA-GUI 导出的 .cal（format 3）。移动端会重建 .cal 中 Open/Short/Load/Through 的测量结果：含有两端口的 1 端口测量和 THRU 时，直接重建完整 12 项双端口 SOLT 误差模型；否则退回 1 端口 SOL + THRU 归一化。",
                    color = VnaTextMuted,
                    fontSize = 11.5.sp,
                    lineHeight = 17.sp,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "注意：.cal 中的隔离（Isolation）测量会一并读取；缺少时按零隔离处理。",
                    color = VnaSecondary,
                    fontSize = 10.5.sp,
                    lineHeight = 15.sp,
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { calFilePicker.launch(arrayOf("*/*")) },
                    enabled = !busy,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = VnaPrimary,
                        contentColor = Color(0xFF00201C),
                        disabledContainerColor = VnaSurfaceElevated,
                        disabledContentColor = VnaTextMuted,
                    ),
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) {
                    Text(if (busy) "处理中…" else "选择 .cal 文件", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                if (message != null && (message!!.contains("导入") || message!!.contains(".cal"))) {
                    Text(message!!, color = if (message!!.contains("失败")) VnaError else GoodGreen, fontSize = 11.5.sp, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }

        item {
            SectionCard(title = "电子校准件 · LibreCAL / ECB-60") {
                Text(
                    "通过 USB SCPI 自动切换 Open / Short / Load / Through，测量后直接应用校准。VID:PID 1209:4122，使用 vendor interface 2。",
                    color = VnaTextMuted,
                    fontSize = 11.5.sp,
                    lineHeight = 17.sp,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "端口分配（手动固定，不使用 Auto）：VNA P1 → E-Cal P1 · VNA P2 → E-Cal P2。",
                    color = VnaSecondary,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "双端口校准件会先完成两端 Open/Short/Load，再测 THRU，最后把两端都切到 LOAD 测一次隔离，然后解出完整 12 项误差模型。",
                    color = VnaTextMuted,
                    fontSize = 10.5.sp,
                    lineHeight = 15.sp,
                )
                Spacer(Modifier.height(10.dp))
                if (!ecalConnected) {
                    Text(
                        if (ecalAvailable > 0) "已发现 " + ecalAvailable + " 个电子校准件" else "未发现电子校准件（需要 OTG Hub 同时连接 VNA 与校准件）",
                        color = if (ecalAvailable > 0) GoodGreen else VnaTextMuted,
                        fontSize = 11.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = vm::connectEcal,
                        enabled = !ecalBusy && ecalAvailable > 0,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = VnaPrimary,
                            contentColor = Color(0xFF00201C),
                            disabledContainerColor = VnaSurfaceElevated,
                            disabledContentColor = VnaTextMuted,
                        ),
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                    ) {
                        Text(if (ecalBusy) "连接中…" else "检测并连接电子校准件", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                } else {
                    val info = ecalInfo
                    if (info != null) {
                        Text(info.idn.trim().ifBlank { "电子校准件" }, color = VnaText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            "FW " + info.firmware + " · " + info.ports + " 端口" +
                                if (info.heaterPowerW != null) " · 加热 " + String.format("%.1f W", info.heaterPowerW) else "",
                            color = VnaTextMuted,
                            fontSize = 10.5.sp,
                        )
                        Text(
                            "温度 " + (info.temperatureC?.let { String.format("%.1f °C", it) } ?: "—") +
                                " · " + when (info.stable) {
                                    true -> "已稳定"
                                    false -> "升温中"
                                    null -> "状态未知"
                                },
                            color = when (info.stable) {
                                true -> GoodGreen
                                false -> VnaSecondary
                                null -> VnaTextMuted
                            },
                            fontSize = 10.5.sp,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    if (ecalSets.isNotEmpty()) {
                        Text("系数集", color = VnaTextMuted, fontSize = 11.sp)
                        Row(
                            modifier = Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                        ) {
                            for (set in ecalSets) {
                                ChoiceChip(set, ecalSelected == set) { vm.selectEcalCoefficientSet(set) }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                    SettingToggleRow(
                        title = "让电子校准件同时测隔离",
                        subtitle = if (ecalUseIsolation) {
                            "已开启：校准最后会把两端切到 LOAD 测一次隔离（多花约几秒）"
                        } else {
                            "默认关闭：ECB-60 等双端口校准件的隔离通路较差，建议之后用机械 50 Ω 负载单独补测"
                        },
                        checked = ecalUseIsolation,
                    ) { vm.ecalUseIsolation.value = it }
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = vm::loadEcalCoefficients,
                            enabled = !ecalBusy && !busy,
                            shape = RoundedCornerShape(13.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(if (ecalLoaded) "重新加载系数" else "加载系数", fontSize = 12.sp)
                        }
                        Button(
                            onClick = vm::startEcalCalibration,
                            enabled = !ecalBusy && !busy && ecalLoaded && connection is ConnectionState.Connected,
                            shape = RoundedCornerShape(13.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = VnaPrimary,
                                contentColor = Color(0xFF00201C),
                                disabledContainerColor = VnaSurfaceElevated,
                                disabledContentColor = VnaTextMuted,
                            ),
                            modifier = Modifier.weight(1.2f),
                        ) {
                            Text(if (ecalBusy) "校准中…" else "开始电子校准", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (ecalBusy) {
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { ecalProgress.toFloat().coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(4.dp),
                            color = VnaSecondary,
                            trackColor = VnaSurfaceElevated,
                        )
                    }
                    if (ecalLoaded) {
                        Text("系数已加载，可以开始 SOLT。校准期间请勿拔插设备。", color = GoodGreen, fontSize = 10.5.sp, modifier = Modifier.padding(top = 6.dp))
                    }
                    TextButton(onClick = vm::disconnectEcal, enabled = !ecalBusy) {
                        Text("断开电子校准件", color = VnaTextMuted, fontSize = 11.sp)
                    }
                }
                if (ecalMessage != null) {
                    Text(
                        ecalMessage!!,
                        color = if (ecalMessage!!.contains("失败") || ecalMessage!!.contains("没有")) VnaError else VnaTextMuted,
                        fontSize = 11.5.sp,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                Text(
                    "提示：B60 每次切换标准都会重新下发扫描配置（约 0.5 秒）；完整 2 端口 SOLT（含隔离）通常需要 15–25 秒。",
                    color = VnaTextMuted,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        item {
            SectionCard(title = "1 端口 SOL 校准") {
                Text(
                    "依次连接 Open / Short / Load 三个标准件，测量后计算方向性、源匹配与反射跟踪误差。校准会按频率保存，并应用到之后的所有扫描。",
                    color = VnaTextMuted,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CalConnBadge(connected = connection is com.droidvna.app.device.ConnectionState.Connected)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (config.twoPort) "双端口扫描" else "单端口扫描（S22 不可用）",
                        color = VnaTextMuted,
                        fontSize = 11.sp,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoiceChip("端口 1", port == 1) { vm.calPort.value = 1 }
                    ChoiceChip("端口 2", port == 2) { vm.calPort.value = 2 }
                    if (info?.numPorts == 1) {
                        Text("设备仅支持 1 端口", color = VnaSecondary, fontSize = 11.sp, modifier = Modifier.padding(start = 6.dp))
                    }
                }
            }
        }

        item {
            SectionCard(
                title = "测量标准件 · 端口 " + port,
                trailing = {
                    Text(if (busy) "采集中…" else "保持连接稳定", color = if (busy) VnaSecondary else VnaTextMuted, fontSize = 10.5.sp)
                },
            ) {
                if (busy) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().height(3.dp),
                        color = VnaSecondary,
                        trackColor = VnaSurfaceElevated,
                    )
                    Spacer(Modifier.height(10.dp))
                }
                StandardCaptureCard(
                    standard = CalStandard.OPEN,
                    captured = open,
                    port = port,
                    busy = busy,
                    onCapture = { vm.captureStandard(port, CalStandard.OPEN) },
                )
                Spacer(Modifier.height(8.dp))
                StandardCaptureCard(
                    standard = CalStandard.SHORT,
                    captured = short,
                    port = port,
                    busy = busy,
                    onCapture = { vm.captureStandard(port, CalStandard.SHORT) },
                )
                Spacer(Modifier.height(8.dp))
                StandardCaptureCard(
                    standard = CalStandard.LOAD,
                    captured = load,
                    port = port,
                    busy = busy,
                    onCapture = { vm.captureStandard(port, CalStandard.LOAD) },
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { vm.computeSol(port) },
                    enabled = canApply && (port == 1 || (info?.numPorts ?: 1) >= 2),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = VnaPrimary,
                        contentColor = Color(0xFF00201C),
                        disabledContainerColor = VnaSurfaceElevated,
                        disabledContentColor = VnaTextMuted,
                    ),
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                ) {
                    Text("计算并应用端口 " + port + " SOL", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
                if (port == 2 && info?.numPorts == 1) {
                    Text("端口 2 校准需要双端口设备。", color = VnaError, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
                }
                if (message != null) {
                    Text(message!!, color = if (message!!.contains("应用")) GoodGreen else VnaTextMuted, fontSize = 11.5.sp, modifier = Modifier.padding(top = 6.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { vm.clearDraft() }) { Text("清除捕获", color = VnaTextMuted, fontSize = 11.sp) }
                    if (calibration.solFor(port) != null) {
                        TextButton(onClick = { vm.clearCalPort(port) }) { Text("移除端口 " + port + " 校准", color = VnaTextMuted, fontSize = 11.sp) }
                    }
                }
            }
        }

        item {
            SectionCard(title = "标准件模型（可选）") {
                Text(
                    "默认使用理想 Open / Short / Load。若标准件存在偏移延迟，可在下面填写（皮秒）。更精确的校准套件系数请使用桌面版 LibreVNA-GUI。",
                    color = VnaTextMuted,
                    fontSize = 11.5.sp,
                    lineHeight = 17.sp,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = openDelay,
                        onValueChange = { vm.calOpenDelayPs.value = it },
                        label = { Text("Open 延迟") },
                        suffix = { Text("ps") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = shortDelay,
                        onValueChange = { vm.calShortDelayPs.value = it },
                        label = { Text("Short 延迟") },
                        suffix = { Text("ps") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        item {
            SectionCard(title = "THRU 归一化（仅传输）") {
                Text(
                    "将 Port 1 与 Port 2 直连后捕获一次 THRU，按「实测 / 理想」归一化 S21 / S12：消除跟踪误差，但不扣除端口失配。",
                    color = VnaTextMuted,
                    fontSize = 11.5.sp,
                    lineHeight = 17.sp,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "THRU 延迟为 0 时理想直通为 1，即传统 0 dB 归一化；填了延迟就会把相位一并计入。" +
                        "同一次捕获也会用于下面的完整 SOLT，不需要重复测量。",
                    color = VnaSecondary,
                    fontSize = 10.5.sp,
                    lineHeight = 15.sp,
                )
                Spacer(Modifier.height(10.dp))
                ThruCaptureCard(
                    captured = draft.thru,
                    busy = busy,
                    onCapture = vm::captureThru,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = vm::computeThru,
                    enabled = draft.thru != null && !busy,
                    shape = RoundedCornerShape(13.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (calibration.thrus.isEmpty()) "应用 THRU 归一化" else "重新应用 THRU 归一化", fontSize = 13.sp)
                }
                if (message != null && message!!.contains("THRU")) {
                    Text(message!!, color = GoodGreen, fontSize = 11.5.sp, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }

        item {
            SectionCard(title = "双端口 SOLT · 完整 12 项误差修正") {
                Text(
                    "同时解出方向性、源匹配、反射跟踪、接收机匹配、传输跟踪与隔离。相比 1 端口 SOL + THRU 归一化，它还会扣除端口失配，因此 S21 / S12 在端口反射较大时依然准确。",
                    color = VnaTextMuted,
                    fontSize = 11.5.sp,
                    lineHeight = 17.sp,
                )
                Spacer(Modifier.height(10.dp))
                SoltRequirement("端口 1 · Open / Short / Load", draft.p1.keys.containsAll(CalStandard.entries.toList()))
                SoltRequirement("端口 2 · Open / Short / Load", draft.p2.keys.containsAll(CalStandard.entries.toList()))
                SoltRequirement("THRU（P1 ↔ P2 直连）", draft.thru != null)
                SoltRequirement("隔离（可选，两端接 50 Ω 负载）", draft.isolation != null, optional = true)
                Spacer(Modifier.height(10.dp))
                IsolationCaptureCard(
                    captured = draft.isolation,
                    busy = busy,
                    onCapture = vm::captureIsolation,
                )
                val currentSolt = calibration.solt
                if (currentSolt != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "隔离项可以单独替换：其余误差项和 THRU 原始数据都会保留，" +
                            "不需要重做 Open / Short / Load。E-Cal 校准的结果同样适用。",
                        color = VnaTextMuted,
                        fontSize = 10.5.sp,
                        lineHeight = 15.sp,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = vm::applyIsolationOnly,
                            enabled = draft.isolation != null && !busy,
                        ) {
                            Text(
                                "只用本次捕获更新隔离项",
                                color = if (draft.isolation != null) VnaSecondary else VnaTextMuted,
                                fontSize = 11.5.sp,
                            )
                        }
                        if (currentSolt.hasIsolation) {
                            TextButton(onClick = vm::clearIsolationTerms, enabled = !busy) {
                                Text("清除隔离修正", color = VnaTextMuted, fontSize = 11.5.sp)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = thruDelay,
                    onValueChange = { vm.calThruDelayPs.value = it },
                    label = { Text("THRU 延迟（可选）") },
                    suffix = { Text("ps") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = vm::computeSolt,
                    enabled = draft.canSolveSolt && !busy && (info?.numPorts ?: 1) >= 2,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = VnaPrimary,
                        contentColor = Color(0xFF00201C),
                        disabledContainerColor = VnaSurfaceElevated,
                        disabledContentColor = VnaTextMuted,
                    ),
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                ) {
                    Text("计算并应用完整 SOLT", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
                if ((info?.numPorts ?: 1) < 2) {
                    Text(
                        "完整 SOLT 需要双端口 VNA；单端口设备请使用上面的 1 端口 SOL。",
                        color = VnaError,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                } else if (!draft.canSolveSolt) {
                    Text(
                        "还需要补齐上面的标准件测量才能计算。",
                        color = VnaTextMuted,
                        fontSize = 10.5.sp,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                if (message != null && message!!.contains("SOLT")) {
                    val failed = message!!.contains("无解") || message!!.contains("需要")
                    Text(
                        message!!,
                        color = if (failed) VnaError else GoodGreen,
                        fontSize = 11.5.sp,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }

        if (message != null) {
            item {
                val error = message!!.contains("失败") || message!!.contains("无效") || message!!.contains("不一致") || message!!.contains("无法")
                SectionCard {
                    Text(message!!, color = if (error) VnaError else GoodGreen, fontSize = 12.5.sp)
                }
            }
        }

        item { SectionLabel("当前校准状态") }
        item {
            SectionCard {
                if (calibration.isEmpty && savedCal.calibration.isEmpty) {
                    Text("尚未应用任何校准。", color = VnaTextMuted, fontSize = 12.sp)
                } else if (calibration.isEmpty) {
                    Text("当前没有启用校准（有已保存的校准，可在应用设置里启用）。", color = VnaTextMuted, fontSize = 12.sp)
                } else {
                    CalibrationChips(calibration, vm)
                }
                if (!calibration.isEmpty) {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ChoiceChip(if (calEnabled) "校准已启用" else "校准已停用", calEnabled) {
                            vm.setCalibrationEnabled(!calEnabled)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    if (rememberCal) {
                        "校准会自动保存，下次启动时自动载入" +
                            if (savedCal.updatedAt.isNotBlank()) "（最近保存 " + savedCal.updatedAt.replace('T', ' ') + "）" else ""
                    } else {
                        "校准记忆已关闭：本次运行的校准不会被保存。"
                    },
                    color = if (rememberCal) GoodGreen else VnaSecondary,
                    fontSize = 10.5.sp,
                )
                Spacer(Modifier.height(4.dp))
                val range = calibration.calibratedRange()
                if (range != null) {
                    val covered = range.covers(config.startHz, config.stopHz)
                    Text(
                        "已校准频段 " + formatFrequency(range.startHz.toDouble(), 3) + " – " +
                            formatFrequency(range.endHz.toDouble(), 3),
                        color = if (covered) GoodGreen else VnaSecondary,
                        fontSize = 10.5.sp,
                    )
                    if (!covered) {
                        Text(
                            "当前扫描范围覆盖未校准频段（" +
                                formatFrequency(config.startHz.toDouble(), 3) + " – " +
                                formatFrequency(config.stopHz.toDouble(), 3) +
                                "），超出部分按未校准处理；建议重做校准或缩小扫描范围。",
                            color = VnaSecondary,
                            fontSize = 10.5.sp,
                            lineHeight = 15.sp,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                } else {
                    Text(
                        "校准按频率插值；大幅改变扫描范围后建议重新校准。",
                        color = VnaTextMuted,
                        fontSize = 10.5.sp,
                    )
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlineButton("测量界面", onClick = { vm.navigate(com.droidvna.app.AppScreen.MEASURE) })
                OutlineButton("导出校准后的数据", onClick = { vm.showExportSheet.value = true })
                if (!calibration.isEmpty) {
                    TextButton(onClick = { vm.clearCalibration() }) { Text("清除全部校准", color = VnaError, fontSize = 11.sp) }
                }
            }
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
private fun CalConnBadge(connected: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).background(if (connected) GoodGreen else VnaError, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(if (connected) "设备在线" else "设备离线", color = if (connected) GoodGreen else VnaError, fontSize = 10.5.sp)
    }
}

@Composable
private fun StandardCaptureCard(
    standard: CalStandard,
    captured: SweepResult?,
    port: Int,
    busy: Boolean,
    onCapture: () -> Unit,
) {
    val hint = when (standard) {
        CalStandard.OPEN -> "连接开路标准件到端口 " + port
        CalStandard.SHORT -> "连接短路标准件到端口 " + port
        CalStandard.LOAD -> "连接 50 Ω 负载到端口 " + port
    }
    SurfaceCard(borderColor = if (captured != null) GoodGreen.copy(alpha = 0.45f) else VnaOutline) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(standard.label.uppercase(), color = VnaText, fontSize = 15.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    if (captured != null) {
                        Spacer(Modifier.width(8.dp))
                        IconCheck()
                    }
                }
                Text(hint, color = VnaTextMuted, fontSize = 11.sp)
                if (captured != null) {
                    Text("已捕获 " + captured.size + " 点 · " + captured.freqHz.first() / 1_000_000 + " – " + captured.freqHz.last() / 1_000_000 + " MHz", color = GoodGreen, fontSize = 10.sp)
                }
            }
            Button(
                onClick = onCapture,
                enabled = !busy,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (captured == null) VnaPrimary else VnaSurfaceElevated,
                    contentColor = if (captured == null) Color(0xFF00201C) else VnaText,
                    disabledContainerColor = VnaSurfaceElevated,
                    disabledContentColor = VnaTextMuted,
                ),
            ) {
                Text(if (captured == null) "捕获" else "重测", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (captured != null) {
            Spacer(Modifier.height(8.dp))
            MiniTrace(captured, port, Modifier.fillMaxWidth().height(52.dp))
        }
    }
}

@Composable
private fun ThruCaptureCard(captured: SweepResult?, busy: Boolean, onCapture: () -> Unit) {
    SurfaceCard(borderColor = if (captured != null) GoodGreen.copy(alpha = 0.45f) else VnaOutline) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("THRU", color = VnaText, fontSize = 15.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    if (captured != null) {
                        Spacer(Modifier.width(8.dp))
                        IconCheck()
                    }
                }
                Text("用低损耗直通连接 Port 1 ↔ Port 2", color = VnaTextMuted, fontSize = 11.sp)
                if (captured != null) {
                    Text("已捕获 " + captured.size + " 点", color = GoodGreen, fontSize = 10.sp)
                }
            }
            Button(
                onClick = onCapture,
                enabled = !busy,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (captured == null) VnaPrimary else VnaSurfaceElevated,
                    contentColor = if (captured == null) Color(0xFF00201C) else VnaText,
                ),
            ) { Text(if (captured == null) "捕获" else "重测", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
        }
        if (captured != null) {
            Spacer(Modifier.height(8.dp))
            ThruMiniTrace(captured, Modifier.fillMaxWidth().height(52.dp))
        }
    }
}

@Composable
private fun SurfaceCard(borderColor: Color, content: @Composable () -> Unit) {
    androidx.compose.material3.Surface(
        color = VnaSurfaceElevated,
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp)) { content() }
    }
}

@Composable
private fun IconCheck() {
    androidx.compose.material3.Icon(
        Icons.Default.Check,
        contentDescription = null,
        tint = GoodGreen,
        modifier = Modifier.size(15.dp),
    )
}

@Composable
private fun MiniTrace(captured: SweepResult, port: Int, modifier: Modifier) {
    val values = if (port == 1) captured.s11 else captured.s22
    val color = if (port == 1) com.droidvna.app.model.SParam.S11.color else com.droidvna.app.model.SParam.S22.color
    Canvas(modifier = modifier.background(Color(0xFF0E161C), RoundedCornerShape(8.dp))) {
        var lo = Double.POSITIVE_INFINITY
        var hi = Double.NEGATIVE_INFINITY
        for (c in values) {
            if (!c.isFinite()) continue
            val db = c.magDb()
            lo = min(lo, db)
            hi = max(hi, db)
        }
        if (!lo.isFinite()) return@Canvas
        if (hi - lo < 10) {
            lo -= 5
            hi += 5
        }
        val pad = 4.dp.toPx()
        val path = Path()
        for (i in values.indices) {
            val v = values[i]
            if (!v.isFinite()) continue
            val x = pad + (size.width - 2 * pad) * i / (values.size - 1).coerceAtLeast(1)
            val y = pad + (size.height - 2 * pad) * (1f - ((v.magDb() - lo) / (hi - lo)).toFloat())
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color = color, style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
private fun ThruMiniTrace(captured: SweepResult, modifier: Modifier) {
    Canvas(modifier = modifier.background(Color(0xFF0E161C), RoundedCornerShape(8.dp))) {
        val values = captured.s21
        var lo = 0.0
        var hi = -80.0
        for (c in values) {
            if (!c.isFinite()) continue
            val db = c.magDb()
            lo = min(lo, db)
            hi = max(hi, db)
        }
        if (!lo.isFinite()) return@Canvas
        if (hi - lo < 1.0) {
            lo -= 0.5
            hi += 0.5
        }
        val pad = 4.dp.toPx()
        val path = Path()
        for (i in values.indices) {
            val v = values[i]
            if (!v.isFinite()) continue
            val x = pad + (size.width - 2 * pad) * i / (values.size - 1).coerceAtLeast(1)
            val y = pad + (size.height - 2 * pad) * (1f - ((v.magDb() - lo) / (hi - lo)).toFloat())
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color = VnaSecondary, style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
private fun SoltRequirement(label: String, done: Boolean, optional: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
        Box(
            Modifier
                .size(8.dp)
                .background(if (done) GoodGreen else VnaOutline, CircleShape)
        )
        Spacer(Modifier.width(8.dp))
        Text(label, color = if (done) VnaText else VnaTextMuted, fontSize = 11.5.sp)
        if (optional) {
            Spacer(Modifier.width(6.dp))
            Text(if (done) "已测" else "可跳过", color = VnaTextMuted, fontSize = 10.sp)
        }
    }
}

@Composable
private fun IsolationCaptureCard(captured: SweepResult?, busy: Boolean, onCapture: () -> Unit) {
    SurfaceCard(borderColor = if (captured != null) GoodGreen.copy(alpha = 0.45f) else VnaOutline) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "ISOLATION",
                        color = VnaText,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                    )
                    if (captured != null) {
                        Spacer(Modifier.width(8.dp))
                        IconCheck()
                    }
                }
                Text("两端都接 50 Ω 负载，测量剩余串扰", color = VnaTextMuted, fontSize = 11.sp)
                if (captured != null) {
                    Text("已捕获 " + captured.size + " 点", color = GoodGreen, fontSize = 10.sp)
                }
            }
            Button(
                onClick = onCapture,
                enabled = !busy,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (captured == null) VnaPrimary else VnaSurfaceElevated,
                    contentColor = if (captured == null) Color(0xFF00201C) else VnaText,
                ),
            ) { Text(if (captured == null) "捕获" else "重测", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
        }
        if (captured != null) {
            Spacer(Modifier.height(8.dp))
            ThruMiniTrace(captured, Modifier.fillMaxWidth().height(52.dp))
        }
    }
}

@Composable
private fun CalibrationChips(calibration: CalibrationSet, vm: MainViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        calibration.solt?.let { solt ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("12 项 SOLT", color = GoodGreen, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.width(8.dp))
                Text(
                    solt.size.toString() + " 点 · " + if (solt.hasIsolation) "含隔离" else "无隔离",
                    color = VnaTextMuted,
                    fontSize = 10.5.sp,
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { vm.repo.clearSolt() }) {
                    Text("删除", color = VnaTextMuted, fontSize = 10.5.sp)
                }
            }
        }
        for (cal in calibration.sol) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("端口 " + cal.port + " SOL", color = GoodGreen, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.width(8.dp))
                Text(cal.size.toString() + " 点", color = VnaTextMuted, fontSize = 10.5.sp)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { vm.clearCalPort(cal.port) }) {
                    Text("删除", color = VnaTextMuted, fontSize = 10.5.sp)
                }
            }
        }
        for (thru in calibration.thrus) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("THRU S" + thru.toPort + thru.fromPort, color = GoodGreen, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.width(8.dp))
                Text(thru.freqHz.size.toString() + " 点", color = VnaTextMuted, fontSize = 10.5.sp)
                Spacer(Modifier.weight(1f))
            }
        }
        if (calibration.thrus.isNotEmpty()) {
            TextButton(onClick = { vm.repo.clearThru() }) { Text("删除 THRU 归一化", color = VnaTextMuted, fontSize = 10.5.sp) }
        }
    }
}