/*
 * LibreVNA Mobile - native Android client for LibreVNA / Harogic B60.
 * Copyright (C) 2026 The LibreVNA Mobile Authors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.librevna.mobile.ui.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import com.librevna.mobile.ui.text.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.librevna.mobile.MainViewModel
import com.librevna.mobile.model.SweepConfig
import com.librevna.mobile.model.formatFrequency
import com.librevna.mobile.model.formatIfbw
import com.librevna.mobile.ui.components.ChoiceChip
import com.librevna.mobile.ui.components.SectionCard
import com.librevna.mobile.ui.components.SectionLabel
import com.librevna.mobile.ui.theme.VnaError
import com.librevna.mobile.ui.theme.VnaPrimary
import com.librevna.mobile.ui.theme.VnaSecondary
import com.librevna.mobile.ui.theme.VnaSurface
import com.librevna.mobile.ui.theme.VnaSurfaceElevated
import com.librevna.mobile.ui.theme.VnaText
import com.librevna.mobile.ui.theme.VnaTextMuted
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SweepSettingsSheet(vm: MainViewModel) {
    val show by vm.showSweepSheet.collectAsStateWithLifecycle()
    if (!show) return
    // Snapshot the configuration when the sheet opens. This prevents a stale sheet from
    // applying an old configuration after a device has been connected/changed underneath it.
    val config = remember { vm.repo.config.value }
    val info = remember { vm.repo.info.value }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = { vm.showSweepSheet.value = false },
        sheetState = sheetState,
        containerColor = VnaSurface,
        dragHandle = { Box(Modifier.padding(vertical = 10.dp).background(VnaSurfaceElevated, RoundedCornerShape(50)).height(4.dp).widthIn(min = 42.dp)) },
    ) {
        SweepSheetContent(vm, config, info)
    }
}

@Composable
private fun SweepSheetContent(
    vm: MainViewModel,
    config: SweepConfig,
    info: com.librevna.mobile.model.VnaDeviceInfo?,
) {
    val maxPoints = min(info?.maxPoints ?: 1001, 2001)
    val minFreq = info?.minFreqHz ?: 5_000L
    val maxFreq = info?.maxFreqHz ?: 6_000_000_000L
    val minPower = info?.minPowerDbm ?: -40.0
    val maxPower = info?.maxPowerDbm ?: 10.0
    val minBw = info?.minIfbwHz ?: 1
    val maxBw = info?.maxIfbwHz ?: 200_000

    var startText by remember(config.startHz) { mutableStateOf(String.format(Locale.US, "%.6f", config.startHz / 1e6)) }
    var stopText by remember(config.stopHz) { mutableStateOf(String.format(Locale.US, "%.6f", config.stopHz / 1e6)) }
    var points by remember(config.points) { mutableFloatStateOf(config.points.toFloat()) }
    var ifbw by remember(config.ifbwHz) { mutableStateOf(config.ifbwHz) }
    var power by remember(config.powerDbm) { mutableFloatStateOf(config.powerDbm.toFloat()) }
    var averaging by remember(config.averaging) { mutableStateOf(config.averaging) }
    var twoPort by remember(config.twoPort) { mutableStateOf(config.twoPort) }
    var logSweep by remember(config.logSweep) { mutableStateOf(config.logSweep) }
    var continuous by remember(config.continuous) { mutableStateOf(config.continuous) }
    var error by remember { mutableStateOf<String?>(null) }

    fun apply() {
        val startMhz = startText.toDoubleOrNull()
        val stopMhz = stopText.toDoubleOrNull()
        error = when {
            startMhz == null || stopMhz == null -> "频率必须是数字（MHz）"
            startMhz < minFreq / 1e6 -> "起始频率低于设备下限 " + formatFrequency(minFreq.toDouble())
            stopMhz > maxFreq / 1e6 -> "终止频率高于设备上限 " + formatFrequency(maxFreq.toDouble())
            stopMhz <= startMhz -> "终止频率必须大于起始频率"
            else -> null
        }
        if (error != null) return
        val cfg = SweepConfig(
            startHz = (startMhz!! * 1e6).roundToLong(),
            stopHz = (stopMhz!! * 1e6).roundToLong(),
            points = points.roundToInt().coerceIn(11, maxPoints),
            ifbwHz = ifbw.coerceIn(max(info?.minIfbwHz ?: 1, 1), maxBw),
            powerDbm = power.toDouble().coerceIn(minPower, maxPower),
            twoPort = twoPort,
            averaging = averaging.coerceIn(1, 64),
            logSweep = logSweep,
            continuous = continuous,
        )
        vm.applyConfig(cfg)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("扫描设置", color = VnaText, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("所有参数在点击应用后立即下发到设备", color = VnaTextMuted, fontSize = 11.5.sp)
            }
            TextButton(onClick = { vm.showSweepSheet.value = false }) { Text("关闭", color = VnaTextMuted) }
        }

        SectionCard(title = "频率范围") {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = startText,
                    onValueChange = { startText = it; error = null },
                    label = { Text("起始") },
                    suffix = { Text("MHz") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = stopText,
                    onValueChange = { stopText = it; error = null },
                    label = { Text("终止") },
                    suffix = { Text("MHz") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for ((label, start, stop) in SweepConfig.PRESETS) {
                    ChoiceChip(label, false) {
                        startText = String.format(Locale.US, "%.6f", start / 1e6)
                        stopText = String.format(Locale.US, "%.6f", stop / 1e6)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "可用范围 " + formatFrequency(minFreq.toDouble()) + " – " + formatFrequency(maxFreq.toDouble()),
                color = VnaTextMuted,
                fontSize = 10.5.sp,
            )
        }

        SectionCard(title = "扫描点数与中频带宽") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("点数", color = VnaTextMuted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                Text(points.roundToInt().toString(), color = VnaText, fontSize = 20.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            }
            Slider(
                value = points,
                onValueChange = { points = it },
                valueRange = 11f..maxPoints.toFloat(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (p in listOf(101, 401, 1001)) {
                    if (p <= maxPoints) ChoiceChip(p.toString(), points.roundToInt() == p) { points = p.toFloat() }
                }
            }
            Spacer(Modifier.height(14.dp))
            Text("中频带宽（IFBW）", color = VnaTextMuted, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val choices = (
                    listOf(15, 30, 100, 300, 1_000, 3_000, 10_000, 30_000, 50_000, 75_000, 100_000, 200_000)
                        .filter { it in minBw..maxBw } + ifbw
                    ).distinct().sorted()
                for (bw in choices) {
                    ChoiceChip(formatIfbw(bw), ifbw == bw) { ifbw = bw }
                }
            }
            Text(
                "更小的 IFBW 降低噪声，但首帧和扫描更慢。" +
                    if (minBw <= 100) " 当前设备最低 " + formatIfbw(minBw) + "。" else "",
                color = VnaTextMuted,
                fontSize = 10.5.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        SectionCard(title = "功率与平均") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("激励功率", color = VnaTextMuted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                Text(String.format(Locale.US, "%.1f dBm", power), color = VnaText, fontSize = 18.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            }
            Slider(
                value = power,
                onValueChange = { power = (it * 2f).roundToInt() / 2f },
                valueRange = minPower.toFloat()..maxPower.toFloat(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("平均次数", color = VnaTextMuted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                Text(if (averaging > 1) averaging.toString() + "×" else "关", color = if (averaging > 1) VnaSecondary else VnaTextMuted, fontSize = 13.sp)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (n in listOf(1, 2, 4, 8, 16)) {
                    ChoiceChip(if (n == 1) "关" else n.toString() + "×", averaging == n) { averaging = n }
                }
            }
        }

        SectionCard(title = "模式") {
            ToggleRow("双端口扫描", "测量 S11 / S21 / S12 / S22；关闭后仅激励端口 1", twoPort) { twoPort = it }
            ToggleRow("对数频率扫描", "点数按对数分布，适合宽频带", logSweep) { logSweep = it }
            ToggleRow("连接后自动连续扫描", "关闭时使用「单次」按钮触发", continuous) { continuous = it }
        }

        if (error != null) {
            Text(error!!, color = VnaError, fontSize = 12.5.sp)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = ::apply,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = VnaPrimary, contentColor = Color(0xFF00201C)),
                modifier = Modifier.weight(1f).height(50.dp),
            ) {
                Text("应用并开始扫描", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            TextButton(onClick = { vm.showSweepSheet.value = false }) {
                Text("取消", color = VnaTextMuted)
            }
        }
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = VnaText, fontSize = 13.sp)
            Text(subtitle, color = VnaTextMuted, fontSize = 10.5.sp)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}