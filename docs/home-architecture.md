# 主页架构（Home）

本文描述 Dockhand 主页的当前骨架：双列表面板、焦点模型、顶栏路径和侧边导航。
目标是把「本地磁盘」和「MTP 设备」抽象成同一种浏览体验，同时给后续的文件互传留出稳定的扩展点。

## 1. 分层

```
domain/
  model/        FileSource / FileLocation / FileEntry / FilePlace / FileBreadcrumb
  repository/   FileSystemProvider / FileBrowserRepository
data/
  file/         LocalFileSystemProvider / MtpFileSystemProvider（FFM + mtp-rs）
ui/screen/home/
  HomeScreen.kt          窗口布局 + 顶栏 + Scaffold
  HomeTopBar.kt          焦点面板路径 → 面包屑
  HomeDrawer.kt          侧边导航
  HomeViewModel.kt       ViewModel（导航、焦点、历史、加载）
  HomeUiState.kt         不可变 UI 状态
  component/
    DualPaneBrowser.kt   顶部工具栏 + 左右面板
    FileToolbar.kt       工具栏、新建弹窗、操作错误提示
    FileBrowserPane.kt   单个列表 + 焦点遮罩 + 空/错误态
```

依赖方向只有一条：

```
UI (Compose) → HomeViewModel → FileBrowserRepository → FileSystemProvider → 平台 API
```

UI 不直接接触 `java.nio.file`，也不判断“这是不是 MTP”；平台差异全部收敛在 provider 里。

## 2. 核心模型

- **FileSource**：文件来源。本地是固定的 `local`；MTP 用
  `mtp:<deviceLocation>:<storageId>`，同时区分设备和存储。
- **FileLocation**：`source + path`。`path` 对 UI 不透明：
  - 本地 provider 使用 POSIX 绝对路径；
  - MTP provider 可以使用以 `/` 分段的逻辑路径。
- **FileEntry**：列表行，带自身的 `FileLocation`，所以“进入目录”不需要拼路径。
- **FilePlace**：侧边栏入口（个人目录、常用目录、挂载磁盘、MTP 设备）。
- **FileBreadcrumb**：顶栏的一段，带点击后应导航到的 `path`。

## 3. 数据流

```
provider.observePlaces() ─┐
                          ├─ combine + distinctUntilChanged ─→ HomeViewModel
provider.observePlaces() ─┘                                      │
                                                                  ├─ places
                                                                  ├─ leftPane / rightPane
                                                                  └─ focusedPane
                                                                        │
provider.observeChanges(location) ── per-pane watcher ──→ load() ────────┤
                                                                        │
                          HomeScreen ← viewModel() + collectAsState ───────┘
                              │
                 ┌────────────┴────────────┐
                 │                         │
            HomeTopBar                DualPaneBrowser
        （焦点面板路径）          （左右两个 FileBrowserPane）
```

`HomeScreen` 只负责通过 `viewModel()` 取得 `HomeViewModel`，并把回调转给纯 UI 组件；
加载、导航、焦点和历史全部在 ViewModel 的 `viewModelScope` 中执行。
`HomeViewModel` 暴露 `StateFlow<HomeUiState>`，界面用 `collectAsState()` 订阅。

所有 provider 都必须**立即发出一个值**（哪怕是空列表）。仓库对每个 provider 做了
`onStart { emit(emptyList()) }`，避免某个后端首帧较慢时整个侧边栏被 `combine` 阻塞。

本地 provider 的侧边栏列表同时使用两种机制：

- `WatchService` 监听 `/Volumes` 和用户主目录的直接子项变化，磁盘插拔/常用目录变化会立即触发重扫；
- 每 3 秒一次的 ticker 兜底，覆盖偶发的 watcher 漏报；重复列表由 `distinctUntilChanged` 过滤。

## 4. 双列表与焦点

每个面板都是独立的 `FilePaneState`：

```
location      当前路径
entries       当前列表（FileRowUi，summary 已在加载阶段预计算）
selectedIds   选中项
isLoading     加载中
errorMessage  读取错误
backStack / forwardStack
```

初始状态：

- 左面板：`FilePlaceKind.Home`（没有则本机第一个入口）；
- 右面板：本机 `/`（根目录不再出现在侧边栏）；
- 焦点默认在左面板。

交互规则：

