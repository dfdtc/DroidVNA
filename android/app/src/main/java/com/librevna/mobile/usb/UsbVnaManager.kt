/*
 * LibreVNA Mobile - native Android client for LibreVNA / Harogic B60.
 * Copyright (C) 2026 The LibreVNA Mobile Authors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.librevna.mobile.usb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/** Finds supported USB VNAs (LibreVNA family and Harogic B60) and handles the Android USB permission flow. */
class UsbVnaManager(private val context: Context) {
    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private val _devices = MutableStateFlow(findSupportedVna())
    private val _allDevices = MutableStateFlow(allDevices())
    private val _attached = MutableSharedFlow<UsbDevice>(extraBufferCapacity = 2)
    private val waiters = mutableMapOf<String, CompletableDeferred<Boolean>>()

    val devices: StateFlow<List<UsbDevice>> = _devices
    val allDevices: StateFlow<List<UsbDevice>> = _allDevices
    val attached: SharedFlow<UsbDevice> = _attached

    private val permissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            if (intent?.action != ACTION_USB_PERMISSION) return
            val device = IntentCompat.getParcelableExtra(intent, UsbManager.EXTRA_DEVICE, UsbDevice::class.java) ?: return
            val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
            val waiter = synchronized(waiters) { waiters.remove(device.deviceName) }
            waiter?.complete(granted)
            refresh()
        }
    }

    private val attachReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            val device = intent?.let { IntentCompat.getParcelableExtra(it, UsbManager.EXTRA_DEVICE, UsbDevice::class.java) }
            when (intent?.action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    refresh()
                    if (device != null && isSupportedVna(device)) _attached.tryEmit(device)
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> refresh()
            }
        }
    }

    init {
        ContextCompat.registerReceiver(
            context,
            permissionReceiver,
            IntentFilter(ACTION_USB_PERMISSION),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        ContextCompat.registerReceiver(
            context,
            attachReceiver,
            IntentFilter().apply {
                addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
                addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            },
            ContextCompat.RECEIVER_EXPORTED,
        )
    }

    fun refresh() {
        _devices.value = findSupportedVna()
        _allDevices.value = allDevices()
    }

    fun hasPermission(device: UsbDevice): Boolean = usbManager.hasPermission(device)

    fun requestPermission(device: UsbDevice): CompletableDeferred<Boolean> {
        val deferred = CompletableDeferred<Boolean>()
        synchronized(waiters) { waiters[device.deviceName] = deferred }
        val intent = Intent(ACTION_USB_PERMISSION).setPackage(context.packageName)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        val pending = PendingIntent.getBroadcast(context, device.deviceId, intent, flags)
        usbManager.requestPermission(device, pending)
        return deferred
    }

    fun openDevice(device: UsbDevice) = usbManager.openDevice(device)

    fun findSupportedVna(): List<UsbDevice> =
        usbManager.deviceList.values.filter { isSupportedVna(it) }.sortedBy { it.deviceName }

    fun allDevices(): List<UsbDevice> = usbManager.deviceList.values.sortedBy { it.deviceName }

    companion object {
        const val ACTION_USB_PERMISSION = "com.librevna.mobile.USB_PERMISSION"
        private val IDS = setOf(
            0x0483 to 0x564E, // LibreVNA V1 / V2
            0x0483 to 0x4121, // LibreVNA (ST VID)
            0x1209 to 0x4121, // LibreVNA (pid.codes)
            0x367F to 0x0200, // Harogic B60 (VNB-60), same protocol as LibreVNA
        )

        fun isSupportedVna(device: UsbDevice): Boolean = IDS.contains(device.vendorId to device.productId)

        fun isHarogicB60(device: UsbDevice): Boolean =
            device.vendorId == 0x367F && device.productId == 0x0200

        /** LibreCAL and Harogic ECB-60 electronic calibration modules. */
        fun isEcal(device: UsbDevice): Boolean =
            device.vendorId == EcalDevice.VENDOR_ID && device.productId == EcalDevice.PRODUCT_ID

        @Deprecated("Use isSupportedVna", ReplaceWith("isSupportedVna(device)"))
        fun isLibreVna(device: UsbDevice): Boolean = isSupportedVna(device)

        fun deviceLabel(device: UsbDevice): String = when {
            isHarogicB60(device) -> "Harogic B60"
            device.productName.isNullOrBlank() -> "USB VNA"
            else -> device.productName!!
        }

        fun describe(device: UsbDevice): String {
            return String.format("%s · %04X:%04X · %s", deviceLabel(device), device.vendorId, device.productId, device.deviceName)
        }
    }
}