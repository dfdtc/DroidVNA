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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
        assertEquals(31, bytes.size)
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(100_000_000L, bb.long)
        assertEquals(6_000_000_000L, bb.long)
        assertEquals(1001, bb.short.toInt())
        assertEquals(10_000, bb.int)
        assertEquals(-1000, bb.short.toInt())
        assertEquals(0x1D, bb.get().toInt() and 0xFF) // standby + suppress peaks + fixed power + log sweep
        val stages = bb.short.toInt() and 0xFFFF
        assertEquals(1, stages and 0x7)
        assertEquals(0, (stages shr 3) and 0x7)
        assertEquals(1, (stages shr 6) and 0x7)
        assertEquals(-1000, bb.short.toInt())
        assertEquals(0, bb.short.toInt())
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