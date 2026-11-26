# 外部文件拖入（Finder → 面板）实现步骤

> 状态：D1 / D2 已确认；Step 0 探针通过，按方案 B 实现完成；剩余第 9 节实机验收。
> 相关：拖出（面板 → 其他程序/另一面板）见 [external-file-drag-out.md](external-file-drag-out.md)。
> 范围：从其他应用（Finder）拖入文件/文件夹，按拖拽动作复制或移动到指针所在文件面板的当前目录。
> 分工：
> - 实现、单元测试、编译验证：本 agent。
> - 实机拖拽、动作观测、视觉与窗口兼容性、MTP 设备验证：用户（见第 9 节）。
> - Step 0 探针已由用户实机通过；本 agent 负责单测与编译验证，第 9 节实机验收由用户完成。

## 0. 已确认的产品规则

- **R1 动作优先**：拖拽事件带明确动作时，不弹确认框，直接执行对应操作；没有明确动作时，弹复制确认框。
- **R2 只认面板**：指针落在哪个文件面板，哪个面板成为焦点面板并作为目标目录；指针进入面板后显示蒙层，提示“松手复制/移动到当前目录”。
- 蒙层只在面板区域内出现；工具栏、分隔条、侧边栏不算目标。

## 1. 目标与非目标

### 目标

1. Finder 拖入文件/文件夹到左/右文件面板。
2. 有明确动作时直接执行：复制 → 复制任务；移动 → 移动任务；不弹窗。
3. 无明确动作（或动作不受支持）时弹现有复制确认框，确认后走复制任务。
4. 拖入项落到指针所在面板的当前目录，该面板同时获得焦点。
5. 悬停时显示蒙层：目标面板高亮 + 动作提示 + 目标路径。
6. 完全复用现有任务管线：进度、字节进度、速度/ETA、取消、`_副本` 重名、跨 provider/MTP、结束后刷新双面板。
7. **数据安全**：任何情况下都不能让拖拽源（Finder）在异步复制完成前删除源文件（见第 3 节）。

### 非目标（本轮不做）

- 从 Dockhand 拖出到 Finder。
- 拖到面板边缘自动滚动/自动切换目录。
- 纯文本、图片、浏览器 URL、非 `file:` 资源的导入。
- Link 动作的真实链接创建（按“无受支持动作”处理，弹复制框）。
- 拖放排序。

## 2. 关键语义

| 拖拽源动作 | 业务操作 | 是否弹窗 | 蒙层文案 |
| --- | --- | --- | --- |
| Copy | `copyWithProgress` | 否 | 松手复制到当前目录 |
| Move | `moveWithProgress` | 否 | 松手移动到当前目录 |
| Link | 不支持，按复制处理 | 是（复制确认框） | 松手后确认复制到当前目录 |
| 无动作（NONE / null） | 按复制处理 | 是（复制确认框） | 松手后确认复制到当前目录 |

- 直接任务使用 `taskPreferences.background` 作为后台默认值，与现有复制/移动确认框的初始值一致。
- 移动任务使用 `moveWithProgress`（先复制、成功后再删除源），跨 provider/MTP 与现有移动流程一致。
- 重名规则：`_副本`、`_副本1`、`_副本2` 递增；源名本身带 `_副本[N]` 时从下一个序号继续，不叠加 `_副本_副本`（例如重复拖入同一目录）。
- 复制确认框复用 `CopyRequestState` + `CopyRequestDialog`，不新增对话框组件。

## 3. 平台事实与接入方式（本次设计的关键）

### 3.1 链路

```
Finder 拖拽
  → macOS DnD（pasteboard: file URLs）
  → AWT DropTarget（窗口内的 Compose 根组件）
  → DropTargetDragEvent / DropTargetDropEvent
  → javaFileListFlavor（List<File>）
  → 绝对路径 → FileEntry
  → ExternalDropController（面板命中、蒙层、焦点）
  → HomeViewModel：按动作复制/移动 或 弹复制框
  → TaskManager → 现有复制/移动管线
```

### 3.2 本项目锁定的事实

| 事实 | 结论 |
| --- | --- |
| Compose Multiplatform | 1.13.0-rc01（`gradle/libs.versions.toml`） |
| Compose Desktop 的 AWT 桥接 | 在窗口的 Compose 根组件上安装 `DropTarget`，`dragOver` 调用 `acceptDrag(dropAction)`，`drop` 调用 `acceptDrop(dropAction)`，随后 `dropComplete(result)` |
| `DragAndDropEvent.action` | 来自 `dtde.dropAction`，只读；Compose 没有提供修改“接受动作”的入口 |
| Compose 的 `drop` 在 NONE 动作下 | `acceptDrop(0)` 会抛 `IllegalArgumentException`（AWT 限制），`onDrop` 不会被调用 |
| `DragData.FilesList.readFiles()` | 返回 `file://` URI 字符串，不是路径（只有方案 A 会用到） |
| 拖放事件线程 | AWT `DropTargetListener` 在 EDT 上同步执行，禁止磁盘 IO 与大任务 |
| Compose 根组件 | `ComposeWindow` 的 `contentPane` 中的 `ComposeWindowPanel`，其 `dropTarget` 由 Compose 安装 |

