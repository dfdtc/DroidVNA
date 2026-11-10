/*
 * DroidVNA - native Android client for LibreVNA / Harogic B60.
 * Copyright (C) 2026 The DroidVNA Authors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.droidvna.app.ui.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import com.droidvna.app.ui.text.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.droidvna.app.model.Complex
import com.droidvna.app.model.Marker
import com.droidvna.app.model.SParam
import com.droidvna.app.model.SweepConfig
import com.droidvna.app.model.SweepResult
import com.droidvna.app.model.formatFrequency
import com.droidvna.app.model.formatFrequencyShort
import com.droidvna.app.ui.components.SweepPulse
import com.droidvna.app.ui.text.tr
import com.droidvna.app.ui.theme.VnaOutline
import com.droidvna.app.ui.theme.VnaPrimary
import com.droidvna.app.ui.theme.VnaSecondary
import com.droidvna.app.ui.theme.VnaSurface
import com.droidvna.app.ui.theme.VnaSurfaceElevated
import com.droidvna.app.ui.theme.VnaText
import com.droidvna.app.ui.theme.VnaTextMuted
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

enum class ChartMode(val label: String, val unit: String) {
    DB("dB", "dB"),
    PHASE("Phase", "°"),
    VSWR("VSWR", ""),
    SMITH("Smith", ""),
}

val SParam.color: Color
    get() = when (this) {
        SParam.S11 -> Color(0xFF4DD0E1)
        SParam.S21 -> Color(0xFFFFB74D)
        SParam.S12 -> Color(0xFFBA68C8)
        SParam.S22 -> Color(0xFF81C784)
    }

private data class ChartGeometry(
    val plot: Rect,
    val viewStart: Double,
    val viewStop: Double,
    val yMin: Double,
    val yMax: Double,
    val logX: Boolean = false,
)

private data class SmithGeometry(
    val cx: Float,
    val cy: Float,
    val radius: Float,
    /** Reflection-coefficient position under (cx, cy); full-circle view is the origin. */
    val focusRE: Float = 0f,
    val focusIM: Float = 0f,
    /** Half-width of the viewport in |Γ| units; the full circle is span 1. */
    val span: Float = 1f,
)

/**
 * Zoom/pan state of the Smith view.
 *
 * [re]/[im] is the Γ shown at the centre of the widget and [span] is the half-width of the
 * visible window in Γ units, so the untouched full-circle view is `SmithView.FULL`.
 */
private data class SmithView(val re: Float = 0f, val im: Float = 0f, val span: Float = 1f) {
    companion object {
        val FULL = SmithView()
        /** Never zoom in past this; also keeps the unit-circle rim reachable while panning. */
        const val MIN_SPAN = 0.02f
        const val MAX_SPAN = 1f
    }
}

