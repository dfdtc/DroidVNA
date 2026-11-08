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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.droidvna.app.BuildConfig
import com.droidvna.app.MainViewModel
import com.droidvna.app.ui.components.ChoiceChip
import com.droidvna.app.ui.components.OutlineButton
import com.droidvna.app.ui.components.SectionCard
import com.droidvna.app.ui.components.SettingToggleRow
import com.droidvna.app.ui.text.AppLanguage
import com.droidvna.app.ui.text.Text
import com.droidvna.app.ui.theme.VnaError
import com.droidvna.app.ui.theme.VnaSurface
import com.droidvna.app.ui.theme.VnaText
import com.droidvna.app.ui.theme.VnaTextMuted

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSettingsSheet(vm: MainViewModel) {
    val show by vm.showAppSettings.collectAsStateWithLifecycle()
    if (!show) return
    val language by vm.language.collectAsStateWithLifecycle()
    val rememberSweep by vm.rememberSweep.collectAsStateWithLifecycle()
    val rememberCal by vm.rememberCalibration.collectAsStateWithLifecycle()
    val autoStart by vm.autoStartSweep.collectAsStateWithLifecycle()
    val savedCal by vm.savedCalibration.collectAsStateWithLifecycle()
    val calEnabled by vm.calibrationEnabled.collectAsStateWithLifecycle()
    val hideWatermark by vm.hideWatermark.collectAsStateWithLifecycle()
    var confirmForget by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = { vm.showAppSettings.value = false },
        sheetState = sheetState,
        containerColor = VnaSurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("应用设置", color = VnaText, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("LibreVNA / Harogic VNA client for Android", color = VnaTextMuted, fontSize = 11.5.sp)
                }
                TextButton(onClick = { vm.showAppSettings.value = false }) {
                    Text("关闭", color = VnaTextMuted)
                }
            }

            SectionCard(title = "界面语言") {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    for (option in AppLanguage.entries) {
                        ChoiceChip(option.label, language == option) { vm.setLanguage(option) }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("切换后立即生效。", color = VnaTextMuted, fontSize = 11.sp)
            }

            SectionCard(title = "界面") {
                SettingToggleRow(
                    title = "关闭应用水印",
                    subtitle = "关闭后迹线图背景不再显示半透明的 DroidVNA 标识。",
                    checked = hideWatermark,
                ) { vm.setHideWatermark(it) }
            }

            SectionCard(title = "设置记忆") {
                SettingToggleRow(
                    title = "记住扫描设置",
                    subtitle = "下次启动沿用频率范围、点数、IFBW、功率与平均。关闭时只在本次运行内保留。",
                    checked = rememberSweep,
                ) { vm.setRememberSweep(it) }
                SettingToggleRow(
                    title = "连接后自动连续扫描",
                    subtitle = "关闭后连接设备只下发设置，需要手动点「开始扫描」。",
                    checked = autoStart,
                ) { vm.setAutoStartSweep(it) }
            }

            SectionCard(title = "校准记忆") {
                SettingToggleRow(
                    title = "自动保存并载入上次校准",
                    subtitle = "SOL / THRU 与电子校准（E-Cal）的结果会立即保存到应用私有目录，下次启动自动载入。",
                    checked = rememberCal,
                ) { vm.setRememberCalibration(it) }
                Spacer(Modifier.height(6.dp))
                val saved = savedCal
                if (saved.calibration.isEmpty) {
                    Text("暂无保存的校准。", color = VnaTextMuted, fontSize = 11.5.sp)
                } else {
                    val parts = ArrayList<String>()
                    if (saved.calibration.sol.isNotEmpty()) {
                        parts.add("SOL 端口 " + saved.calibration.sol.joinToString(",") { it.port.toString() })
                    }
                    if (saved.calibration.thrus.isNotEmpty()) {
                        parts.add("THRU " + (saved.calibration.thrus.size / 2) + " 组")
                    }
                    val origin = when (saved.source) {
                        "ECAL" -> "电子校准"
                        "IMPORT" -> ".cal 导入"
                        "SOL" -> "手动 SOL"
                        "THRU" -> "手动 THRU"
                        else -> "混合"
                    }
                    Text(
                        "已保存：" + parts.joinToString(" · ") + " · " + origin,
                        color = VnaText,
                        fontSize = 12.sp,
                    )
                    Text(
                        (if (saved.updatedAt.isNotBlank()) "保存时间 " + saved.updatedAt.replace('T', ' ') else "保存时间未知") +
                            (if (saved.deviceKey.isNotBlank()) " · 设备 " + saved.deviceKey else "") +
                            (if (saved.detail.isNotBlank()) " · " + saved.detail else ""),
                        color = VnaTextMuted,
                        fontSize = 10.5.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ChoiceChip(if (calEnabled) "校准已启用" else "校准已停用", calEnabled) {
                            vm.setCalibrationEnabled(!calEnabled)
                        }
                        OutlineButton("忘记已保存的校准", onClick = {
                            confirmForget = true
                        })
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Android 应用没有可写的 /tmp，校准快照存放在应用私有目录（filesDir/calibration.json），随应用卸载一起删除。",
                    color = VnaTextMuted,
                    fontSize = 10.5.sp,
                    lineHeight = 15.sp,
                )
            }

            SectionCard(title = "关于") {
                Text(
                    "DroidVNA " + BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")",
                    color = VnaText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "原生 Android USB Host 实现 · 协议 v14",
                    color = VnaTextMuted,
                    fontSize = 11.5.sp,
                )
                Text(
                    "支持 LibreVNA V1 / P2 / PT、Harogic B60 与 LibreCAL / ECB-60",
                    color = VnaTextMuted,
                    fontSize = 11.5.sp,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "非官方应用，与 LibreVNA 项目及 Harogic 无隶属关系。LibreVNA 硬件、协议与桌面版 LibreVNA-GUI 由 Jan Käberich 及贡献者开发。",
                    color = VnaTextMuted,
                    fontSize = 10.5.sp,
                    lineHeight = 15.sp,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "本应用基于 LibreVNA-GUI 的协议与校准逻辑改写，按 GNU GPL-3.0 发布。",
                    color = VnaTextMuted,
                    fontSize = 10.5.sp,
                    lineHeight = 15.sp,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "github.com/jankae/LibreVNA",
                    color = VnaTextMuted,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }

    if (confirmForget) {
        AlertDialog(
            onDismissRequest = { confirmForget = false },
            containerColor = VnaSurface,
            title = { Text("忘记已保存的校准？", color = VnaText, fontSize = 17.sp, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "当前校准会立即停止应用，并且下次启动不会再自动载入。随时可以重新做一次 SOL / THRU 或电子校准。",
                    color = VnaTextMuted,
                    fontSize = 12.5.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmForget = false
                    vm.forgetCalibration()
                }) { Text("忘记", color = VnaError, fontSize = 13.sp) }
            },
            dismissButton = {
                TextButton(onClick = { confirmForget = false }) { Text("取消", color = VnaTextMuted, fontSize = 13.sp) }
            },
        )
    }
}