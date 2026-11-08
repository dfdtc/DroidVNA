/*
 * DroidVNA - native Android client for LibreVNA / Harogic B60.
 * Copyright (C) 2026 The DroidVNA Authors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.droidvna.app

import android.content.Intent
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.IntentCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.droidvna.app.ui.DroidVnaApp
import com.droidvna.app.ui.theme.DroidVnaTheme
import com.droidvna.app.usb.UsbVnaManager

class MainActivity : ComponentActivity() {
    private lateinit var viewModel: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            DroidVnaTheme {
                val vm: MainViewModel = viewModel()
                viewModel = vm
                DroidVnaApp(vm)
            }
        }
        handleUsbIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleUsbIntent(intent)
    }

    private fun handleUsbIntent(intent: Intent?) {
        if (intent?.action != UsbManager.ACTION_USB_DEVICE_ATTACHED) return
        val device = IntentCompat.getParcelableExtra(intent, UsbManager.EXTRA_DEVICE, android.hardware.usb.UsbDevice::class.java)
            ?: return
        if (!UsbVnaManager.isSupportedVna(device)) return
        if (::viewModel.isInitialized) {
            viewModel.message("检测到 " + UsbVnaManager.deviceLabel(device) + "，请在设备页连接")
            viewModel.navigate(AppScreen.DEVICE)
        }
    }
}