@Composable
fun VnaChart(
    result: SweepResult?,
    config: SweepConfig,
    enabled: Set<SParam>,
    mode: ChartMode,
    markers: List<Marker>,
    onMoveMarker: (Int, Double) -> Unit,
    sweepRate: Double,
    averaging: Int,
    averagingProgress: Float,
    calibrated: Boolean,
    sweepActive: Boolean = false,
    singleSweep: Boolean = false,
    autoY: Boolean = true,
    onAutoYChange: (Boolean) -> Unit = {},
    logFrequencyAxis: Boolean = false,
    /** Marker shown with a highlight ring, normally the one M1..Mn chip / trace slider edits. */
    focusedMarkerId: Int? = null,
    /** Called when the user drags the trace-freedom slider along the right edge of the chart. */
    onMoveFocusedMarker: (Double) -> Unit = {},
    /** Label of the frequency slider (already translated); null hides the slider. */
    sliderLabel: String? = null,
    /** Draw the app identity as a faint watermark behind the traces. */
    showWatermark: Boolean = true,
    modifier: Modifier = Modifier,
) {
    var viewStart by remember(config.startHz, config.stopHz) { mutableDoubleStateOf(config.startHz.toDouble()) }
    var viewStop by remember(config.startHz, config.stopHz) { mutableDoubleStateOf(config.stopHz.toDouble()) }
    var manualYMin by remember { mutableDoubleStateOf(-80.0) }
    var manualYMax by remember { mutableDoubleStateOf(0.0) }
    var crosshairFreq by remember { mutableStateOf<Double?>(null) }
    /** Zoom/pan of the Smith view; reset by the fit button, double tap and a config change. */
    var smithView by remember { mutableStateOf(SmithView.FULL) }
    var lastTapTime by remember { mutableLongStateOf(0L) }
    var lastTapPosition by remember { mutableStateOf(Offset.Zero) }
    var sliderEditing by remember { mutableStateOf(false) }
    // The slider always addresses the full configured bandwidth, so its frequency axis is the
    // sweep config itself - unlike the plot, which follows the user's zoom.
    val focusedMarker = markers.firstOrNull { it.id == focusedMarkerId } ?: markers.firstOrNull()
    val sliderFraction = focusedMarker?.let { fractionOf(it.freqHz, config) }

    LaunchedEffect(config.startHz, config.stopHz) {
        viewStart = config.startHz.toDouble()
        viewStop = config.stopHz.toDouble()
        onAutoYChange(true)
        crosshairFreq = null
    }
    LaunchedEffect(mode) {
        onAutoYChange(true)
    }

    val computedY = if (mode == ChartMode.SMITH) (1.0 to -1.0) else computeYRange(result, enabled, mode, viewStart, viewStop)
    LaunchedEffect(autoY, computedY) {
        if (autoY) {
            manualYMin = computedY.first
            manualYMax = computedY.second
        }
    }
    val yMin = if (autoY) computedY.first else manualYMin
    val yMax = if (autoY) computedY.second else manualYMax

    val currentResult = rememberUpdatedState(result)
    val currentEnabled = rememberUpdatedState(enabled)
    val currentMarkers = rememberUpdatedState(markers)
    val currentMode = rememberUpdatedState(mode)
    val currentConfig = rememberUpdatedState(config)
    val currentViewStart = rememberUpdatedState(viewStart)
    val currentViewStop = rememberUpdatedState(viewStop)
    val currentAutoY = rememberUpdatedState(autoY)
    val currentLogX = rememberUpdatedState(logFrequencyAxis)
    val currentYMin = rememberUpdatedState(yMin)
    val currentYMax = rememberUpdatedState(yMax)
    val currentSmithView = rememberUpdatedState(smithView)
    val currentOnMoveMarker = rememberUpdatedState(onMoveMarker)
    val currentOnAutoYChange = rememberUpdatedState(onAutoYChange)

    fun resetView() {
        viewStart = config.startHz.toDouble()
        viewStop = config.stopHz.toDouble()
        // The Smith view is not a frequency window: "fit" there means back to the whole circle.
        smithView = SmithView.FULL
        onAutoYChange(true)
        crosshairFreq = null
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(VnaSurface),
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val width = size.width.toFloat()
                        val height = size.height.toFloat()
                        val modeNow = currentMode.value
                        val plot = if (modeNow == ChartMode.SMITH) smithBounds(width, height, this)
                        else plotBounds(width, height, this)
                        val smith = if (modeNow == ChartMode.SMITH) {
                            smithGeometry(width, height, this, currentSmithView.value)
                        } else {
                            null
                        }
                        val geometry = ChartGeometry(
                            plot = if (smith != null) Rect(smith.cx - smith.radius, smith.cy - smith.radius, smith.cx + smith.radius, smith.cy + smith.radius) else plot,
                            viewStart = currentViewStart.value,
                            viewStop = currentViewStop.value,
                            yMin = currentYMin.value,
                            yMax = currentYMax.value,
                            logX = currentLogX.value,
                        )

                        fun markerScreenPos(marker: Marker): Offset {
                            return if (smith != null) {
                                val sweep = currentResult.value
                                val idx = sweep?.nearestIndex(marker.freqHz) ?: 0
                                val gamma = sweep?.s11?.getOrNull(idx) ?: Complex.NAN
                                gammaToOffset(gamma, smith)
                            } else {
                                Offset(freqToX(marker.freqHz, geometry), geometry.plot.center.y)
                            }
                        }

                        val hitMarker = if (modeNow == ChartMode.SMITH) {
                            currentMarkers.value
                                .minByOrNull { markerScreenPos(it).minus(down.position).getDistance() }
                                ?.takeIf { markerScreenPos(it).minus(down.position).getDistance() < 36.dp.toPx() }
                        } else {
                            // Markers are vertical lines: only the horizontal distance matters.
                            currentMarkers.value
                                .minByOrNull { abs(freqToX(it.freqHz, geometry) - down.position.x) }
                                ?.takeIf { abs(freqToX(it.freqHz, geometry) - down.position.x) < 28.dp.toPx() }
                        }

                        // Show the readout immediately for a tap, not only after a move event.
                        if (hitMarker == null && geometry.plot.contains(down.position)) {
                            crosshairFreq = xToFreq(down.position.x, geometry)
                        }

                        if (hitMarker != null && modeNow != ChartMode.SMITH) {
                            var last = down.position
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                val freq = xToFreq(change.position.x, geometry)
                                    .coerceIn(currentConfig.value.startHz.toDouble(), currentConfig.value.stopHz.toDouble())
                                currentOnMoveMarker.value(hitMarker.id, freq)
                                last = change.position
                                change.consume()
                            }
                        } else if (hitMarker != null && modeNow == ChartMode.SMITH && smith != null) {
                            // On the Smith circle the trace is a curve, not a column: the marker
                            // follows the finger along the trace in two dimensions. Only the
                            // distance to the finger matters (the trace picks the frequency), so
                            // the grab point is offset-tracked and the marker glides under the
                            // fingertip instead of jumping to the nearest trace point.
                            val sweep = currentResult.value
                            val anchor = markerScreenPos(hitMarker)
                            if (sweep == null) return@awaitEachGesture
                            val offset = anchor - down.position
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                nearestTraceFreq(change.position + offset, sweep, currentEnabled.value, smith)?.let { snap ->
                                    currentOnMoveMarker.value(hitMarker.id, snap.second)
                                }
                                change.consume()
                            }
                        } else {
                            var transformed = false
                            var totalMove = 0f
                            var last = down.position
                            val downTime = System.currentTimeMillis()
                            while (true) {
                                val event = awaitPointerEvent()
                                val pressed = event.changes.filter { it.pressed }
                                if (pressed.size >= 2) {
                                    transformed = true
                                    crosshairFreq = null
                                    if (modeNow == ChartMode.SMITH) {
                                        // Pinch and two-finger drag steer the Smith viewport.
                                        val zoom = event.calculateZoom()
                                        val pan = event.calculatePan()
                                        if (smith != null) {
                                            val next = applySmithTransform(currentSmithView.value, zoom, pan, smith.radius)
                                            if (next != currentSmithView.value) smithView = next
                                        }
                                    } else {
                                        val zoom = event.calculateZoom()
                                        val pan = event.calculatePan()
                                        val centroid = event.calculateCentroid(useCurrent = true)
                                        applyTransform(
                                            zoom = zoom,
                                            pan = pan,
                                            centroid = centroid,
                                            plot = geometry.plot,
                                            config = currentConfig.value,
                                            viewStartState = currentViewStart,
                                            viewStopState = currentViewStop,
                                            yMinState = currentYMin,
                                            yMaxState = currentYMax,
                                            autoYState = currentAutoY,
                                            logX = currentLogX.value,
                                        ) { s, e, lo, hi ->
                                            viewStart = s
                                            viewStop = e
                                            manualYMin = lo
                                            manualYMax = hi
                                            currentOnAutoYChange.value(false)
                                        }
                                    }
                                    event.changes.forEach { it.consume() }
                                } else if (pressed.size == 1 && !transformed) {
                                    val pos = pressed[0].position
                                    totalMove += (pos - last).getDistance()
                                    last = pos
                                    val inPlot = geometry.plot.contains(pos)
                                    crosshairFreq = if (inPlot) xToFreq(pos.x, geometry) else null
                                    pressed[0].consume()
                                }
                                if (event.changes.none { it.pressed }) break
                            }
                            if (!transformed && totalMove < 8.dp.toPx() && System.currentTimeMillis() - downTime < 350) {
                                // keep the crosshair from the tap; double tap resets
                                val now = System.currentTimeMillis()
                                if (now - lastTapTime < 320 && (down.position - lastTapPosition).getDistance() < 48.dp.toPx()) {
                                    resetView()
                                }
                                lastTapTime = now
                                lastTapPosition = down.position
                            }
                        }
                    }
                }
        ) {
            val plot = if (mode == ChartMode.SMITH) smithBounds(size.width, size.height, this)
            else plotBounds(size.width, size.height, this)
            if (mode == ChartMode.SMITH) {
                val smith = smithGeometry(size.width, size.height, this, smithView)
                drawSmith(result, enabled, markers, smith, smithView, showWatermark, this, focusedMarker?.id)
            } else {
                drawXy(
                    result, enabled, markers, plot, mode, viewStart, viewStop, yMin, yMax,
                    sweepRate, averagingProgress, calibrated, logFrequencyAxis, showWatermark, this,
                )
            }
            crosshairFreq?.let { f ->
                if (mode == ChartMode.SMITH) {
                    val smith = smithGeometry(size.width, size.height, this, smithView)
                    drawSmithCrosshair(result, enabled, f, smith, plot, this)
                } else {
                    drawXyCrosshair(result, enabled, f, plot, mode, viewStart, viewStop, yMin, yMax, logFrequencyAxis, this)
                }
            }
        }

        // Fit button: restores the full frequency span (dB / Phase / VSWR) or the whole Smith
        // circle when it has been zoomed. It is highlighted while a view is off its default so
        // the button visibly does something, and tells the user how to zoom.
        val zoomed = if (mode == ChartMode.SMITH) {
            smithView != SmithView.FULL
        } else {
            viewStart != config.startHz.toDouble() || viewStop != config.stopHz.toDouble()
        }
        IconButton(
            onClick = { resetView() },
            modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(38.dp),
        ) {
            Icon(
                Icons.Default.CenterFocusStrong,
                contentDescription = if (mode == ChartMode.SMITH) tr("复位圆图") else tr("缩放至全带宽"),
                tint = if (zoomed) VnaPrimary else VnaTextMuted,
            )
        }
        // Zoom readout for the Smith view, so a pinch has visible feedback.
        if (mode == ChartMode.SMITH && smithView.span < SmithView.FULL.span) {
            Text(
                text = String.format("×%.1f", 1f / smithView.span),
                color = VnaPrimary,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 46.dp, top = 40.dp)
                    .background(Color(0xCC0F171D), RoundedCornerShape(5.dp))
                    .padding(horizontal = 5.dp, vertical = 2.dp),
            )
        }

        if (result != null) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 62.dp, top = 30.dp)
                    .background(Color(0xCC0F171D), RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (sweepActive) {
                    SweepPulse(active = true, singleSweep = singleSweep)
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = if (result.isPartial) {
                        val measured = if (result.measuredPoints >= 0) result.measuredPoints else countFinitePoints(result)
                        String.format(
                            "%d/%d pts · %s",
                            measured,
                            result.size,
                            if (sweepActive) "采集中" else "已暂停",
                        )
                    } else {
                        String.format("%d pts · %.1f/s", result.size, sweepRate)
                    },
                    color = VnaTextMuted,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                )
                if (averaging > 1) {
                    Text(
                        text = "  AVG " + String.format("%.0f%%", averagingProgress * 100),
                        color = VnaSecondary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                if (calibrated) {
                    Text("  CAL", color = VnaPrimary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }
                if (mode == ChartMode.VSWR || mode == ChartMode.SMITH) {
                    Text("  S11/S22", color = VnaTextMuted, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }

        // Trace-freedom slider along the bottom of the plot. On the Smith circle the trace is a
        // curve, so a precise tap on it is hard on a small screen; this steers the focused marker
        // along the sweep by frequency and makes that interaction obvious.
        if (focusedMarker != null && sliderFraction != null) {
            TraceSlider(
                x = sliderFraction,
                label = sliderLabel ?: "迹线频率",
                markerLabel = "M" + focusedMarker.id,
                frequencyText = formatFrequency(snapToSweep(focusedMarker.freqHz, result), 4),
                dragging = sliderEditing,
                onDrag = { t ->
                    sliderEditing = true
                    onMoveFocusedMarker(snapToSweep(frequencyAt(t, config), result))
                },
                onDragEnd = { sliderEditing = false },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }

        if (result == null) {
            androidx.compose.foundation.layout.Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (sweepActive) {
                    SweepPulse(active = true, singleSweep = singleSweep, showLabel = false)
                    Spacer(Modifier.height(10.dp))
                }
                Text(
                    if (sweepActive) "正在采集…" else "等待扫描数据",
                    color = VnaText,
                    fontSize = 18.sp,
                )
                Text(
                    if (sweepActive) "低中频带宽时首个数据点需要更长时间" else "连接 USB VNA 或启动演示模式",
                    color = VnaTextMuted,
                    fontSize = 13.sp,
                )
            }
        }
    }
}

/**
 * Compact slider drawn along the bottom edge of the chart. The marker is steered by dragging
 * the thumb, which is the touch-friendly way to position a marker on the Smith circle where the
 * trace is a thin curve.
 */
@Composable
private fun BoxScope.TraceSlider(
    x: Float,
    label: String,
    markerLabel: String,
    frequencyText: String,
    dragging: Boolean,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .background(Color(0xE60F171D), RoundedCornerShape(9.dp))
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(markerLabel, color = VnaSecondary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        Spacer(Modifier.width(6.dp))
        Text(label, color = VnaTextMuted, fontSize = 10.sp, maxLines = 1)
        Spacer(Modifier.width(8.dp))
        Canvas(
            modifier = Modifier
                .weight(1f)
                .height(26.dp)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            onDrag((offset.x / size.width.toFloat()).coerceIn(0f, 1f))
                        },
                        onDragEnd = onDragEnd,
                        onDragCancel = onDragEnd,
                    ) { change, _ ->
                        onDrag((change.position.x / size.width.toFloat()).coerceIn(0f, 1f))
                        change.consume()
                    }
                },
        ) {
            val cy = size.height / 2f
            val trackLeft = 6.dp.toPx()
            val trackRight = size.width - 6.dp.toPx()
            val thumbX = trackLeft + (trackRight - trackLeft) * x.coerceIn(0f, 1f)
            drawLine(
                color = VnaOutline,
                start = Offset(trackLeft, cy),
                end = Offset(trackRight, cy),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round,
            )
            drawLine(
                color = VnaSecondary.copy(alpha = 0.75f),
                start = Offset(trackLeft, cy),
                end = Offset(thumbX, cy),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round,
            )
            drawCircle(
                color = if (dragging) VnaSecondary else VnaSurfaceElevated,
                radius = 8.dp.toPx(),
                center = Offset(thumbX, cy),
            )
            drawCircle(
                color = VnaSecondary,
                radius = 8.dp.toPx(),
                center = Offset(thumbX, cy),
                style = Stroke(2.dp.toPx()),
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(frequencyText, color = VnaText, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
    }
}

/**
 * Position (0..1) of [freqHz] along the chart's frequency axis. Prefers the frequency axis used
 * to draw the trace data; falls back to the sweep config when no data exists yet.
 */
private fun fractionAt(result: SweepResult?, freqHz: Double, config: SweepConfig): Float? {
    val data = result
    if (data != null && data.size >= 2) {
        val lo = data.freqHz.first().toDouble()
        val hi = data.freqHz.last().toDouble()
        if (hi > lo) return ((freqHz - lo) / (hi - lo)).toFloat().coerceIn(0f, 1f)
    }
    val lo = config.startHz.toDouble()
    val hi = config.stopHz.toDouble()
    if (hi <= lo) return null
    return ((freqHz - lo) / (hi - lo)).toFloat().coerceIn(0f, 1f)
}

/**
 * Fraction (0..1) of [freqHz] along the configured sweep band, or null when the band is empty.
 * The slider always spans the whole configured range, independent of the plot zoom.
 */
internal fun fractionOf(freqHz: Double, config: SweepConfig): Float? {
    val lo = config.startHz.toDouble()
    val hi = config.stopHz.toDouble()
    if (hi <= lo) return null
    return ((freqHz - lo) / (hi - lo)).toFloat().coerceIn(0f, 1f)
}

/** Frequency at fraction [t] (0..1) of the configured sweep band. */
internal fun frequencyAt(t: Float, config: SweepConfig): Double {
    val lo = config.startHz.toDouble()
    val hi = config.stopHz.toDouble()
    return lo + t.coerceIn(0f, 1f).toDouble() * (hi - lo)
}

/**
 * Snap [freqHz] to the nearest measured sweep point so a marker always sits on the trace.
 */
internal fun snapToSweep(freqHz: Double, result: SweepResult?): Double {
    if (result == null || result.freqHz.isEmpty()) return freqHz
    return result.freqHz.getOrNull(result.nearestIndex(freqHz))?.toDouble() ?: freqHz
}

private fun plotBounds(width: Float, height: Float, density: Density): Rect {
    with(density) {
        val left = 56.dp.toPx()
        val top = 16.dp.toPx()
        val right = 16.dp.toPx()
        val bottom = 30.dp.toPx()
        return Rect(left, top, width - right, max(top + 10f, height - bottom))
    }
}
/** Circle of the chart canvas used by the Smith view; the zoomed viewport fills this square. */
private fun smithBounds(width: Float, height: Float, density: Density): Rect {
    val side = min(width, height)
    with(density) {
        val inset = max(12.dp.toPx(), min(width, height) * 0.06f)
        val r = (side / 2f - inset).coerceAtLeast(20f)
        return Rect(width / 2f - r, height / 2f - r, width / 2f + r, height / 2f + r)
    }
}

private fun smithGeometry(width: Float, height: Float, density: Density, view: SmithView = SmithView.FULL): SmithGeometry {
    val bounds = smithBounds(width, height, density)
    val r = bounds.width / 2f
    return SmithGeometry(
        cx = bounds.center.x,
        cy = bounds.center.y,
        radius = r,
        focusRE = view.re,
        focusIM = view.im,
        span = view.span,
    )
}

private fun freqToX(freq: Double, geometry: ChartGeometry): Float {
    val start = geometry.viewStart
    val stop = geometry.viewStop
    if (stop <= start) return geometry.plot.center.x
    val t = if (geometry.logX && start > 0.0 && stop > 0.0) {
        val ls = kotlin.math.ln(start)
        val le = kotlin.math.ln(stop)
        if (le <= ls) 0.0 else ((kotlin.math.ln(freq.coerceAtLeast(1e-9)) - ls) / (le - ls)).toFloat().toDouble()
    } else {
        (freq - start) / (stop - start)
    }
    return geometry.plot.left + t.toFloat() * geometry.plot.width
}

private fun xToFreq(x: Float, geometry: ChartGeometry): Double {
    if (geometry.plot.width <= 0f) return geometry.viewStart
    val t = ((x - geometry.plot.left) / geometry.plot.width).coerceIn(0f, 1f)
    val start = geometry.viewStart
    val stop = geometry.viewStop
    if (geometry.logX && start > 0.0 && stop > 0.0) {
        val ls = kotlin.math.ln(start)
        val le = kotlin.math.ln(stop)
        return kotlin.math.exp(ls + t * (le - ls))
    }
    return start + t * (stop - start)
}

private fun valueToY(value: Double, geometry: ChartGeometry): Float {
    val span = geometry.yMax - geometry.yMin
    if (span <= 0.0) return geometry.plot.center.y
    val t = ((value - geometry.yMin) / span).toFloat()
    return geometry.plot.bottom - t * geometry.plot.height
}

private fun gammaToOffset(gamma: Complex, smith: SmithGeometry): Offset {
    if (!gamma.isFinite()) return Offset.Unspecified
    val span = smith.span.coerceAtLeast(1e-6f)
    return Offset(
        smith.cx + ((gamma.re - smith.focusRE) / span * smith.radius).toFloat(),
        smith.cy - ((gamma.im - smith.focusIM) / span * smith.radius).toFloat(),
    )
}

/** Inverse of [gammaToOffset]: the reflection coefficient under a screen position. */
private fun offsetToGamma(position: Offset, smith: SmithGeometry): Complex {
    val span = smith.span.coerceAtLeast(1e-6f)
    val re = smith.focusRE + (position.x - smith.cx) / smith.radius * span
    val im = smith.focusIM - (position.y - smith.cy) / smith.radius * span
    return Complex(re.toDouble(), im.toDouble())
}

/**
 * Frequency of the Smith trace point closest to [target] on screen.
 *
 * The Smith circle plots reflection coefficients, so a drag is resolved against S11 - the same
 * trace the marker dots sit on. Returns null when the trace has no finite point inside the circle.
 */
private fun nearestTraceFreq(
    target: Offset,
    sweep: SweepResult,
    enabled: Set<SParam>,
    smith: SmithGeometry,
): Pair<Int, Double>? {
    val traced = if (enabled.contains(SParam.S11)) listOf(SParam.S11) else SParam.entries.filter { it != SParam.S21 && it != SParam.S12 && enabled.contains(it) }
    val arr = sweep.param(traced.firstOrNull() ?: SParam.S11)
    var best = -1
    var bestDist = Float.POSITIVE_INFINITY
    for (i in sweep.freqHz.indices) {
        val c = arr.getOrNull(i) ?: continue
        if (!c.isFinite()) continue
        val o = gammaToOffset(c, smith)
        if (o == Offset.Unspecified) continue
        val d = (o - target).getDistanceSquared()
        if (d < bestDist) {
            bestDist = d
            best = i
        }
    }
    if (best < 0) return null
    return best to sweep.freqHz[best].toDouble()
}

/**
 * Apply a pinch/pan to the Smith viewport.
 *
 * The content follows the fingers like a map: pinching out widens the circle, dragging moves it
 * with the finger. The view is clamped so the unit circle can never be lost - its centre must
 * stay inside the window and the window may not grow past the whole circle.
 */
private fun applySmithTransform(
    view: SmithView,
    zoom: Float,
    pan: Offset,
    widgetRadius: Float,
): SmithView {
    if (widgetRadius <= 0f) return view
    // Keep the zoom within the same range applyTransform uses, so a stray scale factor (0 for a
    // degenerate gesture) cannot collapse the view.
    val z = zoom.toDouble().coerceIn(0.01, 100.0)
    val newSpan = (view.span / z).toFloat().coerceIn(SmithView.MIN_SPAN, SmithView.MAX_SPAN)
    // Screen pixels -> Γ units at the new scale.
    val perPixel = view.span / widgetRadius
    // Drag right moves the content right, i.e. the window centre moves left in Γ.
    var newRe = view.re - pan.x * perPixel
    var newIm = view.im + pan.y * perPixel
    // Keep the Γ=0 origin (the Smith centre) inside the window, with a small margin so the
    // centre cross stays grabbable at high zoom.
    val limitRe = (1f - 0.25f) * newSpan.coerceAtMost(SmithView.MAX_SPAN)
    val limitIm = (1f - 0.25f) * newSpan.coerceAtMost(SmithView.MAX_SPAN)
    newRe = newRe.coerceIn(-limitRe.coerceAtLeast(0f), limitRe.coerceAtLeast(0f))
    newIm = newIm.coerceIn(-limitIm.coerceAtLeast(0f), limitIm.coerceAtLeast(0f))
    return SmithView(newRe, newIm, newSpan)
}

private fun applyTransform(
    zoom: Float,
    pan: Offset,
    centroid: Offset,
    plot: Rect,
    config: SweepConfig,
    viewStartState: androidx.compose.runtime.State<Double>,
    viewStopState: androidx.compose.runtime.State<Double>,
    yMinState: androidx.compose.runtime.State<Double>,
    yMaxState: androidx.compose.runtime.State<Double>,
    autoYState: androidx.compose.runtime.State<Boolean>,
    logX: Boolean,
    apply: (Double, Double, Double, Double) -> Unit,
) {
    val sx = viewStartState.value
    val ex = viewStopState.value
    if (ex <= sx) return
    val minF = config.startHz.toDouble()
    val maxF = config.stopHz.toDouble()
    val frac = ((centroid.x - plot.left) / plot.width).coerceIn(0f, 1f).toDouble()
    var ns: Double
    var ne: Double
    val z = zoom.toDouble().coerceAtLeast(0.01)

    if (logX && sx > 0.0 && ex > 0.0 && minF > 0.0) {
        val ls = kotlin.math.ln(sx)
        val le = kotlin.math.ln(ex)
        val lspan = le - ls
        val minRatio = (maxF / minF).pow(1.0 / max(1, config.points - 1) / 4.0)
        val minLogSpan = kotlin.math.ln(max(1.0001, minRatio))
        val maxLogSpan = kotlin.math.ln(maxF / minF).coerceAtLeast(minLogSpan)
        val newLogSpan = (lspan / z).coerceIn(minLogSpan, maxLogSpan)
        val anchor = kotlin.math.exp(ls + frac * lspan)
        var nls = kotlin.math.ln(anchor) - frac * newLogSpan
        var nle = nls + newLogSpan
        val panLog = -(pan.x / plot.width).toDouble() * newLogSpan
        nls += panLog
        nle += panLog
        val lmin = kotlin.math.ln(minF)
        val lmax = kotlin.math.ln(maxF)
        if (nls < lmin) {
            nle += lmin - nls
            nls = lmin
        }
        if (nle > lmax) {
            nls -= nle - lmax
            nle = lmax
        }
        if (nls < lmin) nls = lmin
        if (nle > lmax) nle = lmax
        if (nle - nls < minLogSpan) nle = nls + minLogSpan
        ns = kotlin.math.exp(nls)
        ne = kotlin.math.exp(nle)
    } else {
        val span = ex - sx
        val minSpan = max(1.0, (maxF - minF) / max(1, config.points - 1) / 4.0)
        val maxSpan = (maxF - minF).coerceAtLeast(1.0)
        val anchor = sx + frac * span
        val newSpan = (span / z).coerceIn(minSpan, maxSpan)
        ns = anchor - frac * newSpan
        ne = ns + newSpan
        val panFreq = -(pan.x / plot.width).toDouble() * newSpan
        ns += panFreq
        ne += panFreq
        if (ns < minF) {
            ne += minF - ns
            ns = minF
        }
        if (ne > maxF) {
            ns -= ne - maxF
            ne = maxF
        }
        if (ns < minF) ns = minF
        if (ne > maxF) ne = maxF
        if (ne - ns < minSpan) ne = ns + minSpan
    }

    val sy = yMinState.value
    val ey = yMaxState.value
    var nyMin = sy
    var nyMax = ey
    if (!autoYState.value) {
        val yspan = ey - sy
        if (yspan > 0.0) {
            val fy = ((plot.bottom - centroid.y) / plot.height).coerceIn(0f, 1f).toDouble()
            val yAnchor = sy + fy * yspan
            val newY = (yspan / z).coerceIn(1e-6, 1e9)
            nyMin = yAnchor - fy * newY
            nyMax = nyMin + newY
            val panY = (pan.y / plot.height).toDouble() * newY
            nyMin += panY
            nyMax += panY
        }
    }
    apply(ns, ne, nyMin, nyMax)
}

private fun logTicks(minValue: Double, maxValue: Double): List<Double> {
    if (!minValue.isFinite() || !maxValue.isFinite() || minValue <= 0.0 || maxValue <= minValue) {
        return listOf(minValue)
    }
    val firstDecade = floor(log10(minValue)).toInt()
    val lastDecade = ceil(log10(maxValue)).toInt()
    val sparse = lastDecade - firstDecade > 4
    val multipliers = if (sparse) listOf(1.0) else listOf(1.0, 2.0, 5.0)
    val ticks = ArrayList<Double>()
    for (decade in firstDecade..lastDecade) {
        for (multiplier in multipliers) {
            val value = multiplier * 10.0.pow(decade)
            if (value >= minValue * 0.999999 && value <= maxValue * 1.000001) {
                ticks.add(value)
            }
        }
    }
    if (ticks.isEmpty()) return listOf(minValue, maxValue)
    if (ticks.first() > minValue * 1.05) ticks.add(0, minValue)
    if (ticks.last() < maxValue * 0.95) ticks.add(maxValue)
    return ticks
}

internal fun niceTicks(minValue: Double, maxValue: Double, count: Int): List<Double> {
    if (!minValue.isFinite() || !maxValue.isFinite() || maxValue <= minValue) return listOf(minValue)
    val span = maxValue - minValue
    val raw = span / count.coerceAtLeast(1)
    if (raw <= 0.0) return listOf(minValue)
    val mag = 10.0.pow(floor(log10(raw)))
    val norm = raw / mag
    val step = when {
        norm <= 1.0 -> 1.0
        norm <= 2.0 -> 2.0
        norm <= 2.5 -> 2.5
        norm <= 5.0 -> 5.0
        else -> 10.0
    } * mag
    val first = ceil(minValue / step) * step
    val ticks = ArrayList<Double>()
    var v = first
    var guard = 0
    while (v <= maxValue + step * 1e-6 && guard++ < 100) {
        ticks.add(v)
        v += step
    }
    return ticks
}

private fun countFinitePoints(result: SweepResult): Int {
    if (!result.isPartial) return result.size
    var count = 0
    for (i in result.freqHz.indices) {
        if (result.s11.getOrNull(i)?.isFinite() == true ||
            result.s21.getOrNull(i)?.isFinite() == true ||
            result.s12.getOrNull(i)?.isFinite() == true ||
            result.s22.getOrNull(i)?.isFinite() == true
        ) {
            count++
        }
    }
    return count
}

private fun computeYRange(
    result: SweepResult?,
    enabled: Set<SParam>,
    mode: ChartMode,
    viewStart: Double,
    viewStop: Double,
): Pair<Double, Double> {
    if (mode == ChartMode.PHASE) return -180.0 to 180.0
    if (result == null || enabled.isEmpty()) return if (mode == ChartMode.VSWR) 1.0 to 10.0 else -80.0 to 0.0
    var lo = Double.POSITIVE_INFINITY
    var hi = Double.NEGATIVE_INFINITY
    for (p in enabled) {
        if (mode == ChartMode.VSWR && (p == SParam.S21 || p == SParam.S12)) continue
        val arr = result.param(p)
        for (i in result.freqHz.indices) {
            val f = result.freqHz[i].toDouble()
            if (f < viewStart || f > viewStop) continue
            val c = arr[i]
            if (!c.isFinite()) continue
            val v = when (mode) {
                ChartMode.DB -> c.magDb()
                ChartMode.VSWR -> c.vswr()
                else -> continue
            }
            if (v.isFinite()) {
                lo = min(lo, v)
                hi = max(hi, v)
            }
        }
    }
    if (!lo.isFinite() || !hi.isFinite()) return if (mode == ChartMode.VSWR) 1.0 to 10.0 else -80.0 to 0.0
    if (mode == ChartMode.VSWR) {
        lo = 1.0
        hi = min(20.0, max(2.0, ceil(hi * 1.1)))
        return lo to hi
    }
    val pad = max(3.0, (hi - lo) * 0.12)
    lo = floor((lo - pad) / 5.0) * 5.0
    hi = ceil((hi + pad) / 5.0) * 5.0
    if (hi - lo < 20.0) {
        val mid = (hi + lo) / 2.0
        lo = mid - 10.0
        hi = mid + 10.0
    }
    if (hi > 20.0) hi = 20.0
    if (lo < -140.0) lo = -140.0
    return lo to hi
}

private fun valueOf(c: Complex, mode: ChartMode): Double = when (mode) {
    ChartMode.DB -> c.magDb()
    ChartMode.PHASE -> c.phaseDeg()
    ChartMode.VSWR -> c.vswr()
    ChartMode.SMITH -> Double.NaN
}

private fun axisFormat(value: Double, mode: ChartMode): String = when (mode) {
    ChartMode.PHASE -> String.format("%.0f°", value)
    ChartMode.VSWR -> String.format("%.1f", value)
    else -> String.format("%.0f", value)
}

private fun DrawScope.drawXy(
    result: SweepResult?,
    enabled: Set<SParam>,
    markers: List<Marker>,
    plot: Rect,
    mode: ChartMode,
    viewStart: Double,
    viewStop: Double,
    yMin: Double,
    yMax: Double,
    sweepRate: Double,
    averagingProgress: Float,
    calibrated: Boolean,
    logX: Boolean,
    watermark: Boolean,
    density: Density,
) {
    val geometry = ChartGeometry(plot, viewStart, viewStop, yMin, yMax, logX)
    val grid = Color(0xFF24333D)
    val labelColor = VnaTextMuted

    drawRect(color = Color(0xFF0F171D), topLeft = plot.topLeft, size = plot.size)
    if (watermark) drawWatermark(plot, density)
    val xTicks = if (logX) logTicks(viewStart, viewStop) else niceTicks(viewStart, viewStop, 6)
    for ((index, t) in xTicks.withIndex()) {
        val x = freqToX(t, geometry)
        drawLine(grid, Offset(x, plot.top), Offset(x, plot.bottom), strokeWidth = 1f)
        drawTextAt(
            text = formatFrequencyShort(t),
            x = if (index == 0) plot.left else if (index == xTicks.lastIndex) plot.right else x,
            y = plot.bottom + 18.dp.toPx(),
            paint = textPaint(labelColor, 10.5f, density),
            alignCenter = index != 0 && index != xTicks.lastIndex,
            alignRight = index == xTicks.lastIndex,
        )
    }
    val yTicks = when (mode) {
        ChartMode.PHASE -> listOf(-180.0, -90.0, 0.0, 90.0, 180.0)
        ChartMode.VSWR -> listOf(1.0, 2.0, 5.0, 10.0, 20.0).filter { it >= yMin && it <= yMax }
        else -> niceTicks(yMin, yMax, 5)
    }
    for (t in yTicks) {
        val y = valueToY(t, geometry)
        drawLine(grid, Offset(plot.left, y), Offset(plot.right, y), strokeWidth = 1f)
        drawTextAt(
            text = axisFormat(t, mode),
            x = plot.left - 8.dp.toPx(),
            y = y + 4.dp.toPx(),
            paint = textPaint(labelColor, 10.5f, density),
            alignRight = true,
        )
    }
    drawRect(color = VnaOutline, topLeft = plot.topLeft, size = plot.size, style = Stroke(1.2f))

    if (result == null) return
    clipRect(plot.left, plot.top, plot.right, plot.bottom) {
        for (p in enabled) {
            if (mode == ChartMode.VSWR && (p == SParam.S21 || p == SParam.S12)) continue
            val arr = result.param(p)
            val path = Path()
            var started = false
            var previousPhase = Double.NaN
            for (i in result.freqHz.indices) {
                val f = result.freqHz[i].toDouble()
                if (f < viewStart || f > viewStop) {
                    started = false
                    previousPhase = Double.NaN
                    continue
                }
                val c = arr[i]
                if (!c.isFinite()) {
                    started = false
                    previousPhase = Double.NaN
                    continue
                }
                val v = valueOf(c, mode)
                if (!v.isFinite()) {
                    started = false
                    previousPhase = Double.NaN
                    continue
                }
                if (mode == ChartMode.PHASE) {
                    if (previousPhase.isFinite() && abs(v - previousPhase) > 180.0) started = false
                    previousPhase = v
                }
                val x = freqToX(f, geometry)
                val y = valueToY(v, geometry)
                if (!started) {
                    path.moveTo(x, y)
                    started = true
                } else {
                    path.lineTo(x, y)
                }
            }
            drawPath(path, color = p.color, style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }

    for (marker in markers) {
        val x = freqToX(marker.freqHz, geometry)
        if (x < plot.left || x > plot.right) continue
        drawLine(
            color = VnaSecondary.copy(alpha = 0.7f),
            start = Offset(x, plot.top),
            end = Offset(x, plot.bottom),
            strokeWidth = 1.2.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
        )
        drawTextAt(
            text = "M" + marker.id,
            x = if (x > plot.right - 24.dp.toPx()) x - 22.dp.toPx() else x + 3.dp.toPx(),
            y = plot.top + 12.dp.toPx(),
            paint = textPaint(VnaSecondary, 10f, density),
        )
        val index = result.nearestIndex(marker.freqHz)
        for (p in enabled) {
            if (mode == ChartMode.VSWR && (p == SParam.S21 || p == SParam.S12)) continue
            val c = result.valueAt(p, index)
            if (!c.isFinite()) continue
            val y = valueToY(valueOf(c, mode), geometry)
            if (y in plot.top..plot.bottom) drawCircle(p.color, radius = 3.5.dp.toPx(), center = Offset(x, y))
        }
    }
}

private fun DrawScope.drawXyCrosshair(
    result: SweepResult?,
    enabled: Set<SParam>,
    frequency: Double,
    plot: Rect,
    mode: ChartMode,
    viewStart: Double,
    viewStop: Double,
    yMin: Double,
    yMax: Double,
    logX: Boolean,
    density: Density,
) {
    if (result == null) return
    val geometry = ChartGeometry(plot, viewStart, viewStop, yMin, yMax, logX)
    val index = result.nearestIndex(frequency)
    val f = result.freqHz.getOrNull(index)?.toDouble() ?: frequency
    val x = freqToX(f, geometry)
    if (x < plot.left || x > plot.right) return
    drawLine(Color.White.copy(alpha = 0.55f), Offset(x, plot.top), Offset(x, plot.bottom), strokeWidth = 1f)
    for (p in enabled) {
        if (mode == ChartMode.VSWR && (p == SParam.S21 || p == SParam.S12)) continue
        val c = result.valueAt(p, index)
        if (!c.isFinite()) continue
        val y = valueToY(valueOf(c, mode), geometry)
        if (y in plot.top..plot.bottom) {
            drawCircle(Color.White, radius = 2.5.dp.toPx(), center = Offset(x, y))
            drawCircle(p.color, radius = 4.5.dp.toPx(), center = Offset(x, y), style = Stroke(1.4.dp.toPx()))
        }
    }
    val lines = ArrayList<String>()
    lines.add(formatFrequency(f, 4))
    for (p in enabled) {
        if (mode == ChartMode.VSWR && (p == SParam.S21 || p == SParam.S12)) continue
        val c = result.valueAt(p, index)
        if (!c.isFinite()) continue
        lines.add(
            when (mode) {
                ChartMode.DB -> p.label + "  " + String.format("%.1f dB", c.magDb())
                ChartMode.PHASE -> p.label + "  " + String.format("%.1f°", c.phaseDeg())
                ChartMode.VSWR -> p.label + "  " + String.format("%.2f", c.vswr())
                ChartMode.SMITH -> p.label + "  " + String.format("|Γ| %.3f", c.abs())
            },
        )
    }
    drawTooltip(lines, x, plot, density)
}

private fun DrawScope.drawSmith(
    result: SweepResult?,
    enabled: Set<SParam>,
    markers: List<Marker>,
    smith: SmithGeometry,
    view: SmithView,
    watermark: Boolean,
    density: Density,
    focusedMarkerId: Int? = null,
) {
    val grid = Color(0xFF2A3A45)
    val widget = Path().apply {
        addOval(Rect(smith.cx - smith.radius, smith.cy - smith.radius, smith.cx + smith.radius, smith.cy + smith.radius))
    }
    drawCircle(Color(0xFF0F171D), radius = smith.radius, center = Offset(smith.cx, smith.cy))
    if (watermark) drawWatermark(Rect(smith.cx - smith.radius, smith.cy - smith.radius, smith.cx + smith.radius, smith.cy + smith.radius), density)
    // The Smith grid and traces live inside the zoomable Γ viewport, clipped to the widget's
    // circle (clipPath saves/restores the canvas state itself). The widget border stays fixed.
    clipPath(widget) {
        drawSmithGrid(smith, grid, density)
        if (result != null) drawSmithTraces(result, enabled, markers, smith, density, focusedMarkerId)
    }
    drawPath(widget, color = VnaOutline, style = Stroke(1.4f))
}

/**
 * Constant-resistance/constant-reactance grid, mapped through the current (possibly zoomed)
 * Smith viewport so it scales with the traces when the user pinches.
 */
private fun DrawScope.drawSmithGrid(smith: SmithGeometry, grid: Color, density: Density) {
    // Constant-resistance circles: centre (r/(1+r), 0), radius 1/(1+r) in Γ units.
    for (r in GRID_RESISTANCES) {
        val span = smith.span.coerceAtLeast(1e-6f)
        val gcx = smith.cx + ((r / (1f + r) - smith.focusRE) / span * smith.radius).toFloat()
        val radius = (1f / (1f + r) / span * smith.radius).toFloat()
        drawCircle(grid, radius = radius, center = Offset(gcx, smith.cy), style = Stroke(1f))
    }
    // Constant-reactance circles: centre (1, ±1/x), radius 1/|x| in Γ units.
    for (x0 in GRID_REACTANCES) {
        val gcx = smith.cx + ((1f - smith.focusRE) / smith.span.coerceAtLeast(1e-6f) * smith.radius).toFloat()
        val gcy = smith.cy - ((-1f / x0 - smith.focusIM) / smith.span.coerceAtLeast(1e-6f) * smith.radius).toFloat()
        val radius = (1f / abs(x0) / smith.span.coerceAtLeast(1e-6f) * smith.radius).toFloat()
        drawCircle(grid, radius = radius, center = Offset(gcx, gcy), style = Stroke(1f))
    }
    // The real axis is the horizontal line through the Smith centre (Γ imaginary = 0).
    val yAxis = smith.cy - (-smith.focusIM / smith.span.coerceAtLeast(1e-6f) * smith.radius).toFloat()
    drawLine(grid, Offset(smith.cx - smith.radius, yAxis), Offset(smith.cx + smith.radius, yAxis), strokeWidth = 1f)

    // Real-axis labels (0, 1, 2, 5 Ω) when they fall inside the visible window.
    val labelPaint = textPaint(VnaTextMuted, 9.5f, density)
    for ((r, label) in listOf(0.0f to "0", 1.0f to "1", 2.0f to "2", 5.0f to "5")) {
        val re = r / (1f + r)
        val x = smith.cx + ((re - smith.focusRE) / smith.span.coerceAtLeast(1e-6f) * smith.radius).toFloat()
        if (x !in (smith.cx - smith.radius)..(smith.cx + smith.radius)) continue
        drawTextAt(label, x, yAxis + 16.dp.toPx(), labelPaint, alignCenter = true)
    }
}

/** Trace and marker drawing for the Smith view; clipped to the widget by the caller. */
private fun DrawScope.drawSmithTraces(
    result: SweepResult,
    enabled: Set<SParam>,
    markers: List<Marker>,
    smith: SmithGeometry,
    density: Density,
    focusedMarkerId: Int?,
) {
    for (p in enabled) {
        if (p == SParam.S21 || p == SParam.S12) continue
        val arr = result.param(p)
        val path = Path()
        var started = false
        for (i in result.freqHz.indices) {
            val c = arr[i]
            if (!c.isFinite()) {
                started = false
                continue
            }
            val o = gammaToOffset(c, smith)
            if (!started) {
                path.moveTo(o.x, o.y)
                started = true
            } else {
                path.lineTo(o.x, o.y)
            }
        }
        drawPath(path, color = p.color, style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
    for (marker in markers) {
        val index = result.nearestIndex(marker.freqHz)
        val c = result.s11.getOrNull(index) ?: continue
        if (!c.isFinite()) continue
        val o = gammaToOffset(c, smith)
        if (marker.id == focusedMarkerId) {
            drawCircle(VnaSecondary.copy(alpha = 0.85f), radius = 9.dp.toPx(), center = o, style = Stroke(1.6.dp.toPx()))
        }
        drawCircle(Color.White, radius = 4.dp.toPx(), center = o)
        drawTextAt("M" + marker.id, o.x + 6.dp.toPx(), o.y - 6.dp.toPx(), textPaint(VnaSecondary, 10f, density))
    }
}

/** Normalised-resistance grid families (r = R/Z0) for the Smith chart. */
private val GRID_RESISTANCES = listOf(0.0f, 0.2f, 0.5f, 1.0f, 2.0f, 5.0f)

/** Normalised-reactance grid families (x = X/Z0); both signs draw their own circle. */
private val GRID_REACTANCES = listOf(-5.0f, -2.0f, -1.0f, -0.5f, -0.2f, 0.2f, 0.5f, 1.0f, 2.0f, 5.0f)

private fun DrawScope.drawSmithCrosshair(
    result: SweepResult?,
    enabled: Set<SParam>,
    frequency: Double,
    smith: SmithGeometry,
    plot: Rect,
    density: Density,
) {
    if (result == null) return
    val index = result.nearestIndex(frequency)
    val c = result.s11.getOrNull(index) ?: return
    if (!c.isFinite()) return
    val o = gammaToOffset(c, smith)
    drawCircle(Color.White, radius = 3.dp.toPx(), center = o)
    drawCircle(VnaPrimary, radius = 6.dp.toPx(), center = o, style = Stroke(1.5.dp.toPx()))
    val lines = listOf(
        formatFrequency(result.freqHz.getOrNull(index)?.toDouble() ?: frequency, 4),
        String.format("|Γ| %.3f ∠%.0f°", c.abs(), c.phaseDeg()),
        String.format("VSWR %.2f", c.vswr()),
    )
    drawTooltip(lines, o.x, plot, density)
}

private fun DrawScope.drawTooltip(lines: List<String>, anchorX: Float, plot: Rect, density: Density) {
    if (lines.isEmpty()) return
    val paint = textPaint(VnaText, 11f, density)
    val lineHeight = 16.dp.toPx()
    val pad = 8.dp.toPx()
    // On a small chart (landscape phone in Smith mode) the plot can be narrower than the readout
    // itself. Cap the box to the plot so the position clamp below can never receive an empty
    // range - that would throw IllegalArgumentException and take the whole app down mid-draw.
    val maxBoxWidth = (plot.width - 2 * 4.dp.toPx()).coerceAtLeast(pad * 2 + 1f)
    var width = 0f
    for (line in lines) width = max(width, paint.measureText(line))
    val boxWidth = min(width + pad * 2, maxBoxWidth)
    val boxHeight = lineHeight * lines.size + pad
    var x = anchorX + 12.dp.toPx()
    if (x + boxWidth > plot.right) x = anchorX - 12.dp.toPx() - boxWidth
    val minX = plot.left + 4.dp.toPx()
    val maxX = plot.right - boxWidth - 4.dp.toPx()
    x = if (maxX > minX) x.coerceIn(minX, maxX) else minX
    val y = plot.top + 8.dp.toPx()
    drawRoundRect(
        color = VnaSurfaceElevated.copy(alpha = 0.96f),
        topLeft = Offset(x, y),
        size = Size(boxWidth, boxHeight),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx()),
    )
    drawRoundRect(
        color = VnaOutline,
        topLeft = Offset(x, y),
        size = Size(boxWidth, boxHeight),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx()),
        style = Stroke(1.dp.toPx()),
    )
    var ty = y + pad + 12.dp.toPx()
    for ((i, line) in lines.withIndex()) {
        drawContext.canvas.nativeCanvas.drawText(line, x + pad, ty, paint)
        ty += lineHeight
    }
}

/**
 * App identity painted inside the plot, behind the traces. Kept faint so it reads as paper
 * stock rather than as a label, and drawn straight into the canvas so it never fights the
 * axis labels or the marker tooltip for space.
 *
 * Centred in the plot rather than tucked into a corner: a corner watermark is cropped away on
 * small screens, while the middle of the plot is visible on every layout.
 */
private fun DrawScope.drawWatermark(plot: Rect, density: Density) {
    val cx = plot.center.x
    val cy = plot.center.y
    // Scale the title down on a narrow plot so it can never spill past the plot edges.
    val nominal = 26f
    val titlePaint = textPaint(VnaText.copy(alpha = 0.13f), nominal, density).apply {
        typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
    }
    val maxWidth = plot.width * 0.72f
    if (titlePaint.measureText("DroidVNA") > maxWidth) {
        titlePaint.textSize = titlePaint.textSize * maxWidth / titlePaint.measureText("DroidVNA")
    }
    val sizeSp = with(density) { titlePaint.textSize.toSp() }
    val subtitle = textPaint(VnaTextMuted.copy(alpha = 0.13f), (sizeSp.value * 0.31f).coerceIn(5.5f, 8.5f), density).apply {
        typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
    }
    val titleHeight = titlePaint.fontMetrics.let { it.descent - it.ascent }
    val subtitleHeight = subtitle.fontMetrics.let { it.descent - it.ascent }
    val gap = titleHeight * 0.12f
    val blockHeight = titleHeight + gap + subtitleHeight
    val titleBaseline = cy - blockHeight / 2f - titlePaint.fontMetrics.ascent
    val subtitleCenter = titleBaseline + gap + subtitleHeight / 2f
    drawTextAt("DroidVNA", cx, titleBaseline, titlePaint, alignCenter = true)
    // Centre the subtitle baseline on its own line so it stays centred with the title.
    val subtitleBaseline = subtitleCenter - (subtitle.fontMetrics.ascent + subtitle.fontMetrics.descent) / 2f
    drawTextAt("TOUCH VECTOR NETWORK ANALYZER", cx, subtitleBaseline, subtitle, alignCenter = true)
}

internal fun textPaint(color: Color, sizeSp: Float, density: Density): android.graphics.Paint {
    return android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color.toArgb()
        textSize = with(density) { sizeSp.sp.toPx() }
        typeface = android.graphics.Typeface.create("monospace", android.graphics.Typeface.NORMAL)
    }
}

internal fun DrawScope.drawTextAt(
    text: String,
    x: Float,
    y: Float,
    paint: android.graphics.Paint,
    alignCenter: Boolean = false,
    alignRight: Boolean = false,
) {
    val dx = when {
        alignCenter -> x - paint.measureText(text) / 2f
        alignRight -> x - paint.measureText(text)
        else -> x
    }
    drawContext.canvas.nativeCanvas.drawText(text, dx, y, paint)
}