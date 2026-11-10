/*
 * DroidVNA - native Android client for LibreVNA / Harogic B60.
 * Copyright (C) 2026 The DroidVNA Authors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.droidvna.app.ui.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Guards the English UI layer: visible labels and the runtime messages assembled
 * with string concatenation must not come out half-translated in English mode.
 */
class EnglishStringsTest {

    private fun en(text: String): String = translate(text, AppLanguage.ENGLISH)

    private fun assertTranslated(text: String) {
        val out = en(text)
        assertFalse("Still contains Chinese: $text -> $out", out.any { it in '\u4e00'..'\u9fff' })
    }

    @Test
    fun exactLabelsAreTranslated() {
        val samples = listOf(
            "设置记忆",
            "校准记忆",
            "记住扫描设置",
            "忘记已保存的校准",
            "Y 轴自动缩放",
            "当前迹线",
            "已测",
            "可跳过",
            "已停用",
            "运行",
            "双端口 SOLT · 完整 12 项误差修正",
            "计算并应用完整 SOLT",
            "THRU 归一化（仅传输）",
            "隔离（可选，两端接 50 Ω 负载）",
            "完整 SOLT 需要双端口 VNA；单端口设备请使用上面的 1 端口 SOL。",
        )
        for (sample in samples) {
            assertTranslated(sample)
        }
        assertEquals("Isolation", en("隔离"))
        assertEquals("Compute and apply full SOLT", en("计算并应用完整 SOLT"))
        assertEquals("Saved calibration forgotten", en("已忘记保存的校准"))
        assertEquals("12-term SOLT · 1 point", en("12 项 SOLT · 1 点"))
    }

    @Test
    fun assembledRuntimeMessagesAreTranslated() {
        val samples = listOf(
            "已自动载入上次校准（12 项 SOLT · 1 点 · 手动 SOLT，保存于 2026-11-10 12:00）",
            "隔离测量跳过（发送 SCPI 命令失败：timeout），按零隔离继续",
            "切换到 LOAD 失败（E-Cal 端口 2）",
            "各次捕获的扫描范围不一致（端口 1、THRU），请保持扫描设置不变后重做",
            "校准点数不足，请增大扫描点数后重做",
            "电子校准完成：完整 12 项 SOLT · 401 点 · 含隔离测量",
        )
        for (sample in samples) {
            assertTranslated(sample)
        }
        assertEquals(
            "Switching to LOAD failed (E-Cal port 2)",
            en("切换到 LOAD 失败（E-Cal 端口 2）"),
        )
    }

    @Test
    fun dynamicImporterWarningsAreTranslated() {
        val samples = listOf(
            "端口 1 缺少 Open/Short/Load 中的一项，未生成 SOL",
            "端口 2 的标准件定义缺失，未生成 SOL",
            "端口 2 的标准件没有可用的 Touchstone 数据，未生成 SOL",
            "端口 1 的 SOL 方程无解（标准件过近或数据异常）",
            "未能从 .cal 中恢复校准数据：No usable measurements",
        )
        for (sample in samples) {
            assertTranslated(sample)
        }
    }

    @Test
    fun chineseModeReturnsInputUnchanged() {
        val sample = "校准已启用 · 12 项 SOLT"
        assertEquals(sample, translate(sample, AppLanguage.CHINESE))
    }
}
