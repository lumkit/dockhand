package io.github.lumkit.dockhand.ui.screen.home

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import io.github.lumkit.dockhand.domain.model.DEFAULT_MTP_SHORTCUT_ORDER
import io.github.lumkit.dockhand.domain.model.FileEntry
import io.github.lumkit.dockhand.domain.model.FileInfo
import io.github.lumkit.dockhand.domain.model.FileLocation
import io.github.lumkit.dockhand.shortcut.DefaultShortcutBindings
import io.github.lumkit.dockhand.shortcut.ShortcutAction
import io.github.lumkit.dockhand.shortcut.ShortcutKey
import io.github.lumkit.dockhand.core.app.AppLanguage
import io.github.lumkit.dockhand.core.archive.ArchiveFormat
import io.github.lumkit.dockhand.core.archive.CompressionLevel
import io.github.lumkit.dockhand.core.cache.CacheBreakdown
import io.github.lumkit.dockhand.core.update.AppUpdate
import io.github.lumkit.dockhand.domain.model.FileLayoutMode
import io.github.lumkit.dockhand.domain.model.TaskPreferences
import io.github.lumkit.dockhand.domain.model.FilePlace
import io.github.lumkit.dockhand.domain.model.FilePlaceGroup
import io.github.lumkit.dockhand.domain.model.FileSortMode
import io.github.lumkit.dockhand.domain.model.sortStorageKey
import io.github.lumkit.dockhand.domain.model.breadcrumbs
import top.yukonga.miuix.kmp.theme.ColorSchemeMode

enum class PaneId {
    Left,
    Right,
    ;

    val other: PaneId
        get() = if (this == Left) Right else Left
}

/**
 * 单个文件面板的状态。
 *
 * 两个面板完全对称，都用这个模型；[id] 只用于区分左右，
 * 后续做“复制到另一侧”时可以直接用 [PaneId.other]。
 */
@Immutable
data class FilePaneState(
    val id: PaneId,
    val location: FileLocation? = null,
    /** 已预处理好 summary 的行数据；LazyColumn 只负责渲染。 */
    val entries: List<FileRowUi> = emptyList(),
    val selectedIds: Set<String> = emptySet(),
    /** Shift 区间选择的锚点项目。 */
    val selectionAnchorId: String? = null,
    /** 键盘选择的最新项目，用于把该项滚动到可见边缘。 */
    val selectionLeadId: String? = null,
    /** 当前打开的右键菜单项目。 */
    val contextMenuEntryId: String? = null,
    /** 快捷键请求进入行内重命名的项目。 */
    val inlineRenameEntryId: String? = null,
    /** 网格当前列数；用于左右/上下方向扩选。 */
    val gridColumnCount: Int = 1,
    val isLoading: Boolean = false,
    /** 分页后端是否还有下一页。 */
    val hasMore: Boolean = false,
    /** 正在追加下一页。 */
    val isLoadingMore: Boolean = false,
    val errorMessage: String? = null,
    val backStack: List<FileLocation> = emptyList(),
    val forwardStack: List<FileLocation> = emptyList(),
) {
    val canGoBack: Boolean get() = backStack.isNotEmpty()
    val canGoForward: Boolean get() = forwardStack.isNotEmpty()
    val canGoUp: Boolean get() = location?.breadcrumbs()?.size?.let { it > 1 } ?: false

    val selectedEntries: List<FileEntry>
        get() = entries.asSequence()
            .filter { it.id in selectedIds }
            .map { it.entry }
            .toList()

    val hasSelection: Boolean get() = selectedIds.isNotEmpty()

    val isEmpty: Boolean
        get() = !isLoading && errorMessage == null && entries.isEmpty()
}

@Immutable
data class FileInfoDialogState(
    val id: Long,
    val entry: FileEntry,
    val info: FileInfo? = null,
    val entries: List<FileEntry> = listOf(entry),
    val infos: List<FileInfo> = emptyList(),
    val isLoading: Boolean = true,
    /** 目录大小/项目数仍在后台统计。 */
    val sizeLoading: Boolean = false,
    val error: String? = null,
)