| 操作 | 结果 |
| --- | --- |
| 鼠标移入任意面板 | 不改变焦点 |
| 单击任意面板的目录/文件 | 该面板获得焦点并选中条目；不导航 |
| 双击任意面板的目录 | 该面板获得焦点；进入该目录；顶栏切到该面板路径 |
| 双击任意面板的文件 | 该面板获得焦点并选中；文件打开动作后续接入 |
| 右键任意面板的目录/文件 | 该面板获得焦点并选中；右键菜单/操作窗口后续接入 |
| 单击面板空白处 | 只切换焦点 |
| 单击侧边栏地点 | 当前焦点面板导航到该地点 |
| 单击面包屑 | 当前焦点面板导航到对应祖先 |
| 后退 / 前进 | 只作用于指定面板，走该面板自己的 undo/redo 历史 |
| 上一级 | 只作用于指定面板，返回父目录 |

鼠标悬停不再切换焦点；焦点只由单击面板/条目、侧边栏点击或工具栏动作改变。

视觉上沿用参考实现：

- 失焦面板整体 `alpha = 0.55`；
- 失焦面板叠加 `innerShadow`，明确输入只作用于另一侧；
- 左面板左上角 12dp 圆角、右面板直角，和内容 Surface 的 squircle 裁切对齐。

### 目录实时刷新

每个面板在路径变化后会启动一个 watcher：

```
HomeViewModel.watch(paneId, location)
  → FileBrowserRepository.observeChanges(location)
  → LocalFileSystemProvider.watchDirectory(...)
  → WatchService ENTRY_CREATE / ENTRY_DELETE / ENTRY_MODIFY
```

- watcher 只监听当前目录的直接子项，隐藏文件变化会被忽略；
- 事件在 provider 内合并，ViewModel 再用 `conflate()` 防止重复刷新；
- 路径切换时旧 watcher 被取消，`onCleared()` 时全部清理；
- 两个面板各自独立监听，一侧目录变化不会影响另一侧；
- 侧边栏的 `places` 同样由 watcher + ticker 驱动，插拔磁盘/常用目录变化会自动更新。

### 列表性能约定

`LazyColumn` 的 item 里不做业务计算：

1. provider 在 IO 线程列目录并获取元数据（包括文件夹的 `childCount`）；
2. `HomeViewModel` 在 `Dispatchers.Default` 上完成排序、时区获取和 summary 格式化，一次构造 `FileRowUi`；
3. `LazyColumn` 只读取 `row.name`、`row.summary`、`row.isDirectory` 等已准备好的字段。

时间格式化、文件大小换算、system timezone 查询都只发生在列表加载阶段。

## 5. 文件工具栏

`DualPaneBrowser` 顶部是封装后的 `FileToolbar`，所有动作只作用于焦点面板：

| 按钮 | 行为 |
| --- | --- |
| 后退 / 前进 | 调用 `HomeViewModel.onBack/onForward(focusedPane)` |
| 新建 | 打开 `WindowDialog`：输入名称 + “创建文件”复选框，确认后调用 `HomeViewModel.onCreate(focusedPane, name, createFile)` |
| 同步 | 把非焦点面板导航到焦点面板当前目录；焦点不变，跳转写入目标面板的 undo/redo 历史 |
| 上一级 | 调用 `HomeViewModel.onGoUp(focusedPane)` |

按钮可用状态由 `FilePaneState` 的 `canGoBack/canGoForward/canGoUp` 和两侧路径推导。
新建失败写入 `HomeUiState.operationError`，由工具栏展示一行可关闭的错误提示。

新建对话框底部左侧是“确认”，右侧是“取消”；输入框下方的 `CheckboxPreference`
勾选时创建空文件，未勾选时创建文件夹。

## 6. HomeTopBar 路径

`HomeTopBar` 只接收一个 `FileLocation?`，不保存自己的路径状态：

```
focusedPaneState.location → breadcrumbs() → BreadcrumbBar
```

`breadcrumbs()` 是纯函数，规则：

- 本地根目录显示来源名（“本机”）；
- `/Volumes/<磁盘名>` 从磁盘根开始显示，不出现冗长的 `Volumes` 段；
- MTP 从设备名开始，后面按 `/` 分段。

因此“路径更新”没有额外同步逻辑：**焦点面板或路径变化 → 状态变化 → 顶栏重算**。
点击某一段时，Controller 用同一 `source` 和该段的 `path` 导航。

## 7. 侧边导航

侧边栏完全由 `places` 驱动：

- 本地入口来自 `LocalFileSystemProvider.scanPlaces()`，分组为 `FilePlaceGroup.Local`；
- `/Volumes` 下的挂载磁盘（包括 `Macintosh HD`）单独归入
  `FilePlaceGroup.Mounted`，侧边栏显示为“挂载磁盘”；
