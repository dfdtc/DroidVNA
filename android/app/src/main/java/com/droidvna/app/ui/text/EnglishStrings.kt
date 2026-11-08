/*
 * DroidVNA - native Android client for LibreVNA / Harogic B60.
 * Copyright (C) 2026 The DroidVNA Authors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.droidvna.app.ui.text

/**
 * English UI copy. Chinese remains the source language in the composables; this
 * table is the English translation layer. Multi-word fragments are also used to
 * translate runtime messages assembled with string concatenation.
 */
internal object EnglishStrings {

    val exact: Map<String, String> = mapOf(
        // Shell / navigation
        "返回" to "Back",
        "设备" to "Device",
        "校准" to "Calibration",
        "设置" to "Settings",
        "应用设置" to "App settings",
        "界面语言" to "Interface language",
        "跟随系统" to "System default",
        "切换后立即生效。" to "Takes effect immediately.",
        "关于" to "About",
        "版本" to "Version",
        "协议" to "Protocol",
        "许可" to "License",
        "非官方社区实现" to "Unofficial community implementation",
        "源代码" to "Source code",
        "USB / 状态 / 诊断" to "USB / STATUS / DIAGNOSTICS",
        "TOUCH VECTOR NETWORK ANALYZER" to "TOUCH VECTOR NETWORK ANALYZER",

        // Welcome / connect
        "把矢量网络分析仪装进口袋" to "A vector network analyzer in your pocket",
        "通过 USB-C OTG 直接连接 LibreVNA / Harogic B60，无需 PC 或共享内核容器。完整支持双端口扫描、触摸图表、标记、1 端口 SOL 校准与 Touchstone 导出。" to
            "Connect LibreVNA / Harogic B60 directly over USB-C OTG - no PC or shared-kernel container. Full 2-port sweeps, touch charts, markers, 1-port SOL calibration and Touchstone export.",
        "连接 USB VNA" to "Connect USB VNA",
        "未检测到 USB VNA（LibreVNA / Harogic B60）。" to "No USB VNA detected (LibreVNA / Harogic B60).",
        "请确认：\n• 使用支持数据传输的 USB-C OTG 转接头\n• LibreVNA / Harogic B60 已上电并连接\n• 固件协议版本为 14（旧固件可能不兼容）\n• 在系统弹窗中允许此应用访问 USB 设备" to
            "Please check:\n• Use a USB-C OTG adapter that supports data\n• LibreVNA / Harogic B60 is powered and connected\n• Firmware protocol version is 14 (older firmware may not work)\n• Allow USB access when the system prompt appears",
        "连接 USB" to "Connect USB",
        "重新扫描" to "Rescan",
        "没有硬件？先试试演示模式" to "No hardware? Try demo mode",
        "演示模式生成一个 1.75–2.65 GHz 带通滤波器的合成测量，带有真实的系统误差，可完整走一遍 SOL 校准流程。" to
            "Demo mode generates a synthetic 1.75-2.65 GHz band-pass filter measurement with realistic systematic errors, enough to walk through a complete SOL calibration.",
        "启动演示设备" to "Start demo device",
        "触摸图表" to "Touch chart",
        "双指缩放、单指读数、拖动标记" to "Pinch to zoom, tap for readout, drag markers",
        "SOL 校准" to "SOL calibration",
        "Open / Short / Load 与 THRU 归一化" to "Open / Short / Load and THRU normalization",
        "离线演示" to "Offline demo",
        "无需硬件即可体验完整流程" to "Explore the full workflow without hardware",
        "提示：USB 调试与安装权限由系统控制；应用本身只需要 USB Host 访问权限。" to
            "Note: USB debugging and install permissions are controlled by the system; the app itself only needs USB Host access.",
        "USB 诊断" to "USB diagnostics",
        "已枚举的 USB 设备（用于排查 OTG/权限问题）" to "Enumerated USB devices (for OTG/permission troubleshooting)",
        "已授权" to "Authorized",
        "未授权" to "Not authorized",
        "需要授权" to "Authorization required",
        "USB 已授权" to "USB authorized",
        "连接" to "Connect",
        "正在连接…" to "Connecting…",
        "ADC 过载：降低发射功率或增加衰减" to "ADC overload: reduce output power or add attenuation",
        "源未稳幅（unlevel）：当前频率/功率下输出不可靠" to "Source unlevel: output is unreliable at this frequency/power",
        "PLL 未锁定：检查参考时钟" to "PLL unlocked: check the reference clock",
        "已连接" to "Connected",

        // Measure
        "采集" to "Acquisition",
        "暂停扫描" to "Pause sweep",
        "开始扫描" to "Start sweep",
        "单次" to "Single",
        "速率" to "Rate",
        "点数" to "Points",
        "平均" to "Avg",
        "关" to "Off",
        "关闭" to "Close",
        "扫描设置" to "Sweep settings",
        "编辑" to "Edit",
        "起始" to "Start",
        "终止" to "Stop",
        "中频带宽" to "IFBW",
        "功率" to "Power",
        "端口" to "Ports",
        "迹线" to "Traces",
        " 个标记" to " markers",
        "＋ 标记" to "+ Marker",
        "峰值" to "Peak",
        "谷值" to "Valley",
        "清除" to "Clear",
        "未校准" to "Uncalibrated",
        "已应用" to "Applied",
        "1 端口 SOL 与 THRU 归一化可显著改善测量精度。" to "1-port SOL and THRU normalization significantly improve accuracy.",
        "打开校准向导" to "Open calibration wizard",
        "当前迹线可保存为 Touchstone .s2p/.s1p 或 CSV，方便在 PC 上继续分析。" to
            "Save the current traces as Touchstone .s2p/.s1p or CSV for further analysis on a PC.",
        "导出数据" to "Export data",
        "导出" to "Export",
        "2-port（S11/S21/S12/S22）" to "2-port (S11/S21/S12/S22)",
        "1-port（S11/S21）" to "1-port (S11/S21)",
        "扫描中" to "Sweeping",
        "待机" to "Idle",
        "单次扫描中" to "Single sweep",
        "正在采集…" to "Acquiring…",
        "等待扫描数据" to "Waiting for sweep data",
        "连接 USB VNA 或启动演示模式" to "Connect a USB VNA or start demo mode",
        "低中频带宽时首个数据点需要更长时间" to "The first point takes longer at low IFBW",
        "采集中" to "Acquiring",
        "已暂停" to "Paused",
        "缩放至全带宽" to "Zoom to full span",
        "标记" to "Marker",
        "Y 自动" to "Y auto",
        "Y 手动" to "Y manual",
        "自动" to "Auto",
        "手动" to "Manual",
        "迹线设置" to "Trace settings",
        "对数频率轴" to "Log frequency axis",
        "X 轴按对数频率显示" to "Show the X axis on a logarithmic frequency scale",
        "更多迹线设置将在这里提供。" to "More trace settings will be available here.",

        // Device
        "连接中" to "Connecting",
        "连接错误" to "Connection error",
        "启动演示" to "Start demo",
        "未连接" to "Disconnected",
        "连接 USB VNA（LibreVNA / Harogic B60）或启动演示模式。" to "Connect a USB VNA (LibreVNA / Harogic B60) or start demo mode.",
        "演示模式" to "Demo mode",
        "USB 设备" to "USB DEVICES",
        "未检测到 USB VNA" to "No USB VNA detected",
        "请使用数据 USB-C 线 / OTG 转接头，并确认设备已上电。系统 USB 权限弹窗出现时请选择允许。" to
            "Use a data-capable USB-C cable / OTG adapter and make sure the device is powered. Allow access when the system USB prompt appears.",
        "设备信息" to "DEVICE INFO",
        "硬件" to "Hardware",
        "固件" to "Firmware",
        "协议版本" to "Protocol",
        "序列号" to "Serial",
        "端口数" to "Ports",
        "频率范围" to "Frequency range",
        "最大点数" to "Max points",
        "实时状态" to "LIVE STATUS",
        "硬件状态" to "Hardware status",
        "源锁定" to "Source locked",
        "LO 锁定" to "LO locked",
        "ADC 正常" to "ADC OK",
        "输出稳幅" to "Leveled",
        "MCU 温度" to "MCU temperature",
        "第二温度" to "Secondary temp",
        "供电" to "Supply",
        "参考" to "Reference",
        "外部" to "External",
        "内部" to "Internal",
        "当前扫描" to "CURRENT SWEEP",
        "范围" to "Range",
        "点数 / IFBW" to "Points / IFBW",
        "模式" to "Mode",
        "USB 诊断（全部设备）" to "USB DIAGNOSTICS (ALL DEVICES)",
        "系统没有枚举到任何 USB 设备。" to "The system did not enumerate any USB devices.",
        "日志" to "Log",
        "清空日志" to "Clear log",
        "暂无日志" to "No log entries",
        "USB 已连接" to "USB connected",
        "断开" to "Disconnect",
        "扫描速率" to "Sweep rate",
        "数据点" to "Points",
        "状态" to "Status",
        "测量界面" to "Measure",
        "切换到演示" to "Switch to demo",

        // Calibration
        ".cal 校准文件导入" to "Import .cal calibration",
        "1 端口 SOL 校准" to "1-port SOL calibration",
        "导入电脑版 LibreVNA-GUI 导出的 .cal（format 3）。移动端会重建 .cal 中 Open/Short/Load/Through 的测量结果：1 端口 SOL + THRU 归一化会立即生效，并按当前设备序列号保存。" to
            "Import a .cal exported by desktop LibreVNA-GUI (format 3). The app reconstructs the Open/Short/Load/Through measurements as 1-port SOL + THRU normalization, applies it immediately and saves it for the current VNA serial.",
        "注意：移动端当前不做完整 12 项双端口 SOLT 误差修正；.cal 中的 2 端口校准会被折算为上述可用部分。" to
            "Note: the mobile app does not implement the full 12-term 2-port SOLT error model; 2-port data in the .cal is reduced to the subset above.",
        "处理中…" to "Processing…",
        "选择 .cal 文件" to "Choose .cal file",
        "电子校准件 · LibreCAL / ECB-60" to "E-Cal · LibreCAL / ECB-60",
        "通过 USB SCPI 自动切换 Open / Short / Load / Through，测量后直接应用校准。VID:PID 1209:4122，使用 vendor interface 2；VNA 端口 1/2 固定对应校准件端口 1/2。" to
            "Automatically switches Open / Short / Load / Through over USB SCPI and applies the calibration. VID:PID 1209:4122, vendor interface 2; VNA ports 1/2 map to E-Cal ports 1/2.",
        "端口分配（手动固定，不使用 Auto）：VNA P1 → E-Cal P1 · VNA P2 → E-Cal P2。" to
            "Port mapping (manual, no Auto): VNA P1 → E-Cal P1 · VNA P2 → E-Cal P2.",
        "检测并连接电子校准件" to "Detect and connect E-Cal",
        "电子校准件" to "E-Cal",
        "系数集" to "Coefficient sets",
        "重新加载系数" to "Reload coefficients",
        "加载系数" to "Load coefficients",
        "校准中…" to "Calibrating…",
        "开始电子校准" to "Start E-Cal calibration",
        "系数已加载，可以开始 SOLT。校准期间请勿拔插设备。" to "Coefficients loaded. You can start SOLT; do not unplug the device during calibration.",
        "断开电子校准件" to "Disconnect E-Cal",
        "提示：B60 每次切换标准会执行 SA→VNA 复位，单步约多 1 秒；整个 2 端口 SOLT 通常需要 10–20 秒。" to
            "Note: the B60 performs an SA→VNA reset for each standard, adding about 1 s per step; a full 2-port SOLT usually takes 10-20 s.",
        "依次连接 Open / Short / Load 三个标准件，测量后计算方向性、源匹配与反射跟踪误差。校准会按频率保存，并应用到之后的所有扫描。" to
            "Connect Open / Short / Load in turn. The app measures each standard, computes directivity, source match and reflection tracking, then saves the calibration by frequency and applies it to subsequent sweeps.",
        "单端口扫描（S22 不可用）" to "1-port sweep (S22 unavailable)",
        "双端口扫描" to "2-port sweep",
        "端口 1" to "Port 1",
        "端口 2" to "Port 2",
        "设备仅支持 1 端口" to "This device only supports 1 port",
        "捕获" to "Capture",
        "重测" to "Re-capture",
        "保持连接稳定" to "Keep the connection stable",
        "连接开路标准件到端口 " to "Connect the OPEN standard to port ",
        "连接短路标准件到端口 " to "Connect the SHORT standard to port ",
        "连接 50 Ω 负载到端口 " to "Connect the 50 Ω LOAD to port ",
        "用低损耗直通连接 Port 1 ↔ Port 2" to "Connect Port 1 ↔ Port 2 with a low-loss thru",
        "标准件模型（可选）" to "Standard model (optional)",
        "默认使用理想 Open / Short / Load。若标准件存在偏移延迟，可在下面填写（皮秒）。更精确的校准套件系数请使用桌面版 LibreVNA-GUI。" to
            "Ideal Open / Short / Load are used by default. Enter offset delays below (picoseconds) if your standards have them. For more accurate kit coefficients use desktop LibreVNA-GUI.",
        "Open 延迟" to "Open delay",
        "Short 延迟" to "Short delay",
        "THRU 归一化" to "THRU normalization",
        "将 Port 1 与 Port 2 直连后捕获一次 THRU，可消除 S21 / S12 的跟踪误差（0 dB 归一化）。" to
            "Connect Port 1 directly to Port 2, capture THRU once, and remove S21 / S12 tracking error (0 dB normalization).",
        "应用 THRU 归一化" to "Apply THRU normalization",
        "重新应用 THRU 归一化" to "Re-apply THRU normalization",
        "当前校准状态" to "CURRENT CALIBRATION",
        "尚未应用任何校准。" to "No calibration applied yet.",
        "校准按频率插值；大幅改变扫描范围后建议重新校准。" to "Calibration is interpolated by frequency; re-calibrate after large span changes.",
        "清除全部校准" to "Clear all calibration",
        "清除捕获" to "Clear captures",
        "设备在线" to "Device online",
        "设备离线" to "Device offline",
        "删除" to "Delete",
        "删除 THRU 归一化" to "Delete THRU normalization",
        "THRU 已捕获" to "THRU captured",
        "THRU 归一化已应用" to "THRU normalization applied",
        "THRU 数据无效（需要双端口扫描）" to "Invalid THRU data (2-port sweep required)",
        "THRU 没有扫描数据" to "No THRU sweep data",
        "没有扫描数据" to "No sweep data",
        "端口 2 校准需要双端口设备。" to "Port 2 calibration requires a 2-port device.",
        "测量标准件 · 端口 " to "Measure standards · Port ",

        // Sheets
        "扫描点数与中频带宽" to "Sweep points and IFBW",
        "可用范围 " to "Available range ",
        "所有参数在点击应用后立即下发到设备" to "All settings are sent to the device when you tap Apply",
        "功率与平均" to "Power and averaging",
        "激励功率" to "Source power",
        "平均次数" to "Averaging",
        "更小的 IFBW 降低噪声，但首帧和扫描更慢。" to "Lower IFBW reduces noise but makes the first frame and the sweep slower.",
        "当前设备最低 " to "Device minimum ",
        "对数频率扫描" to "Log frequency sweep",
        "点数按对数分布，适合宽频带" to "Points are spaced logarithmically; useful for wide spans",
        "连接后自动连续扫描" to "Start continuous sweep after connecting",
        "关闭时使用「单次」按钮触发" to "When off, use the Single button to trigger a sweep",
        "应用并开始扫描" to "Apply and start sweep",
        "取消" to "Cancel",
        "频率必须是数字（MHz）" to "Frequency must be a number (MHz)",
        "终止频率必须大于起始频率" to "Stop frequency must be greater than start frequency",
        "起始频率低于设备下限 " to "Start frequency is below the device minimum ",
        "终止频率高于设备上限 " to "Stop frequency is above the device maximum ",
        "中频带宽（IFBW）" to "IF bandwidth (IFBW)",
        "导出测量数据" to "Export measurement data",
        "还没有扫描数据" to "No sweep data yet",
        "原始数据" to "Raw data",
        "已校准数据" to "Calibrated data",
        "当前是单端口扫描：.s2p 中的 S12 / S22 会写为 0，建议导出 .s1p。" to
            "This is a 1-port sweep: S12 / S22 in .s2p will be written as 0; exporting .s1p is recommended.",
        "文件扩展名 ." to "File extension .",
        "保存" to "Save",
        "分享" to "Share",
        "保存位置：Download/DroidVNA（Android 10 及以上）。也可以点分享发送到其他应用。" to
            "Saved to Download/DroidVNA (Android 10+). You can also use Share to send it to another app.",

        // Messages / runtime
        "电子校准件已断开" to "E-Cal disconnected",
        "检测到 " to "Detected ",
        "，点击连接" to ", tap Connect",
        "，请在设备页连接" to ", connect it on the Device page",
        "请先连接 USB VNA，再导入 .cal（校准会按当前设备序列号保存）" to
            "Connect a USB VNA before importing a .cal (calibration is saved for the current VNA serial).",
        "无法打开文件" to "Unable to open the file",
        "已导入 " to "Imported ",
        "导入失败：" to "Import failed: ",
        ".cal 记录的是 " to ".cal was recorded on ",
        "，当前设备是 " to ", current device is ",
        "，精度可能受影响" to "; accuracy may be affected",
        "没有发现 LibreCAL / ECB-60（1209:4122）" to "No LibreCAL / ECB-60 found (1209:4122)",
        "USB 权限被拒绝" to "USB permission denied",
        "设备没有报告有效端口数" to "The device did not report a valid port count",
        "已连接 " to "Connected ",
        " · 无系数集" to " · no coefficient sets",
        " · 系数集 " to " · coefficient sets ",
        " · 加热 " to " · heater ",
        "连接电子校准件失败：" to "Failed to connect E-Cal: ",
        "请先连接电子校准件" to "Connect the E-Cal first",
        "设备没有可用系数集" to "The device has no coefficient sets",
        "正在加载系数 " to "Loading coefficients ",
        "已加载 " to "Loaded ",
        " 组系数（" to " coefficient sets (",
        "加载系数失败：" to "Failed to load coefficients: ",
        "请先连接 USB VNA 再开始电子校准" to "Connect a USB VNA before starting E-Cal calibration",
        "请先加载电子校准件系数" to "Load the E-Cal coefficients first",
        "没有可用的校准端口" to "No usable calibration ports",
        "系数不完整，缺少：" to "Incomplete coefficients, missing: ",
        "正在执行电子校准…" to "Running E-Cal calibration…",
        "电子校准件正在升温，等待温度稳定…" to "E-Cal is warming up; waiting for temperature stability…",
        "温度未完全稳定，继续校准" to "Temperature is not fully stable; continuing",
        "电子校准完成：SOL " to "E-Cal complete: SOL ",
        " 端口 · THRU " to " ports · THRU ",
        " 组" to " groups",
        "电子校准失败：" to "E-Cal calibration failed: ",
        "捕获失败：" to "Capture failed: ",
        "没有收到扫描数据，请确认设备正在扫描" to "No sweep data received; make sure the device is sweeping",
        "已捕获 " to "Captured ",
        " 点 · " to " points · ",
        "（端口 " to " (port ",
        "请先捕获 Open / Short / Load" to "Capture Open / Short / Load first",
        "三次捕获的点数不一致，请保持扫描设置不变后重做" to
            "The three captures have different point counts; keep the sweep settings unchanged and capture again",
        "端口 " to "Port ",
        " SOL 校准已应用" to " SOL calibration applied",
        "请先捕获 THRU" to "Capture THRU first",
        "校准已清除" to "Calibration cleared",
        "已清除捕获数据" to "Capture data cleared",
        "还没有可导出的扫描数据" to "No complete sweep data to export",
        "已保存：" to "Saved: ",
        "保存失败：" to "Save failed: ",
        "分享失败：" to "Share failed: ",
        "没有有效数据" to "no valid data",
        "不一致" to "mismatch",
        "失败" to "failed",
        "成功" to "success",
        "未知" to "unknown",
        "没有" to "No ",

        // Device / protocol errors
        "USB 设备已断开" to "USB device disconnected",
        "USB 访问被拒绝。请在系统弹窗中允许访问该 USB 设备。" to
            "USB access denied. Allow access to this USB device in the system prompt.",
        "USB 连接失败: " to "USB connection failed: ",
        "Demo 启动失败: " to "Demo start failed: ",
        "B60: SA → VNA 模式复位…" to "B60: SA → VNA mode reset…",
        "B60: 扫描未启动，执行 SA 模式复位…" to "B60: sweep did not start; performing SA mode reset…",
        "B60 自动恢复失败：请重新插拔设备，或先切到 SA 模式再切回 VNA" to
            "B60 auto-recovery failed: replug the device, or switch to SA mode and back to VNA",
        "设备没有回复 DeviceInfo（固件协议不匹配？）" to "Device did not reply to DeviceInfo (firmware protocol mismatch?)",
        "设备缺少 LibreVNA 所需的 bulk 端点" to "Device is missing the bulk endpoints required by LibreVNA",
        "无法占用 USB 接口 0（可能被其他应用占用）" to "Unable to claim USB interface 0 (it may be in use by another app)",
        "无法打开 USB 设备（权限或占用问题）" to "Unable to open the USB device (permission or busy)",
        "设备拒绝了扫描设置" to "Device rejected the sweep settings",
        "设备拒绝扫描设置" to "Device rejected the sweep settings",
        "无法启动扫描" to "Unable to start the sweep",
        "无法启动单次扫描" to "Unable to start a single sweep",
        "标准件过于相似，无法计算 SOL（检查连接）" to "Standards are too similar to solve SOL (check connections)",
        "缺少 calkit（校准套件定义）" to "Missing calkit (calibration kit definition)",
        "calkit 中没有 standards" to "calkit has no standards",
        "文件缺少 format 3 字段" to "File is missing format 3 fields",
        "不是有效的 JSON .cal 文件" to "Not a valid JSON .cal file",
        "系数 " to "Coefficient ",
        " 不存在或点数为 0" to " does not exist or has zero points",
        "系数没有有效数据" to "coefficient has no valid data",
        "P12_THROUGH 系数没有有效数据" to "P12_THROUGH coefficient has no valid data",
        "缺少 P12_THROUGH 系数" to "Missing P12_THROUGH coefficient",
        "切换到 THROUGH 失败" to "Failed to switch to THROUGH",
        "捕获点数不足" to "Not enough captured points",
        "无法写入下载目录" to "Unable to write to the Downloads folder",
        "无法在下载目录创建文件" to "Unable to create a file in the Downloads folder",
        "无法占用电子校准件" to "Unable to claim the E-Cal",

        // Chart / misc
        "温度 " to "Temperature ",
        "已稳定" to "stable",
        "升温中" to "warming up",
        "状态未知" to "unknown",
        "已发现 " to "Found ",
        " 个电子校准件" to " E-Cal units",
        "未发现电子校准件（需要 OTG Hub 同时连接 VNA 与校准件）" to
            "No E-Cal found (use an OTG hub to connect the VNA and E-Cal together)",
        "个电子校准件" to " E-Cal units",
        " 端口" to " ports",
        "计算并应用" to "Compute and apply ",
        "移除端口 " to "Remove port ",
        " SOL 计算无解" to " SOL solution failed",
        " 没有扫描数据" to " has no sweep data",
        " 没有有效数据" to " has no valid data",
        "图表上的迹线显示选项" to "Trace display options",
        "频率轴" to "Frequency axis",
        "原生 Android USB Host 实现 · 协议 v14" to "Native Android USB Host · protocol v14",
        "支持 LibreVNA V1 / P2 / PT、Harogic B60 与 LibreCAL / ECB-60" to
            "Supports LibreVNA V1 / P2 / PT, Harogic B60 and LibreCAL / ECB-60",
        "非官方应用，与 LibreVNA 项目及 Harogic 无隶属关系。LibreVNA 硬件、协议与桌面版 LibreVNA-GUI 由 Jan Käberich 及贡献者开发。" to
            "Unofficial app, not affiliated with the LibreVNA project or Harogic. LibreVNA hardware, protocol and the LibreVNA-GUI desktop application were created by Jan Käberich and contributors.",
        "本应用基于 LibreVNA-GUI 的协议与校准逻辑改写，按 GNU GPL-3.0 发布。" to
            "This app is a derived work based on the LibreVNA-GUI protocol and calibration logic, released under GNU GPL-3.0.",
        "LibreVNA / Harogic VNA client for Android" to "LibreVNA / Harogic VNA client for Android",
        " · 频率以 Hz 表示，50 Ω 参考阻抗" to " · frequency in Hz, 50 Ω reference",
        " 已捕获（端口 " to " Captured (port ",
        " 点" to " points",
        "不支持的 .cal 格式：" to "Unsupported .cal format: ",
        "（仅支持 format 3）" to " (only format 3 is supported)",
        "发送 SCPI 命令失败：" to "SCPI command failed: ",
        "无法占用电子校准件 " to "Unable to claim E-Cal ",
        "（内核驱动？）" to " (kernel driver?)",
        "无法打开电子校准件（权限或占用问题）" to "Unable to open the E-Cal (permission or busy)",
        "无法解析 " to "Unable to parse ",
        " 第 " to " point ",
        " 点：" to ": ",
        "导入" to "Import",
        "导出校准后的数据" to "Export calibrated data",
        "应用" to "Apply",
        "扫描" to "Sweep",
        "无效" to "Invalid",
        "无法" to "Unable to ",
        "电子校准件缺少 bulk 端点" to "E-Cal is missing bulk endpoints",
        "缺少系数 " to "Missing coefficient ",
        "测量 S11 / S21 / S12 / S22；关闭后仅激励端口 1" to
            "Measures S11 / S21 / S12 / S22; when off only port 1 is driven",

        // Measure header / overflow menu / watermark
        "菜单" to "Menu",
        "设备" to "Device",
        "收起迹线控件" to "Hide trace controls",
        "展开迹线控件" to "Show trace controls",
        "收起迹线与图表控件" to "Hide trace and chart controls",
        "展开迹线与图表控件" to "Show trace and chart controls",
        "收起采集控件" to "Hide acquisition controls",
        "展开采集控件" to "Show acquisition controls",
        "关闭应用水印" to "Disable app watermark",
        "关闭后迹线图背景不再显示半透明的 DroidVNA 标识。" to
            "When enabled, the faint DroidVNA mark behind the trace chart is hidden.",
        "界面" to "Interface",
        "TOUCH VECTOR NETWORK ANALYZER" to "TOUCH VECTOR NETWORK ANALYZER",

    )

