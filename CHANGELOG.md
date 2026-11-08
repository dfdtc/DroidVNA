# 更新日志

本项目从 0.3.0 起公开维护。版本号遵循 [Semantic Versioning](https://semver.org/lang/zh-CN/)，
变更记录格式参考 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)。

## [0.3.0] - 2026-11-08

首个公开版本。

### 新增

- **原生 USB Host**：通过 Android USB Host API 直接访问 LibreVNA / Harogic B60 的
  vendor-specific bulk 端点，按 LibreVNA USB 协议 v14 完成设备发现、权限申请、
  扫描配置、数据接收与设备日志读取，不需要 PC、Termux 或 root。
- **双端口实时扫描**：S11 / S21 / S12 / S22，连续扫描、单次扫描、暂停/恢复，
  支持起止频率、点数、IFBW、功率、平均、单/双端口、对数扫描。
- **触摸优先图表**：双指缩放/平移、单指十字光标、可拖动标记、峰值/谷值搜索、
  双标记 Δ、dB / 相位 / VSWR / Smith 四种视图。
- **1 端口 SOL 校准**：Open / Short / Load，计算方向性、源匹配和反射跟踪，
  按频率保存并插值；可按端口 1 / 端口 2 分别校准。
- **THRU 归一化**：对 S21 / S12 做直通响应修正。
- **LibreVNA-GUI `.cal` 导入**：解析 format 3 校准文件，支持 Touchstone 标准件
  数据和参数化 Open/Short/Load/Through，重建 1 端口 SOL + THRU 并立即应用；
  按当前 VNA 序列号隔离保存。
- **电子校准件（LibreCAL / ECB-60）**：USB SCPI 读取 FACTORY 系数，显示温度、
  稳定状态和加热功率，自动切换 Open/Short/Load/Through 完成 2 端口 SOL + THRU。
- **导出**：Touchstone `.s2p` / `.s1p`、CSV，保存到 `Download/LibreVNA`，
  支持系统分享。
- **演示模式**：内置带通滤波器 DUT 和系统误差模型，无硬件也能走通校准与导出。
- **设备页**：硬件/固件/协议版本、温度、锁定状态、USB 诊断、实时扫描速率和日志。

### 改进

- 低 IFBW 扫描时显示呼吸灯并在图表上渐进绘制已到达的点，不必等待整帧结束。
- 连续扫描采用环形滚动合并：新帧逐点覆盖旧帧，未到达的点保留上一帧数据，
  图表不再每帧清空重画。
- B60 固件 1.8.6 首次配置 bug 的自动恢复：每次下发扫描配置前执行 SA → VNA 复位，
  8 秒无数据时 watchdog 再复位一次；连接和改设置后排空旧 USB 数据。
- 校准数据按设备序列号隔离，演示模式校准不会误用到真实设备。
- 导出的 `.s2p` 保留扩展名（使用 `application/octet-stream`）。

### 已知限制

- 校准模型为 1 端口 SOL + THRU，不包含完整 12 项双端口 SOLT 误差项；
  `.cal` 导入和电子校准件自动校准也只使用该子集。
- 电子校准件固定映射 VNA P1 ↔ Cal P1、P2 ↔ Cal P2，不支持自动端口识别。
- 未实现固件更新、信号源、频谱分析、SCPI 服务器和独立的 Touchstone 校准套件导入。
- 图表为自绘 Compose Canvas，超过 1001 点未做性能优化。
- 仓库内预构建 APK 使用 debug 签名；正式分发请配置自己的 release keystore。

## 更早的版本

0.1.x / 0.2.x 为内部迭代版本，未单独维护更新日志。
