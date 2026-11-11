# 构建与开发

> English version: [docs/en/DEVELOPMENT.md](en/DEVELOPMENT.md)

本文档说明如何从源码构建 DroidVNA、项目结构，以及 USB 协议实现要点。

- 发布新版本：见 [发布流程](RELEASING.md)
- 测试与真机验证：见 [测试与验证](TESTING.md)
- 提交要求：见 [贡献指南](../CONTRIBUTING.md)

## 环境

- JDK **17 或 21**（本机构建使用 Corretto 21；建议用 JDK 21，Android Studio 自带 JBR 可直接使用）
- Android SDK Platform 36、Build-Tools 36.0.0（首次构建时 Gradle 会自动安装 Platform 36）
- Gradle 8.14.4（项目自带 wrapper）

## 构建

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

## Release 签名

仓库内的 APK 使用项目发布密钥签署。要生成可正式分发的 APK：

```bash
cd android
cp keystore.properties.example keystore.properties
# 填入 keystore 路径、storePassword、keyAlias、keyPassword
./gradlew assembleRelease
```

`keystore.properties`、`*.jks`、`*.keystore` 已在 `.gitignore` 中。请离线备份 keystore 和密码；丢失后无法覆盖升级已安装的应用。完整步骤见 [发布流程](RELEASING.md)。

## 技术栈

Kotlin 2.4.21、Jetpack Compose（Material 3，BOM 2025.10.01）、AGP 8.13.2、Gradle 8.14.4、compileSdk / targetSdk 36、minSdk 26、Apache Commons Math 3.6.1（12 项 SOLT 的复数矩阵求解，以及 DTF 的 `FastFourierTransformer` IFFT）。没有 native 库，APK 可直接安装到 arm64 / x86_64 设备。

## 持续集成

[.github/workflows/android.yml](../.github/workflows/android.yml) 在每次 push / PR 时运行单元测试并构建 debug APK，同时上传测试报告和 APK 产物。tag `v*` 也会触发构建。

## 目录结构

```
android/
  app/src/main/java/com/droidvna/app/
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
    model/                       Complex、SweepConfig/Result、SOL 与完整 12 项 SOLT 校准数学（Solt.kt）、复数矩阵适配（ComplexMatrix.kt）、.cal 导入、E-Cal 系数解析
      analysis/                  带宽/XdB 搜索（XdbSearch.kt）与 DTF（Dtf.kt）
    ui/                          Compose 页面、图表（含 DTF 距离域图表）、底部面板、主题
      text/                      中英文文案、翻译 Text 包装、语言状态
      screens/                   测量 / 设备 / 校准页面
      sheets/                    扫描、导出、应用设置、迹线设置、分析面板
    export/                      Touchstone/CSV、MediaStore/分享
    data/CalibrationStore.kt     JSON 持久化
  app/src/test/                  JVM 单元测试
docs/
  en/                            英文文档
  screenshots/                   界面截图
  release-notes/                 每版 Release Notes
  USER-GUIDE.md                  使用指南
  CALIBRATION.md                 校准指南
  TROUBLESHOOTING.md             故障排查
  DEVELOPMENT.md                 本文档
  TESTING.md                     测试与真机验证
  ROADMAP.md                     已知限制与路线图
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
- `SweepSettings` 32 字节 packed C 结构（VNB-60；上游 LibreVNA 为 31 字节），bitfield 按 LSB 排列：
  - standby / syncMaster / suppressPeaks / fixedPower / logSweep / syncMode
  - stages、port1Stage…port4Stage
  - 起止频率、点数、IFBW、起止功率、dwell
  - offset 31：`extTrigger`（Harogic 扩展；`0` = 自由连续扫描，非 0 = 每个点等待 N 次触发事件）。
    **必须显式发送**：固件把包拷进一个共享复用的接收缓冲区，只覆盖实际收到的字节，所以只发 31 字节
    会让该字节沿用上一个包的残留值，残留非 0 时设备等着触发、看起来像卡死（`0` 正常、`1` 约 11 点、
    `0xFF` 完全无数据）。见 `Protocol.SweepSettings` 的 KDoc。
- 外触发尚未实现：`extTrigger` 恒为 0；`SetTrigger`(28) / `ClearTrigger`(29) 只保留包号，等 Harogic
  给出非 0 值的定义后再实现（对应 `Protocol.Type` 与 `Protocol.SweepSettings.extTrigger`）。
- 一个 `VNADatapoint` 可能包含该点的所有 stage；每个 stage 有 P1、P2、Ref 三个复数，descriptor 低 5 位为接收机 mask（bit0 P1、bit1 P2、bit4 Ref），高 3 位为 stage。
- 设备 VID/PID：LibreVNA `0483:564E`、`0483:4121`、`1209:4121`；Harogic B60 `367F:0200`（设备产品字符串 `B60`）；LibreCAL / ECB-60 电子校准件 `1209:4122`（SCPI 在 interface 2，vendor class 0xFF，bulk 0x03/0x83）。
- LibreVNA-GUI 的 USB VNA 驱动实际只有 LibreVNA（含 V1/P2/PT）和 Harogic B60 两种；`SNA5000A`、`SSA3000X` 是 TCP/SCPI 仪器而非 USB VNA，本应用暂未实现。