### 3.3 macOS 动作语义与数据安全（必须读）

已核实的平台行为：

1. **AWT 在 macOS 上对 Finder 文件拖拽默认上报 `ACTION_MOVE`**：不按修饰键时 `DropTargetDropEvent.getDropAction()` 返回 MOVE，按住 Control/Option 才返回 COPY（社区实测：https://stackoverflow.com/questions/38318279）。macOS 原生映射见 JDK 的 `nsDragOperationForModifiers`：无修饰键 → 源允许的动作集合，AWT 再按 MOVE 优先收窄。
2. AWT DnD 规范要求：**MOVE 成功后由拖拽源删除原始数据**；Finder 是源，如果它收到 MOVE，按规范会在 drop 完成时移除源文件（Step 0 已用一次性文件复验：强制 COPY 后源文件保留）。
3. Compose 的 AWT 桥接**无条件接受源上报的动作**。也就是说，用 `Modifier.dragAndDropTarget` 时，普通 Finder 拖拽会被当作 MOVE 接受，Finder 可能在我们的异步复制开始前就删掉源文件。
4. 同一桥接在动作是 NONE 时会因 `acceptDrop(0)` 抛异常，导致“无动作 → 弹复制框”根本走不到 `onDrop`。

结论：

- **不能把“让 OS 执行动作”当成实现方式**。正确做法是：**在 AWT 层强制以 COPY 完成本次 drop 协商（保护源文件），drop 时读取源上报的原始动作作为“用户意图”，再由我们执行复制或移动。**
- 移动由我们自己完成：先复制到目标、确认成功，再删除源（`moveWithProgress`）。因为 OS 看到的是 COPY，Finder 不会删源，不存在竞态。
- 这套语义只有直接接管 AWT `DropTarget` 才能实现；Compose 的 `dragAndDropTarget` 无法选择接受动作。

### 3.4 两种接入方案

| | 方案 A：Compose `dragAndDropTarget` | 方案 B：AWT `DropTarget` 接管 |
| --- | --- | --- |
| 代码量 | 小 | 中（需要替换 Compose 根组件的 DropTarget、坐标映射、蒙层驱动） |
| 动作安全 | 无法强制 COPY，有源文件被删除风险 | 强制 COPY，源文件安全 |
| Move 支持 | 不安全，不建议 | 安全，自己执行移动 |
| 无动作弹框 | Compose 会抛异常，做不到 | 可读取 NONE 并弹框 |
| 与 Compose 的关系 | 使用官方 DnD API | 停用 Compose 的 DropTarget（当前项目未使用 Compose DnD） |
| 结论 | 仅作为失败兜底 | **推荐，默认方案** |

**决策 D-Adapter**：已确认采用方案 B（AWT 接管）。Step 0 已验证：能替换 Compose 的 DropTarget、坐标映射正确、强制 COPY 后源文件保留。方案 A（Compose `dragAndDropTarget`）不采用，仅作为未来 Compose 修复动作协商后的备选。

## 4. 总体设计

### 4.1 分层

```
desktopApp/main.kt
  ├─ ExternalDropController（commonMain，纯 Compose 状态）
  ├─ ExternalFileDropHost.install(window, controller)（jvmMain，AWT DropTarget）
  └─ CompositionLocalProvider(LocalExternalDropController provides controller) { HomeScreen() }

commonMain
  FileBrowserPane ── 注册面板 bounds / 读 hover 状态 / 渲染蒙层
  HomeScreen ─────── 注册 ExternalDropBridge（焦点、drop 回调 → ViewModel）
  HomeViewModel ──── onExternalFilesDropped(paneId, paths, action)
  Repository ─────── entriesForLocalPaths(paths) → 复用 copy/move 管线
```

### 4.2 新增契约

