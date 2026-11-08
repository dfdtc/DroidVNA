/*
 * LibreVNA Mobile - native Android client for LibreVNA / Harogic B60.
 * Copyright (C) 2026 The LibreVNA Mobile Authors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.librevna.mobile.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.librevna.mobile.AppScreen
import com.librevna.mobile.MainViewModel
import com.librevna.mobile.device.ConnectionState
import com.librevna.mobile.ui.components.ConnectionPill
import com.librevna.mobile.ui.screens.CalibrationScreen
import com.librevna.mobile.ui.screens.DeviceScreen
import com.librevna.mobile.ui.screens.MeasureScreen
import com.librevna.mobile.ui.sheets.ExportSheet
import com.librevna.mobile.ui.sheets.SweepSettingsSheet
import com.librevna.mobile.ui.theme.VnaBackground
import com.librevna.mobile.ui.theme.VnaText
import com.librevna.mobile.ui.theme.VnaTextMuted

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibreVnaApp(vm: MainViewModel) {
    val screen by vm.screen.collectAsStateWithLifecycle()
    val connection by vm.repo.connection.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val compact = LocalConfiguration.current.screenWidthDp < 700

    LaunchedEffect(Unit) {
        vm.messages.collect { message ->
            snackbarHostState.showSnackbar(message, withDismissAction = true)
        }
    }
    BackHandler(enabled = screen != AppScreen.MEASURE) {
        vm.navigate(AppScreen.MEASURE)
    }

    Scaffold(
        containerColor = VnaBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = VnaBackground,
                    titleContentColor = VnaText,
                ),
                navigationIcon = {
                    if (screen != AppScreen.MEASURE) {
                        IconButton(onClick = { vm.navigate(AppScreen.MEASURE) }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "返回", tint = VnaText)
                        }
                    }
                },
                title = {
                    Column {
                        Text(
                            text = when (screen) {
                                AppScreen.MEASURE -> "LibreVNA"
                                AppScreen.DEVICE -> "设备"
                                AppScreen.CALIBRATION -> "校准"
                            },
                            fontSize = 21.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.3.sp,
                        )
                        Text(
                            text = when (screen) {
                                AppScreen.MEASURE -> "TOUCH VECTOR NETWORK ANALYZER"
                                AppScreen.DEVICE -> "USB / 状态 / 诊断"
                                AppScreen.CALIBRATION -> "1-PORT SOL + THRU"
                            },
                            fontSize = 8.5.sp,
                            color = VnaTextMuted,
                            letterSpacing = 1.2.sp,
                        )
                    }
                },
                actions = {
                    ConnectionPill(connection) { vm.navigate(AppScreen.DEVICE) }
                    if (!compact) {
                        IconButton(onClick = { vm.showSweepSheet.value = true }) {
                            Icon(Icons.Default.Tune, contentDescription = "扫描设置", tint = VnaTextMuted)
                        }
                        IconButton(onClick = { vm.navigate(AppScreen.CALIBRATION) }) {
                            Icon(Icons.Default.Science, contentDescription = "校准", tint = VnaTextMuted)
                        }
                        IconButton(onClick = { vm.showExportSheet.value = true }) {
                            Icon(Icons.Default.SaveAlt, contentDescription = "导出", tint = VnaTextMuted)
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (screen) {
                AppScreen.MEASURE -> {
                    val configuration = LocalConfiguration.current
                    MeasureScreen(vm, wide = configuration.screenWidthDp >= 1000)
                }
                AppScreen.DEVICE -> DeviceScreen(vm)
                AppScreen.CALIBRATION -> CalibrationScreen(vm)
            }
        }
    }

    SweepSettingsSheet(vm)
    ExportSheet(vm)
}