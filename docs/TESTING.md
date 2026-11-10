# 测试与验证

> English version: [docs/en/TESTING.md](en/TESTING.md)

本文档汇总单元测试覆盖范围、真机验证记录，以及完整 SOLT 的真机验收建议。

构建与测试命令见 [构建与开发](DEVELOPMENT.md)。

## 单元测试

```bash
cd android
./gradlew testDebugUnitTest
```

52 个 JVM 单元测试覆盖：CRC/帧编解码、SweepSettings 布局、Datapoint 解析、单/双 stage 重组、慢速扫描的增量快照、环形滚动合并、SOL 数学与延迟标准、完整 12 项 SOLT 的反解 / DUT 往返修正 / 隔离扣除 / 退化标准件拒绝、**正向模型与独立的信号流推导逐点吻合（1e-12）**、校准后标准件回到理想值、隔离项精确替换、已校准频段覆盖判定、演示模式端到端 SOLT、`.cal` 解析/重建（含纯参数化 calkit 与完整 SOLT）、E-Cal 系数顺序、Touchstone 格式、英文 UI 翻译（标签、拼接消息、导入警告，以及中文模式原样返回）。另有一组稳健性测试覆盖：标准件模型在 0 Hz 的极限值、单频点校准被拒绝、被截断的校准快照不会越界、标准件过于接近时拒绝求解、`.cal` 中 THRU 频段未覆盖 SOL 频段时拒绝生成完整 SOLT。

## 本机手动验证

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

## Harogic B60 真机验证

Harogic B60 真机验证（FW 1.8.6 / HW 0xD0，Android 17 平板，无线 adb）：

- USB 权限弹窗、DeviceInfo、DeviceStatus 正常
- 自动 SA → VNA 复位后，401 点 / 75 kHz IFBW 连续扫描约 19–20 次/秒
- **15 Hz / 101 点**连续与单次扫描均验证可启动并逐步出图：首帧需要数十秒，但已到达的点会立即画到图上（如 `68/101 pts · 采集中`），侧栏显示呼吸灯/「单次扫描中」
- **1 kHz / 401 点**下验证环形滚动：采集到 `209/401 pts` 时曲线仍完整覆盖整个频段，右侧保留上一帧数据，不再出现每帧清空重画
- S11 / S21 实时曲线、暂停/恢复、标记、图表手势正常
- 校准按 B60 序列号隔离；演示模式的校准不会应用到 B60
- 首次配置失败时 watchdog 能自动恢复（日志中可见 SA 复位）；连接/改设置后会先排空旧扫描的 USB 残留点，避免它们误导 watchdog

## 电子校准件 / `.cal` 验证

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

## 完整 SOLT 的真机验收建议

`SoltTest` / `DemoVnaSoltTest` 只能证明算法与流程自洽（正向模型与修正互为逆运算）；真实硬件的验证仍需按下面步骤做，基准是**桌面版 LibreVNA-GUI**：

1. 同一台 VNA、同一套标准件、同一扫描设置，在桌面版做一次 2 端口 SOLT（含隔离），导出 `.cal`。
2. 平板端用同样的标准件重做一次手动 SOLT，或直接导入该 `.cal`。
3. 接一个**两端反射都明显**的已知器件（例如匹配不良的衰减器或一段回波损耗已知的线），对比三种结果的 S21：
   未校准 / THRU 归一化 / 完整 SOLT。
4. 判据：完整 SOLT 的 S21、S11、S22 应与桌面版对同一硬件的同一次测量在噪声内一致；
   THRU 归一化的 S21 在端口失配较大处应能看到系统性偏差，而 SOLT 不应再有。
5. 若 S21 出现明显跳变或杂散，优先检查该次 SOLT 是否真的用了正确方向的 THRU 与隔离数据
   （设备日志里会打印 `12-term SOLT applied (... isolation measured/not measured)`）。

## 提交前自测

见 [贡献指南](../CONTRIBUTING.md)：`./gradlew testDebugUnitTest` 与 `./gradlew assembleDebug` 均需通过；改动 USB / 扫描 / 校准逻辑时应在真机（或演示模式）上实际跑一遍。
