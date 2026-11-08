# LibreVNA Mobile

**LibreVNA / Harogic B60 的原生 Android 触屏客户端** — 通过 USB-C OTG 直接把 VNA 接到平板/手机，不需要 PC、Termux 或共享内核容器。使用 Android USB Host API 直接访问设备的 vendor-specific bulk 端点，按 LibreVNA USB 协议 v14 完成扫描设置、数据接收、校准与导出；同时支持导入电脑版 `.cal`，以及 LibreCAL / Harogic ECB-60 电子校准件的自动 1 端口 SOL + THRU 校准。界面内置 **中文 / English** 两套文案，可在右上角设置中随时切换。

> **English** — A touch-first, native Android USB host client for LibreVNA and Harogic B60 vector network analyzers. It speaks the LibreVNA USB protocol v14 directly (no PC, no Termux), supports 1-port SOL + THRU calibration, LibreVNA-GUI `.cal` import, and LibreCAL / ECB-60 electronic calibration. Licensed under GPL-3.0.

> 本项目是独立实现，不是 LibreVNA 官方 Android 版，也不包含桌面 Qt GUI。硬件与协议来自 [LibreVNA](https://github.com/jankae/LibreVNA) 开源项目（GPLv3）。

![测量界面](docs/screenshots/measure.png)

## 目录

- [项目状态](#项目状态)
- [功能](#功能)
- [截图](#截图)
- [安装](#安装)
- [连接 USB VNA](#连接-usb-vna)
- [触屏操作](#触屏操作)
- [设置](#设置)
- [校准](#校准)
- [导出](#导出)
- [构建](#构建)
- [目录结构](#目录结构)
- [协议实现要点](#协议实现要点)
- [已知限制 / 路线图](#已知限制--路线图)
- [故障排查](#故障排查)
- [测试](#测试)
- [贡献](#贡献)
- [许可与致谢](#许可与致谢)

## 项目状态

- 当前版本 **0.4.1**（versionCode 6），要求 Android 8.0（API 26）及以上。
- 仓库内提供预构建的 debug APK：[dist/LibreVNA-Mobile-0.4.1-debug.apk](dist/LibreVNA-Mobile-0.4.1-debug.apk)，也可以从 GitHub Releases 下载。
- 已在 Xiaomi Pad（Android 17）+ Harogic B60（FW 1.8.6）上完成真机验证；ECB-60（FW 0.3.2）的 USB、系数读取和校准流程已实机跑通，但 0.4.0 及之前的校准结果存在 Smith 杂散，0.4.1 已改为 all-port 标准切换，等待实机复测；LibreVNA 官方硬件尚未上机实测。
- 变更记录见 [CHANGELOG.md](CHANGELOG.md)，发布流程见 [docs/RELEASING.md](docs/RELEASING.md)。

## 功能

- **实时双端口扫描**：S11 / S21 / S12 / S22，连续扫描或单次扫描，可暂停/恢复。
- **慢速扫描进度可见**：低 IFBW 下首帧较慢时，侧栏「采集」和图表上都有呼吸灯指示正在运行；扫描过程中已到达的点会实时画到图上，不必等整帧结束。
- **环形滚动更新**：连续扫描时不再每帧清空重画。新一帧的点逐个覆盖上一帧，尚未到达的点保留上一帧的值，因此图表始终显示完整曲线，读图不会被打断。
- **中英文界面**：右上角齿轮 →「应用设置」→「界面语言」，可选跟随系统 / 中文 / English，切换立即生效并持久化；关于信息也在同一面板。
- **Y 轴自动 / 手动**：图表上方的 `Y auto` 按钮在自动缩放和手动缩放之间切换；手动缩放后点一下即可恢复自动。
- **触摸优先的图表**：
  - 双指缩放 / 平移（横轴与纵轴）
  - 单指点击/拖动显示十字光标读数
  - 拖动标记线改频点，点 `FIT` 恢复全带宽
  - dB、相位、VSWR、Smith 圆图四种显示
  - 迹线设置中可切换**对数频率轴**，X 轴按 1-2-5 对数刻度显示
- **标记**：任意添加、拖动、删除；峰值/谷值搜索；双标记 Δ 差值。
- **扫描设置**：起止频率、点数、IFBW、功率、平均次数、双端口/单端口、对数扫描、自动连续扫描。B60 的 IFBW 可选 **15 Hz – 75 kHz**。
- **1 端口 SOL 校准**：Open / Short / Load，计算方向性、源匹配和反射跟踪误差，按频率保存并插值应用；可选手动延迟。校准按设备序列号隔离，不会把演示/其他设备的校准套到 B60 上。
- **THRU 归一化**：对 S21 / S12 做直通响应校准。
- **电脑 .cal 导入**：读取 LibreVNA-GUI format 3 的 .cal，从桌面版导出的校准文件在移动端重建 1 端口 SOL + THRU 并立即应用；按当前 VNA 序列号保存。
- **电子校准件（LibreCAL / ECB-60）**：通过 USB SCPI 读取 FACTORY 系数，自动切换 Open/Short/Load/Through，测量后直接应用 1 端口 SOL + THRU；支持温度/稳定状态显示。
- **导出**：Touchstone `.s2p` / `.s1p`、CSV，保存到 `Download/LibreVNA`，也可通过系统分享发送。
- **演示模式**：内置 1.75–2.65 GHz 带通滤波器 DUT 和真实的系统误差模型，没有硬件也能完整走通 SOL 校准与导出。
- **设备页**：硬件/固件/协议版本、锁定与温度状态、USB 诊断、设备日志。
- **协议 v14**：支持 LibreVNA V1 / P2 / PT 与 Harogic B60（同一 USB 协议，具体以设备 DeviceInfo 为准）。
- **即插即用**：同时识别 LibreVNA 的 `0483:564E`、`0483:4121`、`1209:4121` 和 Harogic B60 的 `367F:0200`，USB attach intent 与权限流程一致；电子校准件识别 `1209:4122`（LibreCAL / ECB-60）。

## 截图

| 连接页 | 校准 |
| --- | --- |
| ![连接页](docs/screenshots/welcome.png) | ![校准](docs/screenshots/calibration.png) |

| Smith 圆图 | 设备页 |
| --- | --- |
| ![Smith](docs/screenshots/smith.png) | ![设备](docs/screenshots/device.png) |

**电子校准件实机（ECB-60，FW 0.3.2；FACTORY 系数加载与校准流程；0.4.0 结果为 Smith 杂散，0.4.1 已修复测量时序、待复测）：**

![电子校准件](docs/screenshots/ecal.png)

**Harogic B60 实机（401 点 / 75 kHz IFBW，端口开路）：**

![Harogic B60](docs/screenshots/b60.png)

**环形滚动更新（B60 实机，1 kHz / 401 点，当前帧已采集 209/401 点；右侧仍是上一帧，曲线不会被清空）：**

![低 IFBW 扫描进度](docs/screenshots/low_ifbw.png)

**English UI / 应用设置 / 迹线设置：**

| English UI | 应用设置 |
| --- | --- |
| ![English UI](docs/screenshots/measure-en.png) | ![应用设置](docs/screenshots/settings.png) |

![迹线设置](docs/screenshots/trace-settings.png)

## 安装

已构建的 debug APK（仓库内）：

```
dist/LibreVNA-Mobile-0.4.1-debug.apk
大小: 16,936,059 字节
SHA-256: FC6E9A6A1B7A61073A02D64BF3977DA1428BC1DDF700AB8EDB254EDFB4DC1EFF
```

```bash
adb install -r dist/LibreVNA-Mobile-0.4.1-debug.apk
```

也可以从仓库的 **Releases** 页面下载同一版本。debug APK 使用 Android 默认 debug 签名，适合测试；正式分发请按 [docs/RELEASING.md](docs/RELEASING.md) 使用自己的 release keystore。

小米/红米/HyperOS 如果报：

```
INSTALL_FAILED_USER_RESTRICTED: Install canceled by user
```

请使用 shell 安装方式（本机验证可用）：

```bash
adb push dist/LibreVNA-Mobile-0.4.1-debug.apk /data/local/tmp/librevna.apk
adb shell pm install -r -t /data/local/tmp/librevna.apk
```

或在开发者选项中打开 **USB 安装 / 通过 USB 安装应用**。应用本身只需要 USB Host 访问权限，不需要 root。

要求：

- Android 8.0（API 26）及以上
- 支持 USB Host / OTG 的平板或手机
- 支持数据传输的 USB-C 线 / OTG 转接头
- LibreVNA / Harogic B60 固件协议版本 14（旧固件请先用桌面版 LibreVNA-GUI 升级）

## 连接 USB VNA

1. 用 OTG 转接头连接 LibreVNA 或 Harogic B60 与平板/手机，设备上电。
   - Harogic B60 / VNB-60 在系统里显示为 **B60**，VID:PID 为 `367F:0200`。
2. 打开应用；顶部状态为 **OFFLINE** 时点击它进入「设备」页。
3. 在「USB 设备」中点击 **连接**。
4. 系统弹出 USB 权限请求时选择允许。
5. 连接成功后状态变为 **USB**，设备页显示硬件、固件、温度、锁定状态和实时扫描速率。

> LibreVNA / B60 的 USB 电流预算约 500 mA。部分手机会限流，建议使用带外部供电的 USB-C Hub。设备页的「USB 诊断」会列出系统枚举到的所有 USB 设备及其授权状态，便于排查 OTG/线材问题。

### Harogic B60 固件 1.8.6 的首次配置问题与自动恢复

B60（VNB-60，FW 1.8.6，HW 0xD0）有一个已知的固件行为：连接后的**第一次 VNA SweepSettings 会被 ACK**，但扫描没有真正启动；2 秒后固件超时并重新初始化，表现为日志里每 2 秒一次 status、0 个 datapoint。桌面版 LibreVNA-GUI 的规避方式是切到 SA 模式再切回 VNA，或断开重连。

本应用在 B60 的**每次下发扫描配置前**自动执行 SA → VNA 模式复位（发送一帧合法的 SA 配置，再 SetIdle，再下发 VNA 配置），包括连接、修改 IFBW/点数/频率和恢复扫描；因此用户不需要手动切 SA，低 IFBW 切回高 IFBW 后也不会停留在慢速状态。若复位仍失败，后台 watchdog 会在 8 秒无数据时再执行一次 SA 复位；仍失败会在应用「设备 → 日志」里提示重新插拔。8 秒而不是按 IFBW 无限放大，是为了避免把真正在缓慢扫描的低带宽任务误判成卡死（正常扫描的首个数据点仍在毫秒级到达）。

- B60 默认 **75 kHz IFBW**（规格范围 **15 Hz – 75 kHz**）和 **401 点**；实测 75 kHz 连续扫描约 **19–20 次/秒**。B60 的 DeviceInfo 会误报 11 Hz – 93.5 kHz，应用已按规格覆盖为 15 Hz – 75 kHz。
- 这个规避只改变设备当前的 SA/VNA 模式，不修改 B60 的持久 DeviceConfiguration 或校准数据。
- 如果 Harogic 后续提供修复固件，可以去掉这段复位逻辑。
- 低 IFBW（如 1 kHz、100 Hz、15 Hz）会显著增加每点积分时间：首帧和整帧都更慢，但应用会边扫边画，并在「采集」旁显示呼吸灯，不会看起来像卡死。

## 触屏操作

- **单指**：在图表上点击或拖动 = 十字光标读数；在标记线附近拖动 = 移动标记。
- **双指**：捏合缩放；双指拖动平移。
- **FIT**（图表右上角）：恢复全带宽/自动纵轴。
- **双击**：也可重置视图。
- **标记**：侧栏「＋ 标记」在带宽中心加标记；「峰值」「谷值」自动搜索；标记卡片上的 × 删除。
- **暂停**：右上/侧栏的大按钮暂停或恢复连续扫描；「单次」触发一次 standby 扫描。
- **Y 轴**：图表上方 `Y auto` / `Y manual` 按钮切换自动与手动缩放；手动捏合缩放后点一下即可恢复自动。
- **迹线**：图表上方或「迹线」卡片右侧的齿轮打开迹线设置。

## 设置

### 应用设置

点击右上角齿轮打开「应用设置」：

- **界面语言**：跟随系统 / 中文 / English，切换后立即生效，并保存到应用私有 SharedPreferences。
- **关于**：应用版本、Android USB Host 实现、协议版本、支持设备、GPL-3.0 与上游项目信息。

### 迹线设置

图表上方或「迹线」卡片右侧的齿轮打开。当前包含：

- **对数频率轴**：X 轴按对数频率显示，使用 1-2-5 对数刻度；关闭后恢复线性轴。设置会持久化。

后续的迹线显示选项会继续加在这个面板中。

## 校准

### 从电脑导入 .cal

1. 在电脑版 LibreVNA-GUI 完成校准（SOL / SOLT / THRU），保存为 `.cal`（format 3）。
2. 把 `.cal` 拷到平板（USB、网盘、LocalSend 均可）。
3. 平板先连接 VNA，打开「校准 → .cal 校准文件导入 → 选择 .cal 文件」。
4. 应用解析 calkit 和测量点，重建 1 端口 SOL + THRU 归一化，并按当前 VNA 序列号保存。

限制：移动端当前只应用 1 端口 SOL + THRU，不使用 `.cal` 中的完整 12 项双端口误差项；如果 `.cal` 记录的是另一台 VNA，界面会提示，精度可能下降。calkit 支持 Touchstone 数据，也支持纯参数化 Open/Short/Load（C/L/Cfirst 等）以及参数化 Through（Z0/delay/loss）。

### 电子校准件 LibreCAL / ECB-60

1. 用带外部供电的 OTG Hub 同时连接 VNA 与校准件（ECB-60 有加热器，手机直供可能不够）。
2. 「校准 → 电子校准件」→「检测并连接」，允许 USB 权限，确认 IDN / FW / 端口 / 温度。
3. 选择系数集（通常 `FACTORY`）→「加载系数」，等待进度到 100%。
4. 固定手动映射 VNA P1 ↔ Cal P1、P2 ↔ Cal P2，**不使用 Auto**；点「开始电子校准」。
5. 应用会先把**所有用到的 E-Cal 端口设成同一个标准**（Open/Short/Load），再测所有 VNA 端口；THRU 前先全部设回 NONE，再建立 P1→P2 直通；结束把所有 cal 端口设回 NONE。

注意：温度未稳定时会等待最多 90 秒；B60 每次切换标准会执行 SA→VNA 复位，整体耗时比桌面版长。端口状态每次都会用 `:PORT?` 校验，系数流必须完整；否则会中止或回退逐点读取，避免截断系数造成“杂散”校准。

### 手动 1 端口 SOL

1. 连接设备或启动演示模式。
2. 打开「校准」页，选择端口 1 或端口 2。
3. 按提示依次连接 **Open / Short / Load**（端口 2 会自动切换为双端口扫描）。
4. 每次点击「捕获」，应用会等待下一次完整扫描并保存原始数据。
5. 点「计算并应用端口 N SOL」。
6. 如需修正 S21/S12 响应，把 Port 1 与 Port 2 直连，捕获 THRU 后点「应用 THRU 归一化」。

实现说明：

- 1 端口误差模型：`Γm = e00 + e01e10·Γa / (1 - e11·Γa)`。用 Open/Short/Load 三组测量解出 e00、e11、e01e10，再按频率插值应用到 S11 或 S22。
- 标准件默认按理想 Open（Γ=+1）、Short（Γ=-1）、Load（Γ=0）处理，可填写 Open/Short 的偏移延迟（ps）。
- 校准按频率保存到应用私有目录，重启后仍生效；改变扫描范围后建议重新校准。
- **不是**完整的 12 项双端口误差修正；.cal 导入和 E-Cal 自动校准同样只使用 1 端口 SOL + THRU 子集，不包含完整的双端口误差项、源/接收机幅度校准和校准套件高阶系数。需要计量级结果请使用桌面版 LibreVNA-GUI。

## 导出

在测量页或顶部保存按钮打开「导出测量数据」：

- Touchstone `.s2p`：`# Hz S RI R 50`，列为 S11 S21 S12 S22（每列 re/im）。
- Touchstone `.s1p`：仅 S11。
- CSV：频率 + 各参数的 dB / 相位。
- 保存位置：`Download/LibreVNA`（Android 10+，MediaStore）；Android 8/9 保存到应用外部目录。
- 分享按钮：通过系统分享面板发送文件。

文件名形如 `librevna_20261107_205533_cal.s2p`。`cal` 表示当前数据已应用校准，`raw` 表示未校准。

## 构建

环境：

- JDK **17 或 21**（本机构建使用 Corretto 21；Gradle 8.10.2 与 JDK 25 不兼容，Android Studio 自带 JBR 25 不能直接用于本项目）
- Android SDK Platform 35、Build-Tools 35.0.0
- Gradle 8.10.2（项目自带 wrapper）

```bash
cd android
cp local.properties.example local.properties
# 编辑 local.properties 中的 sdk.dir 为本机 Android SDK 路径
export JAVA_HOME=/path/to/jdk-21
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

APK 输出：

```
android/app/build/outputs/apk/debug/app-debug.apk
```

Windows 示例：

```powershell
$env:JAVA_HOME = "C:\path\to\jdk-21"
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
cd android
Copy-Item local.properties.example local.properties
.\gradlew.bat assembleDebug
.\gradlew.bat testDebugUnitTest
```

### Release 签名

仓库内 APK 使用 debug 签名。要生成可正式分发的 APK：

```bash
cd android
cp keystore.properties.example keystore.properties
# 填入 keystore 路径、storePassword、keyAlias、keyPassword
./gradlew assembleRelease
```

`keystore.properties`、`*.jks`、`*.keystore` 已在 `.gitignore` 中。请离线备份 keystore 和密码；丢失后无法覆盖升级已安装的应用。完整步骤见 [docs/RELEASING.md](docs/RELEASING.md)。

技术栈：Kotlin 2.1、Jetpack Compose（Material 3）、AGP 8.8、compileSdk 35、minSdk 26。没有 native 库，APK 可直接安装到 arm64 / x86_64 设备。

持续集成：[.github/workflows/android.yml](.github/workflows/android.yml) 在每次 push / PR 时运行单元测试并构建 debug APK。

## 目录结构

```
android/
  app/src/main/java/com/librevna/mobile/
    MainActivity.kt              Activity、USB attach intent
    MainViewModel.kt             UI 状态、标记、校准会话、导出
    LibreVnaApplication.kt       Application 级 USB / Repository
    usb/
      Protocol.kt                v14 编解码：帧、CRC、SweepSettings、DeviceInfo、Datapoint
      LibrevnaUsbLink.kt         USB bulk 收发、Ack/Nack、设备日志
      UsbVnaSource.kt            USB 设备 -> VnaSource
      UsbVnaManager.kt           设备枚举、权限、attach/detach
      SweepAssembler.kt          按 point/stage 重组完整 sweep
      EcalDevice.kt               LibreCAL / ECB-60 SCPI USB 驱动、系数读取与端口切换
    device/
      VnaSource.kt / DemoVnaSource.kt
      VnaRepository.kt           连接、扫描控制、平均、校准应用、持久化
    model/                       Complex、SweepConfig/Result、SOL/THRU 校准数学、.cal 导入、E-Cal 系数解析
    ui/                          Compose 页面、图表、底部面板、主题
      text/                      中英文文案、翻译 Text 包装、语言状态
      screens/                   测量 / 设备 / 校准页面
      sheets/                    扫描、导出、应用设置、迹线设置
    export/                      Touchstone/CSV、MediaStore/分享
    data/CalibrationStore.kt     JSON 持久化
  app/src/test/                  JVM 单元测试（21 个）
docs/
  screenshots/                   界面截图
  RELEASING.md                   发布流程
.github/                         CI、issue / PR 模板
dist/                            预构建 APK
```

## 协议实现要点

- USB 接口 0（vendor specific），bulk 端点：
  - `0x01` OUT 数据
  - `0x81` IN 数据
  - `0x82` IN 设备日志
- 帧：`0x5A | uint16 length LE | uint8 type | payload | uint32 CRC32`；VNADatapoint 的 CRC 为 0。
- `SweepSettings` 31 字节 packed C 结构，bitfield 按 LSB 排列：
  - standby / syncMaster / suppressPeaks / fixedPower / logSweep / syncMode
  - stages、port1Stage…port4Stage
  - 起止频率、点数、IFBW、起止功率、dwell
- 一个 `VNADatapoint` 可能包含该点的所有 stage；每个 stage 有 P1、P2、Ref 三个复数，descriptor 低 5 位为接收机 mask（bit0 P1、bit1 P2、bit4 Ref），高 3 位为 stage。
- 设备 VID/PID：LibreVNA `0483:564E`、`0483:4121`、`1209:4121`；Harogic B60 `367F:0200`（设备产品字符串 `B60`）；LibreCAL / ECB-60 电子校准件 `1209:4122`（SCPI 在 interface 2，vendor class 0xFF，bulk 0x03/0x83）。
- LibreVNA-GUI 的 USB VNA 驱动实际只有 LibreVNA（含 V1/P2/PT）和 Harogic B60 两种；`SNA5000A`、`SSA3000X` 是 TCP/SCPI 仪器而非 USB VNA，本应用暂未实现。

## 已知限制 / 路线图

- 没有固件更新、手动控制、信号源、频谱分析、完整 12 项双端口校准、幅度/频率校准和 SCPI 服务器。
- 没有独立的 Touchstone 校准套件文件导入；`.cal` 导入仅重建 1 端口 SOL + THRU，不包含完整 SOLT 误差项。
- E-Cal 固定 VNA P1/P2 ↔ Cal P1/P2 映射，不包含自动端口识别；需要带外部供电的 OTG Hub。
- 图表为自绘 Compose Canvas，极大点数（>1001）未做性能优化。
- 仓库内预构建 APK 为 debug 签名；正式分发请配置自己的 release keystore。
- 在手机上使用前建议先用演示模式确认布局与操作。
- 英文翻译覆盖主要界面和常见消息；极少数底层设备/协议日志可能保留原文。

## 故障排查

| 现象 | 处理 |
| --- | --- |
| 设备页没有 USB VNA | 换数据线/OTG 转接头；确认设备上电；点「重新扫描」；查看 USB 诊断里是否枚举到 `0483:564E` 或 `367F:0200` |
| 连接后一直 CONNECTING | 检查系统 USB 权限弹窗；关闭桌面版 LibreVNA-GUI（可能已占用接口）；重新插拔 |
| 设备回复 DeviceInfo 失败 | 固件协议可能不是 v14；用桌面版升级固件 |
| 扫描暂停/速率不变 | 点「开始扫描」；单次模式后需要再点开始扫描才会连续扫描 |
| 小米/HyperOS 安装失败 | 用 `adb push` + `pm install`，或打开开发者选项的 USB 安装 |
| 导出文件带 `.txt` 后缀 | 旧版本问题；当前版本使用 `application/octet-stream`，会保留 `.s2p` |
| 校准后曲线没变化 | 确认捕获时标准件确实连接；注意校准只在已保存的频率范围内插值；尝试清除校准重做 |
| B60 连上后一直没有点/每 2 秒 status | 这是 FW 1.8.6 首次配置 bug；应用会自动 SA→VNA 复位。若「设备 → 日志」提示自动恢复失败，拔插 B60 后再连接 |
| B60 扫描很慢 | 检查 IFBW；先用默认 75 kHz 快速查看。需要低噪声时再降到 1 kHz / 100 Hz / 15 Hz，首帧会更慢，但图表会逐步出点且「采集」旁有呼吸灯 |
| .cal 导入失败 | 确认是 LibreVNA-GUI format 3 文件；标准件需要带 Touchstone 数据；先连接 USB VNA，导入会按当前序列号保存；日志/提示会给出缺失的 Open/Short/Load 或 Through |
| 找不到电子校准件 | 确认 VID:PID `1209:4122`、使用供电 OTG Hub；先在系统 USB 诊断里看到设备，再点「检测并连接」并允许权限 |
| E-Cal 校准失败 | 确认 VNA 能正常出数、系数已加载 100%、温度稳定；B60 切换标准时会自动 SA→VNA 复位，失败后重试；不要在校准中拔插设备 |

## 测试

```bash
cd android
./gradlew testDebugUnitTest
```

21 个 JVM 单元测试覆盖：CRC/帧编解码、SweepSettings 布局、Datapoint 解析、单/双 stage 重组、慢速扫描的增量快照、环形滚动合并、SOL 数学与延迟标准、`.cal` 解析/重建（含纯参数化 calkit）、E-Cal 系数顺序、Touchstone 格式。

本机手动验证（Xiaomi Pad，Android 17）：

- 演示模式连续扫描、暂停/单次、峰值/谷值标记、拖动标记
- dB / 相位 / VSWR / Smith 切换与单指读数、双指缩放
- Open/Short/Load 捕获 → SOL 计算 → 校准后 S11/S21 变化
- THRU 捕获与归一化
- `.s2p` 保存到 `Download/LibreVNA` 并校验 9 列数据
- 校准跨应用重启持久化
- 中文 / English 切换即时生效，强制停止并重装后仍保持所选语言
- 应用设置中的语言与关于面板、迹线设置中的对数频率轴开关均可正常打开
- 对数频率轴在 500 MHz – 3 GHz 下显示 500M / 1G / 2G / 3G 刻度；Y auto 与 Y manual 可来回切换

Harogic B60 真机验证（FW 1.8.6 / HW 0xD0，Android 17 平板，无线 adb）：

- USB 权限弹窗、DeviceInfo、DeviceStatus 正常
- 自动 SA → VNA 复位后，401 点 / 75 kHz IFBW 连续扫描约 19–20 次/秒
- **15 Hz / 101 点**连续与单次扫描均验证可启动并逐步出图：首帧需要数十秒，但已到达的点会立即画到图上（如 `68/101 pts · 采集中`），侧栏显示呼吸灯/「单次扫描中」
- **1 kHz / 401 点**下验证环形滚动：采集到 `209/401 pts` 时曲线仍完整覆盖整个频段，右侧保留上一帧数据，不再出现每帧清空重画
- S11 / S21 实时曲线、暂停/恢复、标记、图表手势正常
- 校准按 B60 序列号隔离；演示模式的校准不会应用到 B60
- 首次配置失败时 watchdog 能自动恢复（日志中可见 SA 复位）；连接/改设置后会先排空旧扫描的 USB 残留点，避免它们误导 watchdog

电子校准件 / `.cal` 验证：

- `.cal` 解析与 1 端口 SOL + THRU 重建已由 JVM 单元测试覆盖；并用 VNStudio 导出的真实 `.cal` 验证通过：
  - `ec_2p_solt_10m6g_5dbm_1khz_...cal`（ECB-60 电子校准件，Touchstone 系数）：2 端口 SOL + THRU，0 警告
  - `default_calbrate_<序列号>.cal`（MCB-60M 手动套件，纯参数化 calkit）：2 端口 SOL + THRU，0 警告
- LibreCAL / ECB-60 的 USB 权限、IDN、2 端口、温度/稳定、FACTORY 7 组系数加载和自动流程已在 ECB-60（FW 0.3.2，序列号已隐去）上跑通；但 0.4.0 及之前的校准结果在 Smith 图上表现为杂散。0.4.1 已按 LibreVNA-GUI 的 **all-port 标准切换** 修正测量时序，并增加 `:PORT?` 状态校验和系数流完整性校验，待实机复测确认。`.cal` 导入也已在平板上用 VNStudio 文件实测。

LibreVNA 官方硬件尚未上机实测；USB 协议路径已由 B60 验证。

## 贡献

欢迎提交 issue 和 PR。开始前请阅读 [CONTRIBUTING.md](CONTRIBUTING.md)，其中包含构建、测试、硬件验证和代码风格要求。

- Bug 报告请附应用版本、Android 版本、VNA/E-Cal 型号与固件、复现步骤和设备日志。
- 提交前请运行 `./gradlew testDebugUnitTest` 和 `./gradlew assembleDebug`。
- 请勿提交 `local.properties`、keystore、`build/`、`LibreVNA/` 或 handout 文件。

## 许可与致谢

- 本项目以 **GNU General Public License v3.0** 发布，完整文本见 [LICENSE](LICENSE)，版权与第三方声明见 [NOTICE](NOTICE)。
- Copyright (C) 2026 The LibreVNA Mobile Authors。
- LibreVNA 硬件、固件、USB 协议与桌面 GUI 由 Jan Käberich 及贡献者开发，项目地址：<https://github.com/jankae/LibreVNA>，同样以 GPL-3.0 发布。本项目的协议实现、校准公式和 `.cal` 格式参考了该项目。
- AndroidX、Jetpack Compose、Kotlin 和 Kotlin Coroutines 使用 Apache License 2.0。
- 本项目与 LibreVNA 官方、Harogic 均无隶属关系；LibreVNA 是硬件产品的名称，Android 应用为社区独立实现。