- MTP 入口来自 `MtpFileSystemProvider`，分组为 `FilePlaceGroup.Mtp`；
- 固定入口的名称由 UI 按 `FilePlaceKind` 本地化（桌面、文稿、下载……）；
- 动态名称（用户名、磁盘名、设备名）来自 provider 的 `label`。

高亮规则：

- 地点允许前缀命中，并选择路径最长的命中项。例如
  `~/Documents/sub` 会高亮“文稿”，而不是“个人目录”。

MTP provider 会把每台设备的每个存储挂到侧边栏，标签为
`设备型号 · 存储名`；没有设备时 MTP 分组为空。

MTP 存储行可以点击展开，展开后显示“相片（/DCIM/Camera）/ 下载 / 电影 / 音乐 /
图片”快捷入口；展开状态和快捷入口顺序写入设置，重启应用后保留。

每个分组内的条目右侧有拖拽手柄，按住上下拖动可调整该分组内部的顺序；
分组本身保持“本地 / 挂载磁盘 / MTP设备”的固定顺序。每个分组的顺序写入
`SettingsRepository` 的 `drawer_place_order_<group>`，重启应用后保留；
新增条目会自动追加到末尾。

## 8. MTP 后端

`MtpFileSystemProvider` 使用内嵌的 **mtp-rs**（MIT OR Apache-2.0）作为 MTP/PTP 实现：

```
Compose/JVM
  → FFM (JDK 25)
  → libdockhand_mtp.dylib
  → mtp-rs (pure Rust, nusb)
  → USB
```

- 原生桥源码在 `native/mtp-bridge`，通过 C ABI 暴露：
  - `dockhand_mtp_storages`
  - `dockhand_mtp_list(device_key, storage_id, path)`
  - `dockhand_mtp_mkdir(device_key, storage_id, path)`
  - `dockhand_mtp_upload(device_key, storage_id, local, remote)`
  - `dockhand_mtp_download(device_key, storage_id, remote, local)`
- 多设备通过 `MtpDevice::open_by_location` 选择；设备 key 使用 USB location ID。
- Gradle 的 `buildMtpBridge` 任务构建 release dylib，并复制到 resources 的 `native/`。
- JVM 侧从 resources 解压 dylib，用 `SymbolLookup.libraryLookup` + FFM 调用。
- MTP 没有目录事件：`observeChanges` 每 4 秒轮询当前目录并比较结果；
  侧边栏每 5 秒刷新 storage 列表。
- 当前限制：
  - 需要 JDK 25 + `--enable-native-access=ALL-UNNAMED`；
  - 原生库按当前 macOS 架构构建；分发 Intel/Universal 需要额外构建对应 slice；
  - mtp-rs 的目录列表通常不返回 modified time，MTP 行的 summary 可能只有大小；
  - MTP 内部复制尚未实现，跨 provider 传输仍需后续 `FileTransferRepository`；
  - 分发时需要对嵌入的 dylib 做签名/公证。

UI、HomeViewModel、侧边栏和顶栏不需要感知这些细节。

## 9. 文件互传

当前已具备：

- `FileSystemProvider.copy(source, destinationDirectory, overwrite)`；
- `FileBrowserRepository.copy(entries, destinationDirectory, overwrite)`；
- 本地 provider 的文件/目录递归复制实现。

注意：工具栏的“同步”是**目录跳转**（把非焦点面板导航到焦点面板目录），不是复制。
复制到另一侧仍需要单独的 UI 和传输队列。

尚未接入：

- 跨 provider 的流式复制（例如 MTP → 本地）。仓库会显式拒绝，而不是静默失败；
- 传输任务队列、进度、取消、冲突策略；
- 双栏“复制到另一侧”按钮。

建议下一步新增：

```
domain/transfer/
  TransferRequest / TransferTask / TransferProgress
  FileTransferRepository
data/transfer/
  DefaultFileTransferRepository   // 串行队列 + 进度 + 取消
```

跨 provider 时由 `FileTransferRepository` 打开 source 的读取流、写入 target 的写入流，
不要试图让某一个 provider 直接理解另一个 provider 的路径。

## 10. 扩展检查清单

- [x] 双栏工具栏：后退 / 前进 / 新建 / 同步 / 上一级
- [x] 两个文件面板的目录变化监听
- [x] 侧边导航的磁盘/目录变化监听
- [ ] 刷新动作与“复制到另一侧”（复制而非同步）
- [ ] 更多菜单：排序、显示隐藏文件
- [ ] 多选（⌘/Shift）与批量复制
- [ ] 传输队列与进度面板
- [x] MTP 真实后端（内嵌 mtp-rs，FFM + Rust bridge）
- [ ] 文件预览 / 打开方式