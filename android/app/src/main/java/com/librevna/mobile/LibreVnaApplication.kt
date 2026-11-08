/*
 * LibreVNA Mobile - native Android client for LibreVNA / Harogic B60.
 * Copyright (C) 2026 The LibreVNA Mobile Authors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.librevna.mobile

import android.app.Application
import com.librevna.mobile.device.VnaRepository
import com.librevna.mobile.usb.UsbVnaManager

class LibreVnaApplication : Application() {
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