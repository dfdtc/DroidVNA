# DroidVNA

**DroidVNA — LibreVNA / Harogic B60 的原生 Android 触屏客户端** — 通过 USB-C OTG 直接把 VNA 接到平板/手机，不需要 PC、Termux 或共享内核容器。使用 Android USB Host API 直接访问设备的 vendor-specific bulk 端点，按 LibreVNA USB 协议 v14 完成扫描设置、数据接收、校准与导出；同时支持导入电脑版 `.cal`，以及 LibreCAL / Harogic ECB-60 电子校准件的自动校准；校准引擎支持完整的 12 项双端口 SOLT（含可选隔离测量）。界面内置 **中文 / English** 两套文案，可在右上角设置中随时切换。

**English name:** DroidVNA — a touch-first, native Android USB host client for LibreVNA and Harogic B60 vector network analyzers. It speaks the LibreVNA USB protocol v14 directly (no PC, no Termux), supports full 12-term two-port SOLT calibration (with an optional isolation measurement) plus 1-port SOL + THRU, LibreVNA-GUI `.cal` import (rebuilt as a full SOLT when the file allows), and LibreCAL / ECB-60 electronic calibration. Licensed under GPL-3.0.

> **声明**：DroidVNA 不是 LibreVNA 官方 Android 版，也不包含桌面 Qt GUI。它是**基于 [LibreVNA-GUI](https://github.com/jankae/LibreVNA) 的 USB 协议、校准数学与 E-Cal 逻辑改写**的 Android 客户端，按 GPL-3.0 发布；应用名中的 “VNA” 指矢量网络分析仪，“Droid” 指 Android，与 LibreVNA 商标所有者无隶属关系。

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

- 当前版本 **0.7.0-beta**（versionCode 9），要求 Android 8.0（API 26）及以上；compileSdk / targetSdk 36。
- 仓库内提供预构建的 **release** APK：[dist/DroidVNA-0.7.0-beta-release.apk](dist/DroidVNA-0.7.0-beta-release.apk)，也可以从 GitHub Releases 下载。
- 已在 Xiaomi Pad（Android 17）+ Harogic B60（FW 1.8.6）上完成真机验证；ECB-60（FW 0.3.2）的 USB、系数读取和校准流程已实机跑通，但 0.4.0 及之前的校准结果存在 Smith 杂散，0.4.1 已改为 all-port 标准切换，等待实机复测；LibreVNA 官方硬件尚未上机实测。
- 完整的 **12 项 SOLT** 已在真机（B60 + ECB-60，FW 0.3.2）上跑通 E-Cal 自动流程并成功应用（401 点 / 500 MHz–3 GHz），隔离勾选框与「未校准频段」提示也已实机验证；但**绝对精度尚未与桌面版 / 已知 DUT 对照**，**手动 SOLT 流程尚未上机**（缺机械校准件），因此本版标记为 **beta**。真机验收方法见下面「测试」一节的建议流程。
- 变更记录见 [CHANGELOG.md](CHANGELOG.md)，发布流程见 [docs/RELEASING.md](docs/RELEASING.md)。

## 功能

- **实时双端口扫描**：S11 / S21 / S12 / S22，连续扫描或单次扫描，可暂停/恢复。
- **慢速扫描进度可见**：低 IFBW 下首帧较慢时，侧栏「采集」和图表上都有呼吸灯指示正在运行；扫描过程中已到达的点会实时画到图上，不必等整帧结束。
- **环形滚动更新**：连续扫描时不再每帧清空重画。新一帧的点逐个覆盖上一帧，尚未到达的点保留上一帧的值，因此图表始终显示完整曲线，读图不会被打断。
- **单一入口的右上角菜单**：所有设置入口（扫描设置、迹线设置、校准、导出、设备、应用设置、收起控件）都收在右上角的 `⋮` 菜单里；测量页不再有标题栏，迹线图占满上下高度。
- **背景水印**：DroidVNA 标识以半透明水印画在迹线图背景里（位于迹线之下）。应用设置里可以打开「关闭应用水印」把这一层整个关掉。
- **可收起的控件行**：迹线选择/模式/Y 轴那一行、以及大屏右侧的采集卡片列都能一键收起（菜单「收起迹线控件」、行尾 `⌃` 按钮、采集卡片上的 `⌃`）。收起后图表更大，标记快捷操作仍保留。
- **横屏竖排采集栏**：手机横屏时采集控件不再挤在底部，而是作为竖列固定在左侧，图表占据右侧。
- **设置记忆**：扫描参数、标记位置、迹线参数、图表模式、Y 轴缩放、对数频率轴、水印开关、控件收起状态、界面语言都会自动保存，下次启动原样恢复；「连接后自动连续扫描」可以关掉，改成手动点「开始扫描」。
- **校准记忆**：SOLT / SOL / THRU / 电子校准（E-Cal）/ `.cal` 导入的结果会立即保存为一份带设备序列号与来源标记的快照，下次启动自动载入，并在测量页顶部提示「已自动载入上次校准」。可以随时「停用校准」（保留系数）或「忘记已保存的校准」。
- **中英文界面**：`⋮` →「应用设置」→「界面语言」，可选跟随系统 / 中文 / English，切换立即生效并持久化；关于信息也在同一面板。
- **Y 轴自动 / 手动**：图表上方的 `Y 自动` 按钮在自动缩放和手动缩放之间切换；在图表上捏合缩放会自动切到手动，点一下即可恢复自动。
- **触摸优先的图表**：
  - 双指缩放 / 平移（横轴与纵轴）
  - 单指点击/拖动显示十字光标读数
  - 拖动标记线改频点，点 `FIT` 恢复全带宽
  - dB、相位、VSWR、Smith 圆图四种显示
  - 迹线设置中可切换**对数频率轴**，X 轴按 1-2-5 对数刻度显示
- **标记**：任意添加、拖动、删除；峰值/谷值搜索；双标记 Δ 差值。
- **扫描设置**：起止频率、点数、IFBW、功率、平均次数、双端口/单端口、对数扫描、自动连续扫描。B60 的 IFBW 可选 **15 Hz – 75 kHz**。
- **1 端口 SOL 校准**：Open / Short / Load，计算方向性、源匹配和反射跟踪误差，按频率保存并插值应用；可选手动延迟。校准按设备序列号隔离，不会把演示/其他设备的校准套到 B60 上。
- **完整 12 项 SOLT 双端口校准**：两端 Open / Short / Load + THRU，可选再加一次隔离测量，解出方向性、源匹配、反射跟踪、接收机匹配、传输跟踪与隔离，按多端口公式 `S = B·A⁻¹` 逐点修正。相比 THRU 归一化，它还会扣除端口失配，因此 S21 / S12 在端口反射较大时依然准确。
- **THRU 归一化**：对 S21 / S12 做直通响应校准（仅 0 dB 归一化，不含失配修正；单端口设备或旧校准仍可用）。
- **电脑 .cal 导入**：读取 LibreVNA-GUI format 3 的 .cal，在移动端重建校准并立即应用；含两端口的 1 端口测量与 THRU 时直接重建完整 12 项 SOLT（并读取其中的 Isolation），否则退回 1 端口 SOL + THRU；按当前 VNA 序列号保存。
- **电子校准件（LibreCAL / ECB-60）**：通过 USB SCPI 读取 FACTORY 系数，自动切换 Open/Short/Load/Through；双端口校准件直接解出完整 12 项 SOLT。**隔离测量是可选勾选框（默认关闭）**，因为 ECB-60 这类双端口 E-Cal 的隔离通路较差；可之后用机械 50 Ω 负载单独补测。支持温度/稳定状态显示。
- **导出**：Touchstone `.s2p` / `.s1p`、CSV，保存到 `Download/DroidVNA`，也可通过系统分享发送。
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

**手机横屏：采集控件作为竖列停在左侧，迹线图占右侧（2400×1080 模拟 873×393 dp）：**

![手机横屏](docs/screenshots/phone-landscape.png)

![迹线设置](docs/screenshots/trace-settings.png)

## 安装

已构建的 release APK（仓库内）：

```
dist/DroidVNA-0.7.0-beta-release.apk
大小: 13,160,344 字节
SHA-256: 0D05C1087175DFBB93F43BC606D313079F6902E19C15089F39AC35ED6AC069EC
```

```bash
adb install -r dist/DroidVNA-0.7.0-beta-release.apk
```

也可以从仓库的 **Releases** 页面下载同一版本。该 APK 是 release 构建，使用项目发布密钥签署（证书 CN=Houran CHEN）；换用别的 keystore 重新签名后无法覆盖安装，只能先卸载。签名流程见 [docs/RELEASING.md](docs/RELEASING.md)。

小米/红米/HyperOS 如果报：

```
INSTALL_FAILED_USER_RESTRICTED: Install canceled by user
```

请使用 shell 安装方式（本机验证可用）：

```bash
adb push dist/DroidVNA-0.7.0-beta-release.apk /data/local/tmp/droidvna.apk
adb shell pm install -r -t /data/local/tmp/droidvna.apk
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
- **Y 轴**：控件行的 `Y 自动` / `Y 手动` 切换自动与手动缩放；手动捏合缩放后点一下即可恢复自动。
- **迹线**：控件行的迹线参数 chip 直接开关 S11/S21/S12/S22，模式 chip 切换 dB/Phase/VSWR/Smith。
- **收起控件**：控件行末尾的 `⌃` 收掉整行，菜单里的「收起迹线控件」等价；大屏右侧采集卡片的 `⌃` 收掉整列。

## 设置

### 右上角菜单

测量页只有一行控件：左边是迹线/模式/Y 轴选择（可横向滚动），右边是连接状态、收起按钮和唯一的 `⋮` 菜单。菜单项：

- **扫描设置**：起止频率、点数、IFBW、功率、平均、单/双端口、对数扫描、自动连续扫描。
- **迹线设置**：对数频率轴、Y 轴自动缩放开关、当前迹线摘要。
- **校准** / **导出** / **设备**：进入校准向导、导出面板、设备页。
- **收起 / 展开迹线控件**：等价于控件行末尾的 `⌃`。
- **应用设置**：见下。

### 应用设置

- **界面语言**：跟随系统 / 中文 / English，切换后立即生效并持久化。
- **界面 → 关闭应用水印**：打开后迹线图背景不再绘制半透明的 DroidVNA 标识。
- **设置记忆 → 记住扫描设置**：下次启动沿用频率范围、点数、IFBW、功率与平均。
- **设置记忆 → 连接后自动连续扫描**：关闭后连接设备只下发设置，需要手动点「开始扫描」。
- **校准记忆 → 自动保存并载入上次校准**：显示已保存校准的来源（电子校准 / .cal 导入 / 手动 SOL）、端口、THRU 组数、保存时间与设备序列号；可以「停用校准」（保留系数、只看原始数据）或「忘记已保存的校准」。
- **关于**：版本、协议、支持设备、GPL-3.0 与上游项目信息。

设置保存在应用私有目录：`filesDir/settings.json`（写入采用临时文件 + 重命名，掉电不会留下半个文件）。

### 迹线设置

图表控件行末尾、或「迹线」卡片右侧的齿轮、或右上角菜单都能打开。当前包含：

- **对数频率轴**：X 轴按对数频率显示，使用 1-2-5 对数刻度；关闭后恢复线性轴。
- **Y 轴自动缩放**：关闭后锁定当前 Y 轴范围，与双击图表恢复自动等效。

后续的迹线显示选项会继续加在这个面板中。

## 校准

### 从电脑导入 .cal

1. 在电脑版 LibreVNA-GUI 完成校准（SOL / SOLT / THRU），保存为 `.cal`（format 3）。
2. 把 `.cal` 拷到平板（USB、网盘、LocalSend 均可）。
3. 平板先连接 VNA，打开「校准 → .cal 校准文件导入 → 选择 .cal 文件」。
4. 应用解析 calkit 和测量点，重建校准（数据足够时是完整 12 项 SOLT），并按当前 VNA 序列号保存。

限制：若 `.cal` 只包含单端口测量，移动端只能重建 1 端口 SOL + THRU；两端口的 1 端口测量 + THRU 都在时才会重建完整 12 项 SOLT。如果 `.cal` 记录的是另一台 VNA，界面会提示，精度可能下降。calkit 支持 Touchstone 数据，也支持纯参数化 Open/Short/Load（C/L/Cfirst 等）以及参数化 Through（Z0/delay/loss）。

### 电子校准件 LibreCAL / ECB-60

1. 用带外部供电的 OTG Hub 同时连接 VNA 与校准件（ECB-60 有加热器，手机直供可能不够）。
2. 「校准 → 电子校准件」→「检测并连接」，允许 USB 权限，确认 IDN / FW / 端口 / 温度。
3. 选择系数集（通常 `FACTORY`）→「加载系数」，等待进度到 100%。
4. 固定手动映射 VNA P1 ↔ Cal P1、P2 ↔ Cal P2，**不使用 Auto**；点「开始电子校准」。
5. 应用会先把**所有用到的 E-Cal 端口设成同一个标准**（Open/Short/Load），再测所有 VNA 端口；THRU 前先全部设回 NONE，再建立 P1→P2 直通；结束把所有 cal 端口设回 NONE。
6. 双端口校准件会一次性解出**完整 12 项 SOLT**并立即应用；单端口校准件只能得到 1 端口 SOL。

**隔离测量是可选勾选框，默认关闭。** 打开后校准最后会再花约几秒，把所有 cal 端口切到 LOAD 测一次隔离。默认关闭的原因：ECB-60 这类双端口电子校准件的隔离通路本身很差，它测出的串扰往往比用两个机械 50 Ω 负载测的还差；这种情况下更好的做法是先用 E-Cal 完成其余 11 项，之后再用机械负载**单独补测隔离**（见下一节）。

注意：温度未稳定时会等待最多 90 秒；B60 每次切换标准会执行 SA→VNA 复位，整体耗时比桌面版长。端口状态每次都会用 `:PORT?` 校验，系数流必须完整；否则会中止或回退逐点读取，避免截断系数造成“杂散”校准。

### 手动 1 端口 SOL

1. 连接设备或启动演示模式。
2. 打开「校准」页，选择端口 1 或端口 2。
3. 按提示依次连接 **Open / Short / Load**（端口 2 会自动切换为双端口扫描）。
4. 每次点击「捕获」，应用会等待下一次完整扫描并保存原始数据。
5. 点「计算并应用端口 N SOL」。
6. 如需修正 S21/S12 响应，把 Port 1 与 Port 2 直连，捕获 THRU 后点「应用 THRU 归一化」。

### 手动完整 SOLT（双端口）

1. 连接设备（双端口）或启动演示模式，先确定扫描频率与点数。
2. 依次捕获 **端口 1** 的 Open / Short / Load，再捕获 **端口 2** 的 Open / Short / Load（在校准页切换「端口 1 / 端口 2」）。
3. 用低损耗直通连接 P1 ↔ P2，捕获 **THRU**。若直通件有额外电长度，在「THRU 延迟」里填皮秒值。
4. 可选：两端各接 50 Ω 负载，捕获 **ISOLATION**，把残余串扰从 S21 / S12 中扣除；跳过则按零隔离处理。
5. 点「计算并应用完整 SOLT」。

### 单独补测 / 替换隔离项

隔离项可以**在不重做其他测量的前提下单独替换**，无论当前校准来自手动 SOLT、`.cal` 导入还是 E-Cal：

1. 保持当前扫描设置不变，两端各接一个 50 Ω 负载。
2. 在「双端口 SOLT」分区点 **捕获**（ISOLATION 卡片）。
3. 点 **「只用本次捕获更新隔离项」**。

原理：传输跟踪是按 `(THRU 实测 S21 − 隔离)` 求出的，所以改了隔离必须重算 T。应用会把 THRU 的实测值与理想值一起保存在校准快照里（`calibration.json` 的 `solt.through`），因此重算的是**精确值**，而不是近似修一下 —— 反射项和 load match 完全不动。也可以用「清除隔离修正」把隔离归零。

LibreVNA 的 `.cal` 格式本身就是**按测量项存储**的（`measurements` 里每项一条，隔离是独立的 `Isolation` 项），所以这种“只换一项测量”的做法与桌面版的数据模型一致；限制只在我们自己的 `calibration.json` 上，而它现在也保存了 THRU 原始数据，因此可以做到精确重算。

实现说明：

- 1 端口误差模型：`Γm = e00 + e01e10·Γa / (1 - e11·Γa)`。用 Open/Short/Load 三组测量解出 e00、e11、e01e10（与 LibreVNA-GUI `computeOSL` 相同的闭式解），再按频率插值应用到 S11 或 S22。
- 12 项模型：在 1 端口三项之上，用 THRU 测量解出接收机匹配（load match）、传输跟踪与隔离，共 12 项；修正按多端口公式 `S = B·A⁻¹` 逐点进行（复数 2×2 求解使用 Apache Commons Math 的 `FieldLUDecomposition`）。
- 完整 SOLT 存在时优先于 1 端口 SOL / THRU 归一化应用；两者同时存在不会重复修正。
- 标准件默认按理想 Open（Γ=+1）、Short（Γ=-1）、Load（Γ=0）、THRU（理想直通）处理，可填写 Open/Short 的偏移延迟和 THRU 延迟（ps）。
- **手工 SOLT 会校验各次捕获的扫描范围**：Open/Short/Load/THRU/隔离的起止频率必须一致，否则直接拒绝并提示（重采样到更窄网格会把带外点静默钳位到端点值，解出的误差项会错得没有症状）。`.cal` 导入同样要求 THRU 与端口 2 的频段覆盖端口 1，否则不生成完整 SOLT 并给出警告。
- **标准件过于接近时拒绝求解**：方程为奇异（行列式趋零）或解出的方向性/源匹配幅值超过 10（物理上不可能的量级）都会判为无解，而不是应用一组离谱的系数。注意这只能挡住"数值上爆掉"的情况；把 Open 和 Short 插反这类错误从数据本身无法识别。
- THRU 归一化保存的是 `实测/理想`（与 `.cal` 导入的语义一致）；THRU 延迟为 0 时理想直通为 1，等同于传统的 0 dB 归一化。
- 校准按频率保存到应用私有目录（`filesDir/calibration.json`，格式 version 3；旧版本文件仍可载入），重启后自动载入并自动应用。快照记录设备序列号、来源（手动 SOLT / SOL / THRU / E-Cal / .cal 导入）和更新时间，在「应用设置 → 校准记忆」里可见，也可一键停用或删除。
- **扫描范围超出已校准频段时会提示**：测量页的「校准」卡片和校准页的状态卡都会显示「测量范围覆盖未校准频段」，并给出已校准频段；超出部分按未校准处理（与修正引擎跳过带外点的行为一致）。
- **仍然不做**源/接收机幅度校准（功率校准）、频率校准和校准套件高阶系数拟合；标准件按理想或导入的 Touchstone / 参数模型处理。需要计量级结果请使用桌面版 LibreVNA-GUI。

## 导出

在测量页或顶部保存按钮打开「导出测量数据」：

- Touchstone `.s2p`：`# Hz S RI R 50`，列为 S11 S21 S12 S22（每列 re/im）。
- Touchstone `.s1p`：仅 S11。
- CSV：频率 + 各参数的 dB / 相位。
- 保存位置：`Download/DroidVNA`（Android 10+，MediaStore）；Android 8/9 保存到应用外部目录。
- 分享按钮：通过系统分享面板发送文件。

文件名形如 `droidvna_20261107_205533_cal.s2p`。`cal` 表示当前数据已应用校准，`raw` 表示未校准。

## 构建

环境：

- JDK **17 或 21**（本机构建使用 Corretto 21；建议用 JDK 21，Android Studio 自带 JBR 可直接使用）
- Android SDK Platform 36、Build-Tools 36.0.0（首次构建时 Gradle 会自动安装 Platform 36）
- Gradle 8.14.4（项目自带 wrapper）

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

仓库内的 APK 使用临时自签名证书。要生成可正式分发的 APK：

```bash
cd android
cp keystore.properties.example keystore.properties
# 填入 keystore 路径、storePassword、keyAlias、keyPassword
./gradlew assembleRelease
```

`keystore.properties`、`*.jks`、`*.keystore` 已在 `.gitignore` 中。请离线备份 keystore 和密码；丢失后无法覆盖升级已安装的应用。完整步骤见 [docs/RELEASING.md](docs/RELEASING.md)。

技术栈：Kotlin 2.4.21、Jetpack Compose（Material 3，BOM 2025.10.01）、AGP 8.13.2、Gradle 8.14.4、compileSdk / targetSdk 36、minSdk 26、Apache Commons Math 3.6.1（12 项 SOLT 的复数矩阵求解）。没有 native 库，APK 可直接安装到 arm64 / x86_64 设备。

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
    model/                       Complex、SweepConfig/Result、SOL 与完整 12 项 SOLT 校准数学（Solt.kt）、复数矩阵适配（ComplexMatrix.kt）、.cal 导入、E-Cal 系数解析
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

- 没有固件更新、手动控制、信号源、频谱分析、幅度/功率校准、频率校准和 SCPI 服务器。
- 完整 12 项 SOLT 支持双端口（2 端口即全部 12 项）；三端口/四端口设备的扩展未实现。手动 THRU 按理想直通处理，非理想直通请填延迟或改用 E-Cal / `.cal`。
- 双端口电子校准件（如 ECB-60）的隔离通路较差，E-Cal 自带的隔离测量默认关闭；需要隔离修正时建议用两个机械 50 Ω 负载单独补测（校准页支持只替换隔离项）。
- 没有独立的 Touchstone 校准套件文件导入；`.cal` 导入可重建完整 SOLT，但单端口 `.cal` 仍只得到 1 端口 SOL + THRU。
- E-Cal 固定 VNA P1/P2 ↔ Cal P1/P2 映射，不包含自动端口识别；需要带外部供电的 OTG Hub。
- 图表为自绘 Compose Canvas，极大点数（>1001）未做性能优化。
- 仓库内预构建 APK 为 release 构建 + 临时自签名证书；正式分发请配置自己的 release keystore 重新构建。
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

48 个 JVM 单元测试覆盖：CRC/帧编解码、SweepSettings 布局、Datapoint 解析、单/双 stage 重组、慢速扫描的增量快照、环形滚动合并、SOL 数学与延迟标准、完整 12 项 SOLT 的反解 / DUT 往返修正 / 隔离扣除 / 退化标准件拒绝、**正向模型与独立的信号流推导逐点吻合（1e-12）**、校准后标准件回到理想值、隔离项精确替换、已校准频段覆盖判定、演示模式端到端 SOLT、`.cal` 解析/重建（含纯参数化 calkit 与完整 SOLT）、E-Cal 系数顺序、Touchstone 格式。另有一组稳健性测试覆盖：标准件模型在 0 Hz 的极限值、单频点校准被拒绝、被截断的校准快照不会越界、标准件过于接近时拒绝求解、`.cal` 中 THRU 频段未覆盖 SOL 频段时拒绝生成完整 SOLT。

本机手动验证（Xiaomi Pad，Android 17）：

- 演示模式连续扫描、暂停/单次、峰值/谷值标记、拖动标记
- dB / 相位 / VSWR / Smith 切换与单指读数、双指缩放
- Open/Short/Load 捕获 → SOL 计算 → 校准后 S11/S21 变化
- THRU 捕获与归一化
- `.s2p` 保存到 `Download/DroidVNA` 并校验 9 列数据
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

- `.cal` 解析与重建（1 端口 SOL + THRU 和完整 12 项 SOLT 两条路径）已由 JVM 单元测试覆盖；并用 VNStudio 导出的真实 `.cal` 验证通过：
  - `ec_2p_solt_10m6g_5dbm_1khz_...cal`（ECB-60 电子校准件，Touchstone 系数）：2 端口 SOL + THRU，0 警告
  - `default_calbrate_<序列号>.cal`（MCB-60M 手动套件，纯参数化 calkit）：2 端口 SOL + THRU，0 警告
- LibreCAL / ECB-60 的 USB 权限、IDN、2 端口、温度/稳定、FACTORY 7 组系数加载和自动流程已在 ECB-60（FW 0.3.2，序列号已隐去）上跑通；但 0.4.0 及之前的校准结果在 Smith 图上表现为杂散。0.4.1 已按 LibreVNA-GUI 的 **all-port 标准切换** 修正测量时序，并增加 `:PORT?` 状态校验和系数流完整性校验。`.cal` 导入也已在平板上用 VNStudio 文件实测。
- **0.7.0-beta 的完整 12 项 SOLT 实机结果**（2026-11-10，B60 FW 1.8.6 + ECB-60 FW 0.3.2，Android 17 平板）：
  - E-Cal 自动流程在 **401 点 / 500 MHz – 3 GHz** 下解算出完整 12 项模型并成功应用；测量页显示
    `采集中 CAL`，校准卡显示「已应用 · 12 项 SOLT · P1 SOL · P2 SOL」，全程无崩溃。
  - 隔离勾选框两种设置都跑通：开启 →「电子校准完成：… 含隔离测量」，关闭 →「… 未做隔离测量」（默认关闭）。
  - 校准后的物理合理性：E-Cal 置 NONE（等效开路）时 S11 ≈ 0 dB（|Γ|≈1）、S21 落在 −80…−100 dB 噪底。
  - 「测量范围覆盖未校准频段」提示在真机上按预期出现。
  - **仍未验证**：与桌面版 / 已知 DUT 的幅度相位精度对照；手动 SOLT 全流程（缺机械校准件）。

LibreVNA 官方硬件尚未上机实测；USB 协议路径已由 B60 验证。

### 完整 SOLT 的真机验收建议

`SoltTest` / `DemoVnaSoltTest` 只能证明算法与流程自洽（正向模型与修正互为逆运算）；真实硬件的验证仍需按下面步骤做，基准是**桌面版 LibreVNA-GUI**：

1. 同一台 VNA、同一套标准件、同一扫描设置，在桌面版做一次 2 端口 SOLT（含隔离），导出 `.cal`。
2. 平板端用同样的标准件重做一次手动 SOLT，或直接导入该 `.cal`。
3. 接一个**两端反射都明显**的已知器件（例如匹配不良的衰减器或一段回波损耗已知的线），对比三种结果的 S21：
   未校准 / THRU 归一化 / 完整 SOLT。
4. 判据：完整 SOLT 的 S21、S11、S22 应与桌面版对同一硬件的同一次测量在噪声内一致；
   THRU 归一化的 S21 在端口失配较大处应能看到系统性偏差，而 SOLT 不应再有。
5. 若 S21 出现明显跳变或杂散，优先检查该次 SOLT 是否真的用了正确方向的 THRU 与隔离数据
   （设备日志里会打印 `12-term SOLT applied (... isolation measured/not measured)`）。

## 贡献

欢迎提交 issue 和 PR。开始前请阅读 [CONTRIBUTING.md](CONTRIBUTING.md)，其中包含构建、测试、硬件验证和代码风格要求。

- Bug 报告请附应用版本、Android 版本、VNA/E-Cal 型号与固件、复现步骤和设备日志。
- 提交前请运行 `./gradlew testDebugUnitTest` 和 `./gradlew assembleDebug`。
- 请勿提交 `local.properties`、keystore、`build/`、`LibreVNA/` 或 handout 文件。

## 许可与致谢

### 本项目做了什么，借用了什么

DroidVNA 的 **UI、图表、触摸交互、USB 权限流程、导出和演示模式** 是为 Android 新写的 Kotlin/Compose 代码，**没有编译或链接 LibreVNA 的 C++/Qt 代码**。但下面这些部分**不是独立设计的**，而是依据 LibreVNA-GUI 的源码改写/翻译：

| 模块 | 对应上游 |
| --- | --- |
| `usb/Protocol.kt` 帧、CRC、SweepSettings、DeviceInfo、Datapoint | LibreVNA 固件/USB 协议 v14、LibreVNA-GUI 驱动 |
| `model/Calibration.kt` SOL 误差模型与求解 | LibreVNA-GUI 校准数学 |
| `model/Solt.kt` 12 项误差模型、`computeOSL`/`computeSOLT` 闭式解、多端口修正 | `Calibration/calibration.cpp`（`Point`、`computeOSL`、`computeSOLT`、`correctMeasurement`） |
| `model/CalStandardModel.kt` Open/Short/Load/Through 参数模型 | `Calibration/calstandard.cpp`、`Util::addTransmissionLine` |
| `model/CalibrationFileImporter.kt` `.cal` format 3 | LibreVNA-GUI 校准文件格式 |
| `usb/EcalDevice.kt`、`model/EcalCoefficients.kt` E-Cal SCPI 与流程 | `Calibration/LibreCAL/caldevice.cpp`、`librecaldialog.cpp` |

因此 **本项目按 GPL-3.0 对 LibreVNA-GUI 的衍生作品对待**，而不是可以改为 MIT/Apache 的独立实现。

### 许可与致谢

- 本项目以 **GNU General Public License v3.0** 发布，完整文本见 [LICENSE](LICENSE)，版权与第三方声明见 [NOTICE](NOTICE)。
- Copyright (C) 2026 The DroidVNA Authors。
- LibreVNA 硬件、固件、USB 协议与桌面 GUI 由 Jan Käberich 及贡献者开发，项目地址：<https://github.com/jankae/LibreVNA>，同样以 GPL-3.0 发布。
- AndroidX、Jetpack Compose、Kotlin 和 Kotlin Coroutines 使用 Apache License 2.0。

### 名称与商标

- 应用名为 **DroidVNA**：“Droid” 指 Android，“VNA” 指矢量网络分析仪（通用术语）。
- 原名 “LibreVNA Mobile” 含有第三方产品名 **LibreVNA**，容易被认为是官方产品，已改名。
- 本项目与 LibreVNA 项目、Jan Käberich 以及 Harogic 均**无隶属、赞助或背书关系**；README、应用内“关于”和驱动日志中提及 LibreVNA，仅用于说明兼容性与出处。


### AI Generated Content Declaration

- 此项目的几乎全部内容都完全由AI生成，除了这个声明以外。
- This project almost entirely generated by AI/SI (for viewer from the US), except this declaration and open source License. 