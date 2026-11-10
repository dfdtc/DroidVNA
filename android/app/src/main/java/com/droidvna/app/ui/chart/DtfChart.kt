/*
 * DroidVNA - native Android client for LibreVNA / Harogic B60.
 * Copyright (C) 2026 The DroidVNA Authors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.droidvna.app.ui.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.dp
import com.droidvna.app.model.analysis.DtfResult
import com.droidvna.app.model.analysis.formatDistanceShort
import com.droidvna.app.ui.theme.VnaOutline
import com.droidvna.app.ui.theme.VnaPrimary
import com.droidvna.app.ui.theme.VnaSecondary
import com.droidvna.app.ui.theme.VnaTextMuted
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Distance-domain trace of a DTF result.
 *
 * The chart is intentionally read-only: the fault table below it carries the exact values,
 * and the main trace chart keeps its own marker/cursor interactions. Drawing is limited to a
 * peak-preserving down-sampled trace (see [DtfResult.samples]), so a wide distance range does
 * not allocate thousands of path segments per frame.
 */
@Composable
fun DtfChart(result: DtfResult, modifier: Modifier = Modifier) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(232.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF0F171D)),
    ) {
        drawDtfPlot(result)
    }
}

private fun DrawScope.drawDtfPlot(result: DtfResult) {
    val left = 46.dp.toPx()
    val top = 12.dp.toPx()
    val right = 10.dp.toPx()
    val bottom = 26.dp.toPx()
    val plot = Rect(left, top, size.width - right, max(top + 10f, size.height - bottom))
    if (plot.width <= 0f || plot.height <= 0f) return

    val xMax = result.displayStopM.takeIf { it > 0.0 } ?: return
    val maxDb = result.samples.maxOfOrNull { it.magnitudeDb } ?: 0.0
    val minDb = result.samples.minOfOrNull { it.magnitudeDb } ?: -60.0
    val yMax = max(5.0, ceil((maxDb + 5.0) / 10.0) * 10.0)
    val yMin = max(-100.0, floor(min(minDb, -40.0) / 10.0) * 10.0)
    val ySpan = (yMax - yMin).coerceAtLeast(1e-6)

    fun xOf(distance: Double): Float =
        plot.left + (distance / xMax).coerceIn(0.0, 1.0).toFloat() * plot.width

    fun yOf(db: Double): Float =
        plot.bottom - ((db - yMin) / ySpan).coerceIn(0.0, 1.0).toFloat() * plot.height

    val grid = Color(0xFF24333D)
    val labelPaint = textPaint(VnaTextMuted, 10f, this)
    drawRect(Color(0xFF0F171D), topLeft = plot.topLeft, size = plot.size)

    val xTicks = niceTicks(0.0, xMax, 5)
    for ((index, tick) in xTicks.withIndex()) {
        val x = xOf(tick)
        drawLine(grid, Offset(x, plot.top), Offset(x, plot.bottom), strokeWidth = 1f)
        drawTextAt(
            text = formatDistanceShort(tick),
            x = if (index == 0) plot.left else if (index == xTicks.lastIndex) plot.right else x,
            y = plot.bottom + 16.dp.toPx(),
            paint = labelPaint,
            alignCenter = index != 0 && index != xTicks.lastIndex,
            alignRight = index == xTicks.lastIndex,
        )
    }

    val yTicks = niceTicks(yMin, yMax, 5)
    for (tick in yTicks) {
        val y = yOf(tick)
        drawLine(grid, Offset(plot.left, y), Offset(plot.right, y), strokeWidth = 1f)
        drawTextAt(
            text = String.format("%.0f", tick),
            x = plot.left - 7.dp.toPx(),
            y = y + 4.dp.toPx(),
            paint = labelPaint,
            alignRight = true,
        )
    }
    drawRect(color = VnaOutline, topLeft = plot.topLeft, size = plot.size, style = Stroke(1.2f))

    val thresholdY = yOf(result.peakThresholdDb)
    if (result.peakThresholdDb in yMin..yMax) {
        drawLine(
            color = VnaTextMuted.copy(alpha = 0.35f),
            start = Offset(plot.left, thresholdY),
            end = Offset(plot.right, thresholdY),
            strokeWidth = 1f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f)),
        )
    }

    if (result.samples.isEmpty()) return

    clipRect(plot.left, plot.top, plot.right, plot.bottom) {
        val trace = Path()
        var started = false
        var firstX = 0f
        var lastX = 0f
        for (sample in result.samples) {
            val x = xOf(sample.distanceM)
            val y = yOf(sample.magnitudeDb)
            if (!started) {
                trace.moveTo(x, y)
                firstX = x
                started = true
            } else {
                trace.lineTo(x, y)
            }
            lastX = x
        }
        if (started) {
            val fill = Path().apply {
                addPath(trace)
                lineTo(lastX, plot.bottom)
                lineTo(firstX, plot.bottom)
                close()
            }
            drawPath(fill, color = VnaPrimary.copy(alpha = 0.10f))
            drawPath(
                trace,
                color = VnaPrimary,
                style = Stroke(width = 1.9.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }

        for ((index, fault) in result.faults.withIndex()) {
            if (fault.distanceM < 0.0 || fault.distanceM > xMax) continue
            val x = xOf(fault.distanceM)
            val y = yOf(fault.magnitudeDb)
            drawLine(
                color = VnaSecondary.copy(alpha = 0.45f),
                start = Offset(x, plot.top),
                end = Offset(x, plot.bottom),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f)),
            )
            drawCircle(VnaSecondary, radius = 2.6.dp.toPx(), center = Offset(x, y))
            drawTextAt(
                text = "F" + (index + 1),
                x = if (x > plot.right - 24.dp.toPx()) x - 3.dp.toPx() else x + 3.dp.toPx(),
                y = plot.top + 11.dp.toPx(),
                paint = textPaint(VnaSecondary, 9.5f, this),
                alignRight = x > plot.right - 24.dp.toPx(),
            )
        }
    }
}
