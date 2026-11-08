/*
 * DroidVNA - native Android client for LibreVNA / Harogic B60.
 * Copyright (C) 2026 The DroidVNA Authors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.droidvna.app.data

/**
 * Everything the app remembers between launches, minus the calibration coefficients
 * (those live in [CalibrationStore] because of their size).
 *
 * Only plain Kotlin types are used here so the whole record can be written by
 * [SettingsStore] on a background thread without touching Android APIs.
 */
data class PersistedSettings(
    /** Selected UI language key, see AppLanguage.fromKey. */
    val language: String? = null,
    /** Log frequency axis on the trace chart. */
    val logFrequencyAxis: Boolean = false,
    /** Hide the "DroidVNA" watermark drawn behind the trace chart. */
    val hideWatermark: Boolean = false,
    /** Chart mode key (DB / PHASE / VSWR / SMITH). */
    val chartMode: String = "DB",
    /** Trace parameters that are switched on. */
    val enabledParams: Set<String> = setOf("S11", "S21"),
    /** Auto Y scaling; manual after a pinch and restored to auto on request. */
    val chartAutoY: Boolean = true,
    /** Marker frequencies in Hz. */
    val markerFreqs: List<Double> = emptyList(),
    /** Delta-marker readout. */
    val deltaMarkers: Boolean = false,
    /** Sweep parameters, including the device limits last seen when saving them. */
    val startHz: Long = 500_000_000L,
    val stopHz: Long = 3_000_000_000L,
    val points: Int = 401,
    val ifbwHz: Int = 10_000,
    val powerDbm: Double = -10.0,
    val twoPort: Boolean = true,
    val averaging: Int = 1,
    val logSweep: Boolean = false,
    val continuous: Boolean = true,
    /** Sweep configuration was changed by the user at least once. */
    val sweepConfigured: Boolean = false,
    /** Device limits captured together with the sweep parameters. */
    val limitMinFreqHz: Long? = null,
    val limitMaxFreqHz: Long? = null,
    val limitMinIfbwHz: Int? = null,
    val limitMaxIfbwHz: Int? = null,
    val limitMaxPoints: Int? = null,
    val limitMinPowerDbm: Double? = null,
    val limitMaxPowerDbm: Double? = null,
    val limitNumPorts: Int? = null,
    /** Collapse state of the top control row / side card column. */
    val topControlsCollapsed: Boolean = false,
    val sideControlsCollapsed: Boolean = false,
    /** Remember calibration automatically so the last good set is restored on launch. */
    val rememberCalibration: Boolean = true,
    /** Apply the restored calibration to the sweeps. */
    val calibrationEnabled: Boolean = true,
    /** Start continuous sweeping automatically once a device is connected. */
    val autoStartSweep: Boolean = true,
)