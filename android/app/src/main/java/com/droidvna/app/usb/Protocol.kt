/*
 * DroidVNA - native Android client for LibreVNA / Harogic B60.
 * Copyright (C) 2026 The DroidVNA Authors
 * SPDX-License-Identifier: GPL-3.0-only
 *
 * Based in part on LibreVNA-GUI (https://github.com/jankae/LibreVNA),
 * Copyright (C) Jan Käberich and contributors, GPL-3.0.
 */

package com.droidvna.app.usb

import com.droidvna.app.model.Complex
import com.droidvna.app.model.SweepConfig
import com.droidvna.app.model.VnaDeviceInfo
import com.droidvna.app.model.VnaStatus
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32

/**
 * Wire format of the LibreVNA USB protocol (protocol version 14).
 *
 * Frame: 0x5A | uint16 length (including header and CRC) | uint8 type | payload | uint32 CRC32.
 * VNA datapoints carry a CRC of zero for throughput reasons.
 */
object Protocol {
    const val VERSION = 14
    const val HEADER: Byte = 0x5A

    object Type {
        const val SWEEP_SETTINGS = 2
        const val SPECTRUM_ANALYZER_SETTINGS = 13
        const val DEVICE_INFO = 5
        const val ACK = 7
        const val NACK = 10
        const val DEVICE_STATUS = 25
        const val REQUEST_DEVICE_CONFIGURATION = 23
        const val DEVICE_CONFIGURATION = 24
        const val REQUEST_DEVICE_STATUS = 26
        const val REQUEST_DEVICE_INFO = 15
        const val SET_IDLE = 20
        const val VNA_DATAPOINT = 27
        const val INITIATE_SWEEP = 32
        const val START_STATUS_UPDATES = 31
        const val STOP_STATUS_UPDATES = 30
    }

    fun crc32(data: ByteArray, length: Int): Int {
        val crc = CRC32()
        crc.update(data, 0, length)
        return crc.value.toInt()
    }

    fun encode(type: Int, payload: ByteArray): ByteArray {
        val total = 4 + payload.size + 4
        require(total <= 0xFFFF) { "packet too large" }
        val out = ByteArray(total)
        out[0] = HEADER
        out[1] = (total and 0xFF).toByte()
        out[2] = ((total shr 8) and 0xFF).toByte()
        out[3] = type.toByte()
        payload.copyInto(out, 4)
        if (type != Type.VNA_DATAPOINT) {
            val crc = crc32(out, total - 4)
            out[total - 4] = (crc and 0xFF).toByte()
            out[total - 3] = ((crc shr 8) and 0xFF).toByte()
            out[total - 2] = ((crc shr 16) and 0xFF).toByte()
            out[total - 1] = ((crc shr 24) and 0xFF).toByte()
        }
        return out
    }

    /**
     * SweepSettings is a packed C struct of 31 bytes. The bitfield bytes are encoded from the
     * least-significant bit, matching GCC's ARM ABI used by the firmware.
     */
    fun encodeSweepSettings(config: SweepConfig, standby: Boolean): ByteArray {
        val bb = ByteBuffer.allocate(31).order(ByteOrder.LITTLE_ENDIAN)
        bb.putLong(config.startHz)
        bb.putLong(config.stopHz)
        bb.putShort(config.points.toShort())
        bb.putInt(config.ifbwHz)
        bb.putShort((config.powerDbm * 100).toInt().toShort())

        var flags = 0
        if (standby) flags = flags or 0x01
        // Match the desktop GUI defaults: suppress invalid peaks and keep the source power fixed
        // across the sweep when start and stop power are equal (the app always sends equal power).
        flags = flags or 0x04
        flags = flags or 0x08
        if (config.logSweep) flags = flags or 0x10
        bb.put(flags.toByte())

        val stages = if (config.twoPort) 1 else 0
        var stageField = (stages and 0x07)
        stageField = stageField or ((0 and 0x07) shl 3)       // port1 stage
        stageField = stageField or ((1 and 0x07) shl 6)       // port2 stage
        bb.putShort(stageField.toShort())

        bb.putShort((config.powerDbm * 100).toInt().toShort()) // excitation stop = start
        bb.putShort(0) // dwell time
        return bb.array()
    }

