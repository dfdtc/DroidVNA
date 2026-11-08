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
import java.nio.ByteBuffer
import java.nio.ByteOrder

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
        val bytes = Protocol.encodeSweepSettings(config, standby)
        Log.d("VnaB60", "start points=" + config.points + " ifbw=" + config.ifbwHz + " twoPort=" + config.twoPort)
        // VNB-60 firmware 1.8.6 often ignores the first VNA configuration after connecting,
        // and can remain in a slow/invalid sweep state after a low-IFBW sweep. The documented
        // workaround is to enter SA mode once and return to VNA. Apply it for every B60
        // configuration change: it costs well under a second and makes IFBW changes reliable.
        if (isB60) {
            emitLog("B60: SA → VNA 模式复位…")
            link.send(Type.SPECTRUM_ANALYZER_SETTINGS, saPayload(), awaitAck = true, timeoutMs = 2000)
            delay(300)
        }
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
            startB60Watchdog(config, bytes)
        }
        return ok
    }

    /**
     * VNB-60 firmware 1.8.6 sometimes accepts the first SweepSettings but never starts the
     * sweep; the firmware times out after ~2 s and reinitializes. The desktop GUI workaround is
     * to switch to SA mode and back, or to reconnect. This watchdog performs that recovery
     * asynchronously: idle + re-send, then (if needed) a short SA-mode reset.
     */
    private fun startB60Watchdog(config: SweepConfig, bytes: ByteArray) {
        val v0 = version
        // The firmware bug produces no usable datapoints at all, while a working sweep
        // delivers its first usable point within about a second even at 15 Hz IFBW. A
        // shorter fixed window avoids aborting a slow low-IFBW sweep with a false SA reset.
        val watchdogDelayMs = 8_000L
        watchdogJob = scope.launch {
            delay(watchdogDelayMs)
            if (!isActive) return@launch
            if (lastDataVersion == v0) return@launch
            emitLog("B60: 扫描未启动，执行 SA 模式复位…")
            Log.d("VnaB60", "watchdog: SA reset")
            link.send(Type.SPECTRUM_ANALYZER_SETTINGS, saPayload(), awaitAck = true, timeoutMs = 2000)
            delay(400)
            link.send(Type.SET_IDLE, ByteArray(0), awaitAck = true, timeoutMs = 1000)
            delay(300)
            if (!isActive) return@launch
            version++
            val v1 = version
            assembler = SweepAssembler(config, onSweep = { _sweeps.tryEmit(it) }, onPartial = { _sweeps.tryEmit(it) })
            val ok = link.send(Type.SWEEP_SETTINGS, bytes, awaitAck = true, timeoutMs = 4000)
            lastDataVersion = -1
            Log.d("VnaB60", "watchdog reconfigure=" + ok)
            delay(3000)
            if (isActive && lastDataVersion != v1) {
                emitLog("B60 自动恢复失败：请重新插拔设备，或先切到 SA 模式再切回 VNA")
            }
        }
    }

    private fun saPayload(): ByteArray {
        val bb = ByteBuffer.allocate(34).order(ByteOrder.LITTLE_ENDIAN)
        bb.putLong(500_000_000L)
        bb.putLong(501_000_000L)
        bb.putInt(10_000)
        bb.putShort(10)
        bb.put(0x80.toByte()) // applyReceiverCorrection
        bb.put(0) // tracking generator / sync bits
        bb.putLong(0)
        bb.putShort(0)
        return bb.array()
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