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
 *
 * The Harogic VNB-60 speaks the same protocol but its `SweepSettings` payload is **32** bytes
 * instead of the 31 an upstream LibreVNA uses (one vendor extension byte, `extTrigger`). See
 * [SweepSettings] for why that byte must always be sent explicitly.
 */
object Protocol {
    const val VERSION = 14
    const val HEADER: Byte = 0x5A

    /**
     * Packet types of protocol version 14 (`Protocol::PacketType`).
     *
     * The list is complete so that reserved types stay reserved and cannot accidentally be
     * renumbered; entries marked "device -> host" describe frames the device sends.
     */
    object Type {
        const val SWEEP_SETTINGS = 2 // host -> device, 32 B (31 B on upstream LibreVNA)
        const val MANUAL_STATUS = 3 // device -> host
        const val MANUAL_CONTROL = 4
        const val DEVICE_INFO = 5 // device -> host, 57 B
        /** Writes the on-device flash. Never send this by accident. */
        const val FIRMWARE_PACKET = 6
        const val ACK = 7 // device -> host; global, carries no request id
        /** Erases the on-device flash. Never send this by accident. */
        const val CLEAR_FLASH = 8
        const val PERFORM_FIRMWARE_UPDATE = 9
        const val NACK = 10 // device -> host
        const val REFERENCE = 11
        /** Rejected by the VNB-60 firmware ("Generator requested but disabled"). */
        const val GENERATOR = 12
        /** Rejected by the VNB-60 firmware ("SA requested but disabled"). */
        const val SPECTRUM_ANALYZER_SETTINGS = 13
        const val SPECTRUM_ANALYZER_RESULT = 14 // device -> host
        const val REQUEST_DEVICE_INFO = 15
        const val REQUEST_SOURCE_CAL = 16
        const val REQUEST_RECEIVER_CAL = 17
        const val SOURCE_CAL_POINT = 18
        const val RECEIVER_CAL_POINT = 19
        const val SET_IDLE = 20
        const val REQUEST_FREQUENCY_CORRECTION = 21
        const val FREQUENCY_CORRECTION = 22
        const val REQUEST_DEVICE_CONFIGURATION = 23
        const val DEVICE_CONFIGURATION = 24
        const val DEVICE_STATUS = 25 // device -> host, 6 B on the VNB-60 (VD0 variant)
        const val REQUEST_DEVICE_STATUS = 26
        const val VNA_DATAPOINT = 27 // device -> host, CRC is always zero
        /**
         * Reserved for external triggering together with [SweepSettings.extTrigger].
         * Harogic has not documented the semantics yet, so these are only declared, never sent.
         */
        const val SET_TRIGGER = 28 // device -> host
        const val CLEAR_TRIGGER = 29 // device -> host
        const val STOP_STATUS_UPDATES = 30
        const val START_STATUS_UPDATES = 31
        const val INITIATE_SWEEP = 32
        const val PERFORM_ACTION = 33
        const val RESET_DEVICE_CONFIGURATION = 34
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
     * Builds the 32-byte `SweepSettings` payload of a sweep configuration.
     *
     * [extTrigger] is the Harogic extension byte; it stays [SweepSettings.EXT_TRIGGER_OFF] until
     * external triggering is actually implemented (Harogic has not defined the semantics yet).
     */
    fun encodeSweepSettings(
        config: SweepConfig,
        standby: Boolean,
        extTrigger: Int = SweepSettings.EXT_TRIGGER_OFF,
    ): ByteArray = SweepSettings.fromConfig(config, standby, extTrigger).encode()

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