```kotlin
// commonMain: ui/component/ExternalFileDrop.kt
enum class ExternalDropAction { Copy, Move, Link }

@Immutable
data class ExternalDropHover(
    val paneId: PaneId,
    val action: ExternalDropAction?,   // null = 无明确动作，松手弹复制框
)

@Stable
class ExternalDropController {
    /** 当前悬停的面板与动作；null 表示指针不在任何面板内。 */
    var hover: ExternalDropHover? by mutableStateOf(null)
        private set

    var bridge: ExternalDropBridge? = null

    /** bounds 使用与 AWT 事件一致的逻辑坐标。 */
    fun updatePaneBounds(paneId: PaneId, bounds: Rect)
    fun removePaneBounds(paneId: PaneId)
    fun paneAt(position: Offset): PaneId?
    fun onDragMoved(position: Offset, action: ExternalDropAction?): Boolean // 返回是否有目标面板
    fun onDragExited()
    fun onDrop(position: Offset, paths: List<String>, action: ExternalDropAction?): Boolean
}

interface ExternalDropBridge {
    fun onPaneFocused(paneId: PaneId)
    fun onDrop(paneId: PaneId, paths: List<String>, action: ExternalDropAction?)
}

val LocalExternalDropController = staticCompositionLocalOf<ExternalDropController?> { null }
```

```kotlin
// commonMain: HomeViewModel
fun onExternalFilesDropped(paneId: PaneId, paths: List<String>, action: ExternalDropAction?)

// 从现有确认流程抽取，供拖放直接复用
private fun startCopyTask(entries: List<FileEntry>, destination: FileLocation, background: Boolean, titleEntry: FileEntry = entries.first())
private fun startMoveTask(entries: List<FileEntry>, destination: FileLocation, background: Boolean, titleEntry: FileEntry = entries.first())
```

```kotlin
// commonMain: domain/repository
suspend fun entriesForLocalPaths(paths: List<String>): List<FileEntry> // 默认空
```

```kotlin
// jvmMain: ui/component/ExternalFileDropHost.kt
object ExternalFileDropHost {
    fun install(window: ComposeWindow, controller: ExternalDropController)
}

internal fun javaFileListPaths(files: List<*>): List<String>          // 纯函数：List<File> → 绝对路径
internal fun externalDropActionOf(awtDropAction: Int): ExternalDropAction? // 纯函数
```

### 4.3 AWT 宿主行为

安装：

- 从 `MiuixWindow(init = { window -> ... })` 取得 `ComposeWindow`。
- 在 `window.contentPane` 的组件树里找到当前持有 `dropTarget` 的 Compose 根组件（Compose Desktop 的 `ComposeWindowPanel`）。
- 用 `DropTarget(component, ACTION_COPY, listener, true)` 替换它（`component.dropTarget = ...`）。
- Compose 组件可能晚于窗口出现：用 `Swing Timer` 重试，直到替换成功或超时；失败时输出 `[external-drop]` 错误日志。
- 保存宿主引用，避免监听器被回收；窗口重建时重新安装。

事件：

| AWT 事件 | 行为 |
| --- | --- |
| `dragEnter` | 读取 `dtde.getDropAction()` 作为意图；`controller.onDragMoved(point, intent)`；有面板则 `acceptDrag(ACTION_COPY)`，否则 `rejectDrag()` |
| `dragOver` | 同 `dragEnter` |
| `dropActionChanged` | 更新意图与蒙层文案；仍然 `acceptDrag(ACTION_COPY)` |
| `dragExit` | `controller.onDragExited()` |
| `drop` | 读取意图（必须在 acceptDrop 之前）；`acceptDrop(ACTION_COPY)`；读取 `javaFileListFlavor` → 绝对路径；`controller.onDrop(point, paths, intent)`；`dropComplete(handled)` |

约束：

- `drop` 里只做 `getTransferData(javaFileListFlavor)` → 绝对路径字符串，不做磁盘 stat。
- 指针不在任何面板：不显示蒙层，`rejectDrag`/`rejectDrop`（只认面板）。
- 强制 `ACTION_COPY` 发生在 `acceptDrag` 与 `acceptDrop` 两处；drop 结束后 Finder 不会删除源文件。
- 读取意图必须在 `acceptDrop` 之前完成（`acceptDrop` 会改写 `currentDA`）。
- 窗口失焦/最小化时 AWT 可能不派发 `dragExit`：在 `ComposeWindow` 上挂 `WindowFocusListener`，失焦时调用 `controller.onDragExited()`（已实现）。
- 不要指望 `dropComplete(false)` 阻止源删除；必须在 `acceptDrag` / `acceptDrop` 阶段就固定 COPY。
- `DropTarget` 替换后 Compose 自带的 `dragAndDropTarget` 修饰符不再收到外部拖拽（当前项目未使用）。
- 意图读取后立即 `acceptDrop(ACTION_COPY)`，再读取 Transferable；源文件不会因 MOVE 协商被删除。

坐标：

- `DropTargetEvent.location` 是 AWT 组件逻辑坐标；Compose 的 `boundsInRoot()` 是像素坐标。
- 面板注册 bounds 时除以 `LocalDensity.current.density`，统一成逻辑坐标；宿主直接用 `dtde.location` 命中。
- Step 0 已核对同一指针位置的 AWT 坐标与面板 bounds 一致。

