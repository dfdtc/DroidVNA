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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.droidvna.app.BuildConfig
import com.droidvna.app.MainViewModel
import com.droidvna.app.ui.components.ChoiceChip
import com.droidvna.app.ui.components.SectionCard
import com.droidvna.app.ui.text.AppLanguage
import com.droidvna.app.ui.text.Text
import com.droidvna.app.ui.theme.VnaSurface
import com.droidvna.app.ui.theme.VnaText
import com.droidvna.app.ui.theme.VnaTextMuted

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSettingsSheet(vm: MainViewModel) {
    val show by vm.showAppSettings.collectAsStateWithLifecycle()
    if (!show) return
    val language by vm.language.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = { vm.showAppSettings.value = false },
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
}