/*
 * DroidVNA - native Android client for LibreVNA / Harogic B60.
 * Copyright (C) 2026 The DroidVNA Authors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.droidvna.app.model.analysis

import com.droidvna.app.model.Complex
import kotlin.math.abs

/** Which side(s) of the reference cursor an XdB search walks along. */
enum class XdbSide(val label: String) {
    LEFT("左侧"),
    RIGHT("右侧"),
    BOTH("双侧");

    val searchesLeft: Boolean get() = this != RIGHT
    val searchesRight: Boolean get() = this != LEFT
}

/** Where the reference level for an XdB search comes from. */
enum class XdbReferenceMode(val label: String) {
    PEAK("峰值"),
    CURSOR("游标"),
}

/** A point on the trace used as the 0 dB reference of an XdB search. */
data class XdbCursor(
    val index: Int,
    val frequencyHz: Double,
    val levelDb: Double,
)

/**
 * One found crossing of the trace through the target level.
 *
 * [frequencyHz] is linearly interpolated between the two adjacent sweep points, so a -3 dB
 * edge is not forced onto the nearest measured point.
 */
data class XdbCrossing(
    val frequencyHz: Double,
    val levelDb: Double,
    val side: XdbSide,
)

/**
 * Result of an XdB (or bandwidth) search.
 *
 * A bandwidth search is simply the [XdbSide.BOTH] case of this result: it returns the lower
 * and upper edge, and therefore also the bandwidth and centre frequency.
 */
data class XdbSearchResult(
    val reference: XdbCursor,
    /** Positive magnitude of the requested drop below [XdbCursor.levelDb]. */
    val dropDb: Double,
    val targetLevelDb: Double,
    val lower: XdbCrossing?,
    val upper: XdbCrossing?,
    val side: XdbSide,
) {
    val bandwidthHz: Double?
        get() = if (lower != null && upper != null) upper.frequencyHz - lower.frequencyHz else null

    val centerHz: Double?
        get() = if (lower != null && upper != null) (lower.frequencyHz + upper.frequencyHz) / 2.0 else null

    val foundCount: Int
        get() = (if (lower != null) 1 else 0) + (if (upper != null) 1 else 0)
}

/**
 * XdB marker search on a scalar dB trace.
 *
 * This is the core behind both the bandwidth search and the XdB search: find the level
 * `reference - dropDb`, then walk towards lower and/or higher frequencies until the trace
 * crosses it. The first crossing next to the reference wins, which keeps the result stable
 * for ripple inside the pass band. The exact crossing is interpolated in the dB domain.
 */
object XdbSearch {

    /** Reference cursor on the global maximum of a trace, or null when it has no finite point. */
    fun peakCursor(freqHz: LongArray, values: Array<Complex>): XdbCursor? {
        if (freqHz.isEmpty() || freqHz.size != values.size) return null
        var bestIndex = -1
        var bestLevel = Double.NEGATIVE_INFINITY
        for (i in values.indices) {
            val value = values[i]
            if (!value.isFinite()) continue
            val level = value.magDb()
            if (level.isFinite() && level > bestLevel) {
                bestLevel = level
                bestIndex = i
            }
        }
        if (bestIndex < 0) return null
        return XdbCursor(bestIndex, freqHz[bestIndex].toDouble(), bestLevel)
    }

    /**
     * Reference cursor at [frequencyHz] (the "custom cursor" case), snapped to the nearest
     * finite sweep point. Returns null when the trace has no finite point at all.
     */
    fun cursorAt(
        freqHz: LongArray,
        values: Array<Complex>,
        frequencyHz: Double,
    ): XdbCursor? {
        if (freqHz.isEmpty() || freqHz.size != values.size) return null
        val index = nearestFiniteIndex(freqHz, values, frequencyHz) ?: return null
        val level = values[index].magDb()
        if (!level.isFinite()) return null
        return XdbCursor(index, freqHz[index].toDouble(), level)
    }

    /**
     * Searches [dropDb] below [reference] on the requested side(s).
     *
     * [dropDb] is positive (3.0 means "3 dB below the reference"). Returns null only for
     * malformed input; when no edge is found the corresponding crossing is null and the
     * caller can report how many edges were actually found.
     */
    fun search(
        freqHz: LongArray,
        values: Array<Complex>,
        reference: XdbCursor,
        dropDb: Double,
        side: XdbSide,
    ): XdbSearchResult? {
        if (freqHz.isEmpty() || freqHz.size != values.size) return null
        if (reference.index !in values.indices) return null
        if (!values[reference.index].isFinite()) return null
        if (!dropDb.isFinite() || dropDb <= 0.0) return null

        val target = reference.levelDb - dropDb
        val lower = if (side.searchesLeft) {
            findCrossing(freqHz, values, reference.index, target, -1, XdbSide.LEFT)
        } else {
            null
        }
        val upper = if (side.searchesRight) {
            findCrossing(freqHz, values, reference.index, target, +1, XdbSide.RIGHT)
        } else {
            null
        }
        return XdbSearchResult(
            reference = reference,
            dropDb = dropDb,
            targetLevelDb = target,
            lower = lower,
            upper = upper,
            side = side,
        )
    }

    /**
     * Walks from the reference towards [direction] and returns the first crossing of
     * [targetDb]. A gap of non-finite samples (an unfinished sweep) ends the walk: searching
     * across a hole would invent an edge that was never measured.
     */
    private fun findCrossing(
        freqHz: LongArray,
        values: Array<Complex>,
        referenceIndex: Int,
        targetDb: Double,
        direction: Int,
        side: XdbSide,
    ): XdbCrossing? {
        var index = referenceIndex
        var previousLevel = values[index].magDb()
        if (!previousLevel.isFinite()) return null
        while (true) {
            val next = index + direction
            if (next !in values.indices) return null
            val value = values[next]
            if (!value.isFinite()) return null
            val level = value.magDb()
            if (!level.isFinite()) return null
            if (level <= targetDb) {
                val f0 = freqHz[index].toDouble()
                val f1 = freqHz[next].toDouble()
                val denominator = previousLevel - level
                val t = if (abs(denominator) < 1e-12) {
                    1.0
                } else {
                    ((previousLevel - targetDb) / denominator).coerceIn(0.0, 1.0)
                }
                return XdbCrossing(
                    frequencyHz = f0 + t * (f1 - f0),
                    levelDb = targetDb,
                    side = side,
                )
            }
            index = next
            previousLevel = level
        }
    }

    private fun nearestFiniteIndex(
        freqHz: LongArray,
        values: Array<Complex>,
        frequencyHz: Double,
    ): Int? {
        var lo = 0
        var hi = freqHz.size - 1
        while (lo < hi) {
            val mid = (lo + hi) / 2
            if (freqHz[mid] < frequencyHz) lo = mid + 1 else hi = mid
        }
        if (lo > 0 && abs(freqHz[lo - 1] - frequencyHz) < abs(freqHz[lo] - frequencyHz)) lo -= 1
        for (offset in 0 until freqHz.size) {
            val lower = lo - offset
            if (lower >= 0 && values[lower].isFinite()) return lower
            val upper = lo + offset
            if (upper < freqHz.size && values[upper].isFinite()) return upper
            if (lower < 0 && upper >= freqHz.size) break
        }
        return null
    }
}