    /**
     * Longest keys first, so "端口 " wins over "端口" and "已连接 " wins over "连接".
     * This is used only when a runtime string (often built with +) has no exact match.
     */
    private val fragments: List<Pair<String, String>> = exact.entries
        .filter { (zh, _) -> zh.length >= 2 && zh.any { it in '\u4e00'..'\u9fff' } }
        .sortedByDescending { it.key.length }
        .map { it.key to it.value }

    private val patterns: List<Pair<Regex, (MatchResult) -> String>> = listOf(
        Regex("^切换到 (.+) 失败$") to { m -> "Failed to switch to " + m.groupValues[1] },
        Regex("^不支持的 \\.cal 格式：(.*)（仅支持 format 3）$") to { m ->
            "Unsupported .cal format: " + m.groupValues[1] + " (only format 3 is supported)"
        },
        Regex("^无法解析 (\\S+) 第 (\\d+) 点：(.*)$") to { m ->
            "Unable to parse " + m.groupValues[1] + " point " + m.groupValues[2] + ": " + m.groupValues[3]
        },
    )

    fun translateWords(text: String): String {
        for ((pattern, replacement) in patterns) {
            val match = pattern.matchEntire(text) ?: continue
            return replacement(match)
        }
        var out = text
        for ((zh, en) in fragments) {
            if (out.contains(zh)) out = out.replace(zh, en)
        }
        // Clean punctuation that is still Chinese after phrase replacement.
        out = out.replace('，', ',').replace('。', '.').replace('：', ':').replace('（', '(').replace('）', ')')
        return out
    }
}