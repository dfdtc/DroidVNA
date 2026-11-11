# 故障排查

> English version: [docs/en/TROUBLESHOOTING.md](en/TROUBLESHOOTING.md)

先确认应用版本、Android 版本、VNA / E-Cal 型号与固件版本，再看「设备 → 日志」和「USB 诊断」。提交 issue 时请附上这些信息与复现步骤。

## 安装类问题

小米/红米/HyperOS 报 `INSTALL_FAILED_USER_RESTRICTED: Install canceled by user` 时，可使用 shell 安装方式（本机验证可用）：

```bash
adb push dist/DroidVNA-0.7.1-beta-release.apk /data/local/tmp/droidvna.apk
adb shell pm install -r -t /data/local/tmp/droidvna.apk
```

或在开发者选项中打开 **USB 安装 / 通过 USB 安装应用**。应用本身只需要 USB Host 访问权限，不需要 root。

如果安装时提示签名不一致，说明设备上已安装用其他 keystore 签名的版本：先卸载旧版，再安装当前 APK（卸载会清除已保存的校准与设置）。

## 常见现象与处理

| 现象 | 处理 |
| --- | --- |
| 设备页没有 USB VNA | 换数据线/OTG 转接头；确认设备上电；点「重新扫描」；查看 USB 诊断里是否枚举到 `0483:564E` 或 `367F:0200` |
| 连接后一直 CONNECTING | 检查系统 USB 权限弹窗；关闭桌面版 LibreVNA-GUI（可能已占用接口）；重新插拔 |
| 设备回复 DeviceInfo 失败 | 固件协议可能不是 v14；用桌面版升级固件 |
| 扫描暂停/速率不变 | 点「开始扫描」；单次模式后需要再点开始扫描才会连续扫描 |
| 小米/HyperOS 安装失败 | 用 `adb push` + `pm install`，或打开开发者选项的 USB 安装 |
| 导出文件带 `.txt` 后缀 | 旧版本问题；当前版本使用 `application/octet-stream`，会保留 `.s2p` |
| 校准后曲线没变化 | 确认捕获时标准件确实连接；注意校准只在已保存的频率范围内插值；尝试清除校准重做 |
| B60 连上后一直没有点/每 2 秒 status | 这是 B60 的 `extTrigger` 残留问题（旧版本把 31 字节的 `SweepSettings` 当完整结构，固件沿用缓冲区里上次的触发计数值）；当前版本固定发送 32 字节并把 `extTrigger` 写成 0。若仍无数据，应用会在 6 秒后重发配置 2 次，之后提示重新插拔 |
| B60 扫描很慢 | 检查 IFBW；先用默认 75 kHz 快速查看。需要低噪声时再降到 1 kHz / 100 Hz / 15 Hz，首帧会更慢，但图表会逐步出点且「采集」旁有呼吸灯 |
| E-Cal 报「端口 X OPEN/SHORT/LOAD 没有扫描数据」 | 采集超时以前固定 8 秒，而低 IFBW / 多点数的**一遍完整扫描**可能远超 8 秒（例如 15 Hz + 2001 点约 2–7 分钟）。现在超时按点数/IFBW 自动放大（约 2 个扫描周期），并每秒显示「已采集 x/2001 点…」。若仍失败，看「设备 → 日志」里是否 `Capturing …` 之后完全没有数据点：那才是 VNA 真的没出数（检查 extTrigger/连接与 USB 供电） |
| E-Cal 在低 IFBW 下很慢 | 正常：E-Cal 用当前扫描设置，每个标准都要等至少一遍完整扫描。想快就临时把 IFBW 提到 30–75 kHz、点数降到 401，校准完再改回来 |
| `.cal` 导入失败 | 确认是 LibreVNA-GUI format 3 文件；标准件需要带 Touchstone 数据；先连接 USB VNA，导入会按当前序列号保存；日志/提示会给出缺失的 Open/Short/Load 或 Through |
| 找不到电子校准件 | 确认 VID:PID `1209:4122`、使用供电 OTG Hub；先在系统 USB 诊断里看到设备，再点「检测并连接」并允许权限 |
| E-Cal 校准失败 | 确认 VNA 能正常出数、系数已加载 100%、温度稳定；B60 切换标准时会重新下发扫描配置，失败后重试；不要在校准中拔插设备 |

## 相关文档

- 连接与 B60 固件行为的说明见 [使用指南](USER-GUIDE.md)
- 校准流程与限制见 [校准指南](CALIBRATION.md)
- 已知限制见 [路线图与已知限制](ROADMAP.md)