### 4.4 交互规则

| 场景 | 行为 |
| --- | --- |
| 拖拽进入面板 | 面板 `onPaneFocused`；蒙层显示动作提示 + 目标路径 |
| 在面板内移动 | 更新动作文案；不重复聚焦 |
| 离开面板 / 拖拽结束 / 窗口失焦 | 清除蒙层 |
| 松手 | 按意图执行复制/移动/弹复制框；蒙层立即清除 |
| 拖拽内容不是文件列表 | 不接收（reject），无蒙层 |
| 指针在工具栏/分隔条/侧边栏 | 不接收，无蒙层 |
| 目标面板 `location == null` | 不接收；可选 snackbar“目标目录不可用” |
| 目录拖入自身子目录 | 跳过该项并提示，其余照常 |

## 5. 改动文件清单

| 文件 | 类型 | 内容 |
| --- | --- | --- |
| `shared/src/commonMain/kotlin/io/github/lumkit/dockhand/ui/component/ExternalFileDrop.kt` | 新增 | `ExternalDropAction`、`ExternalDropHover`、`ExternalDropController`、`ExternalDropBridge`、`LocalExternalDropController` |
| `shared/src/jvmMain/kotlin/io/github/lumkit/dockhand/ui/component/ExternalFileDropHost.kt` | 新增 | AWT `DropTarget` 接管、URI → 路径、动作映射 |
| `desktopApp/src/main/kotlin/io/github/lumkit/dockhand/main.kt` | 修改 | 创建 controller、安装 host、提供 CompositionLocal |
| `shared/src/commonMain/kotlin/io/github/lumkit/dockhand/ui/screen/home/HomeScreen.kt` | 修改 | 注册 `ExternalDropBridge`（焦点 + drop → VM） |
| `shared/src/commonMain/kotlin/io/github/lumkit/dockhand/ui/component/FileBrowserPane.kt` | 修改 | 注册面板 bounds、渲染蒙层 |
| `shared/src/commonMain/kotlin/io/github/lumkit/dockhand/ui/component/DualPaneBrowser.kt` | 不改 | controller 经 CompositionLocal 传递，无需透传参数 |
| `shared/src/commonMain/kotlin/io/github/lumkit/dockhand/ui/screen/home/HomeViewModel.kt` | 修改 | `onExternalFilesDropped`、抽取 `startCopyTask` / `startMoveTask`、复制框回退 |
| `shared/src/commonMain/kotlin/io/github/lumkit/dockhand/domain/repository/FileSystemProvider.kt` | 修改 | `entriesForLocalPaths` 默认实现 |
| `shared/src/commonMain/kotlin/io/github/lumkit/dockhand/domain/repository/FileBrowserRepository.kt` | 修改 | 接口默认 + `DefaultFileBrowserRepository` 路由 |
| `shared/src/jvmMain/kotlin/io/github/lumkit/dockhand/data/file/LocalFileSystemProvider.kt` | 修改 | `entriesForLocalPaths`（IO、包含隐藏文件） |
| `shared/src/commonMain/composeResources/values/strings.xml` | 修改 | 蒙层文案 |
| `shared/src/commonMain/composeResources/values-en-rUS/strings.xml` | 修改 | 蒙层文案（英文） |
| `shared/src/jvmTest/kotlin/io/github/lumkit/dockhand/ui/component/ExternalFileDropTest.kt` | 新增 | URI → 路径、动作映射、controller 命中 |
| `shared/src/jvmTest/kotlin/io/github/lumkit/dockhand/data/file/LocalFileSystemProviderTest.kt` | 修改 | `entriesForLocalPaths` 用例 |
| `shared/src/commonTest/kotlin/io/github/lumkit/dockhand/ui/screen/home/HomeViewModelTest.kt` | 修改 | 动作分发、弹框回退、任务参数 |

## 6. 实施步骤

每步以“可编译”为界。Step 0 实机门禁已通过。

### Step 0：探针（已完成，方案 B 成立）

> 用户实机结论：
> - Compose 根组件替换成功：`androidx.compose.ui.awt.ComposeWindowPanel`。
> - 动作值：无修饰键 = Move，Option = Copy，Command = Move，Command+Option = Link。
> - 强制 COPY 后源文件保留（`probe source exists = true`）。
> - 蒙层与面板边界一致，工具栏/侧边栏不接收。
>
> 按方案 B 继续实现；探针日志已在正式实现中移除。

探针阶段做过的事（留档）：

