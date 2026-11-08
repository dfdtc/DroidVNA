/*
 * DroidVNA - native Android client for LibreVNA / Harogic B60.
 * Copyright (C) 2026 The DroidVNA Authors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.droidvna.app.ui.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.droidvna.app.MainViewModel
import com.droidvna.app.ui.components.SectionCard
import com.droidvna.app.ui.components.SettingToggleRow
import com.droidvna.app.ui.text.Text
import com.droidvna.app.ui.theme.VnaSurface
import com.droidvna.app.ui.theme.VnaText
import com.droidvna.app.ui.theme.VnaTextMuted

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TraceSettingsSheet(vm: MainViewModel) {
    val show by vm.showTraceSettings.collectAsStateWithLifecycle()
    if (!show) return
    val logAxis by vm.logFrequencyAxis.collectAsStateWithLifecycle()
    val autoY by vm.chartAutoY.collectAsStateWithLifecycle()
    val mode by vm.chartMode.collectAsStateWithLifecycle()
    val enabled by vm.enabledParams.collectAsStateWithLifecycle()
    val delta by vm.deltaMarkers.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = { vm.showTraceSettings.value = false },
        sheetState = sheetState,
        containerColor = VnaSurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("迹线设置", color = VnaText, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("图表上的迹线显示选项", color = VnaTextMuted, fontSize = 11.5.sp)
                }
                TextButton(onClick = { vm.showTraceSettings.value = false }) {
                    Text("关闭", color = VnaTextMuted)
                }
            }

            SectionCard(title = "频率轴") {
                SettingToggleRow(
                    title = "对数频率轴",
                    subtitle = "X 轴按对数频率显示",
                    checked = logAxis,
                ) { vm.setLogFrequencyAxis(it) }
            }

            SectionCard(title = "Y 轴") {
                SettingToggleRow(
                    title = "Y 轴自动缩放",
                    subtitle = "关闭后锁定当前 Y 轴范围，与双击图表恢复自动等效。",
                    checked = autoY,
                ) { vm.chartAutoY.value = it }
            }

            SectionCard(title = "当前迹线") {
                Text(
                    "迹线与模式：" + enabled.joinToString(" + ") { it.label } + " · " + mode.label +
                        if (delta) " · Δ 标记" else "",
                    color = VnaTextMuted,
                    fontSize = 11.5.sp,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "这些选择会随设置记忆自动保存，下次启动时恢复。",
                    color = VnaTextMuted,
                    fontSize = 10.5.sp,
                )
            }

            Text(
                "更多迹线设置将在这里提供。",
                color = VnaTextMuted,
                fontSize = 10.5.sp,
            )
            Spacer(Modifier.height(4.dp))
        }
    }
}