# GitHub Release 更新检查

## 行为

- App 启动后约 800ms 请求 GitHub Release；只保留比当前 `APP_VERSION` 新的版本。
- 检测到新版本：
  - Drawer 设置按钮的图标上显示 Badge「1」；
  - 设置 sheet 里的版本选项 end action 显示“有新版本”；
  - 未忽略该版本时，第一次打开设置 sheet 会自动滚动到版本选项；同一版本只滚一次
    （`update_scroll_version` 持久化）。
- 点击版本选项：
  - 无更新：sheet 内 snackbar 提示“已是最新版”；
  - 有更新：弹出“有更新啦”Dialog，正文是 Release body，按钮为“前往下载”和“忽略此版本”。
- “前往下载”：用系统浏览器打开 Release 里的 macOS 安装包（优先 `.dmg`，其次 `.zip`）
  或 Release 页面。
- “忽略此版本”：记录 `ignored_update_version`，Badge 消失，但版本选项的“有新版本”文案保留。
  新版本出现后 Badge 会重新出现。
- 检查失败（离线、限流、404）静默处理，不弹错误。

## API

HTTP 请求使用 **Ktor Client + CIO engine**，JSON 仍用 kotlinx.serialization 手工解析。

- `GET https://api.github.com/repos/lumkit/dockhand/releases/latest`。
- 只有预发布 Release 时 `/latest` 返回 404，退回到
  `GET /repos/lumkit/dockhand/releases?per_page=10`，取最新非 draft。
- 请求头：`Accept: application/vnd.github+json`、`X-GitHub-Api-Version`、
  `User-Agent: Dockhand/<APP_VERSION>`；公开仓库，不需要 token。
- 未认证限流 60 次/小时/IP；当前每次启动只检查一次。
- 版本比较支持前导 `v`、`x.y.z` 和 `-prerelease`；稳定版大于同核心的预发布版。
- Release body 按纯文本展示，不解析 Markdown。

## 持久化

`SettingsRepository` 新增：

- `ignored_update_version`：用户忽略的版本（去掉前导 v）。
- `update_scroll_version`：已经自动滚动提示过的版本。

## 代码

- `shared/src/commonMain/.../core/update/Update.kt`
  - `AppUpdate`、`UpdateRepository`、`compareVersions`
- `shared/src/jvmMain/.../data/update/GithubUpdateRepository.kt`
  - Ktor Client（CIO engine）请求与 JSON 解析
- `data/AppContainer.kt` / `data/AppContainer.jvm.kt`
  - 注入 `GithubUpdateRepository`
- `ui/screen/home/HomeUiState.kt`、`HomeViewModel.kt`
  - `latestUpdate`、`ignoredUpdateVersion`、`updateScrollVersion`
  - 启动检查、忽略、滚动记录
- `ui/screen/home/HomeScreen.kt`、`HomeDrawer.kt`
  - 设置按钮 Badge、状态透传
- `ui/component/SettingsBottomSheet.kt`
  - 版本行 end action/点击、更新 Dialog、本地 snackbar、自动滚动
- `shared/build.gradle.kts` / `gradle/libs.versions.toml`
  - 新增 `ktor-client-core`、`ktor-client-cio`
- `desktopApp/build.gradle.kts`
  - jpackage 追加 `jdk.crypto.ec`（Ktor CIO 的 HTTPS）

## 测试

- `VersionCompareTest`（4）：稳定版/预发布、数字比较、前导 v、缺失段、非法输入。
- `GithubUpdateRepositoryTest`（2）：dmg 优先、无 asset 回退 Release 页面。
- `HomeViewModelTest` 新增 update 检查 + 忽略后 Badge 清除。
- `./gradlew build` 通过。

## 实机验收

1. 发布一个高于 `0.1.0-beta.1` 的 Release 后重启 App：
   - Drawer 设置图标出现 Badge「1」；
   - 打开设置自动滚动到版本选项，版本行显示“有新版本”。
2. 再打开设置：同一版本不再自动滚动。
3. 无更新时点击版本选项：sheet 内出现“已是最新版”snackbar。
4. 有更新时点击：Dialog 标题“有更新啦”，正文为 Release 内容；
   - “前往下载”打开浏览器；
   - “忽略此版本”关闭 Dialog，Badge 消失，版本行“有新版本”仍在。
5. 关闭再打开设置：被忽略的版本不再自动滚动；版本行文案仍在。
6. 发布更高版本后重启：Badge 重新出现，并再次自动滚动一次。
7. 离线/404/限流：无 Badge、无错误弹窗。

## 注意

- 只检测 Release，不比较 draft；`latest` 不含预发布，预发布仓库走列表回退。
- 未实现 ETag 条件请求；当前每次启动一次请求，远低于 60 次/小时。
- 下载优先直链 `.dmg`/`.zip`；可根据实际发布资产调整。