1. `ExternalFileDrop.kt`：controller（hover、bounds、paneAt）+ 蒙层。
2. `ExternalFileDropHost.kt`：找到 Compose 根组件、替换 `DropTarget`、强制 COPY、记录动作日志。
3. `main.kt`：安装/卸载宿主，`CompositionLocalProvider` 下发 controller。
4. `FileBrowserPane`：注册逻辑坐标 bounds，悬停显示蒙层。
5. 日志验证 `dtde.getDropAction()`、`getSourceActions()`、坐标命中、drop 后源文件存在性。

### Step 1：动作与控制器（commonMain）

新增 `ExternalFileDrop.kt`：

- `ExternalDropAction`、`ExternalDropHover`、`ExternalDropController`、`ExternalDropBridge`、`LocalExternalDropController`。
- controller 逻辑：
  - `updatePaneBounds` / `removePaneBounds`：维护 `mutableStateMapOf<PaneId, Rect>`。
  - `paneAt(position)`：按 bounds 命中，多个重叠时取最后注册的。
  - `onDragMoved(position, action)`：命中面板则 `hover = ExternalDropHover(paneId, action)` 并调用 `bridge.onPaneFocused(paneId)`（同一面板连续移动不要重复聚焦）；**未命中则 `hover = null`**（指针从面板移到工具栏时蒙层要消失）；返回是否命中。
  - `onDragExited()`：`hover = null`。
  - `onDrop(...)`：命中面板 → `bridge.onDrop(...)`；`hover = null`；返回是否处理。
- 注意：`hover` 是 Compose 状态，AWT 线程写入即触发重组；不在 controller 里做任务、不做 IO。

验收：`:shared:compileKotlinJvm` 通过；控制器命中逻辑单元测试通过。

### Step 2：AWT 宿主（jvmMain）

新增 `ExternalFileDropHost.kt`：

```kotlin
object ExternalFileDropHost {
    fun install(window: ComposeWindow, controller: ExternalDropController) {
        // 1. 查找 contentPane 组件树中 dropTarget != null 的 Compose 根组件
        // 2. component.dropTarget = DropTarget(component, ACTION_COPY, listener, true)
        // 3. 保存引用；窗口重建/延迟出现时重试
    }
}
```

监听器实现要点（伪代码）：

```kotlin
override fun dragEnter(dtde: DropTargetDragEvent) {
    val handled = controller.onDragMoved(
        position = dtde.location.toLogical(),
        action = externalDropActionOf(dtde.dropAction),
    )
    if (handled) dtde.acceptDrag(DnDConstants.ACTION_COPY) else dtde.rejectDrag()
}
override fun dragOver(dtde: DropTargetDragEvent) { /* 同 dragEnter */ }
override fun dropActionChanged(dtde: DropTargetDragEvent) {
    controller.onDragMoved(dtde.location.toLogical(), externalDropActionOf(dtde.dropAction))
    dtde.acceptDrag(DnDConstants.ACTION_COPY)
}
override fun dragExit(dte: DropTargetEvent) { controller.onDragExited() }
override fun drop(dtde: DropTargetDropEvent) {
    val position = dtde.location.toLogical()
    if (!dtde.transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor) ||
        controller.paneAt(position) == null
    ) {
        dtde.rejectDrop(); return
    }
    val intent = externalDropActionOf(dtde.dropAction)   // 必须在 acceptDrop 之前读
    dtde.acceptDrop(DnDConstants.ACTION_COPY)            // 保护源文件，禁止 OS 删除
    @Suppress("UNCHECKED_CAST")
    val files = dtde.transferable.getTransferData(DataFlavor.javaFileListFlavor) as List<*>
    val paths = javaFileListPaths(files)
    val handled = paths.isNotEmpty() && controller.onDrop(position, paths, intent)
    dtde.dropComplete(handled)
}
```

- `toLogical()`：`Offset(dtde.location.x.toFloat(), dtde.location.y.toFloat())`（AWT 逻辑坐标，与面板 bounds 同一坐标空间）。
- 组件发现：递归遍历 `window.contentPane` 的 `JComponent` 子组件，取 `dropTarget != null` 的那个；若暂时没有，`invokeLater` 重试。
- 安装失败时输出错误日志；正常路径不打印日志。
- 不做磁盘 IO；只把 `List<File>` 映射成绝对路径字符串（stat 见 Step 5 的 provider）。

验收：编译通过；Step 0 已通过，本步代码保持稳定。

### Step 3：面板蒙层与 bounds 注册（FileBrowserPane）

