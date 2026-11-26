# 面板文件拖出（面板 → 其他程序 / 另一面板）

> 状态：实现完成，单测与编译通过；实机验收见第 7 节。
> 相关：[外部文件拖入](external-file-drop.md)。

## 1. 交互规则

- **长按触发**：按住条目约 500ms 进入拖出准备；随后移动超过 slop 才开始系统拖拽。
  长按后不移动直接松手不会触发投放，避免误把文件“移动”到原目录。
- **单击拖拽仍是框选/选择**：长按检测使用 `PointerEventPass.Final`，不消费事件；
  单击、双击、右键、普通框选都保持原行为。
- **拖的是当前选择**：
  - 按下的条目已在选中项中 → 拖动整个选择；
  - 按下的条目未选中 → 先把它设为唯一选中项，再只拖它。
- **目标**：Finder、桌面等外部程序，或另一个文件面板。
  在**源面板**、工具栏、分隔条、空白区域松手不触发动作；另一个面板仍是有效目标
  （按“到其他程序或另一面板”的需求保留）。
- **支持**：多选、文件、目录、隐藏文件。
- **不支持（源）**：MTP 条目。MTP 没有本地文件路径，不能直接交给其他程序；
  选中项混合本地/MTP 时只拖本地项。

## 2. 动作语义

源侧声明 `COPY | MOVE | LINK`，最终动作由目标程序和修饰键决定，
与 Finder 的拖拽行为一致；初始动作为 MOVE。

| 目标 | 行为 |
| --- | --- |
| 外部程序，目标以 Copy 完成 | 源保留 |
| 外部程序，目标以 Move 完成 | 目标接管数据后，后台任务删除源文件 |
| 外部程序，目标以 Link 完成 | 源保留，目标自行创建链接/替身 |
| 外部程序，取消 / 无动作 | 源不动 |
| 另一面板，Move 意图 | 走现有 `moveWithProgress`（先复制成功再删源） |
| 另一面板，Copy 意图 | 走现有 `copyWithProgress` |
| 另一面板，Link / 无明确动作 | 复用拖入的复制确认框 |

此前探针中 Finder 作为**源**时：普通拖拽 Move、Option Copy、Command Move、Command+Option Link。
我们作为源时，最终动作由目标程序决定，以目标光标/实际结果为准。

### 内部投放保护

面板内投放时，`ExternalFileDropHost` 会先标记“内部投放”。源侧的 `exportDone`
读到标记后**不删除源文件**：面板的移动/复制由 ViewModel 的任务完成，源文件由移动任务
自己删除。这样也规避了 macOS 上 Java → Java 拖拽把目标接受的 COPY 误报成 MOVE 的问题，
避免源文件被删两次。
另外，`exportDone` 还会检查拖拽结束时指针是否仍在窗口内；窗口内松手一律不删源。

## 3. 实现结构

| 层 | 文件 | 内容 |
| --- | --- | --- |
| common | `ui/component/ExternalFileDrag.kt` | 选择语义 `dragOutEntries`；`LongPressDragState`；`Modifier.longPressDragSource`；`expect startFileDragOut` |
| common | `ui/component/DragPreviewCache.kt` | 缩略图/列表图标渲染缓存（按 80dp 预览像素尺寸渲染；缩略图最多 128、图标最多 64）、按路径/资源取快照 |
| jvm | `ui/component/ExternalFileDragHost.kt` | AWT `TransferHandler`、`FileListTransferable`（javaFileListFlavor）、`localEntriesForDrag`、`shouldDeleteMovedSource`、合成 MouseEvent 启动拖拽 |
| jvm | `ui/component/ExternalFileDropHost.kt` | 内部投放标记 `markInternalDrop`；复用根组件查找 |
| ui | `ui/component/FileBrowserPane.kt` | 列表/网格条目长按检测；拖出准备标记暂停框选；拖后点击抑制；选择回调；AsyncImage.onSuccess 写入缩略图缓存 |
| ui | `ui/component/DualPaneBrowser.kt`、`HomeScreen.kt` | 拖出回调透传 |
| vm | `HomeViewModel.kt` | `onEntriesMovedOut`：外部 Move 后后台删除源（仅本地） |
| main | `desktopApp/.../main.kt` | 安装/卸载 `ExternalFileDragHost` |

### 为什么不用 Compose 的 dragAndDropSource

Compose Desktop 的 `Modifier.dragAndDropSource` 默认检测是“按住即拖”，不能配置成长按；
在条目上和框选手势冲突。因此拖出改在 AWT 层用 `TransferHandler` 启动：
Compose 侧只负责长按识别，真正拖拽由 AWT 完成，数据用 `javaFileListFlavor`。

### 拖拽数据

`FileListTransferable` 只提供 `DataFlavor.javaFileListFlavor`，返回 `List<File>`
（文件和目录都支持）。源声明 COPY/MOVE/LINK，初始动作 MOVE；目标可自行选择动作。

### 拖拽预览

`FileDragTransferHandler` 在拖拽开始时用 Java2D 生成 `BufferedImage`：

- 每个预览图 **80dp × 80dp**，向右下偏移 **8dp** 叠放，最多 5 个，第一个条目在最上层；
- 图片/视频优先用 AsyncImage 已加载的缩略图；其他文件用**列表项自己的图标**
  （Compose SVG 资源，按 ContentScale.Fit 居中，和列表里看到的一致）；
  两者都缺失时才回退系统文件图标（`FileSystemView.getSystemIcon`）；
- 图标下方绘制圆角 Badge，显示本次拖出的**总条目数**（超过 5 个时仍显示真实总数），
  字号/高度随 density 缩放；
