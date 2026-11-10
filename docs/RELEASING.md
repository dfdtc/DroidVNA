# 发布流程

> English version: [docs/en/RELEASING.md](en/RELEASING.md)

本文档说明如何从当前代码生成一个可发布的 GitHub Release。命令以 PowerShell 为例，
Linux/macOS 下把 `\` 换成 `/`、`.\gradlew.bat` 换成 `./gradlew` 即可。
下面用 `0.8.0` 作为示例版本号，实际发布时替换成当前版本。

## 1. 确定版本号

修改 `android/app/build.gradle.kts`：

```kotlin
versionCode = 10         // 每次发布 +1，不能回退（0.7.0-beta 为 9）
versionName = "0.8.0"    // 展示给用户的版本
```

同时更新：

- `CHANGELOG.md` 增加对应版本条目
- 按 `CHANGELOG.md` 新建 `docs/release-notes/0.8.0.md`（可复制上一版格式），并同步英文版 `docs/en/release-notes/0.8.0.md`
- [使用指南](USER-GUIDE.md) 「安装」一节中的 APK 文件名、大小和 SHA-256
- `dist/` 中的 APK 文件名

## 2. 配置 Release 签名

仓库提交的 APK 使用项目发布密钥签署（证书 CN=Houran CHEN），但 `keystore.properties`
和密钥文件不入库，因此新克隆的仓库默认构建出的是**未签名** release APK。

要生成可覆盖升级的签名 APK：

```powershell
cd android
Copy-Item keystore.properties.example keystore.properties
# 编辑 keystore.properties，填入 keystore 路径和密码
```

`keystore.properties`、`*.jks`、`*.keystore` 已在 `.gitignore` 中，不会被提交。
**请离线备份 keystore 和密码**，丢失后无法覆盖升级已安装的应用。

如果暂时没有 release keystore，可以继续发布 debug APK，但要在 Release 说明中明确标注
「debug 签名，仅供测试」。

## 3. 跑测试并构建

```powershell
$env:JAVA_HOME = "C:\path\to\jdk-21"
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
cd android
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleRelease
```

输出：

- 已配置签名：`android/app/build/outputs/apk/release/app-release.apk`
- 未配置签名：`android/app/build/outputs/apk/release/app-release-unsigned.apk`（不能直接安装分发）

## 4. 复制并校验 APK

```powershell
Copy-Item android\app\build\outputs\apk\release\app-release.apk dist\DroidVNA-0.8.0-release.apk
Get-FileHash dist\DroidVNA-0.8.0-release.apk -Algorithm SHA256
(Get-Item dist\DroidVNA-0.8.0-release.apk).Length
```

把文件名、字节数和 SHA-256 写进 [使用指南](USER-GUIDE.md) 的「安装」一节。

> `.gitignore` 只白名单了 `dist/` 中特定版本的 APK。发布新版本时，要么把新文件名加到
> `!` 规则后面，要么用 `git add -f dist/DroidVNA-0.8.0-release.apk` 强制加入版本库。

## 5. 提交并打标签

```powershell
git add -A
git commit -m "发布 0.8.0"
git tag -a v0.8.0 -m "DroidVNA 0.8.0"
git push origin main
git push origin v0.8.0
```

## 6. 创建 GitHub Release

在 GitHub 仓库页面选择 **Releases → Draft a new release**：

- Tag：`v0.8.0`
- Title：`DroidVNA 0.8.0`
- 说明：从 `CHANGELOG.md` 复制对应版本的内容，补充升级注意事项
- 附件：上传 `dist/DroidVNA-0.8.0-release.apk`

也可以使用 GitHub CLI：

```bash
gh release create v0.8.0 dist/DroidVNA-0.8.0-release.apk \
  --title "DroidVNA 0.8.0" \
  --notes-file docs/release-notes/0.8.0.md
```

## 7. 发布后检查

- 在未安装过应用的设备上从 Release 下载并安装。
- 检查「关于/设备」页版本号与 tag 一致。
- 用真实 VNA 验证连接、连续扫描、校准和 `.s2p` 导出。
- 确认仓库没有提交 `local.properties`、keystore、`build/` 或 handout 文件。

## 8. 版本号约定

- `0.x`：公开测试阶段，协议/校准行为仍可能调整。
- 补丁版本：bug 修复，不改变数据格式。
- 次版本：新增功能，保持向后兼容。
- 主版本：不兼容的协议、文件格式或校准数据变更。
