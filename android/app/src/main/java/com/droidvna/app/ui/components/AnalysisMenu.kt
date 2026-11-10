/*
 * DroidVNA - native Android client for LibreVNA / Harogic B60.
 * Copyright (C) 2026 The DroidVNA Authors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.droidvna.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.droidvna.app.AnalysisTool
import com.droidvna.app.MainViewModel
import com.droidvna.app.ui.text.Text
import com.droidvna.app.ui.theme.VnaOutline
import com.droidvna.app.ui.theme.VnaSurfaceElevated
import com.droidvna.app.ui.theme.VnaText
import com.droidvna.app.ui.theme.VnaTextMuted

/**
 * "分析" entry point shown next to the marker shortcuts.
 *
 * It is deliberately a small local menu instead of another app-wide sheet: the marker bar is
 * where a user thinks about cursors, and the second-level menu keeps the three marker
 * analyses (bandwidth, XdB, DTF) one tap away from it.
 */
@Composable
fun AnalysisMenuButton(vm: MainViewModel, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        Surface(
            color = Color.Transparent,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, VnaOutline),
            modifier = Modifier.clickable { expanded = true },
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("分析", color = VnaText, fontSize = 13.sp)
                Spacer(Modifier.width(5.dp))
                Text("▾", color = VnaTextMuted, fontSize = 10.sp)
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = VnaSurfaceElevated,
            modifier = Modifier.width(262.dp),
        ) {
            AnalysisEntry(
                title = "带宽搜索",
                subtitle = "找 -X dB 的双侧边沿，默认 -3 dB",
            ) {
                expanded = false
                vm.openAnalysis(AnalysisTool.BANDWIDTH)
            }
            AnalysisEntry(
                title = "XdB 搜索",
                subtitle = "可选左侧 / 右侧 / 双侧",
            ) {
                expanded = false
                vm.openAnalysis(AnalysisTool.XDB)
            }
            HorizontalDivider(color = VnaOutline)
            AnalysisEntry(
                title = "DTF",
                subtitle = "时域 / 故障点距离（IFFT）",
            ) {
                expanded = false
                vm.openAnalysis(AnalysisTool.DTF)
            }
        }
    }
}

@Composable
private fun AnalysisEntry(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                color = VnaText,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = subtitle,
                color = VnaTextMuted,
                fontSize = 10.5.sp,
            )
        }
    }
}