@Immutable
data class CopyRequestState(
    val entry: FileEntry,
    val destination: FileLocation,
    val background: Boolean = false,
    val entries: List<FileEntry> = listOf(entry),
    /** 从系统剪贴板粘贴时，确认框使用“粘贴”文案。 */
    val isPaste: Boolean = false,
)

@Immutable
data class MoveRequestState(
    val entry: FileEntry,
    val destination: FileLocation,
    val background: Boolean = false,
    val entries: List<FileEntry> = listOf(entry),
)

@Immutable
data class DeleteRequestState(
    val entry: FileEntry,
    val background: Boolean = false,
    val entries: List<FileEntry> = listOf(entry),
)

@Immutable
data class RenameRequestState(
    val entry: FileEntry,
    val newName: String,
    val background: Boolean = false,
    val entries: List<FileEntry> = listOf(entry),
)

@Immutable
data class OpenWithState(
    val entry: FileEntry,
    val applications: List<io.github.lumkit.dockhand.core.platform.ExternalApplication> = emptyList(),
    val loading: Boolean = true,
)

enum class ExtractDestinationMode {
    CurrentDirectory,
    Folder,
}

@Immutable
data class ExtractRequestState(
    val entry: FileEntry,
    val mode: ExtractDestinationMode = ExtractDestinationMode.CurrentDirectory,
    val folderName: String = "",
    val toOtherPane: Boolean = false,
    val deleteSource: Boolean = false,
    val background: Boolean = false,
)

@Immutable
data class ExtractPasswordState(
    val request: io.github.lumkit.dockhand.core.archive.ExtractRequest,
    val archiveName: String,
    val background: Boolean = false,
    val password: String = "",
)

@Immutable
data class CompressRequestState(
    val entry: FileEntry,
    val entries: List<FileEntry> = listOf(entry),
    val archiveName: String = "",
    val format: ArchiveFormat = ArchiveFormat.Zip,
    val level: CompressionLevel = CompressionLevel.Normal,
    val password: String = "",
    val splitSizeMb: String = "",
    val separateArchives: Boolean = false,
    val deleteSource: Boolean = false,
    val toOtherPane: Boolean = false,
    val background: Boolean = false,
)

@Immutable
data class TrashRequestState(
    val entry: FileEntry,
    val background: Boolean = false,
    val entries: List<FileEntry> = listOf(entry),
)

const val STARTUP_PATH_LAST = "__last__"
const val STARTUP_PATH_DESKTOP = "local:desktop"
val DEFAULT_KEY_COLOR = Color(0xFF6750A4)

