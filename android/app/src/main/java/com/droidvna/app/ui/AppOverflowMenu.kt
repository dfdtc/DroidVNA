/*
 * DroidVNA - native Android client for LibreVNA / Harogic B60.
 * Copyright (C) 2026 The DroidVNA Authors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.droidvna.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.droidvna.app.AppScreen
import com.droidvna.app.MainViewModel
import com.droidvna.app.ui.text.Text
import com.droidvna.app.ui.text.tr
import com.droidvna.app.ui.theme.VnaOutline
import com.droidvna.app.ui.theme.VnaSurfaceElevated
import com.droidvna.app.ui.theme.VnaText
import com.droidvna.app.ui.theme.VnaTextMuted

/**
 * Single "more" button that replaces the individual settings icons in the top bar.
 *
 * Everything that used to be a separate action (sweep settings, calibration, export, device
 * page, app settings and the control-row collapse) lives in one menu, so the bar keeps a
 * single control at its right edge and the chart gets the vertical space back.
 */
@Composable
fun AppOverflowMenu(
    vm: MainViewModel,
    modifier: Modifier = Modifier,
    tint: Color = VnaTextMuted,
) {
    var expanded by remember { mutableStateOf(false) }
    val collapsed by vm.topControlsCollapsed.collectAsStateWithLifecycle()

    Box(modifier) {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = tr("菜单"), tint = tint)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = VnaSurfaceElevated,
            modifier = Modifier.width(228.dp),
        ) {
            MenuEntry("扫描设置", Icons.Default.Tune) {
                expanded = false
                vm.showSweepSheet.value = true
            }
            MenuEntry("迹线设置", Icons.Default.Settings) {
                expanded = false
                vm.showTraceSettings.value = true
            }
            MenuEntry("校准", Icons.Default.Science) {
                expanded = false
                vm.navigate(AppScreen.CALIBRATION)
            }
            MenuEntry("导出", Icons.Default.SaveAlt) {
                expanded = false
                vm.showExportSheet.value = true
            }
            MenuEntry("设备", Icons.Default.Usb) {
                expanded = false
                vm.navigate(AppScreen.DEVICE)
            }
            HorizontalDivider(color = VnaOutline)
            MenuEntry(
                if (collapsed) "展开迹线控件" else "收起迹线控件",
                if (collapsed) Icons.Default.UnfoldMore else Icons.Default.UnfoldLess,
            ) {
                expanded = false
                vm.toggleTopControls()
            }
            MenuEntry("应用设置", Icons.Default.Settings) {
                expanded = false
                vm.showAppSettings.value = true
            }
        }
    }
}

@Composable
private fun MenuEntry(label: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        Icon(icon, contentDescription = null, tint = VnaTextMuted, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, color = VnaText, fontSize = 13.5.sp)
    }
}