/*
 * DroidVNA - native Android client for LibreVNA / Harogic B60.
 * Copyright (C) 2026 The DroidVNA Authors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.droidvna.app

import android.app.Application
import com.droidvna.app.device.VnaRepository
import com.droidvna.app.usb.UsbVnaManager

class DroidVnaApplication : Application() {
    lateinit var usbManager: UsbVnaManager
        private set
    lateinit var repository: VnaRepository
        private set

    override fun onCreate() {
        super.onCreate()
        usbManager = UsbVnaManager(this)
        repository = VnaRepository(this, usbManager)
    }
}