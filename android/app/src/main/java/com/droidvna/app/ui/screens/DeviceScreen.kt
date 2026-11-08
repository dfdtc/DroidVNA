/*
 * DroidVNA - native Android client for LibreVNA / Harogic B60.
 * Copyright (C) 2026 The DroidVNA Authors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.droidvna.app.ui.screens

import android.hardware.usb.UsbDevice
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import com.droidvna.app.ui.text.Text
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
import com.droidvna.app.MainViewModel
import com.droidvna.app.device.ConnectionState
import com.droidvna.app.device.SourceKind
import com.droidvna.app.model.formatFrequency
import com.droidvna.app.model.formatIfbw
import com.droidvna.app.ui.components.ChoiceChip
import com.droidvna.app.ui.components.GoodGreen
import com.droidvna.app.ui.components.KeyValueRow
import com.droidvna.app.ui.components.Metric
import com.droidvna.app.ui.components.OutlineButton
import com.droidvna.app.ui.components.SectionCard
import com.droidvna.app.ui.components.SectionLabel
import com.droidvna.app.ui.components.StatusDot
import com.droidvna.app.ui.theme.VnaError
import com.droidvna.app.ui.theme.VnaPrimary
import com.droidvna.app.ui.theme.VnaSecondary
import com.droidvna.app.ui.theme.VnaSurfaceElevated
import com.droidvna.app.ui.theme.VnaText
import com.droidvna.app.ui.theme.VnaTextMuted
import com.droidvna.app.usb.UsbVnaManager

@Composable
fun DeviceScreen(vm: MainViewModel) {
    val connection by vm.repo.connection.collectAsStateWithLifecycle()
    val info by vm.repo.info.collectAsStateWithLifecycle()
    val status by vm.repo.status.collectAsStateWithLifecycle()
    val logs by vm.repo.logs.collectAsStateWithLifecycle()
    val devices by vm.usb.devices.collectAsStateWithLifecycle()
    val allDevices by vm.usb.allDevices.collectAsStateWithLifecycle()
    val config by vm.repo.config.collectAsStateWithLifecycle()
    val busy by vm.repo.connectBusy.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp),
    ) {
        item {
            when (val conn = connection) {
                is ConnectionState.Connected -> ConnectedCard(vm, conn, info, config)
                is ConnectionState.Connecting -> SectionCard(title = "连接中") {
                    Text(conn.target, color = VnaTextMuted, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                }
                is ConnectionState.Error -> SectionCard(title = "连接错误") {
                    Text(conn.message, color = VnaError, fontSize = 12.5.sp)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { vm.usb.refresh() },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = VnaPrimary, contentColor = Color(0xFF00201C)),
                        ) { Text("重新扫描") }
                        OutlinedButton(onClick = vm::demo, shape = RoundedCornerShape(12.dp)) { Text("启动演示") }
                    }
                }
                ConnectionState.Disconnected -> SectionCard(title = "未连接") {
                    Text("连接 USB VNA（LibreVNA / Harogic B60）或启动演示模式。", color = VnaTextMuted, fontSize = 12.5.sp)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { devices.firstOrNull()?.let { vm.connect(it) } },
                            enabled = devices.isNotEmpty() && !busy,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = VnaPrimary, contentColor = Color(0xFF00201C)),
                        ) {
                            Icon(Icons.Default.Usb, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("连接 USB")
                        }
                        OutlinedButton(onClick = vm::demo, shape = RoundedCornerShape(12.dp)) { Text("演示模式") }
                    }
                }
            }
        }

        item { SectionLabel("USB 设备") }
        if (devices.isEmpty()) {
            item {
                SectionCard {
                    Text("未检测到 USB VNA", color = VnaText, fontSize = 13.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "请使用数据 USB-C 线 / OTG 转接头，并确认设备已上电。系统 USB 权限弹窗出现时请选择允许。",
                        color = VnaTextMuted,
                        fontSize = 11.5.sp,
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(onClick = { vm.usb.refresh() }, shape = RoundedCornerShape(12.dp)) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("重新扫描")
                    }
                }
            }
        } else {
            items(devices) { device ->
                UsbDeviceCard(
                    device = device,
                    granted = vm.usb.hasPermission(device),
                    onConnect = { vm.connect(device) },
                )
            }
        }

        if (info != null) {
            item { SectionLabel("设备信息") }
            item {
                SectionCard(title = info!!.displayName) {
                    KeyValueRow("硬件", "0x" + String.format("%02X", info!!.hardwareVersion) + " Rev." + info!!.hardwareRevision)
                    KeyValueRow("固件", info!!.firmware)
                    KeyValueRow("协议版本", info!!.protocolVersion.toString())
                    KeyValueRow("序列号", info!!.serial.ifBlank { info!!.usbName })
                    KeyValueRow("端口数", info!!.numPorts.toString())
                    KeyValueRow("频率范围", formatFrequency(info!!.minFreqHz.toDouble()) + " – " + formatFrequency(info!!.maxFreqHz.toDouble()))
                    KeyValueRow("最大点数", info!!.maxPoints.toString())
                    KeyValueRow("IFBW", formatIfbw(info!!.minIfbwHz) + " – " + formatIfbw(info!!.maxIfbwHz))
                    KeyValueRow("功率", String.format("%.1f – %.1f dBm", info!!.minPowerDbm, info!!.maxPowerDbm))
                }
            }
        }

        if (status != null) {
            item { SectionLabel("实时状态") }
            item {
                SectionCard(title = "硬件状态") {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        StatusDot(status!!.sourceLocked, "源锁定")
                        StatusDot(status!!.loLocked, "LO 锁定")
                        StatusDot(status!!.adcOverload?.not(), "ADC 正常")
                        StatusDot(status!!.unlevel?.not(), "输出稳幅")
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        status!!.temperatureC?.let { Metric("MCU 温度", String.format("%.1f °C", it)) }
                        status!!.secondaryTemperatureC?.let { Metric("第二温度", String.format("%.1f °C", it)) }
                        status!!.supplyVoltage?.let { Metric("供电", String.format("%.2f V", it)) }
                        status!!.extRefInUse?.let { Metric("参考", if (it) "外部" else "内部") }
                    }
                }
            }
        }

        item { SectionLabel("当前扫描") }
        item {
            SectionCard {
                KeyValueRow("范围", formatFrequency(config.startHz.toDouble(), 3) + " – " + formatFrequency(config.stopHz.toDouble(), 3))
                KeyValueRow("点数 / IFBW", config.points.toString() + " / " + formatIfbw(config.ifbwHz))
                KeyValueRow("功率", String.format("%.1f dBm", config.powerDbm))
                KeyValueRow("模式", if (config.twoPort) "2-port" else "1-port")
            }
        }

        item { SectionLabel("USB 诊断（全部设备）") }
        if (allDevices.isEmpty()) {
            item { Text("系统没有枚举到任何 USB 设备。", color = VnaTextMuted, fontSize = 11.5.sp, modifier = Modifier.padding(horizontal = 4.dp)) }
        } else {
            items(allDevices) { device ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(if (UsbVnaManager.isSupportedVna(device)) GoodGreen else VnaTextMuted))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        UsbVnaManager.describe(device),
                        color = VnaText,
                        fontSize = 10.5.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        if (vm.usb.hasPermission(device)) "已授权" else "未授权",
                        color = if (vm.usb.hasPermission(device)) GoodGreen else VnaTextMuted,
                        fontSize = 9.5.sp,
                    )
                }
            }
        }

        item {
            SectionCard(
                title = "日志",
                trailing = {
                    IconButton(onClick = { vm.repo.clearLog() }) {
                        Icon(Icons.Default.Delete, contentDescription = "清空日志", tint = VnaTextMuted, modifier = Modifier.size(18.dp))
                    }
                },
            ) {
                Text(
                    text = if (logs.isEmpty()) "暂无日志" else logs.takeLast(60).joinToString("\n"),
                    color = VnaTextMuted,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 14.sp,
                    modifier = Modifier.heightIn(max = 260.dp),
                )
            }
        }

    }
}

@Composable
private fun ConnectedCard(
    vm: MainViewModel,
    connection: ConnectionState.Connected,
    info: com.droidvna.app.model.VnaDeviceInfo?,
    config: com.droidvna.app.model.SweepConfig,
) {
    val status by vm.repo.status.collectAsStateWithLifecycle()
    SectionCard(title = if (connection.kind == SourceKind.USB) "USB 已连接" else "演示模式") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(9.dp).clip(CircleShape).background(if (connection.kind == SourceKind.USB) GoodGreen else VnaSecondary))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(connection.label, color = VnaText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    (info?.hardwareName ?: "LibreVNA") + " · FW " + (info?.firmware ?: "?"),
                    color = VnaTextMuted,
                    fontSize = 11.sp,
                )
            }
            OutlinedButton(onClick = vm::disconnect, shape = RoundedCornerShape(12.dp)) { Text("断开") }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Metric("扫描速率", String.format("%.1f/s", vm.repo.sweepRate.value))
            Metric("数据点", config.points.toString())
            Metric("状态", if (status?.sourceLocked == true) "LOCK" else "WAIT")
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceChip("测量界面", true) { vm.navigate(com.droidvna.app.AppScreen.MEASURE) }
            ChoiceChip("校准", false) { vm.navigate(com.droidvna.app.AppScreen.CALIBRATION) }
            if (connection.kind == SourceKind.USB) {
                OutlinedButton(onClick = vm::demo, shape = RoundedCornerShape(12.dp)) { Text("切换到演示", fontSize = 11.sp) }
            }
        }
    }
}

@Composable
private fun UsbDeviceCard(device: UsbDevice, granted: Boolean, onConnect: () -> Unit) {
    Surface(color = VnaSurfaceElevated, shape = RoundedCornerShape(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Usb, contentDescription = null, tint = GoodGreen)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(UsbVnaManager.deviceLabel(device), color = VnaText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    String.format("%04X:%04X · %s", device.vendorId, device.productId, device.deviceName),
                    color = VnaTextMuted,
                    fontSize = 10.5.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
            Text(if (granted) "已授权" else "需要授权", color = if (granted) GoodGreen else VnaSecondary, fontSize = 10.sp)
            Spacer(Modifier.width(10.dp))
            Button(
                onClick = onConnect,
                shape = RoundedCornerShape(11.dp),
                colors = ButtonDefaults.buttonColors(containerColor = VnaPrimary, contentColor = Color(0xFF00201C)),
            ) { Text("连接", fontSize = 12.sp) }
        }
    }
}