# Dockhand Agent Guide

## 文件操作任务规范

复制任务已经是本项目的参考实现。新增移动、删除、重命名、压缩等耗时文件操作时，必须复用同一套交互与任务生命周期，不能另起一套简化流程。

### 1. 右键菜单动作

- 右键动作先进入确认弹窗，不直接开始耗时操作。
- 确认弹窗参考 `CopyRequestDialog` / `MoveRequestDialog`：
  - `OverlayDialog`
  - 标题、`是否将%1$s复制/移动到%2$s？` 摘要
  - `后台执行` 复选框
  - 确认按钮在左，使用 `ButtonDefaults.textButtonColorsPrimary()`
  - 取消按钮在右，默认样式
  - 两个按钮各 `Modifier.weight(1f)`
  - 按钮行 `Arrangement.spacedBy(8.dp)`
- 弹窗必须渲染在主 `Scaffold` 内，保证 Overlay 有宿主。
- 同目录移动不显示；跨设备移动显示，不应按设备类型隐藏。

### 2. 任务启动

所有耗时操作统一通过 `TaskManager.start`：

```kotlin
taskManager.start(
    title = getString(Res.string.task_xxx_title, entry.name),
    detail = getString(Res.string.task_xxx_destination, destination.displayPath()),
    background = request.background,
    onFinished = { refreshAllPanes() },
) {
    files.xxxWithProgress(...)
}
```

必须：

- 标题格式参考 `复制 %1$s` / `移动 %1$s`
- 不使用中文括号
- `background` 来自确认弹窗 checkbox
- `onFinished` 在成功、失败、取消时都刷新左右两个面板
- 后台任务计入 Badge；Badge 统计 `isBackground && (Running || Waiting)`
- 只有后台任务发系统通知；通知不可用时用 snackbar 兜底
- 所有终态任务自动从任务列表移除
- 不重做 TaskRow 布局；只复用现有 TaskRow

### 3. 进度与等待

操作必须向任务上报：

- 文件级进度：`onProgress`
- 字节级进度：`onBytes`
- MTP 设备等待：`onWaiting(deviceName)` / `onWaiting(null)`

要求：

- TaskRow 显示真实进度、速度和 ETA
- MTP 同设备操作进入 `TaskState.Waiting`
- 等待文案：`等待<设备名>上一个任务`
- 等待中的任务必须可以立即取消
- 不要用顶层文件计数冒充字节进度
- 空 `JsonNull` 数据必须安全解析，不能导致任务失败

### 4. 取消

取消必须覆盖完整链路：

- 取消 UI 任务协程
- MTP 上传/下载通过原生取消标记中断
- 等待设备锁的 JVM 排队任务直接取消，不进入阻塞的 FFM 调用
- 复制/移动已写入的目标需要清理
- 删除阶段检查 `isCancelled`，递归删除逐项检查
- 取消后释放 MTP 设备锁
- 取消 MTP 传输后执行 `MTP_CANCEL_COOLDOWN_MS`（当前 2 秒）
- 取消后不能阻塞后续 MTP 任务

### 5. 复制/移动语义

- 复制：复制到目标，不删除源
- 移动：先复制到目标，确认成功后再删除源
- 目标重名规则：
  - 文件：`name_副本.ext`、`name_副本1.ext`
  - 目录：`name_副本`、`name_副本1`
- 跨 provider / 跨设备移动允许，通过临时文件或直接流式传输完成
- 复制/移动开始前、删除源前都要检查取消
- 失败或取消时尽可能回滚目标残留
- 临时目录使用 `createTaskTempDirectory`，结束时必须清理

### 6. MTP 约束

- 同一 MTP 设备严格串行
- 不同 MTP 设备可以并行
- 同设备传输在 JVM 侧按设备排队
- 传输中刷新目录不能使用旧快照冒充刷新
- MTP 取消后让设备冷却，避免下一个任务立刻 `GeneralError`
- 传输进度通过 `dockhand_mtp_transfer_progress` 查询
- 等待状态通过 `dockhand_mtp_transfer_waiting` 查询

## 窗口与设置

- 设置内容封装在 `SettingsBottomSheet`，Drawer 只负责打开/关闭
- 设置统一使用 DataStore
- 界面缩放通过 `LocalDensity` 应用，简介窗口等独立窗口也要应用
- 简介窗口、设置弹窗需要滚动条
- Floating 窗口大小恢复开关放在“启动”卡片内

## 构建

- 调试阶段不要运行 release / DMG 构建
- 常规验证使用：
  - `./gradlew build`
  - `./gradlew :shared:jvmTest`
- release 使用 ProGuard；修改包名/入口/反射类后同步更新 `desktopApp/proguard-rules.pro`
- 改包路径时保留旧应用数据目录迁移

## 交互文案

- 不使用中文括号
- 任务标题示例：
  - `复制 文件名`
  - `移动 文件名`
- 任务副标题示例：
  - `到 目标路径`
- MTP 显示路径：`MTP 设备名称 + 逻辑路径`