    fun parseDeviceInfo(payload: ByteArray): VnaDeviceInfo? {
        if (payload.size < 57) return null
        val bb = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
        val protocol = bb.short.toInt() and 0xFFFF
        val major = bb.get().toInt() and 0xFF
        val minor = bb.get().toInt() and 0xFF
        val patch = bb.get().toInt() and 0xFF
        val hardware = bb.get().toInt() and 0xFF
        val revision = (bb.get().toInt() and 0xFF).toChar()
        val minFreq = bb.long
        val maxFreq = bb.long
        val minBw = bb.int
        val maxBw = bb.int
        val maxPoints = bb.short.toInt() and 0xFFFF
        val minPower = bb.short / 100.0
        val maxPower = bb.short / 100.0
        bb.int // min RBW
        bb.int // max RBW
        bb.get() // max amplitude points
        bb.long // max frequency (harmonic)
        val numPorts = bb.get().toInt() and 0xFF
        val maxDwell = bb.short.toInt() and 0xFFFF
        return VnaDeviceInfo(
            protocolVersion = protocol,
            firmware = major.toString() + "." + minor + "." + patch,
            hardwareVersion = hardware,
            hardwareRevision = revision,
            hardwareName = hardwareName(hardware),
            minFreqHz = if (minFreq <= 0) 5_000L else minFreq,
            maxFreqHz = if (maxFreq <= 0) 6_000_000_000L else maxFreq,
            minIfbwHz = if (minBw <= 0) 1 else minBw,
            maxIfbwHz = if (maxBw <= 0) 200_000 else maxBw,
            maxPoints = if (maxPoints <= 0) 1001 else maxPoints,
            minPowerDbm = minPower,
            maxPowerDbm = maxPower,
            numPorts = if (numPorts <= 0) 2 else numPorts,
            maxDwellUs = maxDwell,
        )
    }

    fun parseDeviceStatus(payload: ByteArray, hardwareVersion: Int): VnaStatus {
        if (payload.isEmpty()) return VnaStatus()
        val flags = payload[0].toInt() and 0xFF
        fun bit(n: Int) = (flags and (1 shl n)) != 0
        return when (hardwareVersion) {
            0x01 -> VnaStatus(
                sourceLocked = bit(3),
                loLocked = bit(4),
                adcOverload = bit(5),
                unlevel = bit(6),
                extRefInUse = bit(1),
                temperatureC = if (payload.size >= 4) (payload[1].toInt() and 0xFF).toDouble() else null,
                secondaryTemperatureC = if (payload.size >= 4) (payload[2].toInt() and 0xFF).toDouble() else null,
            )
            0xD0 -> VnaStatus(
                sourceLocked = bit(3),
                loLocked = bit(4),
                adcOverload = bit(5),
                unlevel = bit(6),
                extRefInUse = bit(1),
                temperatureC = if (payload.size >= 2) (payload[1].toInt() and 0xFF).toDouble() else null,
                supplyVoltage = if (payload.size >= 5) readU16(payload, 2) / 1000.0 else null,
                supplyCurrent = if (payload.size >= 7) readU16(payload, 4) / 1000.0 else null,
            )
            0xFE -> VnaStatus(
                sourceLocked = bit(0),
                loLocked = bit(1),
                adcOverload = bit(2),
                unlevel = bit(3),
                temperatureC = if (payload.size >= 2) (payload[1].toInt() and 0xFF).toDouble() else null,
                secondaryTemperatureC = if (payload.size >= 4) readU16(payload, 2) / 100.0 else null,
            )
            else -> VnaStatus(
                sourceLocked = bit(0),
                loLocked = bit(1),
                adcOverload = bit(2),
                unlevel = bit(3),
                temperatureC = if (payload.size >= 2) (payload[1].toInt() and 0xFF).toDouble() else null,
            )
        }
    }

