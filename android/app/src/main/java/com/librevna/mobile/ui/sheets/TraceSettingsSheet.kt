/*
 * LibreVNA Mobile - native Android client for LibreVNA / Harogic B60.
 * Copyright (C) 2026 The LibreVNA Mobile Authors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.librevna.mobile.ui.sheets

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
import com.librevna.mobile.MainViewModel
import com.librevna.mobile.ui.components.SectionCard
import com.librevna.mobile.ui.components.SettingToggleRow
import com.librevna.mobile.ui.text.Text
import com.librevna.mobile.ui.theme.VnaSurface
import com.librevna.mobile.ui.theme.VnaText
import com.librevna.mobile.ui.theme.VnaTextMuted

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TraceSettingsSheet(vm: MainViewModel) {
    val show by vm.showTraceSettings.collectAsStateWithLifecycle()
    if (!show) return
    val logAxis by vm.logFrequencyAxis.collectAsStateWithLifecycle()
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

            Text(
                "更多迹线设置将在这里提供。",
                color = VnaTextMuted,
                fontSize = 10.5.sp,
            )
            Spacer(Modifier.height(4.dp))
        }
    }
}