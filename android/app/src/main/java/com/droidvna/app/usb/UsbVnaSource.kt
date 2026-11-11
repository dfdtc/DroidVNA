/*
 * DroidVNA - native Android client for LibreVNA / Harogic B60.
 * Copyright (C) 2026 The DroidVNA Authors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.droidvna.app.usb

import android.content.Context
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.util.Log
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbManager
import com.droidvna.app.device.VnaSource
import com.droidvna.app.model.SweepConfig
import com.droidvna.app.model.SweepResult
import com.droidvna.app.model.VnaDeviceInfo
import com.droidvna.app.model.VnaStatus
import com.droidvna.app.usb.Protocol.Type
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.IOException

class UsbVnaSource private constructor(
    private val link: VnaUsbLink,
    override val info: VnaDeviceInfo,
) : VnaSource {
    private val _status = MutableStateFlow<VnaStatus?>(null)
    private val _sweeps = MutableSharedFlow<SweepResult>(extraBufferCapacity = 4)
    private val _logs = MutableSharedFlow<String>(extraBufferCapacity = 32)
    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 8)

    override val status: StateFlow<VnaStatus?> = _status
    override val sweeps: SharedFlow<SweepResult> = _sweeps
    override val logs: SharedFlow<String> = _logs
    override val errors: SharedFlow<String> = _errors

    private var assembler: SweepAssembler? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var watchdogJob: Job? = null
    private val isB60 = info.hardwareName == "Harogic B60"

    @Volatile private var version = 0
    @Volatile private var lastDataVersion = -1

    private fun handle(type: Int, payload: ByteArray) {
        when (type) {
            Type.VNA_DATAPOINT -> {
                val dp = DatapointCodec.parse(payload) ?: return
                // Only a datapoint that actually produced a usable receiver stage counts
                // as sweep progress. The B60 also streams frames with an invalid reference
                // while it is reinitializing; those must not keep the watchdog quiet.
                val accepted = assembler?.accept(dp) ?: false
                if (accepted) lastDataVersion = version
            }
            Type.DEVICE_STATUS -> {
                _status.value = Protocol.parseDeviceStatus(payload, info.hardwareVersion)
            }
        }
    }

    private fun emitLog(line: String) {
        _logs.tryEmit(line)
    }

    private fun emitError(message: String) {
        _errors.tryEmit(message)
    }

    override suspend fun start(config: SweepConfig, standby: Boolean): Boolean {
        watchdogJob?.cancel()
        version++
        lastDataVersion = -1
        assembler = SweepAssembler(config, onSweep = { _sweeps.tryEmit(it) }, onPartial = { _sweeps.tryEmit(it) })
        // Always send the complete 32-byte SweepSettings. The last byte is the Harogic
        // extTrigger extension: if it is left out, the VNB-60 firmware keeps whatever value a
        // previous packet left in its shared receive buffer and then waits for that many
        // trigger events per point (residue 0xFF => no data at all). External triggering is
        // not implemented, so the byte is sent explicitly as 0.
        val settings = SweepSettings.fromConfig(config, standby)
        val bytes = settings.encode()
        Log.d("VnaB60", "start " + settings)
        // Abort a possibly running sweep first.
        link.send(Type.SET_IDLE, ByteArray(0), awaitAck = true, timeoutMs = 1000)
        if (isB60) {
            // Let queued datapoints from the aborted sweep drain out of the USB pipe.
            // Otherwise they arrive after the new configuration and falsely mark it as
            // alive, which silences the watchdog for a sweep that never started.
            delay(500)
        }
        lastDataVersion = -1
        val ok = link.send(
            Type.SWEEP_SETTINGS,
            bytes,
            awaitAck = true,
            timeoutMs = 4000,
        )
        Log.d("VnaB60", "start result=" + ok)
        if (!ok) {
            assembler = null
        } else if (isB60 && !standby) {
            startB60Watchdog(bytes)
        }
        return ok
    }

    /**
     * Recovery for a B60 configuration that was acknowledged but produces no data.
     *
     * This used to be an "SA mode" reset, which was a misdiagnosis: switching modes only
     * helped because the mode switch happened to overwrite the firmware's shared receive
     * buffer, and with an extTrigger residue it worked only by coincidence. Now that the
     * 32-byte SweepSettings is always sent the residue is gone, so recovery just means
     * re-sending the very same configuration instead of pretending to switch modes.
     *
     * Nothing happens while datapoints arrive; a normal sweep delivers its first usable
     * point within milliseconds even at the lowest IFBW, so the wait is not scaled by IFBW.
     */
    private fun startB60Watchdog(payload: ByteArray) {
        val v0 = version
        watchdogJob = scope.launch {
            repeat(B60_RECONFIGURE_ATTEMPTS) { attempt ->
                delay(B60_WATCHDOG_MS)
                if (!isActive) return@launch
                if (lastDataVersion == v0) return@launch
                emitLog("B60: 扫描未启动，重发扫描配置…")
                Log.d("VnaB60", "watchdog: resend sweep settings (" + (attempt + 1) + "/" + B60_RECONFIGURE_ATTEMPTS + ")")
                link.send(Type.SET_IDLE, ByteArray(0), awaitAck = true, timeoutMs = 1000)
                delay(300)
                if (!isActive) return@launch
                val ok = link.send(Type.SWEEP_SETTINGS, payload, awaitAck = true, timeoutMs = 4000)
                Log.d("VnaB60", "watchdog reconfigure=" + ok)
            }
            if (isActive && lastDataVersion != v0) {
                emitLog("B60 自动恢复失败：请重新插拔设备后重连")
            }
        }
    }

    override suspend fun initiate(): Boolean = link.send(Type.INITIATE_SWEEP, ByteArray(0), true, 4000)

    override suspend fun stop() {
        watchdogJob?.cancel()
        version++
        assembler = null
        link.send(Type.SET_IDLE, ByteArray(0), true, 2000)
    }

    override fun close() {
        watchdogJob?.cancel()
        version++
        assembler = null
        scope.cancel()
        link.close()
    }

    companion object {
        /** How long a B60 configuration may stay silent before it is re-sent. */
        private const val B60_WATCHDOG_MS = 6_000L

        /** Number of times a silent B60 configuration is re-sent before giving up. */
        private const val B60_RECONFIGURE_ATTEMPTS = 2

        suspend fun open(context: Context, device: UsbDevice): UsbVnaSource {
            val manager = context.getSystemService(Context.USB_SERVICE) as UsbManager
            val connection = manager.openDevice(device) ?: throw IOException("无法打开 USB 设备（权限或占用问题）")
            val usbInterface = device.getInterface(0)
            if (!connection.claimInterface(usbInterface, true)) {
                connection.close()
                throw IOException("无法占用 USB 接口 0（可能被其他应用占用）")
            }

            val endpoints = (0 until usbInterface.endpointCount).map { usbInterface.getEndpoint(it) }
            val dataOut = endpoints.firstOrNull {
                it.direction == UsbConstants.USB_DIR_OUT && it.address == 0x01
            } ?: endpoints.firstOrNull {
                it.direction == UsbConstants.USB_DIR_OUT && it.type == UsbConstants.USB_ENDPOINT_XFER_BULK
            }
            val dataIn = endpoints.firstOrNull {
                it.direction == UsbConstants.USB_DIR_IN && it.address == 0x81
            } ?: endpoints.firstOrNull {
                it.direction == UsbConstants.USB_DIR_IN && it.type == UsbConstants.USB_ENDPOINT_XFER_BULK
            }
            val logIn: UsbEndpoint? = endpoints.firstOrNull {
                it.direction == UsbConstants.USB_DIR_IN && it.address == 0x82
            }
            if (dataOut == null || dataIn == null) {
                connection.releaseInterface(usbInterface)
                connection.close()
                throw IOException("设备缺少 LibreVNA 所需的 bulk 端点")
            }

            var sourceRef: UsbVnaSource? = null
            val link = VnaUsbLink(
                connection = connection,
                usbInterface = usbInterface,
                dataIn = dataIn,
                dataOut = dataOut,
                // Android's synchronous bulkTransfer serializes transfers on one UsbDeviceConnection.
                // Reading the device log endpoint concurrently starves the data endpoint (observed as
                // one sweep point every ~2.6 s on a B60), so the log reader is intentionally disabled.
                logIn = null,
                onFrame = { type, payload -> sourceRef?.handle(type, payload) },
                onLogLine = { line -> sourceRef?.emitLog(line) },
                onError = { message -> sourceRef?.emitError(message) },
            )
            link.start()

            val info = link.requestDeviceInfo()
            if (info == null) {
                link.close()
                throw IOException("设备没有回复 DeviceInfo（固件协议不匹配？）")
            }
            val isB60 = device.vendorId == 0x367F && device.productId == 0x0200
            // The Harogic DeviceInfo reports 11 Hz – 93.5 kHz, but the VNB-60 specification
            // (and what the firmware actually accepts) is 15 Hz – 75 kHz. Override the
            // reported limits so the UI exposes the real low-IFBW range.
            val completeInfo = info.copy(
                hardwareName = if (isB60) "Harogic B60" else info.hardwareName,
                minIfbwHz = if (isB60) 15 else info.minIfbwHz,
                maxIfbwHz = if (isB60) 75_000 else info.maxIfbwHz,
                serial = try {
                    device.serialNumber ?: device.deviceName
                } catch (e: Exception) {
                    device.deviceName
                },
                usbName = device.productName ?: if (isB60) "B60" else "LibreVNA",
            )
            val source = UsbVnaSource(link, completeInfo)
            sourceRef = source
            link.send(Type.START_STATUS_UPDATES, ByteArray(0), awaitAck = false)
            link.send(Type.REQUEST_DEVICE_STATUS, ByteArray(0), awaitAck = false)
            return source
        }
    }
}