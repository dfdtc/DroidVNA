/*
 * LibreVNA Mobile - native Android client for LibreVNA / Harogic B60.
 * Copyright (C) 2026 The LibreVNA Mobile Authors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.librevna.mobile.usb

import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import com.librevna.mobile.model.VnaDeviceInfo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Owns the bulk endpoints of the LibreVNA's vendor-specific USB interface (interface 0):
 *   0x01 OUT data, 0x81 IN data, 0x82 IN log.
 */
class LibrevnaUsbLink(
    private val connection: UsbDeviceConnection,
    private val usbInterface: UsbInterface,
    private val dataIn: UsbEndpoint,
    private val dataOut: UsbEndpoint,
    private val logIn: UsbEndpoint?,
    private val onFrame: (type: Int, payload: ByteArray) -> Unit,
    private val onLogLine: (String) -> Unit,
    private val onError: (String) -> Unit,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val decoder = PacketDecoder(onPacket = ::handlePacket)
    private val ackChannel = Channel<Int>(Channel.UNLIMITED)
    private val sendMutex = Mutex()

    @Volatile private var running = true
    @Volatile private var infoDeferred: CompletableDeferred<VnaDeviceInfo?>? = null

    fun start() {
        scope.launch { readLoop() }
        if (logIn != null) scope.launch { logLoop(logIn) }
    }

    private fun readLoop() {
        val buffer = ByteArray(16 * 1024)
        while (running) {
            val read = try {
                connection.bulkTransfer(dataIn, buffer, buffer.size, 500)
            } catch (e: Exception) {
                if (running) onError("USB read failed: " + (e.message ?: e.javaClass.simpleName))
                -1
            }
            if (read > 0) {
                try {
                    decoder.feed(buffer, 0, read)
                } catch (e: Exception) {
                    onError("Packet decode failed: " + (e.message ?: "unknown"))
                }
            }
        }
    }

    private fun logLoop(endpoint: UsbEndpoint) {
        val buffer = ByteArray(2048)
        val line = StringBuilder()
        while (running) {
            val read = try {
                connection.bulkTransfer(endpoint, buffer, buffer.size, 500)
            } catch (e: Exception) {
                -1
            }
            if (read > 0) {
                for (i in 0 until read) {
                    val c = buffer[i].toInt() and 0xFF
                    if (c == 10) {
                        if (line.isNotEmpty()) onLogLine(line.toString())
                        line.setLength(0)
                    } else if (c != 13 && line.length < 4096) {
                        line.append(c.toChar())
                    }
                }
            }
        }
    }

    private fun handlePacket(type: Int, payload: ByteArray) {
        when (type) {
            Protocol.Type.ACK -> ackChannel.trySend(Protocol.Type.ACK)
            Protocol.Type.NACK -> ackChannel.trySend(Protocol.Type.NACK)
            Protocol.Type.DEVICE_INFO -> {
                val info = Protocol.parseDeviceInfo(payload)
                infoDeferred?.complete(info)
            }
            else -> onFrame(type, payload)
        }
    }

    suspend fun requestDeviceInfo(): VnaDeviceInfo? {
        val deferred = CompletableDeferred<VnaDeviceInfo?>()
        infoDeferred = deferred
        val sent = send(Protocol.Type.REQUEST_DEVICE_INFO, ByteArray(0), true, 2500)
        val result = if (sent) withTimeoutOrNull(2500) { deferred.await() } else null
        infoDeferred = null
        return result
    }

    suspend fun send(
        type: Int,
        payload: ByteArray,
        awaitAck: Boolean = true,
        timeoutMs: Long = 2500,
    ): Boolean = withContext(Dispatchers.IO) {
        sendMutex.withLock {
            if (!running) return@withLock false
            if (awaitAck) {
                while (ackChannel.tryReceive().isSuccess) {
                    // drop stale acknowledgements
                }
            }
            val packet = Protocol.encode(type, payload)
            var offset = 0
            while (offset < packet.size) {
                val chunk = if (offset == 0) packet else packet.copyOfRange(offset, packet.size)
                val written = try {
                    connection.bulkTransfer(dataOut, chunk, chunk.size, 1000)
                } catch (e: Exception) {
                    onError("USB write failed: " + (e.message ?: e.javaClass.simpleName))
                    -1
                }
                if (written <= 0) {
                    onError("USB write timed out")
                    return@withLock false
                }
                offset += written
            }
            if (!awaitAck) return@withLock true
            val ack = withTimeoutOrNull(timeoutMs) {
                while (true) {
                    when (ackChannel.receive()) {
                        Protocol.Type.ACK -> return@withTimeoutOrNull true
                        Protocol.Type.NACK -> return@withTimeoutOrNull false
                    }
                }
                @Suppress("UNREACHABLE_CODE")
                false
            }
            ack ?: false
        }
    }

    fun close() {
        if (!running) return
        running = false
        scope.cancel()
        try {
            connection.releaseInterface(usbInterface)
        } catch (_: Exception) {
        }
        try {
            connection.close()
        } catch (_: Exception) {
        }
    }
}