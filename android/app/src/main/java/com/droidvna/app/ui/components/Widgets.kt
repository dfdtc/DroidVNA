/*
 * DroidVNA - native Android client for LibreVNA / Harogic B60.
 * Copyright (C) 2026 The DroidVNA Authors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.droidvna.app.ui.components

import androidx.compose.animation.core.Animatable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import com.droidvna.app.ui.text.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.droidvna.app.device.ConnectionState
import com.droidvna.app.device.SourceKind
import com.droidvna.app.model.SParam
import com.droidvna.app.ui.chart.color
import com.droidvna.app.ui.text.tr
import com.droidvna.app.ui.theme.VnaError
import com.droidvna.app.ui.theme.VnaOutline
import com.droidvna.app.ui.theme.VnaPrimary
import com.droidvna.app.ui.theme.VnaSecondary
import com.droidvna.app.ui.theme.VnaSurface
import com.droidvna.app.ui.theme.VnaSurfaceElevated
import com.droidvna.app.ui.theme.VnaText
import com.droidvna.app.ui.theme.VnaTextMuted

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
fun ConnectionPill(state: ConnectionState, compact: Boolean = false, onClick: () -> Unit) {
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
        if (compact) {
            // Narrow screens only get the state colour; the label lives in the tooltip/menu.
            Row(
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(color))
            }
        } else {
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
fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = VnaText, fontSize = 13.sp)
            Text(subtitle, color = VnaTextMuted, fontSize = 10.5.sp)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}


/**
 * Collapsible container: the content animates its height and fades out, so the chart
 * smoothly reclaims the space instead of jumping when the controls are hidden.
 */
@Composable
fun CollapsibleSection(
    expanded: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val progress = remember { androidx.compose.animation.core.Animatable(if (expanded) 1f else 0f) }
    LaunchedEffect(expanded) {
        progress.animateTo(
            targetValue = if (expanded) 1f else 0f,
            animationSpec = tween(durationMillis = 180),
        )
    }
    if (progress.value > 0f) {
        Box(
            modifier
                .fillMaxWidth()
                .clipToBounds()
                .alpha(progress.value),
        ) {
            content()
        }
    }
}

/** Small chevron control used to collapse/expand a control group. */
@Composable
fun CollapseToggle(
    expanded: Boolean,
    contentDescription: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        color = VnaSurfaceElevated,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, VnaOutline),
        modifier = modifier.clickable { onClick() },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = tr(contentDescription),
                tint = VnaTextMuted,
                modifier = Modifier.size(18.dp),
            )
        }
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