- 读取 `LocalExternalDropController.current`。
- 在既有外层 `Box` 的 `onGloballyPositioned` 中注册 bounds（逻辑坐标 = `boundsInRoot() / density`）；`DisposableEffect(pane.id)` 里 `removePaneBounds`。
- 蒙层（仅在 `hover?.paneId == pane.id` 时显示）：
  - `matchParentSize()` 覆盖层，圆角与面板一致；
  - 8% primary 填充 + 2dp primary 描边；
  - 居中卡片：图标 + 文案 + 目标路径；
  - 文案按动作切换（见第 2 节表格）；`location == null` 时显示“目标目录不可用”；
  - Move 时可在路径下方加一行“移动会移除源文件”的提示（可选项）；
  - 覆盖层不挂 `pointerInput`，不能吃掉事件。
- 蒙层放在面板最上层，保证可见；面板失焦时的 `innerShadow` 不能盖住它。

### Step 4：接线（main / HomeScreen）

`desktopApp/main.kt`（实际实现用 `FrameWindowScope.window`，生命周期比 `init` 更好管理）：

```kotlin
val dropController = remember { ExternalDropController() }
MiuixWindow(...) {
    val window = window
    DisposableEffect(window, dropController) {
        ExternalFileDropHost.install(window, dropController)
        onDispose { ExternalFileDropHost.uninstall(window, dropController) }
    }
    CompositionLocalProvider(LocalExternalDropController provides dropController) {
        Box(...) { App(container) }
    }
}
```

`HomeScreen.kt`：

```kotlin
val dropController = LocalExternalDropController.current
DisposableEffect(dropController) {
    dropController?.bridge = object : ExternalDropBridge {
        override fun onPaneFocused(paneId: PaneId) = homeViewModel.onPaneFocus(paneId)
        override fun onDrop(paneId: PaneId, paths: List<String>, action: ExternalDropAction?) =
            homeViewModel.onExternalFilesDropped(paneId, paths, action)
    }
    onDispose { dropController?.bridge = null }
}
```

验收：编译通过；暂停任务、文件简介等弹窗打开时拖拽仍能落到主窗口面板。

### Step 5：路径 → FileEntry（provider）

- `FileSystemProvider` 增加 `entriesForLocalPaths` 默认空实现。
- `FileBrowserRepository` 增加默认空实现；`DefaultFileBrowserRepository` 路由到本地 provider。
- `LocalFileSystemProvider` 实现（`withContext(Dispatchers.IO)`）：
  - 路径去重；`Path.of(raw).toAbsolutePath().normalize()`；
  - 复用/拆分 `readEntry`，拖入路径显式包含隐藏文件（用户显式拖入即意图）；
  - 不存在的路径自然丢弃；
  - 目录的 `childCount` 沿用现有统计（最多一层）。

### Step 6：ViewModel 动作分发

```kotlin
fun onExternalFilesDropped(paneId: PaneId, paths: List<String>, action: ExternalDropAction?) {
    if (paths.isEmpty()) return
    val destination = _state.value.pane(paneId).location ?: return
    scope.launch {
        val resolved = files.entriesForLocalPaths(paths)
        val entries = resolved.filterNot { shouldSkipForDestination(it, destination) }
        if (entries.size != resolved.size) {
            // 目录拖入自身内部：跳过后用 snackbar 提示
            snackbarRetryAction = null
            _state.update {
                it.copy(
                    snackbarMessage = getString(Res.string.text_drop_skip_self),
                    snackbarCanRetry = false,
                )
            }
        }
        if (entries.isEmpty()) return@launch
        val background = _state.value.taskPreferences.background
        when (action) {
            ExternalDropAction.Copy -> startCopyTask(entries, destination, background)
            ExternalDropAction.Move -> startMoveTask(entries, destination, background)
            // Link 没有对应操作；按“无受支持动作”处理
            ExternalDropAction.Link, null -> _state.update {
                it.copy(
                    copyRequest = CopyRequestState(
                        entry = entries.first(),
                        destination = destination,
                        background = background,
                        entries = entries,
                    )
                )
            }
        }
    }
}
```

- `startCopyTask` / `startMoveTask`：从现有 `onCopyRequestConfirm` / `onMoveRequestConfirm` 抽取；标题（单/批量）、副标题、`waitingTemplate`、`onProgress`/`onBytes`/`onWaiting`/`isCancelled` 完全复用。
- `onCopyRequestConfirm` / `onMoveRequestConfirm` 改为调用抽取后的函数，行为不变。
- `shouldSkipForDestination`：目录拖入自身子目录时跳过（纯函数，可单测）。
- VM 内不做线程切换；IO 由 provider 的 `Dispatchers.IO` 负责。

### Step 7：文案

新增（中/英各一份，不使用中文括号）：

| key | 中文 | 英文 |
| --- | --- | --- |
| `text_drop_copy` | 松手复制到当前目录 | Drop to copy to this folder |
| `text_drop_move` | 松手移动到当前目录 | Drop to move to this folder |
| `text_drop_unknown` | 松手后确认复制到当前目录 | Drop to confirm copying to this folder |
| `text_drop_unavailable` | 目标目录不可用 | Target folder is unavailable |
| `text_drop_skip_self` | 不能把文件夹复制或移动到自身内部 | Cannot copy or move a folder into itself |

