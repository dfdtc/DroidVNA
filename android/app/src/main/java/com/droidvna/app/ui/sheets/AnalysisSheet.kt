/*
 * DroidVNA - native Android client for LibreVNA / Harogic B60.
 * Copyright (C) 2026 The DroidVNA Authors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.droidvna.app.ui.sheets

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.droidvna.app.AnalysisTool
import com.droidvna.app.MainViewModel
import com.droidvna.app.model.SParam
import com.droidvna.app.model.analysis.DtfResult
import com.droidvna.app.model.analysis.DtfWindow
import com.droidvna.app.model.analysis.XdbReferenceMode
import com.droidvna.app.model.analysis.XdbSearchResult
import com.droidvna.app.model.analysis.XdbSide
import com.droidvna.app.model.analysis.formatDistance
import com.droidvna.app.model.formatDb
import com.droidvna.app.model.formatFrequency
import com.droidvna.app.model.formatFrequencyShort
import com.droidvna.app.ui.chart.DtfChart
import com.droidvna.app.ui.components.ChoiceChip
import com.droidvna.app.ui.components.KeyValueRow
import com.droidvna.app.ui.components.Metric
import com.droidvna.app.ui.components.SectionCard
import com.droidvna.app.ui.text.Text
import com.droidvna.app.ui.theme.VnaError
import com.droidvna.app.ui.theme.VnaPrimary
import com.droidvna.app.ui.theme.VnaSecondary
import com.droidvna.app.ui.theme.VnaSurface
import com.droidvna.app.ui.theme.VnaText
import com.droidvna.app.ui.theme.VnaTextMuted
import java.util.Locale

/**
 * Host for the three trace analyses: bandwidth search, XdB search and DTF.
 *
 * All three share the marker bar's "分析" menu and this one sheet. The content is split into
 * independent composables so a future analysis (e.g. group delay) can be added without
 * touching the others.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalysisSheet(vm: MainViewModel) {
    val tool by vm.analysisTool.collectAsStateWithLifecycle()
    val current = tool ?: return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = vm::closeAnalysis,
        sheetState = sheetState,
        containerColor = VnaSurface,
    ) {
        when (current) {
            AnalysisTool.BANDWIDTH -> XdbAnalysisContent(vm, bandwidth = true)
            AnalysisTool.XDB -> XdbAnalysisContent(vm, bandwidth = false)
            AnalysisTool.DTF -> DtfAnalysisContent(vm)
        }
    }
}

@Composable
private fun SheetHeader(title: String, subtitle: String, onClose: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = VnaText, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = VnaTextMuted, fontSize = 11.5.sp)
        }
        TextButton(onClick = onClose) { Text("关闭", color = VnaTextMuted) }
    }
}

// --------------------------------------------------------------------------
// Bandwidth search / XdB search
// --------------------------------------------------------------------------

@Composable
private fun XdbAnalysisContent(vm: MainViewModel, bandwidth: Boolean) {
    val enabled by vm.enabledParams.collectAsStateWithLifecycle()
    val markers by vm.markers.collectAsStateWithLifecycle()
    val focusedMarkerId by vm.focusedMarkerId.collectAsStateWithLifecycle()
    val result by vm.xdbResult.collectAsStateWithLifecycle()
    val resultTrace by vm.xdbTrace.collectAsStateWithLifecycle()
    val message by vm.xdbMessage.collectAsStateWithLifecycle()

    // Only the availability of a sweep is needed here. Collecting it into a boolean instead of
    // reading the live sweep value avoids recomposing the whole sheet on every sweep update.
    var sweepAvailable by remember { mutableStateOf(vm.repo.sweep.value != null) }
    LaunchedEffect(Unit) {
        vm.repo.sweep.collect { sweepAvailable = it != null }
    }

    val availableParams = remember(enabled) {
        val chosen = SParam.entries.filter { enabled.contains(it) }
        if (chosen.isEmpty()) SParam.entries.toList() else chosen
    }
    var trace by remember { mutableStateOf(defaultXdbTrace(availableParams)) }
    var referenceMode by remember { mutableStateOf(XdbReferenceMode.PEAK) }
    var side by remember { mutableStateOf(XdbSide.BOTH) }
    var cursorId by remember { mutableStateOf(focusedMarkerId ?: markers.firstOrNull()?.id) }
    var dropText by remember { mutableStateOf("3.0") }
    var localError by remember { mutableStateOf<String?>(null) }

    val activeCursor = markers.firstOrNull { it.id == cursorId } ?: markers.firstOrNull()

    fun runSearch() {
        val drop = dropText.toDoubleOrNull()
        localError = when {
            !sweepAvailable -> "还没有扫描数据"
            drop == null -> "X 必须是数字（dB）"
            drop <= 0.0 -> "X 必须大于 0"
            referenceMode == XdbReferenceMode.CURSOR && activeCursor == null ->
                "请先在迹线上添加一个标记作为游标"
            else -> null
        }
        if (localError != null) return
        vm.runXdbSearch(
            trace = trace,
            referenceMode = referenceMode,
            cursorMarkerId = activeCursor?.id,
            dropDb = drop!!,
            side = if (bandwidth) XdbSide.BOTH else side,
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SheetHeader(
            title = if (bandwidth) "带宽搜索" else "XdB 搜索",
            subtitle = if (bandwidth) {
                "在指定迹线上找 -X dB 的两个边沿（默认 -3 dB）"
            } else {
                "按左侧 / 右侧 / 双侧搜索参考电平以下的 -X dB 点"
            },
            onClose = vm::closeAnalysis,
        )

        SectionCard(title = "迹线") {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (param in availableParams) {
                    ChoiceChip(param.label, trace == param) { trace = param }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "选择要分析的 S 参数迹线；使用当前扫描数据的快照。",
                color = VnaTextMuted,
                fontSize = 10.5.sp,
            )
        }

        SectionCard(title = "参考") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceChip("峰值", referenceMode == XdbReferenceMode.PEAK) {
                    referenceMode = XdbReferenceMode.PEAK
                }
                ChoiceChip("游标", referenceMode == XdbReferenceMode.CURSOR) {
                    referenceMode = XdbReferenceMode.CURSOR
                }
            }
            Spacer(Modifier.height(10.dp))
            if (referenceMode == XdbReferenceMode.PEAK) {
                Text(
                    "以所选迹线的最高点作为 0 dB 参考。",
                    color = VnaTextMuted,
                    fontSize = 10.5.sp,
                )
            } else if (markers.isEmpty()) {
                Text(
                    "还没有标记。先在图表的标记行点「＋ 标记」，再回来把它选作游标。",
                    color = VnaSecondary,
                    fontSize = 10.5.sp,
                )
            } else {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    for (marker in markers) {
                        ChoiceChip(
                            "M" + marker.id + " " + formatFrequencyShort(marker.freqHz),
                            activeCursor?.id == marker.id,
                        ) { cursorId = marker.id }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "以所选标记所在频点的幅度作为 0 dB 参考。",
                    color = VnaTextMuted,
                    fontSize = 10.5.sp,
                )
            }
        }

        SectionCard(title = if (bandwidth) "带宽定义（-X dB）" else "搜索深度（-X dB）") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = dropText,
                    onValueChange = { dropText = it; localError = null },
                    label = { Text("X") },
                    suffix = { Text("dB") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(10.dp))
                Text("相对参考下降", color = VnaTextMuted, fontSize = 11.sp)
            }
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (value in listOf(1.0, 3.0, 6.0, 10.0, 20.0)) {
                    ChoiceChip(
                        "-" + value.toInt() + " dB",
                        dropText.toDoubleOrNull() == value,
                    ) { dropText = String.format(Locale.US, "%.1f", value) }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "默认 -3 dB；交叉点在相邻采样点之间线性插值。",
                color = VnaTextMuted,
                fontSize = 10.5.sp,
            )
        }

        if (bandwidth) {
            Text(
                "带宽搜索 = XdB 搜索的双侧版本：只添加找到的两个边沿标记。",
                color = VnaTextMuted,
                fontSize = 10.5.sp,
            )
        } else {
            SectionCard(title = "搜索方向") {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    for (option in XdbSide.entries) {
                        ChoiceChip(option.label, side == option) { side = option }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "单侧只添加找到的那个边沿；双侧在两侧都找到时给出带宽。",
                    color = VnaTextMuted,
                    fontSize = 10.5.sp,
                )
            }
        }

        val currentResult = result
        if (currentResult != null) {
            XdbResultCard(currentResult, resultTrace)
        }
        val currentMessage = message
        if (currentMessage != null) {
            Text(
                currentMessage,
                color = if (currentResult == null) VnaSecondary else VnaTextMuted,
                fontSize = 11.5.sp,
            )
        }

        if (localError != null) {
            Text(localError!!, color = VnaError, fontSize = 12.sp)
        }

        Button(
            onClick = ::runSearch,
            enabled = sweepAvailable,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = VnaPrimary, contentColor = Color(0xFF00201C)),
            modifier = Modifier.fillMaxWidth().height(50.dp),
        ) {
            Text(
                if (bandwidth) "搜索带宽并添加标记" else "搜索并添加标记",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
            )
        }
        Text(
            "新的搜索会替换上一次分析生成的标记；手动标记和自定义游标不受影响。",
            color = VnaTextMuted,
            fontSize = 10.sp,
        )
    }
}

@Composable
private fun XdbResultCard(result: XdbSearchResult, trace: SParam?) {
    SectionCard(title = "结果" + if (trace != null) " · " + trace.label else "") {
        KeyValueRow(
            "参考",
            formatFrequency(result.reference.frequencyHz, 4) + " · " + formatDb(result.reference.levelDb),
        )
        KeyValueRow("目标电平", formatDb(result.targetLevelDb))
        KeyValueRow(
            "左边沿",
            result.lower?.let { formatFrequency(it.frequencyHz, 4) } ?: "未找到",
            valueColor = if (result.lower != null) VnaText else VnaTextMuted,
        )
        KeyValueRow(
            "右边沿",
            result.upper?.let { formatFrequency(it.frequencyHz, 4) } ?: "未找到",
            valueColor = if (result.upper != null) VnaText else VnaTextMuted,
        )
        val bandwidth = result.bandwidthHz
        if (bandwidth != null) {
            KeyValueRow("带宽", formatFrequency(bandwidth, 4), valueColor = VnaSecondary)
            val center = result.centerHz
            if (center != null) KeyValueRow("中心频率", formatFrequency(center, 4))
        }
    }
}

private fun defaultXdbTrace(available: List<SParam>): SParam {
    if (available.isEmpty()) return SParam.S11
    // Bandwidth work is normally done on a transmission trace, so prefer S21 when it is shown.
    if (available.contains(SParam.S21)) return SParam.S21
    if (available.contains(SParam.S11)) return SParam.S11
    return available.first()
}

// --------------------------------------------------------------------------
// DTF
// --------------------------------------------------------------------------

@Composable
private fun DtfAnalysisContent(vm: MainViewModel) {
    val info by vm.repo.info.collectAsStateWithLifecycle()
    val result by vm.dtfResult.collectAsStateWithLifecycle()
    val sourceTrace by vm.dtfSourceTrace.collectAsStateWithLifecycle()
    val message by vm.dtfMessage.collectAsStateWithLifecycle()
    val busy by vm.dtfBusy.collectAsStateWithLifecycle()

    var sweepAvailable by remember { mutableStateOf(vm.repo.sweep.value != null) }
    LaunchedEffect(Unit) {
        vm.repo.sweep.collect { sweepAvailable = it != null }
    }

    var trace by remember { mutableStateOf(SParam.S11) }
    var window by remember { mutableStateOf(DtfWindow.HANN) }
    var velocityText by remember { mutableStateOf("0.66") }
    var zeroPadding by remember { mutableStateOf(4) }
    var lossText by remember { mutableStateOf("0.0") }
    var threshold by remember { mutableFloatStateOf(-20f) }
    var stopText by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }

    val twoPort = (info?.numPorts ?: 1) >= 2
    val traces = if (twoPort) listOf(SParam.S11, SParam.S22) else listOf(SParam.S11)

    fun compute() {
        val velocity = velocityText.toDoubleOrNull()
        val loss = lossText.toDoubleOrNull()
        val trimmedStop = stopText.trim()
        val stop = if (trimmedStop.isEmpty()) null else trimmedStop.toDoubleOrNull()
        localError = when {
            !sweepAvailable -> "还没有扫描数据"
            velocity == null || !velocity.isFinite() || velocity <= 0.0 || velocity > 1.0 ->
                "速度因子必须在 0–1 之间"
            loss == null || !loss.isFinite() || loss < 0.0 -> "缆损必须是非负数"
            trimmedStop.isNotEmpty() && (stop == null || !stop.isFinite() || stop <= 0.0) ->
                "距离上限必须是正数"
            else -> null
        }
        if (localError != null) return
        vm.runDtfAnalysis(
            trace = trace,
            window = window,
            velocityFactor = velocity!!,
            zeroPadding = zeroPadding,
            lossDbPerM = loss!!,
            peakThresholdDb = threshold.toDouble(),
            stopDistanceM = stop,
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SheetHeader(
            title = "DTF · 故障距离",
            subtitle = "对反射迹线做加窗 IFFT，再按速度因子换算为距离",
            onClose = vm::closeAnalysis,
        )

        SectionCard(title = "输入迹线") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (param in traces) {
                    ChoiceChip(param.label, trace == param) { trace = param }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "DTF 使用复数反射系数（S11 / S22）。未校准时方向性和端口失配会抬高距离域底噪，建议先做 SOL 校准。",
                color = VnaTextMuted,
                fontSize = 10.5.sp,
            )
        }

        SectionCard(title = "计算参数") {
            Text("窗函数", color = VnaTextMuted, fontSize = 11.sp)
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (option in DtfWindow.entries) {
                    ChoiceChip(option.label, window == option) { window = option }
                }
            }
            Spacer(Modifier.height(14.dp))

            OutlinedTextField(
                value = velocityText,
                onValueChange = { velocityText = it; localError = null },
                label = { Text("速度因子 VF") },
                suffix = { Text("× c") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (value in listOf("0.66", "0.70", "0.80", "1.00")) {
                    ChoiceChip(value, velocityText == value) { velocityText = value }
                }
            }
            Text(
                "实心聚乙烯约 0.66，发泡聚乙烯约 0.80，空气约 1.00。",
                color = VnaTextMuted,
                fontSize = 10.5.sp,
                modifier = Modifier.padding(top = 6.dp),
            )

            Spacer(Modifier.height(14.dp))
            Text("距离插值（IFFT 补零）", color = VnaTextMuted, fontSize = 11.sp)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (factor in listOf(1, 2, 4, 8)) {
                    ChoiceChip(factor.toString() + "×", zeroPadding == factor) { zeroPadding = factor }
                }
            }

            Spacer(Modifier.height(14.dp))
            Text("峰值门限（相对最强反射）", color = VnaTextMuted, fontSize = 11.sp)
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (value in listOf(-10f, -20f, -30f, -40f)) {
                    ChoiceChip(String.format("%.0f dB", value), threshold == value) { threshold = value }
                }
            }

            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = lossText,
                    onValueChange = { lossText = it; localError = null },
                    label = { Text("缆损补偿") },
                    suffix = { Text("dB/m") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = stopText,
                    onValueChange = { stopText = it; localError = null },
                    label = { Text("距离上限") },
                    suffix = { Text("m") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "距离上限留空 = 显示完整无模糊距离。缆损按往返 2×距离 补偿，仅影响显示幅度。",
                color = VnaTextMuted,
                fontSize = 10.5.sp,
            )
        }

        if (localError != null) {
            Text(localError!!, color = VnaError, fontSize = 12.sp)
        }
        val currentMessage = message
        if (currentMessage != null && !busy) {
            Text(currentMessage, color = VnaError, fontSize = 11.5.sp)
        }

        Button(
            onClick = ::compute,
            enabled = sweepAvailable && !busy,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = VnaPrimary, contentColor = Color(0xFF00201C)),
            modifier = Modifier.fillMaxWidth().height(50.dp),
        ) {
            if (busy) {
                CircularProgressIndicator(
                    color = Color(0xFF00201C),
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text("计算中…", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            } else {
                Text("计算 DTF", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
        Text(
            "计算使用当前迹线快照；扫描或校准改变后请重新计算。",
            color = VnaTextMuted,
            fontSize = 10.sp,
        )

        val currentResult = result
        if (currentResult != null) {
            DtfResultContent(currentResult, sourceTrace)
        }
    }
}

@Composable
private fun DtfResultContent(result: DtfResult, sourceTrace: SParam?) {
    SectionCard(title = "距离域响应") {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Metric("最大距离", formatDistance(result.maxDistanceM))
            Metric("分辨率", formatDistance(result.resolutionM))
            Metric("FFT", result.fftSize.toString())
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "基于 " + (sourceTrace?.label ?: "S11") + " · " + result.sourcePoints + " 个扫描点 · " +
                result.window.label + " 窗 · VF " + String.format(Locale.US, "%.2f", result.velocityFactor),
            color = VnaTextMuted,
            fontSize = 10.5.sp,
        )
        Spacer(Modifier.height(10.dp))
        DtfChart(result)
        Spacer(Modifier.height(6.dp))
        Text(
            "横轴：故障距离 · 纵轴：相对幅度（0 dB = 最强反射）",
            color = VnaTextMuted,
            fontSize = 10.sp,
        )
        if (result.wrappedEdgeTrimmed) {
            Spacer(Modifier.height(4.dp))
            Text(
                "距离域为循环卷积：最远端是 0 m 响应的卷绕区，故障表已排除该区域。",
                color = VnaSecondary,
                fontSize = 10.sp,
            )
        }
    }

    SectionCard(title = "故障点") {
        val strongest = result.faults.maxByOrNull { it.magnitudeDb }
        if (result.faults.isEmpty()) {
            Text(
                "没有高于门限的反射点。可以降低峰值门限，或提高扫描点数 / 带宽后重试。",
                color = VnaTextMuted,
                fontSize = 11.sp,
            )
        } else {
            for ((index, fault) in result.faults.withIndex()) {
                val isStrongest = strongest != null && fault == strongest
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "F" + (index + 1),
                        color = if (isStrongest) VnaSecondary else VnaTextMuted,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = if (isStrongest) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.width(30.dp),
                    )
                    Text(
                        formatDistance(fault.distanceM),
                        color = if (isStrongest) VnaSecondary else VnaText,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        String.format("%.1f ns", fault.timeNs),
                        color = VnaTextMuted,
                        fontSize = 10.5.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.width(70.dp),
                    )
                    Text(
                        formatDb(fault.magnitudeDb),
                        color = if (isStrongest) VnaSecondary else VnaText,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "F 编号与上方曲线上的标记对应；按距离从近到远排列。",
            color = VnaTextMuted,
            fontSize = 10.sp,
        )
    }
}
