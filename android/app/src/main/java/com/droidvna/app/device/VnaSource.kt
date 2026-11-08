/*
 * DroidVNA - native Android client for LibreVNA / Harogic B60.
 * Copyright (C) 2026 The DroidVNA Authors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.droidvna.app.device

import com.droidvna.app.model.CalStandard
import com.droidvna.app.model.SweepConfig
import com.droidvna.app.model.SweepResult
import com.droidvna.app.model.VnaDeviceInfo
import com.droidvna.app.model.VnaStatus
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/** A source of calibrated-raw sweeps: either the USB device or the built-in demo DUT. */
interface VnaSource {
    val info: VnaDeviceInfo
    val status: StateFlow<VnaStatus?>
    val sweeps: SharedFlow<SweepResult>
    val logs: SharedFlow<String>
    val errors: SharedFlow<String>

    suspend fun start(config: SweepConfig, standby: Boolean): Boolean
    suspend fun initiate(): Boolean
    suspend fun stop()
    fun close()

    /** Demo mode can fake the standard connected to a port; USB mode ignores this. */
    suspend fun prepareCalStandard(port: Int, kind: CalStandard) {}
    suspend fun prepareCalThru() {}
    suspend fun clearCalPreparation() {}
}