    /**
     * Decodes a `DeviceStatus` packet for the device's `hardware_version` union branch.
     *
     * The VNB-60 reports hardware 0xD0 (`VD0`, 6 bytes): bit0 extRefAvailable, bit1 extRefInUse,
     * bit2 FPGA_configured, bit3 source_locked, bit4 LO_locked, bit5 ADC_overload, bit6 unlevel;
     * then temp_MCU (u8, °C), supply_voltage (u16, mV) and supply_current (u16, mA).
     * A typical healthy frame is `1c 29 57 13 25 02`: FPGA configured, both PLLs locked, 41 °C,
     * 4.951 V, 549 mA.
     */
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
                extRefAvailable = bit(0),
                extRefInUse = bit(1),
                fpgaConfigured = bit(2),
                temperatureC = if (payload.size >= 4) (payload[1].toInt() and 0xFF).toDouble() else null,
                secondaryTemperatureC = if (payload.size >= 4) (payload[2].toInt() and 0xFF).toDouble() else null,
            )
            0xD0 -> VnaStatus(
                sourceLocked = bit(3),
                loLocked = bit(4),
                adcOverload = bit(5),
                unlevel = bit(6),
                extRefAvailable = bit(0),
                extRefInUse = bit(1),
                fpgaConfigured = bit(2),
                temperatureC = if (payload.size >= 2) (payload[1].toInt() and 0xFF).toDouble() else null,
                supplyVoltage = if (payload.size >= 4) readU16(payload, 2) / 1000.0 else null,
                supplyCurrent = if (payload.size >= 6) readU16(payload, 4).toDouble() else null,
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

/**
 * Host -> device sweep configuration (`Protocol::SweepSettings`, packet type 2).
 *
 * Layout, packed, little endian (offsets in bytes):
 * ```
 *  0   8  f_start                  Hz
 *  8   8  f_stop                   Hz
 * 16   2  points
 * 18   4  if_bandwidth             Hz
 * 22   2  cdbm_excitation_start    1/100 dBm
 * 24   1  flags                    see [Flags]
 * 25   2  stage word               see [stageWord]
 * 27   2  cdbm_excitation_stop     1/100 dBm
 * 29   2  dwell_time               us
 * 31   1  extTrigger               Harogic extension, see [extTrigger]
 * ```
 *
 * ### Why the 32nd byte always gets sent
 *
 * Upstream LibreVNA stops at offset 30 (31 bytes). The VNB-60 firmware instead reads a 32-byte
 * struct, and it decodes packets into a *shared, statically reused* receive buffer that is only
 * overwritten by the bytes actually received -- so a 31-byte packet leaves `extTrigger` holding
 * whatever a previous packet put there. A leftover non-zero value makes the firmware wait for
 * that many external trigger events per point, which looks exactly like "the device hangs":
 * residue 0 gives a normal continuous sweep, 1 drops to ~11 points, 0xFF to none at all.
 *
 * Sending the full 32 bytes with an explicit `extTrigger` removes that dependency on buffer
 * history. Sending 32 bytes to an upstream LibreVNA is harmless: its decoder copies the payload
 * into a union that is much larger than the 31-byte struct, so the extra byte lands in padding.
 */
class SweepSettings(
    val startHz: Long,
    val stopHz: Long,
    val points: Int,
    val ifbwHz: Int,
    val excitationStartCdBm: Int,
    val flags: Int,
    val stageWord: Int,
    val excitationStopCdBm: Int,
    val dwellTimeUs: Int,
    /**
     * Harogic extension byte: `0` = free-running continuous sweep, a non-zero value = wait for
     * that many trigger events per point.
     *
     * **Not implemented.** The exact meaning of non-zero values is undocumented (measured
     * behaviour only: 0 -> continuous, 1 -> ~11 points, 255 -> no data), so production code must
     * leave this at [EXT_TRIGGER_OFF]. The field and the two [Protocol.Type] trigger packets
     * ([Protocol.Type.SET_TRIGGER], [Protocol.Type.CLEAR_TRIGGER]) are reserved for the feature.
     */
    val extTrigger: Int = EXT_TRIGGER_OFF,
) {
    init {
        require(extTrigger in 0..EXT_TRIGGER_MAX) {
            "extTrigger must be 0..$EXT_TRIGGER_MAX, was $extTrigger"
        }
        require(points in 1..0xFFFF) { "points must fit into an unsigned 16-bit field, was $points" }
    }

    /** True when the device would be asked to wait for external trigger events. */
    val triggersEnabled: Boolean get() = extTrigger != EXT_TRIGGER_OFF

    fun encode(): ByteArray {
        val bb = ByteBuffer.allocate(SIZE).order(ByteOrder.LITTLE_ENDIAN)
        bb.putLong(startHz)
        bb.putLong(stopHz)
        bb.putShort(points.toShort())
        bb.putInt(ifbwHz)
        bb.putShort(excitationStartCdBm.toShort())
        bb.put(flags.toByte())
        bb.putShort(stageWord.toShort())
        bb.putShort(excitationStopCdBm.toShort())
        bb.putShort(dwellTimeUs.toShort())
        bb.put(extTrigger.toByte())
        return bb.array()
    }

    override fun toString(): String =
        "SweepSettings(${startHz}..${stopHz} Hz, $points pts, IFBW $ifbwHz Hz, " +
            "power ${excitationStartCdBm / 100.0} dBm, flags 0x${flags.toString(16)}, " +
            "stages 0x${stageWord.toString(16)}, dwell ${dwellTimeUs} us, extTrigger $extTrigger)"

    /** Bits of the `flags` byte at offset 24. */
    object Flags {
        const val STANDBY = 0x01
        const val SYNC_MASTER = 0x02
        const val SUPPRESS_PEAKS = 0x04
        const val FIXED_POWER_SETTING = 0x08
        const val LOG_SWEEP = 0x10

        /** Bits 5-6, `syncMode`, see [SyncMode]. */
        const val SYNC_MODE_SHIFT = 5
        const val SYNC_MODE_MASK = 0x03 shl SYNC_MODE_SHIFT
    }

    /**
     * Values of the `syncMode` bitfield in [Flags].
     *
     * [TRIGGER] is the second half of the external-trigger reservation: it is declared, but the
     * app only ever sends [NONE] (0) until Harogic documents `extTrigger` and the trigger packets.
     */
    object SyncMode {
        const val NONE = 0
        const val USB = 1
        const val EXTERNAL_REFERENCE = 2
        /** Reserved: trigger synchronization, "not supported yet by hardware" upstream. */
        const val TRIGGER = 3
    }

    companion object {
        /** Wire size of the packed struct the VNB-60 expects. */
        const val SIZE = 32

        /** Free-running continuous sweep; the only supported value so far. */
        const val EXT_TRIGGER_OFF = 0

        /** Largest value the extension byte can hold (0xFF pauses the sweep completely). */
        const val EXT_TRIGGER_MAX = 0xFF

        /**
         * Packs the stage word at offset 25: three bits per entry, LSB first.
         * A plain two-port sweep uses `stages = 1, port1Stage = 0, port2Stage = 1`.
         */
        fun stageWord(stages: Int, port1Stage: Int = 0, port2Stage: Int = 0, port3Stage: Int = 0, port4Stage: Int = 0): Int {
            fun field(value: Int, index: Int): Int = (value and 0x07) shl (index * 3)
            return field(stages, 0) or field(port1Stage, 1) or field(port2Stage, 2) or
                field(port3Stage, 3) or field(port4Stage, 4)
        }

        /**
         * Maps the app's [SweepConfig] onto the wire structure.
         *
         * The flag defaults match the desktop GUI: suppress invalid peaks and keep the source
         * power fixed across the sweep when start and stop power are equal (the app always sends
         * equal power).
         */
        fun fromConfig(
            config: SweepConfig,
            standby: Boolean,
            extTrigger: Int = EXT_TRIGGER_OFF,
        ): SweepSettings {
            var flags = Flags.SUPPRESS_PEAKS or Flags.FIXED_POWER_SETTING
            if (standby) flags = flags or Flags.STANDBY
            if (config.logSweep) flags = flags or Flags.LOG_SWEEP
            val cdBm = (config.powerDbm * 100).toInt()
            return SweepSettings(
                startHz = config.startHz,
                stopHz = config.stopHz,
                points = config.points,
                ifbwHz = config.ifbwHz,
                excitationStartCdBm = cdBm,
                flags = flags,
                stageWord = stageWord(
                    stages = if (config.twoPort) 1 else 0,
                    port1Stage = 0,
                    port2Stage = 1,
                ),
                excitationStopCdBm = cdBm,
                dwellTimeUs = 0,
                extTrigger = extTrigger,
            )
        }
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
            // A frame is at least 8 bytes (4 header + 0 payload + 4 CRC); the upper bound keeps a
            // corrupted length from growing the buffer, upstream rejects anything above
            // 2 * sizeof(PacketInfo).
            if (total < 8 || total > MAX_FRAME) {
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

    private companion object {
        /** Largest accepted frame length; a datapoint frame is at most 12 + 32 * 9 + 8 bytes. */
        const val MAX_FRAME = 2048
    }
}