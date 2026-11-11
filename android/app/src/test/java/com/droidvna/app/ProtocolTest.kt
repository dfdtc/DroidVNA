/*
 * DroidVNA - native Android client for LibreVNA / Harogic B60.
 * Copyright (C) 2026 The DroidVNA Authors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.droidvna.app

import com.droidvna.app.model.SweepConfig
import com.droidvna.app.usb.Datapoint
import com.droidvna.app.usb.DpValue
import com.droidvna.app.usb.PacketDecoder
import com.droidvna.app.usb.Protocol
import com.droidvna.app.usb.SweepAssembler
import com.droidvna.app.usb.SweepSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ProtocolTest {
    @Test
    fun crcMatchesStandardCrc32() {
        val data = "123456789".toByteArray()
        assertEquals(0xCBF43926.toInt(), Protocol.crc32(data, data.size))
    }

    @Test
    fun encodeDecodeRoundTrip() {
        val payload = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
        val packet = Protocol.encode(Protocol.Type.SWEEP_SETTINGS, payload)
        assertEquals(Protocol.HEADER, packet[0])
        assertEquals(packet.size, (packet[1].toInt() and 0xFF) or ((packet[2].toInt() and 0xFF) shl 8))
        val received = ArrayList<ByteArray>()
        val decoder = PacketDecoder(onPacket = { _, p -> received.add(p) })
        decoder.feed(packet, 0, packet.size)
        assertEquals(1, received.size)
        assertTrue(payload.contentEquals(received[0]))
    }

    @Test
    fun decoderSkipsGarbageAndSplitFrames() {
        val payload = ByteArray(30) { it.toByte() }
        val packet = Protocol.encode(Protocol.Type.DEVICE_STATUS, payload)
        val stream = byteArrayOf(0x11, 0x22, 0x33) + packet + byteArrayOf(0x5A)
        val received = ArrayList<ByteArray>()
        val decoder = PacketDecoder(onPacket = { _, p -> received.add(p) })
        for (b in stream) decoder.feed(byteArrayOf(b), 0, 1)
        assertEquals(1, received.size)
        assertTrue(payload.contentEquals(received[0]))
    }

    @Test
    fun corruptCrcIsRejected() {
        val packet = Protocol.encode(Protocol.Type.DEVICE_STATUS, byteArrayOf(1, 2, 3))
        packet[packet.size - 1] = (packet[packet.size - 1].toInt() xor 0xFF).toByte()
        var count = 0
        val decoder = PacketDecoder(onPacket = { _, _ -> count++ })
        decoder.feed(packet, 0, packet.size)
        assertEquals(0, count)
    }

    @Test
    fun impossibleFrameLengthIsSkippedWithoutThrowing() {
        // A declared length of 7 is impossible (4 header + 4 CRC is the minimum); the decoder must
        // skip one byte and keep scanning instead of running into a negative payload range.
        val bogus = byteArrayOf(0x5A, 0x07, 0x00, 0x02, 0x00, 0x00, 0x00)
        val packet = Protocol.encode(Protocol.Type.DEVICE_STATUS, byteArrayOf(4, 5, 6))
        val received = ArrayList<ByteArray>()
        val decoder = PacketDecoder(onPacket = { _, p -> received.add(p) })
        val stream = bogus + packet
        decoder.feed(stream, 0, stream.size)
        assertEquals(1, received.size)
        assertTrue(byteArrayOf(4, 5, 6).contentEquals(received[0]))
    }

    @Test
    fun sweepSettingsLayout() {
        val config = SweepConfig(
            startHz = 100_000_000L,
            stopHz = 6_000_000_000L,
            points = 1001,
            ifbwHz = 10_000,
            powerDbm = -10.0,
            twoPort = true,
            logSweep = true,
        )
        val bytes = Protocol.encodeSweepSettings(config, standby = true)
        // The VNB-60 reads a 32-byte struct: the 31 upstream fields plus the Harogic
        // extTrigger extension byte. Sending 31 bytes is what lets stale trigger values survive.
        assertEquals(SweepSettings.SIZE, bytes.size)
        assertEquals(32, bytes.size)
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(100_000_000L, bb.long)
        assertEquals(6_000_000_000L, bb.long)
        assertEquals(1001, bb.short.toInt())
        assertEquals(10_000, bb.int)
        assertEquals(-1000, bb.short.toInt())
        assertEquals(
            SweepSettings.Flags.STANDBY or SweepSettings.Flags.SUPPRESS_PEAKS or
                SweepSettings.Flags.FIXED_POWER_SETTING or SweepSettings.Flags.LOG_SWEEP,
            bb.get().toInt() and 0xFF,
        )
        val stages = bb.short.toInt() and 0xFFFF
        assertEquals(1, stages and 0x7)
        assertEquals(0, (stages shr 3) and 0x7)
        assertEquals(1, (stages shr 6) and 0x7)
        assertEquals(-1000, bb.short.toInt())
        assertEquals(0, bb.short.toInt())
        // extTrigger: free-running continuous sweep, sent explicitly instead of left to chance.
        assertEquals(SweepSettings.EXT_TRIGGER_OFF, bb.get().toInt() and 0xFF)
        assertEquals(0, bb.remaining())
    }

    @Test
    fun sweepSettingsReservesExtTriggerByte() {
        val config = SweepConfig(startHz = 1_000_000L, stopHz = 2_000_000L, points = 11)
        val settings = SweepSettings.fromConfig(config, standby = false, extTrigger = 3)
        assertEquals(3, settings.encode()[31].toInt() and 0xFF)
        assertTrue(settings.triggersEnabled)
        assertFalse(SweepSettings.fromConfig(config, standby = false).triggersEnabled)
        // Only a single byte is reserved, so anything wider must be rejected instead of being
        // silently truncated into a wrong trigger count.
        assertThrows(IllegalArgumentException::class.java) {
            SweepSettings.fromConfig(config, standby = false, extTrigger = 0x100)
        }
        assertThrows(IllegalArgumentException::class.java) {
            SweepSettings.fromConfig(config.copy(points = 0), standby = false)
        }
    }

    @Test
    fun stageWordPacksThreeBitsPerEntry() {
        assertEquals(0x41, SweepSettings.stageWord(stages = 1, port2Stage = 1))
        assertEquals(0x00, SweepSettings.stageWord(stages = 0))
        // Five three-bit fields fill 15 bits; the last bit stays unused.
        assertEquals(0x7FFF, SweepSettings.stageWord(7, 7, 7, 7, 7))
    }

    @Test
    fun triggerReservationStaysOff() {
        assertEquals(0x60, SweepSettings.Flags.SYNC_MODE_MASK)
        assertEquals(0, SweepSettings.SyncMode.NONE)
        assertEquals(3, SweepSettings.SyncMode.TRIGGER)
        // A production configuration keeps syncMode 0 and extTrigger 0: the trigger feature is
        // reserved in the structure but must never be enabled by accident.
        val bytes = Protocol.encodeSweepSettings(SweepConfig(), standby = false)
        val flags = bytes[24].toInt() and 0xFF
        assertEquals(0, flags and SweepSettings.Flags.SYNC_MODE_MASK)
        assertEquals(SweepSettings.EXT_TRIGGER_OFF, bytes[31].toInt())
    }

    @Test
    fun packetTypesMatchProtocolVersion14() {
        // Values from Protocol::PacketType (v14); a renumbering would silently send the wrong
        // packet type to the device.
        val expected = mapOf(
            Protocol.Type.SWEEP_SETTINGS to 2,
            Protocol.Type.MANUAL_STATUS to 3,
            Protocol.Type.MANUAL_CONTROL to 4,
            Protocol.Type.DEVICE_INFO to 5,
            Protocol.Type.ACK to 7,
            Protocol.Type.CLEAR_FLASH to 8,
            Protocol.Type.NACK to 10,
            Protocol.Type.GENERATOR to 12,
            Protocol.Type.SPECTRUM_ANALYZER_SETTINGS to 13,
            Protocol.Type.REQUEST_DEVICE_INFO to 15,
            Protocol.Type.SET_IDLE to 20,
            Protocol.Type.REQUEST_DEVICE_CONFIGURATION to 23,
            Protocol.Type.DEVICE_CONFIGURATION to 24,
            Protocol.Type.DEVICE_STATUS to 25,
            Protocol.Type.REQUEST_DEVICE_STATUS to 26,
            Protocol.Type.VNA_DATAPOINT to 27,
            Protocol.Type.SET_TRIGGER to 28,
            Protocol.Type.CLEAR_TRIGGER to 29,
            Protocol.Type.STOP_STATUS_UPDATES to 30,
            Protocol.Type.START_STATUS_UPDATES to 31,
            Protocol.Type.INITIATE_SWEEP to 32,
            Protocol.Type.RESET_DEVICE_CONFIGURATION to 34,
        )
        for ((actual, value) in expected) assertEquals("packet type " + value, value, actual)
        assertEquals(Protocol.VERSION, 14)
    }

    @Test
    fun deviceStatusVd0DecodesHb60Frame() {
        // Real frame from a healthy VNB-60: FPGA configured, both PLLs locked, 41 °C, 4.951 V,
        // 549 mA.
        val payload = byteArrayOf(0x1C, 0x29, 0x57.toByte(), 0x13, 0x25, 0x02)
        val status = Protocol.parseDeviceStatus(payload, 0xD0)
        assertEquals(true, status.fpgaConfigured)
        assertEquals(true, status.sourceLocked)
        assertEquals(true, status.loLocked)
        assertEquals(false, status.adcOverload)
        assertEquals(41.0, status.temperatureC!!, 1e-9)
        assertEquals(4.951, status.supplyVoltage!!, 1e-9)
        assertEquals(549.0, status.supplyCurrent!!, 1e-9)
        assertEquals(false, status.extRefInUse)
    }

    @Test
    fun assemblerBuildsTwoPortSweep() {
        val config = SweepConfig(startHz = 1_000_000L, stopHz = 5_000_000L, points = 5, twoPort = true)
        var result: com.droidvna.app.model.SweepResult? = null
        val assembler = SweepAssembler(config, onSweep = { result = it })
        for (point in 0 until 5) {
            val f = config.frequencyAt(point)
            // stage 0: S11 = 0.5, S21 = 0.25
            assembler.accept(dp(f, point, 0, 0.5, 0.0, 0.25, 0.0, 1.0, 0.0))
            assertNull(result)
            // stage 1: S12 = 0.125, S22 = 0.75
            assembler.accept(dp(f, point, 1, 0.125, 0.0, 0.75, 0.0, 1.0, 0.0))
        }
        assertNotNull(result)
        val r = result!!
        assertEquals(5, r.size)
        assertEquals(0.5, r.s11[0].re, 1e-9)
        assertEquals(0.25, r.s21[2].re, 1e-9)
        assertEquals(0.125, r.s12[4].re, 1e-9)
        assertEquals(0.75, r.s22[1].re, 1e-9)
    }

    @Test
    fun assemblerHandlesCombinedStageDatapoint() {
        val config = SweepConfig(startHz = 1_000_000L, stopHz = 2_000_000L, points = 2, twoPort = true)
        var result: com.droidvna.app.model.SweepResult? = null
        val assembler = SweepAssembler(config, onSweep = { result = it })
        for (point in 0 until 2) {
            val values = listOf(
                DpValue(0.5, 0.0, 0x01),
                DpValue(0.25, 0.0, 0x02),
                DpValue(1.0, 0.0, 0x13),
                DpValue(0.2, 0.0, 0x21),
                DpValue(0.75, 0.0, 0x22),
                DpValue(1.0, 0.0, 0x33),
            )
            assembler.accept(Datapoint(config.frequencyAt(point), -1000, point, values))
        }
        assertNotNull(result)
        assertEquals(0.5, result!!.s11[0].re, 1e-9)
        assertEquals(0.25, result.s21[0].re, 1e-9)
        assertEquals(0.2, result.s12[0].re, 1e-9)
        assertEquals(0.75, result.s22[0].re, 1e-9)
    }

    @Test
    fun assemblerBuildsOnePortSweep() {
        val config = SweepConfig(startHz = 1_000_000L, stopHz = 2_000_000L, points = 2, twoPort = false)
        var result: com.droidvna.app.model.SweepResult? = null
        val assembler = SweepAssembler(config, onSweep = { result = it })
        for (point in 0 until 2) {
            assembler.accept(dp(config.frequencyAt(point), point, 0, 0.1, 0.2, 0.3, 0.4, 1.0, 0.0))
        }
        assertNotNull(result)
        assertEquals(0.1, result!!.s11[0].re, 1e-9)
        assertEquals(0.2, result.s11[0].im, 1e-9)
        assertEquals(0.3, result.s21[1].re, 1e-9)
        assertTrue(result.s12[0].re.isNaN())
    }

    @Test
    fun assemblerEmitsPartialSnapshotForSlowSweep() {
        val config = SweepConfig(startHz = 1_000_000L, stopHz = 4_000_000L, points = 4, twoPort = false)
        val partials = ArrayList<com.droidvna.app.model.SweepResult>()
        var completed: com.droidvna.app.model.SweepResult? = null
        val assembler = SweepAssembler(config, onSweep = { completed = it }, onPartial = { partials.add(it) })
        assembler.accept(dp(config.frequencyAt(0), 0, 0, 0.5, 0.0, 0.25, 0.0, 1.0, 0.0))
        // The assembler deliberately skips partials for the first 250 ms so fast sweeps
        // never replace a complete trace with a one-point snapshot.
        Thread.sleep(300)
        assembler.accept(dp(config.frequencyAt(1), 1, 0, 0.4, 0.0, 0.2, 0.0, 1.0, 0.0))
        assertEquals(1, partials.size)
        assertTrue(partials[0].isPartial)
        assertEquals(2, partials[0].measuredPoints)
        assertTrue(partials[0].s11[0].isFinite())
        assertTrue(partials[0].s11[3].re.isNaN())
        for (point in 2 until 4) {
            assembler.accept(dp(config.frequencyAt(point), point, 0, 0.1, 0.0, 0.2, 0.0, 1.0, 0.0))
        }
        assertNotNull(completed)
        assertEquals(false, completed!!.isPartial)
        assertEquals(0.5, completed!!.s11[0].re, 1e-9)
        assertEquals(4, completed!!.size)
    }

    private fun dp(
        frequency: Long,
        point: Int,
        stage: Int,
        p1re: Double, p1im: Double,
        p2re: Double, p2im: Double,
        refRe: Double, refIm: Double,
    ): Datapoint {
        val values = listOf(
            DpValue(p1re, p1im, (stage shl 5) or 0x01),
            DpValue(p2re, p2im, (stage shl 5) or 0x02),
            DpValue(refRe, refIm, (stage shl 5) or 0x13),
        )
        return Datapoint(frequency, -1000, point, values)
    }

    @Test
    fun datapointPayloadRoundTrip() {
        val values = listOf(
            DpValue(1.0, 2.0, 0x01),
            DpValue(3.0, 4.0, 0x02),
            DpValue(5.0, 6.0, 0x13),
        )
        val size = 12 + values.size * 9
        val bb = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)
        bb.putLong(2_400_000_000L)
        bb.putShort(-1000)
        bb.putShort(7)
        for (v in values) bb.putFloat(v.re.toFloat())
        for (v in values) bb.putFloat(v.im.toFloat())
        for (v in values) bb.put(v.descriptor.toByte())
        val parsed = com.droidvna.app.usb.DatapointCodec.parse(bb.array())
        assertNotNull(parsed)
        assertEquals(2_400_000_000L, parsed!!.frequencyHz)
        assertEquals(7, parsed.pointNum)
        assertEquals(5.0, parsed.values[2].re, 1e-6)
        assertEquals(0x13, parsed.values[2].descriptor)
    }
}