# 贡献指南

感谢你愿意改进 DroidVNA。这个项目面向真实硬件，改动不只影响界面，
还会影响 USB 时序、校准精度和设备兼容性，所以提交前请尽量说明测试环境。

## 可以贡献什么

- Bug 报告：附上应用版本、Android 版本、设备型号、VNA/E-Cal 型号与固件版本、
  复现步骤和相关日志（设备页 → 日志）。
- 新硬件适配：LibreVNA V1 / P2 / PT、Harogic B60 / VNB-60、LibreCAL / ECB-60。
- 校准算法、图表交互、导出格式、文档和翻译改进。
- 不接收从闭源项目复制来的代码，也不接收包含密钥、序列号或他人隐私的数据。

## 开发环境

- JDK 17 或 21
- Android SDK Platform 35、Build-Tools 35.0.0
- Gradle 8.10.2（使用仓库自带 wrapper）
- 一台支持 USB Host / OTG 的 Android 8.0+ 设备，以及可用的 VNA 或演示模式

```bash
cd android
cp local.properties.example local.properties   # 填入本机 SDK 路径
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Windows：

```powershell
$env:JAVA_HOME = "C:\path\to\jdk-21"
cd android
Copy-Item local.properties.example local.properties
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
```

## 提交前检查

1. `./gradlew testDebugUnitTest` 全部通过。
2. `./gradlew assembleDebug` 能生成 APK。
3. 如果改了 USB / 扫描 / 校准逻辑，请在真实设备上至少验证一次连接、扫描和校准；
   没有硬件时用演示模式验证 UI 流程。
4. 更新 `CHANGELOG.md`（用户可见的变化）和 `README.md`（行为、限制、截图有变化时）。
5. 不要提交 `local.properties`、`keystore.properties`、`*.jks`、`build/`、
   `LibreVNA/` 或 handout 文件。

## 代码风格

- Kotlin 官方风格（4 空格缩进，见 `.editorconfig`）。
- Compose UI 保持触摸优先：可点击区域尽量不小于 48 dp，危险操作要二次确认。
- USB / 协议代码避免在主线程阻塞；新增设备行为要有超时和失败提示。
- 校准数学改动要补充 JVM 单元测试，尽量使用固定的合成数据。

## 提交信息与 PR

- 提交信息用一句中文或英文说明「做了什么」，例如
  `修复 B60 低 IFBW 切换后首帧超时`。
- 一个 PR 只做一件事；UI 改动附截图或录屏，协议改动附日志片段。
- PR 描述中写明测试设备和验证步骤。

## 许可

提交代码即表示你同意以 **GPL-3.0** 授权你的贡献，并确认你有权提交这些代码。
详见 `LICENSE` 和 `NOTICE`。