    private fun readU16(data: ByteArray, offset: Int): Int =
        (data[offset].toInt() and 0xFF) or ((data[offset + 1].toInt() and 0xFF) shl 8)

    fun hardwareName(hardwareVersion: Int): String = when (hardwareVersion) {
        0x01 -> "LibreVNA V1"
        0xD0 -> "LibreVNA-H (HAR0)"
        0xE0 -> "LibreVNA-SAP1"
        0xE1 -> "LibreVNA-RSG1"
        0xFD -> "LibreVNA-FD"
        0xFE -> "LibreVNA-P2"
        0xFF -> "LibreVNA-PT"
        else -> String.format("LibreVNA (HW 0x%02X)", hardwareVersion)
    }
}

data class DpValue(val re: Double, val im: Double, val descriptor: Int) {
    val stage: Int get() = (descriptor ushr 5) and 0x07
    val mask: Int get() = descriptor and 0x1F
    fun complex() = Complex(re, im)
}

class Datapoint(
    val frequencyHz: Long,
    val cdbm: Int,
    val pointNum: Int,
    val values: List<DpValue>,
)

object DatapointCodec {
    fun parse(payload: ByteArray): Datapoint? {
        if (payload.size < 12) return null
        val count = (payload.size - 12) / 9
        if (count <= 0 || 12 + count * 9 != payload.size) return null
        val bb = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
        val frequency = bb.long
        val cdbm = bb.short.toInt()
        val point = bb.short.toInt() and 0xFFFF
        val real = DoubleArray(count)
        val imag = DoubleArray(count)
        for (i in 0 until count) real[i] = bb.float.toDouble()
        for (i in 0 until count) imag[i] = bb.float.toDouble()
        val descr = ByteArray(count)
        bb.get(descr)
        val values = ArrayList<DpValue>(count)
        for (i in 0 until count) values.add(DpValue(real[i], imag[i], descr[i].toInt() and 0xFF))
        return Datapoint(frequency, cdbm, point, values)
    }
}

/** Incremental parser that tolerates out-of-order bytes and partial frames. */
class PacketDecoder(
    private val onPacket: (type: Int, payload: ByteArray) -> Unit,
    private val onInvalid: (() -> Unit)? = null,
) {
    private var buffer = ByteArray(16 * 1024)
    private var length = 0

    fun feed(data: ByteArray, offset: Int, count: Int) {
        if (count <= 0) return
        ensure(length + count)
        System.arraycopy(data, offset, buffer, length, count)
        length += count
        parse()
    }

    private fun parse() {
        while (true) {
            var start = 0
            while (start < length && buffer[start] != Protocol.HEADER) start++
            if (start > 0) consume(start)
            if (length < 4) return
            val total = (buffer[1].toInt() and 0xFF) or ((buffer[2].toInt() and 0xFF) shl 8)
            if (total < 7 || total > 2048) {
                consume(1)
                onInvalid?.invoke()
                continue
            }
            if (length < total) return
            val type = buffer[3].toInt() and 0xFF
            val crc = (buffer[total - 4].toInt() and 0xFF) or
                ((buffer[total - 3].toInt() and 0xFF) shl 8) or
                ((buffer[total - 2].toInt() and 0xFF) shl 16) or
                ((buffer[total - 1].toInt() and 0xFF) shl 24)
            val valid = if (type == Protocol.Type.VNA_DATAPOINT) crc == 0 else crc == Protocol.crc32(buffer, total - 4)
            if (!valid) {
                consume(1)
                onInvalid?.invoke()
                continue
            }
            val payload = buffer.copyOfRange(4, total - 4)
            onPacket(type, payload)
            consume(total)
        }
    }

    private fun consume(count: Int) {
        if (count <= 0) return
        System.arraycopy(buffer, count, buffer, 0, length - count)
        length -= count
    }

    private fun ensure(required: Int) {
        if (required <= buffer.size) return
        var size = buffer.size
        while (size < required) size *= 2
        buffer = buffer.copyOf(size.coerceAtMost(256 * 1024))
    }
}