# macOS 打包与合规

## 当前分发模式

默认构建面向 **Developer ID + Hardened Runtime**，不是 Mac App Store 构建。

- 默认 entitlements：`desktopApp/entitlements.plist`
- App Store 沙盒模板：`desktopApp/entitlements-appstore.plist`
- Info.plist 已包含桌面、文稿、下载、外接卷、网络卷用途说明
- `CFBundleShortVersionString` = `1.0.0`
- `CFBundleVersion` = `1`

## Developer ID 签名与公证

需要有效证书：

```text
Developer ID Application: <Name> (<TEAM_ID>)
```

构建签名包：

```bash
./gradlew :desktopApp:packageReleaseDmg \
  -Pdockhand.macSign=true \
  -Pdockhand.macSignIdentity="Developer ID Application: <Name> (<TEAM_ID>)"
```

公证需要 App Store Connect Apple ID、App 专用密码和 Team ID：

```bash
./gradlew :desktopApp:notarizeReleaseDmg \
  -Pdockhand.notaryAppleId="<apple-id>" \
  -Pdockhand.notaryPassword="<app-specific-password>" \
  -Pdockhand.notaryTeamId="<team-id>"
```

不要把证书密码、App 专用密码提交到仓库。

## Mac App Store 构建

Compose Desktop 的 App Store 模式：

```bash
./gradlew :desktopApp:packageReleaseDmg \
  -Pdockhand.appStore=true \
  -Pdockhand.macSign=true \
  -Pdockhand.macSignIdentity="3rd Party Mac Developer Application: <Name> (<TEAM_ID>)" \
  -Pdockhand.provisioningProfile="/absolute/path/embedded.provisionprofile"
```

当前 **不建议直接提交 App Store**，因为存在以下阻塞项：

1. 应用目前直接枚举文件系统，尚未改为 App Sandbox + security-scoped bookmarks。
2. 终端服务通过外部 `open` 启动 Terminal，沙盒下不可用。
3. MTP 原生库、视频缩略图 helper 需要随应用签名，不能在运行时从 JAR 解压后执行。
4. `com.apple.security.device.usb` 是受限 entitlement，需要向 Apple 说明用途并获批。
5. 第三方依赖的隐私清单和 Required Reason API 声明尚未完整审计。

## 隐私清单

`desktopApp/PrivacyInfo.xcprivacy` 是隐私清单源文件。

Compose 默认不会把根目录的隐私清单放进 App Store 要求的位置，因此构建脚本会在
`createDistributableImpl` / `createReleaseDistributableImpl` 之后自动复制到：

```text
Dockhand.app/Contents/Resources/PrivacyInfo.xcprivacy
```

复制后会立即重新执行 `codesign`，避免修改 bundle 导致签名失效。

## 发布前检查

```bash
./scripts/verify-macos-app.sh /path/to/Dockhand.app
codesign --verify --deep --strict --verbose=2 Dockhand.app
spctl --assess --type execute --verbose Dockhand.app
xcrun stapler validate Dockhand-1.0.0.dmg
plutil -lint Dockhand.app/Contents/Info.plist
```

未签名/adhoc 构建的 `spctl` 会显示 rejected，这是预期结果。

并至少人工验证：

- 启动、退出、窗口恢复
- 本地文件浏览/复制/移动/删除/重命名
- 外接磁盘废纸篓
- MTP 设备
- 图片、SVG、视频缩略图
- 设置、快捷键、简介窗口
- 更新后的 TCC 权限提示
