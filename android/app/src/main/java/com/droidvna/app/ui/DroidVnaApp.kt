/*
 * DroidVNA - native Android client for LibreVNA / Harogic B60.
 * Copyright (C) 2026 The DroidVNA Authors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.droidvna.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import com.droidvna.app.AppScreen
import com.droidvna.app.MainViewModel
import com.droidvna.app.device.ConnectionState
import com.droidvna.app.ui.components.ConnectionPill
import com.droidvna.app.ui.screens.CalibrationScreen
import com.droidvna.app.ui.screens.DeviceScreen
import com.droidvna.app.ui.screens.MeasureScreen
import com.droidvna.app.ui.sheets.AppSettingsSheet
import com.droidvna.app.ui.sheets.ExportSheet
import com.droidvna.app.ui.sheets.SweepSettingsSheet
import com.droidvna.app.ui.sheets.TraceSettingsSheet
import com.droidvna.app.ui.text.LocalAppLanguage
import com.droidvna.app.ui.text.Text
import com.droidvna.app.ui.text.resolved
import com.droidvna.app.ui.text.translate
import com.droidvna.app.ui.text.tr
import com.droidvna.app.ui.theme.VnaBackground
import com.droidvna.app.ui.theme.VnaText
import com.droidvna.app.ui.theme.VnaTextMuted

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DroidVnaApp(vm: MainViewModel) {
    val screen by vm.screen.collectAsStateWithLifecycle()
    val connection by vm.repo.connection.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val compact = LocalConfiguration.current.screenWidthDp < 700
    val language by vm.language.collectAsStateWithLifecycle()
    val resolvedLanguage = language.resolved()

    CompositionLocalProvider(LocalAppLanguage provides resolvedLanguage) {
    LaunchedEffect(resolvedLanguage) {
        vm.messages.collect { message ->
            snackbarHostState.showSnackbar(translate(message, resolvedLanguage), withDismissAction = true)
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
                                AppScreen.MEASURE -> "DroidVNA"
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
                    IconButton(onClick = { vm.showAppSettings.value = true }) {
                        Icon(Icons.Default.Settings, contentDescription = tr("设置"), tint = VnaTextMuted)
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
    AppSettingsSheet(vm)
    TraceSettingsSheet(vm)
    }
}