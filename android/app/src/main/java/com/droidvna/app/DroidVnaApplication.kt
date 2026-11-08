/*
 * DroidVNA - native Android client for LibreVNA / Harogic B60.
 * Copyright (C) 2026 The DroidVNA Authors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.droidvna.app

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.droidvna.app.data.PersistedSettings
import com.droidvna.app.data.SettingsStore
import com.droidvna.app.device.VnaRepository
import com.droidvna.app.usb.UsbVnaManager

class DroidVnaApplication : Application() {
    lateinit var usbManager: UsbVnaManager
        private set
    lateinit var repository: VnaRepository
        private set

    /**
     * Settings are kept here (and not only in the ViewModel) so the repository - which is
     * created before any screen exists - can already honour "remember calibration".
     */
    var settings: PersistedSettings by mutableStateOf(PersistedSettings())
        private set

    fun updateSettings(newSettings: PersistedSettings) {
        settings = newSettings
    }

    override fun onCreate() {
        super.onCreate()
        settings = SettingsStore(this).load()
        usbManager = UsbVnaManager(this)
        repository = VnaRepository(
            context = this,
            usb = usbManager,
            rememberEnabled = settings.rememberCalibration,
            autoStart = { settings.autoStartSweep },
        )
    }
}