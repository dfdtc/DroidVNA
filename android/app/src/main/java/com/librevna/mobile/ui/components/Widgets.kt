/*
 * LibreVNA Mobile - native Android client for LibreVNA / Harogic B60.
 * Copyright (C) 2026 The LibreVNA Mobile Authors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.librevna.mobile.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.librevna.mobile.device.ConnectionState
import com.librevna.mobile.device.SourceKind
import com.librevna.mobile.model.SParam
import com.librevna.mobile.ui.chart.color
import com.librevna.mobile.ui.theme.VnaError
import com.librevna.mobile.ui.theme.VnaOutline
import com.librevna.mobile.ui.theme.VnaPrimary
import com.librevna.mobile.ui.theme.VnaSecondary
import com.librevna.mobile.ui.theme.VnaSurface
import com.librevna.mobile.ui.theme.VnaSurfaceElevated
import com.librevna.mobile.ui.theme.VnaText
import com.librevna.mobile.ui.theme.VnaTextMuted

val GoodGreen = Color(0xFF35D39A)

@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    trailing: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = VnaSurface,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, VnaOutline),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (title != null || trailing != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title ?: "",
                        color = VnaText,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    trailing?.invoke()
                }
                Spacer(Modifier.height(12.dp))
            }
            content()
        }
    }
}

@Composable
fun ConnectionPill(state: ConnectionState, onClick: () -> Unit) {
    val (label, color) = when (state) {
        is ConnectionState.Connected -> if (state.kind == SourceKind.USB) "USB" to GoodGreen else "DEMO" to VnaSecondary
        is ConnectionState.Connecting -> "CONNECTING" to VnaSecondary
        is ConnectionState.Error -> "ERROR" to VnaError
        ConnectionState.Disconnected -> "OFFLINE" to VnaTextMuted
    }
    Surface(
        color = color.copy(alpha = 0.14f),
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, color.copy(alpha = 0.5f)),
        modifier = Modifier.clickable { onClick() },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(7.dp))
            Text(label, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
        }
    }
}

@Composable
fun ParamChip(param: SParam, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Surface(
        color = if (selected) param.color.copy(alpha = 0.18f) else VnaSurfaceElevated,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (selected) param.color.copy(alpha = 0.8f) else VnaOutline),
        modifier = Modifier.clickable(enabled = enabled) { onClick() },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (enabled) param.color else VnaTextMuted.copy(alpha = 0.3f)),
            )
            Spacer(Modifier.width(7.dp))
            Text(
                param.label,
                color = if (!enabled) VnaTextMuted.copy(alpha = 0.45f) else if (selected) VnaText else VnaTextMuted,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

@Composable
fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        color = if (selected) VnaPrimary.copy(alpha = 0.16f) else VnaSurfaceElevated,
        shape = RoundedCornerShape(11.dp),
        border = BorderStroke(1.dp, if (selected) VnaPrimary.copy(alpha = 0.75f) else VnaOutline),
        modifier = Modifier.clickable { onClick() },
    ) {
        Text(
            text = label,
            color = if (selected) VnaPrimary else VnaTextMuted,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
        )
    }
}

@Composable
fun Metric(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = VnaText) {
    Column(modifier = modifier) {
        Text(label.uppercase(), color = VnaTextMuted, fontSize = 9.5.sp, letterSpacing = 0.9.sp)
        Spacer(Modifier.height(3.dp))
        Text(value, color = valueColor, fontSize = 14.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun KeyValueRow(label: String, value: String, valueColor: Color = VnaText) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = VnaTextMuted, fontSize = 12.5.sp)
        Text(value, color = valueColor, fontSize = 12.5.sp, fontFamily = FontFamily.Monospace)
    }
}

/**
 * Small pulsing lamp that makes a slow acquisition visible. [active] is true while a
 * continuous sweep is running or a single sweep is waiting for its result.
 */
@Composable
fun SweepPulse(
    active: Boolean,
    singleSweep: Boolean = false,
    showLabel: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val alpha = if (active) {
        val transition = rememberInfiniteTransition(label = "sweepPulse")
        val animated by transition.animateFloat(
            initialValue = 0.18f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 650),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "sweepPulseAlpha",
        )
        animated
    } else {
        1f
    }
    val color = if (active) VnaSecondary else VnaTextMuted
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = if (active) alpha else 0.55f)),
        )
        if (showLabel) {
            Spacer(Modifier.width(6.dp))
            Text(
                text = when {
                    active && singleSweep -> "单次扫描中"
                    active -> "扫描中"
                    else -> "待机"
                },
                color = color,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
    }
}

@Composable
fun StatusDot(ok: Boolean?, label: String) {
    val color = when (ok) {
        true -> GoodGreen
        false -> VnaError
        null -> VnaTextMuted
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, color = VnaTextMuted, fontSize = 12.sp)
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        color = VnaTextMuted,
        fontSize = 10.sp,
        letterSpacing = 1.1.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
fun AccentButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Surface(
        color = if (enabled) VnaPrimary else VnaSurfaceElevated,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.clickable(enabled = enabled) { onClick() },
    ) {
        Text(
            text,
            color = if (enabled) Color(0xFF00201C) else VnaTextMuted,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
        )
    }
}

@Composable
fun OutlineButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Surface(
        color = Color.Transparent,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, if (enabled) VnaOutline else VnaOutline.copy(alpha = 0.4f)),
        modifier = modifier.clickable(enabled = enabled) { onClick() },
    ) {
        Text(
            text,
            color = if (enabled) VnaText else VnaTextMuted.copy(alpha = 0.5f),
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        )
    }
}