- 通过 `getDragImage()` / `getDragImageOffset()` 交给 AWT，鼠标落在第一个图标中心；
  macOS 的 offset 按逻辑点换算，其他平台按图像像素；
- `DragPreviewCache` 在 `AsyncImage.onSuccess` / 行图标 `LaunchedEffect` 时
  按预览像素尺寸渲染缓存；拖拽开始时按路径/资源取快照，通过 `toPixelMap` 转成 AWT 位图；
- 图标/绘制失败时降级为占位方块，不影响拖拽本身。

## 4. 文件清单

- 新增 `shared/src/commonMain/.../ui/component/ExternalFileDrag.kt`
- 新增 `shared/src/commonMain/.../ui/component/DragPreviewCache.kt`
- 新增 `shared/src/jvmMain/.../ui/component/ExternalFileDragHost.kt`
- 修改 `FileBrowserPane.kt`（列表/网格条目、框选协作）
- 修改 `ExternalFileDropHost.kt`（内部投放标记、组件查找）
- 修改 `DualPaneBrowser.kt`、`HomeScreen.kt`、`HomeViewModel.kt`
- 修改 `desktopApp/.../main.kt`
- 新增/修改测试：`ExternalFileDragTest.kt`、`ExternalFileDragHostTest.kt`、`HomeViewModelTest.kt`

## 5. 单元测试

- `ExternalFileDragTest`（4）
  - 未选中项只拖自己；
  - 已选中项带走整个选择且保持顺序；
  - 选中列表为空时回退到按下的条目；
  - MTP 条目不能拖出。
- `ExternalFileDragHostTest`（3）
  - 仅外部 Move 需要删除源（Copy/NONE/内部投放不删）；
  - `localEntriesForDrag` 保留本地存在的项、去重、过滤 MTP/缺失项；
  - `FileListTransferable` 的 flavor 与数据。
- `HomeViewModelTest` 新增 `externalMoveOutDeletesOnlyLocalSources`：
  外部 Move 清理源时只删除本地项，MTP 项不动。

验证：`./gradlew build` 通过。

> 缩略图渲染依赖 Skia，headless 单测无法创建 `ImageBitmap`；缩略图部分由实机拖拽验收。

## 6. 实机验收（用户）

1. 长按文件 500ms 后拖到 Finder/桌面，松手：
   - 若目标以 Move 完成：目标出现文件，源面板该文件消失，任务列表出现“移动 ...”；
   - 若目标以 Copy 完成（常见于按住 Option）：源保留。
2. 多选 2-3 个文件/目录，长按其中一个**已选中**项：整个选择一起拖出。
3. 长按一个**未选中**项：它变为唯一选中项并被拖出。
4. 拖到另一个面板：默认 Move、Option Copy；蒙层文案与任务一致；刷新正常；目标是 MTP 时也能复制/移动。
5. 长按后**不移动**直接松手：不产生任务、不出现 `_副本` 重命名。
6. 普通单击拖拽仍然是框选；单击/双击/右键正常。
7. 拖出后取消删除任务：源保留，目标程序里的副本不受影响。
8. 拖拽中按 Esc / 拖出窗口后取消：源不动。
9. 行内重命名输入框内长按：不启动拖出。
10. 选中项含 MTP：只拖本地项；全为 MTP 时长按不启动拖出。
11. **源面板内松手不触发**；在另一个面板松手仍按 Move/Copy 执行。
12. **动作**：普通拖拽、Option、Command、Command+Option 分别观察目标程序
    的 Move/Copy/动作提示；外部程序应能识别 Copy/Move/Link。
13. **预览**：拖动 1 个、3 个、6 个以上条目时，鼠标旁应显示叠放图标，
    最多 5 个，下方 Badge 显示真实总数（如 6 个时显示 6）。

## 7. 已知限制

- 缩略图和列表图标只使用已经加载并进入缓存的结果；可见但尚未加载完成、
  或缓存已淘汰（缩略图超过 128 个/图标超过 64 个）的条目会回退到系统文件类型图标。
- 选中项包含未渲染过的离屏条目时，该条目的列表图标可能尚未缓存，会回退系统图标。
- 缩略图是拖拽开始时的一次快照，不随文件内容变化实时更新。
- MTP 作为源暂不支持拖出（需要先导出临时文件），作为目标正常。
- 外部 Move 删除源依赖目标程序遵守 MOVE 语义；个别 Java 目标可能误报动作。
- 同面板内移动投放仍按重名规则生成 `_副本`（等价重命名），这是投放任务自身的行为。
- 长按阈值为 Compose `viewConfiguration.longPressTimeoutMillis`（约 500ms）。

## 8. 进度

- [x] 长按识别（准备 → 移动才启动）
- [x] 选择语义（已选中拖整个选择，未选中先选中）
- [x] AWT 拖出宿主（TransferHandler + javaFileListFlavor）
- [x] 面板内部投放（复用 drop 宿主 + 动作意图）
- [x] 外部 Move 源清理（内部投放保护）
- [x] 框选/点击协作（长按准备暂停框选、拖后点击抑制）
- [x] 动作集合 COPY/MOVE/LINK（与 Finder 一致）
- [x] 源面板松手不触发（另一个面板保留）
- [x] 拖拽预览（最多 5 个叠放图标 + 数量 Badge）
- [x] 缩略图预览（AsyncImage 结果缓存 + 系统图标兜底）
- [x] 预览图 80dp × 80dp、偏移 8dp；非图片/视频使用列表项图标
- [x] 单测（4 + 3 + 1）与 `./gradlew build`
- [ ] 实机验收（用户）