@Immutable
data class HomeUiState(
    val places: List<FilePlace> = emptyList(),
    /** 左右面板启动路径：STARTUP_PATH_LAST 或 FilePlace.id。 */
    val startupLeftPath: String = STARTUP_PATH_DESKTOP,
    val startupRightPath: String = STARTUP_PATH_DESKTOP,
    /** Floating 模式关闭时是否恢复上次窗口大小。 */
    val restoreWindowSize: Boolean = true,
    val colorSchemeMode: ColorSchemeMode = ColorSchemeMode.System,
    /** 莫奈主题的手动主色调。 */
    val keyColor: Color = DEFAULT_KEY_COLOR,
    val showHiddenItems: Boolean = false,
    val fileLayoutMode: FileLayoutMode = FileLayoutMode.List,
    /** 新目录默认排序；单个目录未自定义时使用它。 */
    val defaultFileSortMode: FileSortMode = FileSortMode.NameAsc,
    /** 目录级别的排序覆盖；key 由 FileLocation.sortStorageKey() 生成。 */
    val directorySortModes: Map<String, FileSortMode> = emptyMap(),
    val cacheBreakdown: CacheBreakdown = CacheBreakdown(),
    val taskPreferences: TaskPreferences = TaskPreferences(),
    val language: AppLanguage = AppLanguage.System,
    val shortcuts: Map<ShortcutAction, List<ShortcutKey>> =
        DefaultShortcutBindings.mapValues { listOf(it.value) },
    /** 当前显示进度弹窗的前台任务。 */
    val activeTaskId: Long? = null,
    /** 界面缩放百分比，范围 80..110。 */
    val interfaceScale: Int = 100,
    val placesError: String? = null,
    /** 每个侧边栏分组内部的 place id 顺序；由设置持久化。 */
    val drawerPlaceOrder: Map<FilePlaceGroup, List<String>> = emptyMap(),
    /** 已展开的 MTP 存储 source id；默认折叠。 */
    val mtpExpandedSources: Set<String> = emptySet(),
    /** MTP 快捷入口顺序；由设置持久化。 */
    val mtpShortcutOrder: List<String> = DEFAULT_MTP_SHORTCUT_ORDER,
    /** 新建/传输等一次性操作的错误，由顶部工具栏展示。 */
    val operationError: String? = null,
    /** 已打开的文件简介窗口；不随目录切换关闭，可同时存在多个。 */
    val fileInfoDialogs: List<FileInfoDialogState> = emptyList(),
    /** 系统通知不可用时的兜底提示。 */
    val snackbarMessage: String? = null,
    val snackbarCanRetry: Boolean = false,
    /** 检测到的新版本；null 表示没有更新或检查失败。 */
    val latestUpdate: AppUpdate? = null,
    /** 用户已忽略的版本；只影响 Badge，不隐藏版本行的提示。 */
    val ignoredUpdateVersion: String? = null,
    /** 已经自动滚动提示过的版本；一个版本只滚动一次。 */
    val updateScrollVersion: String? = null,
    val copyRequest: CopyRequestState? = null,
    val moveRequest: MoveRequestState? = null,
    val deleteRequest: DeleteRequestState? = null,
    val renameRequest: RenameRequestState? = null,
    val compressRequest: CompressRequestState? = null,
    val extractRequest: ExtractRequestState? = null,
    val extractPasswordRequest: ExtractPasswordState? = null,
    val openWith: OpenWithState? = null,
    val trashRequest: TrashRequestState? = null,
    val leftPane: FilePaneState = FilePaneState(PaneId.Left),
    val rightPane: FilePaneState = FilePaneState(PaneId.Right),
    val focusedPane: PaneId = PaneId.Left,
) {
    val hasUpdate: Boolean get() = latestUpdate != null

    /** 设置按钮 Badge：有新版本且未被忽略。 */
    val hasUpdateBadge: Boolean
        get() = latestUpdate != null && latestUpdate.version != ignoredUpdateVersion

    fun pane(id: PaneId): FilePaneState = when (id) {
        PaneId.Left -> leftPane
        PaneId.Right -> rightPane
    }

    val focusedPaneState: FilePaneState
        get() = pane(focusedPane)

    val focusedLocation: FileLocation?
        get() = focusedPaneState.location

    fun sortModeFor(location: FileLocation?): FileSortMode =
        location?.let { directorySortModes[it.sortStorageKey()] } ?: defaultFileSortMode

    /** 焦点列表当前生效的排序方式。 */
    val focusedSortMode: FileSortMode
        get() = sortModeFor(focusedLocation)

    /** 两个面板都还没有首屏路径。 */
    val isBootstrapping: Boolean
        get() = places.isEmpty() && leftPane.location == null && rightPane.location == null
}

internal fun HomeUiState.withPane(id: PaneId, pane: FilePaneState): HomeUiState = when (id) {
    PaneId.Left -> copy(leftPane = pane)
    PaneId.Right -> copy(rightPane = pane)
}

internal fun HomeUiState.updatePane(
    id: PaneId,
    transform: (FilePaneState) -> FilePaneState,
): HomeUiState = withPane(id, transform(pane(id)))