### Step 8：单元测试（已完成）

1. `jvmTest/.../ui/component/ExternalFileDropTest.kt`
   - `javaFileListPaths`：`List<File>` → 绝对路径；忽略非 File 项；顺序保持；
   - `externalDropActionOf`：COPY/MOVE/LINK/NONE → 枚举/null；
   - `ExternalDropController`：paneAt 命中/未命中、进入/离开更新 hover、重复移动不重复聚焦、drop 路由到 bridge、drop 后清空 hover。

2. `jvmTest/.../data/file/LocalFileSystemProviderTest.kt` 追加
   - 文件、目录、隐藏文件（`showHiddenItems = false` 时仍返回）；
   - 不存在路径、重复路径；
   - 返回的 `FileEntry.location` 是规范化绝对路径，`source.kind == Local`。

3. `commonTest/.../HomeViewModelTest.kt` 追加（fake repository 实现 `entriesForLocalPaths`，并覆盖 `copyWithProgress` / `moveWithProgress` 记录入参；接口默认的 `moveWithProgress` 会抛异常，必须覆盖）
   - Copy → 直接复制任务，不设置 `copyRequest`；
   - Move → 直接移动任务，不设置 `moveRequest`/`copyRequest`；
   - null → 设置 `copyRequest`，不启动任务；确认后走复制；
   - Link → 设置 `copyRequest`；
   - 目标面板 `location == null`、空路径、空 entries → 无任务、无弹窗；
   - 目录自嵌套被过滤，其余正常；
   - 直接任务使用 `taskPreferences.background`；
   - 标题/批量标记/itemNames 正确（若 commonTest 下 `getString` 不可用，改到 jvmTest 或只断言结构与入参）。

4. `commonTest/.../domain/repository/CopyNamePlanTest.kt` 新增
   - 新鲜文件名从 `_副本` 开始；
   - `_副本` → `_副本1`、`_副本2` → `_副本3`；
   - 旧的叠加名 `_副本_副本` 收敛为 `_副本1`；
   - 目录、多扩展名、`_副本-final` 这类非后缀名不被误伤。

5. `jvmTest/.../data/file/LocalCopyEngineTest.kt` 追加
   - 重复复制源名为 `note_副本.txt` 的文件，目标为 `note_副本.txt` + `note_副本1.txt`，不会出现 `note_副本_副本.txt`。

6. AWT 事件本身不做单元测试，已在 Step 0 实机验证。

### Step 9：编译验证（已通过）

```bash
./gradlew :shared:compileKotlinJvm
./gradlew :shared:jvmTest --tests '*ExternalFileDropTest'
./gradlew :shared:jvmTest --tests '*LocalFileSystemProviderTest'
./gradlew :shared:jvmTest --tests '*HomeViewModelTest'
./gradlew :shared:jvmTest
./gradlew build
```

完成定义：

- 新增与现有测试全绿；
- 无新增编译错误；触碰文件不引入新的 warning（现有 warning 除外）；
- `git diff` 自查：无探针日志残留、无临时 println、无无关格式改动。

## 7. 风险与决策

| 编号 | 事项 | 决策 / 处理 |
| --- | --- | --- |
| D1 | 有明确动作时是否弹框 | **已确认**：不弹，按动作执行；无动作弹复制框 |
| D2 | 是否只认面板 | **已确认**：只认面板；指针所在面板成为焦点与目标；悬停显示蒙层 |
| D-Adapter | AWT 接管 vs Compose DnD | **已确认**：Step 0 通过，采用方案 B（AWT 接管） |
| D-Link | Link 动作 | 按“无受支持动作”处理：强制 COPY，弹复制框 |
| D-Background | 直接任务的后台默认值 | 使用 `taskPreferences.background` |
| R1 | Finder 删除源文件 | 强制 `ACTION_COPY`，移动由我们执行；Step 0 已复验源文件保留 |
| R2 | AWT 动作上报不可靠 | 只把 `getDropAction()` 当意图；无论意图如何都以 COPY 协商；Move 由我们完成 |
| R3 | 替换 Compose DropTarget 的耦合 | 限制在 jvmMain 的 `ExternalFileDropHost`；组件类名 `ComposeWindowPanel` 已记录；Compose 升级时回归 |
| R4 | 坐标映射（Retina/缩放） | 面板 bounds 转逻辑坐标；Step 0 已验证蒙层位置一致 |
| R5 | 窗口重建/延迟创建 | 安装器重试 + 重新安装；`DisposableEffect` 注销 bridge |
| R6 | 目录拖入自身子目录 | `shouldSkipForDestination` 过滤 + 提示 |
| R7 | MTP 目标 | 沿用 `copyWithProgress` / `moveWithProgress` 的跨 provider 流式路径 |
| R8 | 移动失败（源删除失败） | 沿用现有移动流程：复制成功、删除失败则任务失败，目标保留；不额外回滚 |
| R9 | 源只允许 MOVE、不允许 COPY | 强制 COPY 会得到 NONE 协商：`acceptDrop(COPY)` 后源可能认为 drop 失败，源不会被删除；若 Step 0 探针遇到此类源，按“拒绝 drop + 提示”处理 |

