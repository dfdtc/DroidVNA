/*
 * LibreVNA Mobile - native Android client for LibreVNA / Harogic B60.
 * Copyright (C) 2026 The LibreVNA Mobile Authors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.librevna.mobile.usb

import android.content.Context
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import com.librevna.mobile.model.CalKitStandard
import com.librevna.mobile.model.CalStandardKind
import com.librevna.mobile.model.Complex
import com.librevna.mobile.model.EcalCoefficients
import java.io.Closeable
import java.io.IOException

data class EcalSnapshot(
    val idn: String,
    val firmware: String,
    val ports: Int,
    val coefficientSets: List<String>,
    val temperatureC: Double?,
    val stable: Boolean?,
    val heaterPowerW: Double?,
)

enum class EcalStandard { OPEN, SHORT, LOAD, THROUGH, NONE }

/**
 * Minimal SCPI driver for LibreCAL / Harogic ECB-60 (VID:PID 1209:4122).
 *
 * SCPI lives on the vendor-specific interface (class 0xFF), normally interface 2. The CDC and
 * mass-storage interfaces of this composite device must not be used for SCPI.
 */
class EcalDevice private constructor(
    private val connection: UsbDeviceConnection,
    private val iface: UsbInterface,
    private val epOut: UsbEndpoint,
    private val epIn: UsbEndpoint,
    val serial: String,
) : Closeable {
    private val rx = StringBuilder()
    private val lock = Any()

    @Volatile private var closed = false

    fun refreshInfo(): EcalSnapshot {
        val idn = query("*IDN?")
        val firmware = query(":FIRMWARE?")
        val ports = query(":PORTS?").toIntOrNull() ?: 0
        val sets = query(":COEFF:LIST?")
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        val temperature = query(":TEMP?", 1000).toDoubleOrNull()
        val stableText = query(":TEMPerature:STABLE?", 1000).trim().uppercase()
        val stable = when (stableText) {
            "TRUE" -> true
            "FALSE" -> false
            else -> null
        }
        val heater = query(":HEATER:POWER?", 700).toDoubleOrNull()
        return EcalSnapshot(idn, firmware, ports, sets, temperature, stable, heater)
    }

    fun temperature(): Double? = query(":TEMP?", 1000).toDoubleOrNull()

    fun stable(): Boolean? = when (query(":TEMPerature:STABLE?", 1000).trim().uppercase()) {
        "TRUE" -> true
        "FALSE" -> false
        else -> null
    }

    fun setStandard(port: Int, standard: EcalStandard, throughPort: Int = 0): Boolean {
        val cmd = when (standard) {
            EcalStandard.OPEN -> ":PORT $port OPEN"
            EcalStandard.SHORT -> ":PORT $port SHORT"
            EcalStandard.LOAD -> ":PORT $port LOAD"
            EcalStandard.THROUGH -> ":PORT $port THROUGH $throughPort"
            EcalStandard.NONE -> ":PORT $port NONE"
        }
        return command(cmd)
    }

    fun setAllNone(ports: Int) {
        for (p in 1..ports) {
            runCatching { command(":PORT $p NONE", 700) }
        }
    }

    /**
     * Read all coefficients required for a 2-port SOLT calibration. Keys are P1_OPEN,
     * P1_SHORT, P1_LOAD, P2_OPEN, P2_SHORT, P2_LOAD and P12_THROUGH.
     */
    fun readCoefficientSet(
        setName: String,
        maxPorts: Int,
        onProgress: (Double) -> Unit = {},
    ): Map<String, CalKitStandard> {
        val ports = maxPorts.coerceIn(1, 2)
        data class Spec(val name: String, val kind: CalStandardKind)
        val specs = ArrayList<Spec>()
        for (p in 1..ports) {
            specs.add(Spec("P${p}_OPEN", CalStandardKind.OPEN))
            specs.add(Spec("P${p}_SHORT", CalStandardKind.SHORT))
            specs.add(Spec("P${p}_LOAD", CalStandardKind.LOAD))
        }
        if (ports >= 2) specs.add(Spec("P12_THROUGH", CalStandardKind.THROUGH))
        val result = LinkedHashMap<String, CalKitStandard>()
        specs.forEachIndexed { index, spec ->
            result[spec.name] = readCoefficient(setName, spec.name, spec.kind)
            onProgress((index + 1).toDouble() / specs.size)
        }
        return result
    }

    private fun readCoefficient(setName: String, param: String, kind: CalStandardKind): CalKitStandard {
        val count = query(":COEFF:NUM? $setName $param").toIntOrNull() ?: 0
        if (count <= 0) throw IOException("系数 $param 不存在或点数为 0")
        val streamed = tryReadStream(setName, param, count)
        if (streamed != null && streamed.size >= 2) {
            return EcalCoefficients.standard(param, kind, streamed)
        }
        // Older firmware or a failed stream: read point by point.
        val points = ArrayList<Pair<Long, List<Complex>>>(count)
        for (i in 0 until count) {
            val line = query(":COEFF:GET? $setName $param $i", 2500)
            val parsed = parseCommaLine(line) ?: throw IOException("无法解析 $param 第 $i 点：$line")
            points.add(parsed)
        }
        return EcalCoefficients.standard(param, kind, points)
    }

    private fun tryReadStream(
        setName: String,
        param: String,
        count: Int,
    ): List<Pair<Long, List<Complex>>>? {
        try {
            flush()
            send(":COEFF:GET? $setName $param")
        } catch (e: Exception) {
            return null
        }
        val points = ArrayList<Pair<Long, List<Complex>>>(count)
        var sawData = false
        repeat(count + 16) {
            val line = readLine(1500)
            if (line.isEmpty()) return if (points.size >= 2) points else null
            when {
                line.startsWith("ERROR") -> return null
                line.startsWith("START") -> Unit
                line.startsWith("END") -> return if (points.size >= 2) points else null
                line.startsWith("!") || line.startsWith("#") -> Unit
                else -> {
                    val parsed = EcalCoefficients.parseLine(line)
                    if (parsed == null) {
                        if (!sawData) return null
                    } else {
                        sawData = true
                        points.add(parsed)
                    }
                }
            }
        }
        return if (points.size >= 2) points else null
    }

    private fun parseCommaLine(line: String): Pair<Long, List<Complex>>? {
        val parts = line.trim().split(",").map { it.trim().toDoubleOrNull() ?: return null }
        if (parts.size < 3) return null
        val freq = parts[0]
        if (freq <= 0.0) return null
        val values = ArrayList<Complex>()
        var i = 1
        while (i + 1 < parts.size) {
            values.add(Complex(parts[i], parts[i + 1]))
            i += 2
        }
        if (values.size != 1 && values.size != 4) return null
        return (freq * 1e9).toLong() to values
    }

    fun query(command: String, timeoutMs: Int = 2000): String {
        if (closed) return ""
        flush()
        send(command)
        return readLine(timeoutMs)
    }

    fun command(command: String, timeoutMs: Int = 2000): Boolean {
        if (closed) return false
        flush()
        send(command)
        val line = readLine(timeoutMs)
        // The firmware replies with an empty line for a successful command. Some firmware
        // revisions answer "OK"; only an explicit error is treated as failure.
        return !line.trim().equals("ERROR", ignoreCase = true) && !line.contains("ERROR", ignoreCase = true)
    }

    private fun send(command: String) {
        val data = (command.trimEnd() + "\r\n").toByteArray(Charsets.US_ASCII)
        var attempt = 0
        while (attempt < 2) {
            val n = connection.bulkTransfer(epOut, data, data.size, 1500)
            if (n == data.size) return
            // CLEAR_FEATURE(ENDPOINT_HALT) on the OUT endpoint; UsbDeviceConnection has no
            // clearHalt() helper on all API levels.
            runCatching { connection.controlTransfer(0x02, 0x01, 0, epOut.address, null, 0, 0) }
            attempt++
        }
        throw IOException("发送 SCPI 命令失败：$command")
    }

    private fun readLine(timeoutMs: Int): String {
        val deadline = System.currentTimeMillis() + timeoutMs
        val buf = ByteArray(8192)
        synchronized(lock) {
            while (true) {
                val idx = rx.indexOf("\n")
                if (idx >= 0) {
                    val line = rx.substring(0, idx).trimEnd('\r')
                    rx.delete(0, idx + 1)
                    return line
                }
                val remaining = (deadline - System.currentTimeMillis()).toInt()
                if (remaining <= 0) return ""
                val n = connection.bulkTransfer(epIn, buf, buf.size, remaining)
                if (n > 0) {
                    rx.append(String(buf, 0, n, Charsets.US_ASCII))
                } else {
                    return ""
                }
            }
        }
    }

    private fun flush() {
        if (closed) return
        val buf = ByteArray(8192)
        while (connection.bulkTransfer(epIn, buf, buf.size, 20) > 0) {
            // discard stale replies
        }
        synchronized(lock) { rx.setLength(0) }
    }

    override fun close() {
        if (closed) return
        closed = true
        runCatching { connection.releaseInterface(iface) }
        runCatching { connection.close() }
    }

    companion object {
        const val VENDOR_ID = 0x1209
        const val PRODUCT_ID = 0x4122
        const val PRODUCT_ECB60 = "ECB-60"

        fun isEcal(device: UsbDevice): Boolean =
            device.vendorId == VENDOR_ID && device.productId == PRODUCT_ID

        fun open(context: Context, device: UsbDevice): EcalDevice {
            val manager = context.getSystemService(Context.USB_SERVICE) as UsbManager
            val connection = manager.openDevice(device)
                ?: throw IOException("无法打开电子校准件（权限或占用问题）")
            val candidates = (0 until device.interfaceCount).map { device.getInterface(it) }
            val iface = candidates.firstOrNull {
                it.interfaceClass == UsbConstants.USB_CLASS_VENDOR_SPEC
            } ?: candidates.getOrNull(2)
                ?: run {
                    connection.close()
                    throw IOException("电子校准件没有 vendor interface")
                }
            if (!connection.claimInterface(iface, true)) {
                connection.close()
                throw IOException("无法占用电子校准件 interface ${iface.id}（内核驱动？）")
            }
            val endpoints = (0 until iface.endpointCount).map { iface.getEndpoint(it) }
            val epOut = endpoints.firstOrNull {
                it.direction == UsbConstants.USB_DIR_OUT && it.type == UsbConstants.USB_ENDPOINT_XFER_BULK
            }
            val epIn = endpoints.firstOrNull {
                it.direction == UsbConstants.USB_DIR_IN && it.type == UsbConstants.USB_ENDPOINT_XFER_BULK
            }
            if (epOut == null || epIn == null) {
                connection.releaseInterface(iface)
                connection.close()
                throw IOException("电子校准件缺少 bulk 端点")
            }
            val serial = try {
                device.serialNumber ?: device.deviceName
            } catch (e: Exception) {
                device.deviceName
            }
            return EcalDevice(connection, iface, epOut, epIn, serial)
        }
    }
}