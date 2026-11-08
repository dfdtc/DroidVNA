# 更新日志

本项目从 0.3.0 起公开维护。版本号遵循 [Semantic Versioning](https://semver.org/lang/zh-CN/)，
变更记录格式参考 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)。

## [0.4.1] - 2026-11-08

### 构建与依赖

- 工具链升级：Gradle **8.14.4**、AGP **8.13.2**、Kotlin **2.4.21**，`kotlinOptions.jvmTarget` 迁移到
  `compilerOptions` DSL；compileSdk / targetSdk 升到 **36**。
- AndroidX 依赖升级：Compose BOM 2025.10.01、core-ktx 1.17.0、activity-compose 1.11.0、
  lifecycle 2.9.4、kotlinx-coroutines 1.11.0、org.json 20260814（未使用需要 AGP 9 / compileSdk 37 的版本）。
- GitHub Actions 升级到消除 Node 20 弃用告警的版本：`checkout@v7`、`setup-java@v6`、
  `setup-android@v4`、`gradle/actions/setup-gradle@v6`、`upload-artifact@v7`；
  CI 安装 `platforms;android-36` 与 `build-tools;36.0.0`。
- 新增 release 构建产物 `dist/DroidVNA-0.4.1-release.apk`（临时自签名证书，仅供测试）。


### 变更

- **应用更名为 DroidVNA**：应用名、包名（`com.droidvna.app`）、Gradle `rootProject.name`、导出目录
  （`Download/DroidVNA`）、导出文件名前缀（`droidvna_`）和界面标题全部改名，避免与第三方产品名混淆。
- **新增衍生作品声明**：README、NOTICE 和应用「关于」中明确说明本项目基于 LibreVNA-GUI 的协议、
  校准数学和 E-Cal 逻辑改写，按 GPL-3.0 发布，与 LibreVNA / Harogic 无隶属关系。


### 修复

- **E-Cal 2 端口校准**：按 LibreVNA-GUI 的流程，先把**所有用到的 E-Cal 端口设成同一个标准**
  （Open / Short / Load）再测所有 VNA 端口；此前逐端口切换会让另一个端口停留在 NONE 或上一个
  标准，导致测到的 Open/Short/Load 与系数定义不一致，SOL 误差项呈噪声状，Smith 图尤其明显。
- **THRU 前先释放所有 E-Cal 端口**，再建立 `:PORT 1 THROUGH 2`，避免残留 LOAD/OPEN 状态。
- 每次 `:PORT` 切换后用 `:PORT?` 校验实际状态，并给继电器 150 ms 稳定时间；失败会明确中止，
  不再静默继续。
- **系数流完整性校验**：`:COEFF:GET?` 流式读取必须拿到与 `:COEFF:NUM?` 完全一致的点数；
  以前截断的流会被当成有效数据，少量点插值到整个频段会直接造成杂散/噪声系数。
- 2 端口 E-Cal 校准开始前强制使用双端口扫描配置，避免 Port 1 的标准件在 1 端口模式下采集、
  其余标准件在 2 端口模式下采集的不一致。
- 明确固定手动端口映射：**VNA P1 → E-Cal P1，VNA P2 → E-Cal P2**，不使用 Auto；界面和日志中显示。

### 说明

- 这个修复针对 E-Cal 测量时序/端口状态，不改动 SOL 数学；`.cal` 导入和 1 端口 SOL 仍使用同一套误差模型。
- 需要在实际 VNB-60 + ECB-60 上复测；如果仍有杂散，请把设备页日志（包含 `E-Cal coefficients:` 一行）发出来。

## [0.4.0] - 2026-11-08

### 新增

- **中文 / English 双语 UI**：右上角齿轮 →「应用设置」→「界面语言」，支持跟随系统 / 中文 / English；
  切换立即生效并持久化到应用私有设置。
- **应用设置面板**：界面语言与关于信息（版本、协议、支持设备、GPL-3.0、上游项目）集中到右上角齿轮。
- **迹线设置面板**：图表上方和「迹线」卡片右侧均可打开，先加入**对数频率轴**（1-2-5 对数刻度），
  设置持久化，后续迹线选项继续加在此处。
- **Y 轴自动 / 手动切换**：图表上方新增 `Y auto` / `Y manual` 按钮；手动缩放后可直接恢复自动缩放。

### 改进

- 所有 Compose 文本统一走 `ui/text/Text` 包装，语言切换无需重启 Activity。
- 图表 X 轴支持线性 / 对数映射，捏合缩放、平移、十字光标和标记在对数轴下同样可用。
- 校准、设备、扫描设置、导出、E-Cal 与常见运行时消息均提供英文文案。

### 已知限制

- 英文翻译覆盖主要界面与常见消息；极少数底层设备/协议日志可能保留原文。

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