## 8. 参考

- Compose Multiplatform 1.13.0-rc01 `AwtDragAndDropManager`：`dragOver` 接受 `dtde.dropAction`，`drop` 接受 `dtde.dropAction`，无修改动作的入口。
- macOS AWT 无修饰键拖拽上报 `ACTION_MOVE` 的社区实测：https://stackoverflow.com/questions/38318279
- macOS Java DnD 动作上报问题：https://bugs.openjdk.org/browse/JDK-8054325
- Java DnD 中 MOVE 由源删除数据：`java.awt.dnd` 规范与 `DragSourceDropEvent` 文档。
- 本机探针结果：ComposeWindowPanel 替换成功；无修饰键 / ⌘ = Move，⌥ = Copy，⌘⌥ = Link；强制 COPY 后源文件保留。

## 9. 实机验证清单（用户负责）

Step 0 探针通过后，全量功能完成时验收：

- [ ] 普通拖入（无修饰键）：按 Step 0 实测动作执行（预期 Move）；蒙层文案与实际操作一致；Move 时目标出现文件、源文件被移除。
- [ ] Option 拖入：蒙层显示“松手复制到当前目录”；松手后源文件保留。
- [ ] Command 拖入：动作与蒙层文案一致；结果与 Step 0 观测一致。
- [ ] Command+Option 拖入：按 Link 处理，弹复制确认框；源文件保留。
- [ ] 拖到左/右面板：只有指针所在面板高亮并成为焦点。
- [ ] 拖到工具栏/分隔条/侧边栏：无蒙层、无任务。
- [ ] 拖拽中移出窗口或 Cmd+Tab：蒙层消失，不产生任务。
- [ ] 多文件 / 目录（含嵌套）：复制或移动完整。
- [ ] 目标重名：生成 `_副本`；移动时源被移除、目标为 `_副本` 名称。
- [ ] 重复拖入/复制同一目录：`_副本`、`_副本1`、`_副本2` 递增；把 `name_副本.txt` 再拖入不应出现 `name_副本_副本.txt`。
- [ ] 拖到 MTP 面板：上传/下载、等待文案、取消均正常。
- [ ] 大文件：进度、速度、ETA 正常；取消后目标残留清理。
- [ ] 目标面板加载中/错误态：不接收，无崩溃。
- [ ] 目录拖到自身子目录：该项被跳过并提示。
- [ ] 连续拖拽、窗口重建（切换全屏等）后仍可用。
- [ ] 文件简介/设置弹窗打开时，主窗口拖入仍可用。

## 10. 里程碑

M0–M3 已完成；M4 待用户实机验收。

| 阶段 | 内容 | 交付 | 验证方 |
| --- | --- | --- | --- |
| M0 | Step 0 探针 | 组件替换成功、动作日志、源文件保留、坐标对齐 | 用户实机 |
| M1 | Step 1/2/3/4 | controller + AWT 宿主 + 蒙层 + 接线 | 编译 |
| M2 | Step 5/6/7 | 路径解析 + 动作分发 + 弹框回退 + 文案 | 单测 + 编译 |
| M3 | Step 8/9 | 单测全绿、`./gradlew build` 通过 | 本 agent |
| M4 | 第 9 节清单 | 实机验收 | 用户 |

## 11. 进度记录

- [x] Step 0 探针（用户实机通过：组件替换成功、动作值符合预期、源文件保留、蒙层对齐）
- [x] Step 1 controller（含单测）
- [x] Step 2 AWT 宿主（正式：强制 COPY + 意图读取）
- [x] Step 3 蒙层 + bounds
- [x] Step 4 接线
- [x] Step 5 provider 路径解析
- [x] Step 6 ViewModel 动作分发（复制/移动/弹框）
- [x] Step 7 文案（含 skip_self）
- [x] Step 8 单测（CopyNamePlan 6、LocalCopyEngine 7、ExternalFileDrop 6、Provider 6、HomeViewModel 28）
- [x] Step 9 编译验证（`./gradlew build` 通过）
- [ ] 第 9 节实机验收（用户）
