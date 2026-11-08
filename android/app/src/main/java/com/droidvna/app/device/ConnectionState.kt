/*
 * DroidVNA - native Android client for LibreVNA / Harogic B60.
 * Copyright (C) 2026 The DroidVNA Authors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.droidvna.app.device

enum class SourceKind { USB, DEMO }

sealed interface ConnectionState {
    data object Disconnected : ConnectionState
    data class Connecting(val target: String) : ConnectionState
    data class Connected(val kind: SourceKind, val label: String) : ConnectionState
    data class Error(val message: String) : ConnectionState
}