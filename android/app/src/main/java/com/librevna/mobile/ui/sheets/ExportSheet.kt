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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import com.librevna.mobile.ui.text.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.librevna.mobile.ExportFormat
import com.librevna.mobile.MainViewModel
import com.librevna.mobile.ui.components.SectionCard
import com.librevna.mobile.ui.theme.VnaPrimary
import com.librevna.mobile.ui.theme.VnaSecondary
import com.librevna.mobile.ui.theme.VnaSurface
import com.librevna.mobile.ui.theme.VnaSurfaceElevated
import com.librevna.mobile.ui.theme.VnaText
import com.librevna.mobile.ui.theme.VnaTextMuted

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportSheet(vm: MainViewModel) {
    val show by vm.showExportSheet.collectAsStateWithLifecycle()
    if (!show) return
    val sweep by vm.repo.sweep.collectAsStateWithLifecycle()
    val calibration by vm.repo.calibration.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = { vm.showExportSheet.value = false },
        sheetState = sheetState,
        containerColor = VnaSurface,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("导出测量数据", color = VnaText, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(
                if (sweep == null) "还没有扫描数据" else "+ " + sweep!!.size + " 点 · " + (if (calibration.isEmpty) "原始数据" else "已校准数据"),
                color = VnaTextMuted,
                fontSize = 12.sp,
            )
            val onePort = sweep != null && sweep!!.s12.all { !it.isFinite() } && sweep!!.s22.all { !it.isFinite() }
            if (onePort) {
                Text(
                    "当前是单端口扫描：.s2p 中的 S12 / S22 会写为 0，建议导出 .s1p。",
                    color = VnaSecondary,
                    fontSize = 11.sp,
                )
            }
            for (format in ExportFormat.entries) {
                SectionCard(title = format.label) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "文件扩展名 ." + format.extension + " · 频率以 Hz 表示，50 Ω 参考阻抗",
                                color = VnaTextMuted,
                                fontSize = 11.sp,
                            )
                        }
                        OutlinedButton(
                            onClick = { vm.shareExport(format) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.padding(end = 6.dp),
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "分享", modifier = Modifier.height(16.dp), tint = VnaTextMuted)
                        }
                        Button(
                            onClick = { vm.saveExport(format) },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = VnaPrimary, contentColor = Color(0xFF00201C)),
                        ) {
                            Icon(Icons.Default.SaveAlt, contentDescription = null, modifier = Modifier.height(16.dp))
                            Spacer(Modifier.padding(horizontal = 3.dp))
                            Text("保存", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            Text(
                "保存位置：Download/LibreVNA（Android 10 及以上）。也可以点分享发送到其他应用。",
                color = VnaTextMuted,
                fontSize = 10.5.sp,
            )
        }
    }
}