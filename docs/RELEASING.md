# 发布流程

本文档说明如何从当前代码生成一个可发布的 GitHub Release。命令以 PowerShell 为例，
Linux/macOS 下把 `\` 换成 `/`、`.\gradlew.bat` 换成 `./gradlew` 即可。

## 1. 确定版本号

修改 `android/app/build.gradle.kts`：

```kotlin
versionCode = 6          // 每次发布 +1，不能回退
versionName = "0.4.1"    // 展示给用户的版本
```

同时更新：

- `CHANGELOG.md` 增加对应版本条目
- `README.md` 中的 APK 文件名、大小和 SHA-256
- `dist/` 中的 APK 文件名

## 2. 跑测试并构建

```powershell
$env:JAVA_HOME = "C:\path\to\jdk-21"
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
cd android
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
```

确认 `android/app/build/outputs/apk/debug/app-debug.apk` 生成成功。

## 3. 生成签名 Release APK（推荐）

仓库内提交的 APK 是 debug 签名，只适合内部测试。正式发布建议使用自己的 keystore：

```powershell
cd android
Copy-Item keystore.properties.example keystore.properties
# 编辑 keystore.properties，填入 keystore 路径和密码
.\gradlew.bat assembleRelease
```

`keystore.properties`、`*.jks`、`*.keystore` 已在 `.gitignore` 中，不会被提交。
**请离线备份 keystore 和密码**，丢失后无法覆盖升级已安装的应用。

如果暂时没有 release keystore，可以继续发布 debug APK，但要在 Release 说明中明确标注
「debug 签名，仅供测试」。

## 4. 复制并校验 APK

```powershell
Copy-Item android\app\build\outputs\apk\debug\app-debug.apk dist\LibreVNA-Mobile-0.4.1-debug.apk
Get-FileHash dist\LibreVNA-Mobile-0.4.1-debug.apk -Algorithm SHA256
(Get-Item dist\LibreVNA-Mobile-0.4.1-debug.apk).Length
```

把文件名、字节数和 SHA-256 写进 `README.md` 的「安装」一节。

> `.gitignore` 目前只白名单了 `dist/LibreVNA-Mobile-0.4.0-debug.apk`。发布新版本时，
> 要么把新文件名加到 `!` 规则后面，要么用 `git add -f dist/LibreVNA-Mobile-0.4.1-debug.apk`
> 强制加入版本库。

## 5. 提交并打标签

```powershell
git add -A
git commit -m "发布 0.4.1"
git tag -a v0.4.1 -m "LibreVNA Mobile 0.4.1"
git push origin main
git push origin v0.4.1
```

## 6. 创建 GitHub Release

在 GitHub 仓库页面选择 **Releases → Draft a new release**：

- Tag：`v0.4.1`
- Title：`LibreVNA Mobile 0.4.1`
- 说明：从 `CHANGELOG.md` 复制对应版本的内容，补充升级注意事项
- 附件：上传 `dist/LibreVNA-Mobile-0.4.1-debug.apk`（或签名的 release APK）

按 `CHANGELOG.md` 新建 `docs/release-notes/0.4.1.md`（可复制 0.4.0 的格式）。

也可以使用 GitHub CLI：

```bash
gh release create v0.4.1 dist/LibreVNA-Mobile-0.4.1-debug.apk \
  --title "LibreVNA Mobile 0.4.1" \
  --notes-file docs/release-notes/0.4.1.md
```

## 7. 发布后检查

- 在未安装过应用的设备上从 Release 下载并安装。
- 检查「关于/设备」页版本号与 tag 一致。
- 用真实 VNA 验证连接、连续扫描、SOL 校准和 `.s2p` 导出。
- 确认仓库没有提交 `local.properties`、keystore、`build/` 或 handout 文件。

## 8. 版本号约定

- `0.x`：公开测试阶段，协议/校准行为仍可能调整。
- 补丁版本：bug 修复，不改变数据格式。
- 次版本：新增功能，保持向后兼容。
- 主版本：不兼容的协议、文件格式或校准数据变更。
