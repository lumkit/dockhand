package io.github.dockhand.ui.screen.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.dockhand.data.datastore.SettingsRepository
import io.github.dockhand.domain.model.FileBreadcrumb
import io.github.dockhand.domain.model.FileBrowserException
import io.github.dockhand.domain.model.FileEntry
import io.github.dockhand.domain.model.FileLocation
import io.github.dockhand.domain.model.FilePlace
import io.github.dockhand.domain.model.FilePlaceKind
import io.github.dockhand.domain.model.FileSourceKind
import io.github.dockhand.domain.model.displayPath
import io.github.dockhand.domain.model.orderedByIds
import io.github.dockhand.domain.model.parentBreadcrumb
import io.github.dockhand.domain.repository.FileBrowserRepository
import io.github.dockhand.task.NoopTaskNotifier
import io.github.dockhand.task.TaskManager
import io.github.dockhand.task.TaskNotifier
import io.github.dockhand.ui.component.FileContextAction
import dockhand.shared.generated.resources.Res
import dockhand.shared.generated.resources.error_name_empty
import dockhand.shared.generated.resources.error_name_invalid
import dockhand.shared.generated.resources.task_copy_destination
import dockhand.shared.generated.resources.task_copy_title
import dockhand.shared.generated.resources.task_waiting_message
import dockhand.shared.generated.resources.text_item_count
import dockhand.shared.generated.resources.text_read_failed
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.getString

/**
 * 主页 ViewModel。
 *
 * 职责边界：
 *  - 收集侧边栏入口、首个可用路径；
 *  - 维护左右面板的路径、历史、加载/错误、选中项；
 *  - 维护“焦点面板”，HomeTopBar 永远展示焦点面板的路径；
 *  - 把单击条目翻译成“聚焦 + 导航”。
 *
 * 生命周期由 ViewModel 负责；生产环境使用 [viewModelScope]，测试可以注入独立 scope。
 */
class HomeViewModel(
    private val files: FileBrowserRepository,
    /** 侧边栏顺序等 UI 设置；测试可省略。 */
    private val settings: SettingsRepository? = null,
    taskNotifier: TaskNotifier = NoopTaskNotifier,
    /** 列表排序 + summary 格式化的线程；测试里可注入 Unconfined。 */
    private val computeDispatcher: CoroutineDispatcher = Dispatchers.Default,
    /** 测试用；默认绑定 [viewModelScope]。 */
    externalScope: CoroutineScope? = null,
) : ViewModel() {

    private val scope: CoroutineScope = externalScope ?: viewModelScope
    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private val taskManager = TaskManager(
        scope = scope,
        notifier = taskNotifier,
        onFallbackMessage = { message ->
            _state.update { it.copy(snackbarMessage = message) }
        },
    )
    val tasks = taskManager.tasks

    fun onCancelTask(id: Long) = taskManager.cancel(id)
    fun onDismissTask(id: Long) = taskManager.dismiss(id)
    fun onSnackbarShown() {
        _state.update { it.copy(snackbarMessage = null) }
    }

    private val loadJobs = mutableMapOf<PaneId, Job>()
    private val moreJobs = mutableMapOf<PaneId, Job>()
    private val watchJobs = mutableMapOf<PaneId, Job>()
    private val fileInfoJobs = mutableMapOf<Long, Job>()
    private var nextFileInfoId = 0L
    private val placesJob: Job
    private val drawerPlaceOrderJob: Job? = settings?.let { repository ->
        scope.launch {
            repository.drawerPlaceOrder
                .catch { }
                .collect { order ->
                    _state.update { it.copy(drawerPlaceOrder = order) }
                }
        }
    }
    private val mtpExpandedJob: Job? = settings?.let { repository ->
        scope.launch {
            repository.mtpExpandedSources
                .catch { }
                .collect { sources ->
                    _state.update { it.copy(mtpExpandedSources = sources) }
                }
        }
    }
    private val startupLeftPathJob: Job? = settings?.let { repository ->
        scope.launch {
            repository.startupPath(PaneId.Left.name)
                .catch { }
                .collect { path -> _state.update { it.copy(startupLeftPath = path) } }
        }
    }
    private val restoreWindowSizeJob: Job? = settings?.let { repository ->
        scope.launch {
            repository.restoreWindowSize
                .catch { }
                .collect { enabled ->
                    _state.update { it.copy(restoreWindowSize = enabled) }
                }
        }
    }
    private val startupRightPathJob: Job? = settings?.let { repository ->
        scope.launch {
            repository.startupPath(PaneId.Right.name)
                .catch { }
                .collect { path -> _state.update { it.copy(startupRightPath = path) } }
        }
    }
    private val mtpShortcutOrderJob: Job? = settings?.let { repository ->
        scope.launch {
            repository.mtpShortcutOrder
                .catch { }
                .collect { order ->
                    _state.update { it.copy(mtpShortcutOrder = order) }
                }
        }
    }
    private var bootstrapped = false

    init {
        placesJob = scope.launch {
            files.observePlaces()
                .catch { error ->
                    _state.update {
                        it.copy(placesError = error.message ?: "无法读取位置列表")
                    }
                }
                .collect { places ->
                    _state.update { it.copy(places = places, placesError = null) }
                    if (!bootstrapped && places.isNotEmpty()) {
                        bootstrapped = true
                        val pendingMtp = pendingMtpStartupSources()
                        if (
                            pendingMtp.isNotEmpty() &&
                            !places.containsMtpSources(pendingMtp)
                        ) {
                            // MTP 枚举比本地路径慢，等设备出现后再恢复上次的 MTP 路径；
                            // 超时则按设备不存在处理，回退到桌面。
                            scope.launch {
                                withTimeoutOrNull(2_000) {
                                    while (!_state.value.places.containsMtpSources(pendingMtp)) {
                                        delay(100)
                                    }
                                }
                                bootstrap(_state.value.places)
                            }
                        } else {
                            bootstrap(places)
                        }
                    }
                }
        }
    }

    private suspend fun bootstrap(places: List<FilePlace>) {
        val home = places.firstOrNull { it.kind == FilePlaceKind.Home }?.location
            ?: places.firstOrNull { it.location.source.kind == FileSourceKind.Local }?.location
            ?: places.first().location
        val desktop = places.firstOrNull { it.kind == FilePlaceKind.Desktop }?.location
        // 两个窗口默认都从桌面开始；没有桌面入口时再回退。
        val leftFallback = desktop ?: home
        val rightFallback = desktop ?: home

        navigate(
            PaneId.Left,
            resolveStartupLocation(PaneId.Left, places, leftFallback),
            recordHistory = false,
        )
        navigate(
            PaneId.Right,
            resolveStartupLocation(PaneId.Right, places, rightFallback),
            recordHistory = false,
        )
    }

    private suspend fun pendingMtpStartupSources(): Set<String> {
        val repository = settings ?: return emptySet()
        return PaneId.entries.mapNotNull { paneId ->
            if (repository.startupPath(paneId.name).first() != STARTUP_PATH_LAST) return@mapNotNull null
            repository.lastLocation(paneId.name).first()
                ?.sourceId
                ?.takeIf { it.startsWith("mtp:") }
        }.toSet()
    }

    private fun List<FilePlace>.containsMtpSources(sourceIds: Set<String>): Boolean {
        val available = map { it.location.source.id }.toSet()
        return available.containsAll(sourceIds)
    }

    private suspend fun resolveStartupLocation(
        paneId: PaneId,
        places: List<FilePlace>,
        fallback: FileLocation,
    ): FileLocation {
        val configured = settings?.startupPath(paneId.name)?.first()
        if (configured != null && configured != STARTUP_PATH_LAST) {
            places.firstOrNull { it.id == configured }?.let { return it.location }
        }
        val last = settings?.lastLocation(paneId.name)?.first()
        if (last != null) {
            val source = places
                .map { it.location.source }
                .firstOrNull { it.id == last.sourceId }
            if (source != null) return FileLocation(source, last.path)
        }
        return fallback
    }

    fun onRestoreWindowSizeChange(enabled: Boolean) {
        _state.update { it.copy(restoreWindowSize = enabled) }
        settings?.let { repository ->
            scope.launch { repository.setRestoreWindowSize(enabled) }
        }
    }

    fun onStartupPathChange(paneId: PaneId, path: String) {
        _state.update {
            if (paneId == PaneId.Left) {
                it.copy(startupLeftPath = path)
            } else {
                it.copy(startupRightPath = path)
            }
        }
        settings?.let { repository ->
            scope.launch { repository.setStartupPath(paneId.name, path) }
        }
    }

    // ---- 面板焦点 ----

    fun onPaneFocus(paneId: PaneId) {
        _state.update {
            if (it.focusedPane == paneId) it else it.copy(focusedPane = paneId)
        }
    }

    /**
     * 列表项单击：只把面板设为焦点并选中该条目，不执行打开/进入。
     */
    fun onEntryClick(paneId: PaneId, entry: FileEntry) {
        onPaneFocus(paneId)
        selectEntry(paneId, entry)
    }

    /**
     * 列表项双击：目录进入，文件预留给后续的打开动作。
     */
    fun onEntryDoubleClick(paneId: PaneId, entry: FileEntry) {
        onPaneFocus(paneId)
        selectEntry(paneId, entry)
        if (entry.isDirectory) {
            navigate(paneId, entry.location)
        }
        // TODO: 文件双击打开由后续的打开方式/预览窗口接入。
    }

    /**
     * 列表项右键：先聚焦并选中，具体的文件夹/文件操作窗口后续接入。
     */
    fun onEntrySecondaryClick(paneId: PaneId, entry: FileEntry) {
        onPaneFocus(paneId)
        selectEntry(paneId, entry)
    }

    /**
     * 右键菜单动作入口；具体文件操作后续接入。
     */
    fun onEntryContextAction(
        paneId: PaneId,
        entry: FileEntry,
        action: FileContextAction,
    ) {
        onPaneFocus(paneId)
        selectEntry(paneId, entry)
        when (action) {
            FileContextAction.ViewInfo -> openFileInfo(entry)
            FileContextAction.Copy -> {
                val destination = _state.value.pane(paneId.other).location
                if (destination == null) {
                    setOperationError("另一侧没有打开目录")
                } else {
                    _state.update {
                        it.copy(
                            copyRequest = CopyRequestState(
                                entry = entry,
                                destination = destination,
                            )
                        )
                    }
                }
            }
            else -> Unit
        }
    }

    fun onCopyRequestBackgroundChange(background: Boolean) {
        _state.update { state ->
            state.copy(copyRequest = state.copyRequest?.copy(background = background))
        }
    }

    fun onCopyRequestDismiss() {
        _state.update { it.copy(copyRequest = null) }
    }

    fun onCopyRequestConfirm() {
        val request = _state.value.copyRequest ?: return
        _state.update { it.copy(copyRequest = null) }
        scope.launch {
            val title = getString(Res.string.task_copy_title, request.entry.name)
            val destinationLabel = request.destination.displayPath()
            val detail = getString(Res.string.task_copy_destination, destinationLabel)
            val waitingTemplate = getString(Res.string.task_waiting_message, "%s")
            taskManager.start(
                title = title,
                detail = detail,
                background = request.background,
                onFinished = { refreshAllPanes() },
            ) {
                files.copyWithProgress(
                    entries = listOf(request.entry),
                    destinationDirectory = request.destination,
                    overwrite = false,
                    onProgress = { completed, total -> report(completed, total) },
                    onBytes = { completed, total -> report(completed, total) },
                    onWaiting = { deviceName ->
                        if (deviceName == null) {
                            running()
                        } else {
                            waiting(waitingTemplate.replace("%s", deviceName))
                        }
                    },
                    isCancelled = { isCancelled },
                )
            }
        }
    }

    private fun refreshAllPanes() {
        PaneId.entries.forEach { paneId ->
            _state.value.pane(paneId).location?.let { location ->
                // 任务结束/取消后两侧都刷新；MTP 没有 watcher，必须显式重新加载。
                load(paneId, location)
            }
        }
    }

    fun onDismissFileInfo(id: Long) {
        fileInfoJobs.remove(id)?.cancel()
        _state.update { state ->
            state.copy(fileInfoDialogs = state.fileInfoDialogs.filterNot { it.id == id })
        }
    }

    private fun openFileInfo(entry: FileEntry) {
        val id = nextFileInfoId++
        _state.update { state ->
            state.copy(
                fileInfoDialogs = state.fileInfoDialogs + FileInfoDialogState(
                    id = id,
                    entry = entry,
                    isLoading = true,
                )
            )
        }
        fileInfoJobs[id] = scope.launch {
            try {
                val info = files.info(entry)
                _state.update { state ->
                    state.copy(
                        fileInfoDialogs = state.fileInfoDialogs.map { dialog ->
                            if (dialog.id == id) {
                                dialog.copy(
                                    info = info,
                                    isLoading = false,
                                    sizeLoading = info.isDirectory,
                                    error = null,
                                )
                            } else {
                                dialog
                            }
                        }
                    )
                }

                if (info.isDirectory) {
                    val usage = try {
                        files.usage(entry)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        null
                    }
                    _state.update { state ->
                        state.copy(
                            fileInfoDialogs = state.fileInfoDialogs.map { dialog ->
                                if (dialog.id == id) {
                                    dialog.copy(
                                        info = usage?.let {
                                            dialog.info?.copy(
                                                sizeBytes = it.sizeBytes,
                                                itemCount = it.itemCount,
                                            )
                                        } ?: dialog.info,
                                        sizeLoading = false,
                                    )
                                } else {
                                    dialog
                                }
                            }
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val message = e.userMessage()
                _state.update { state ->
                    state.copy(
                        fileInfoDialogs = state.fileInfoDialogs.map { dialog ->
                            if (dialog.id == id) {
                                dialog.copy(
                                    isLoading = false,
                                    error = message,
                                )
                            } else {
                                dialog
                            }
                        }
                    )
                }
            } finally {
                fileInfoJobs.remove(id)
            }
        }
    }

    private fun selectEntry(paneId: PaneId, entry: FileEntry) {
        _state.update { state ->
            state.updatePane(paneId) { it.copy(selectedIds = setOf(entry.id)) }
        }
    }

    // ---- 导航 ----

    fun onPlaceClick(place: FilePlace) {
        val paneId = _state.value.focusedPane
        navigate(paneId, place.location)
    }

    /**
     * 拖动侧边栏条目：只调整同一分组内部的顺序，并异步持久化。
     * @return 是否真的发生了移动；到达分组边界时返回 false。
     */
    fun onMovePlace(place: FilePlace, direction: Int): Boolean {
        val state = _state.value
        val groupPlaces = state.places
            .filter { it.group == place.group }
            .orderedByIds(state.drawerPlaceOrder[place.group].orEmpty())
        val from = groupPlaces.indexOfFirst { it.id == place.id }
        val to = from + direction
        if (from < 0 || to !in groupPlaces.indices) return false

        val orderedIds = groupPlaces.map { it.id }.toMutableList()
        orderedIds.removeAt(from)
        orderedIds.add(to, place.id)
        val updated = state.drawerPlaceOrder + (place.group to orderedIds)
        _state.update { it.copy(drawerPlaceOrder = updated) }
        scope.launch {
            settings?.setDrawerPlaceOrder(place.group, orderedIds)
        }
        return true
    }

    /**
     * 点击 MTP 存储行：
     * - 已选中：只切换展开/折叠；
     * - 未选中：焦点面板回到存储根目录，不改变展开状态。
     */
    fun onMtpStorageClick(place: FilePlace, selected: Boolean) {
        if (selected) {
            onToggleMtpExpanded(place.id)
        } else {
            navigate(_state.value.focusedPane, place.location)
        }
    }

    /** 展开/折叠 MTP 存储的快捷入口。 */
    fun onToggleMtpExpanded(sourceId: String) {
        val state = _state.value
        val expanded = if (sourceId in state.mtpExpandedSources) {
            state.mtpExpandedSources - sourceId
        } else {
            state.mtpExpandedSources + sourceId
        }
        _state.update { it.copy(mtpExpandedSources = expanded) }
        scope.launch {
            settings?.setMtpExpandedSources(expanded)
        }
    }

    /** 拖动 MTP 快捷入口：只调整快捷入口顺序，并异步持久化。 */
    fun onMoveMtpShortcut(shortcutId: String, direction: Int): Boolean {
        val current = _state.value.mtpShortcutOrder.toMutableList()
        val from = current.indexOf(shortcutId)
        val to = from + direction
        if (from < 0 || to !in current.indices) return false
        current.removeAt(from)
        current.add(to, shortcutId)
        _state.update { it.copy(mtpShortcutOrder = current) }
        scope.launch {
            settings?.setMtpShortcutOrder(current)
        }
        return true
    }

    fun onBreadcrumbClick(breadcrumb: FileBreadcrumb) {
        val state = _state.value
        val paneId = state.focusedPane
        val location = state.pane(paneId).location ?: return
        if (location.path == breadcrumb.path) return
        navigate(paneId, location.copy(path = breadcrumb.path))
    }

    fun onGoUp(paneId: PaneId) {
        val location = _state.value.pane(paneId).location ?: return
        val parent = location.parentBreadcrumb() ?: return
        navigate(paneId, location.copy(path = parent.path))
    }

    fun onBack(paneId: PaneId) {
        val pane = _state.value.pane(paneId)
        val current = pane.location ?: return
        val target = pane.backStack.lastOrNull() ?: return

        _state.update { state ->
            val latest = state.pane(paneId)
            if (latest.location != current) state
            else state.withPane(
                paneId,
                latest.copy(
                    location = target,
                    entries = emptyList(),
                    selectedIds = emptySet(),
                    isLoading = true,
                    hasMore = false,
                    isLoadingMore = false,
                    errorMessage = null,
                    backStack = latest.backStack.dropLast(1),
                    forwardStack = listOf(current) + latest.forwardStack,
                )
            )
        }
        watch(paneId, target)
        load(paneId, target)
    }

    fun onForward(paneId: PaneId) {
        val pane = _state.value.pane(paneId)
        val current = pane.location ?: return
        val target = pane.forwardStack.firstOrNull() ?: return

        _state.update { state ->
            val latest = state.pane(paneId)
            if (latest.location != current) state
            else state.withPane(
                paneId,
                latest.copy(
                    location = target,
                    entries = emptyList(),
                    selectedIds = emptySet(),
                    isLoading = true,
                    hasMore = false,
                    isLoadingMore = false,
                    errorMessage = null,
                    backStack = listOf(current) + latest.backStack,
                    forwardStack = latest.forwardStack.drop(1),
                )
            )
        }
        watch(paneId, target)
        load(paneId, target)
    }

    fun onRefresh(paneId: PaneId) {
        val pane = _state.value.pane(paneId)
        val location = pane.location ?: return
        _state.update { state ->
            state.updatePane(paneId) {
                it.copy(isLoading = true, isLoadingMore = false, errorMessage = null)
            }
        }
        watch(paneId, location)
        load(paneId, location)
    }

    /** 滚动到底时追加下一页；目前只对 MTP 启用。 */
    fun onLoadMore(paneId: PaneId) {
        val pane = _state.value.pane(paneId)
        val location = pane.location ?: return
        if (
            location.source.kind != FileSourceKind.Mtp ||
            !pane.hasMore ||
            pane.isLoadingMore ||
            pane.isLoading
        ) {
            return
        }

        val offset = pane.entries.size
        _state.update { state ->
            state.updatePane(paneId) { it.copy(isLoadingMore = true) }
        }

        moreJobs[paneId]?.cancel()
        moreJobs[paneId] = scope.launch {
            try {
                val page = files.listPage(location, offset, PAGE_SIZE)
                val sorted = page.entries.sortedWith(ENTRY_ORDER)
                val itemCountTexts = sorted
                    .mapNotNull { it.childCount }
                    .distinct()
                    .associateWith { getString(Res.string.text_item_count, it) }
                val rows = withContext(computeDispatcher) {
                    sorted.toRowUi(
                        timeZone = TimeZone.currentSystemDefault(),
                        itemCountTexts = itemCountTexts,
                    )
                }

                _state.update { state ->
                    val current = state.pane(paneId)
                    if (current.location != location || current.entries.size != offset) state
                    else state.updatePane(paneId) {
                        it.copy(
                            entries = current.entries + rows,
                            hasMore = page.hasMore,
                            isLoadingMore = false,
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { state ->
                    if (state.pane(paneId).location == location) {
                        state.updatePane(paneId) { it.copy(isLoadingMore = false) }
                    } else {
                        state
                    }
                }
                setOperationError(e.userMessage())
            }
        }
    }

    // ---- 文件操作 ----

    /**
     * 在焦点面板当前目录新建文件夹或空文件。
     * [createFile] 来自新建对话框里的 CheckboxPreference：勾选创建文件，未勾选创建文件夹。
     */
    fun onCreate(paneId: PaneId, name: String, createFile: Boolean) {
        val location = _state.value.pane(paneId).location ?: return
        val trimmed = name.trim()
        if (trimmed.isEmpty() || '/' in trimmed || trimmed == "." || trimmed == "..") {
            scope.launch {
                setOperationError(
                    if (trimmed.isEmpty()) {
                        getString(Res.string.error_name_empty)
                    } else {
                        getString(Res.string.error_name_invalid, name)
                    }
                )
            }
            return
        }

        _state.update { it.copy(operationError = null) }
        scope.launch {
            try {
                if (createFile) {
                    files.createFile(location, trimmed)
                } else {
                    files.createDirectory(location, trimmed)
                }
                load(paneId, location)
                // “同步”后两侧可能是同一目录，另一侧也要刷新。
                if (_state.value.pane(paneId.other).location == location) {
                    load(paneId.other, location)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                setOperationError(e.userMessage())
            }
        }
    }

    /**
     * “同步”把非焦点面板跳转到焦点面板的当前目录。
     * 焦点本身不变，跳转仍然写入目标面板的 undo/redo 历史。
     */
    fun onSyncOtherPane(paneId: PaneId) {
        val state = _state.value
        val location = state.pane(paneId).location ?: return
        val targetPane = paneId.other
        if (state.pane(targetPane).location == location) return
        navigate(targetPane, location)
    }

    fun onDismissOperationError() {
        _state.update { it.copy(operationError = null) }
    }

    private fun setOperationError(message: String) {
        _state.update { it.copy(operationError = message) }
    }

    private fun navigate(
        paneId: PaneId,
        target: FileLocation,
        recordHistory: Boolean = true,
    ) {
        val current = _state.value.pane(paneId).location
        if (recordHistory && current == target) return

        _state.update { state ->
            val pane = state.pane(paneId)
            val backStack = if (recordHistory && current != null) {
                listOf(current) + pane.backStack
            } else {
                pane.backStack
            }
            state.withPane(
                paneId,
                pane.copy(
                    location = target,
                    entries = emptyList(),
                    selectedIds = emptySet(),
                    isLoading = true,
                    hasMore = false,
                    isLoadingMore = false,
                    errorMessage = null,
                    backStack = backStack,
                    forwardStack = if (recordHistory) emptyList() else pane.forwardStack,
                )
            ).copy(operationError = null)
        }
        watch(paneId, target)
        load(paneId, target)
        settings?.let { repository ->
            scope.launch {
                repository.setLastLocation(paneId.name, target.source.id, target.path)
            }
        }
    }

    /**
     * 监听面板当前目录，外部（Finder、其他 App、本 App 的复制）发生变化时自动刷新。
     * 每个面板一个 watcher；路径切换时旧 watcher 会被取消。
     */
    private fun watch(paneId: PaneId, location: FileLocation) {
        watchJobs[paneId]?.cancel()
        // MTP 大目录分页时，4 秒一次的全量轮询会重新占用 MTP 会话；
        // MTP 改为手动刷新，避免分页读取被轮询打断。
        if (location.source.kind == FileSourceKind.Mtp) {
            return
        }
        watchJobs[paneId] = scope.launch {
            try {
                files.observeChanges(location)
                    .conflate()
                    .collect {
                        if (_state.value.pane(paneId).location == location) {
                            load(paneId, location)
                        }
                    }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // 某些目录可能无法 watch；退化为手动刷新/其它操作触发的刷新。
            }
        }
    }

    private fun load(paneId: PaneId, location: FileLocation) {
        loadJobs[paneId]?.cancel()
        moreJobs[paneId]?.cancel()
        loadJobs[paneId] = scope.launch {
            try {
                // MTP 目录先加载第一页；本地目录保持一次性列表。
                val page = if (location.source.kind == FileSourceKind.Mtp) {
                    files.listPage(location, 0, PAGE_SIZE)
                } else {
                    null
                }
                val entries = page?.entries ?: files.list(location)

                // 列表加载阶段一次性完成排序 + summary 格式化。
                // LazyColumn 滚动/重组时只读 FileRowUi.summary，不再做任何时间或大小计算。
                val sorted = entries.sortedWith(ENTRY_ORDER)
                val itemCountTexts = sorted
                    .mapNotNull { it.childCount }
                    .distinct()
                    .associateWith { getString(Res.string.text_item_count, it) }
                val rows = withContext(computeDispatcher) {
                    sorted.toRowUi(
                        timeZone = TimeZone.currentSystemDefault(),
                        itemCountTexts = itemCountTexts,
                    )
                }

                _state.update { state ->
                    if (state.pane(paneId).location != location) state
                    else state.updatePane(paneId) {
                        it.copy(
                            entries = rows,
                            isLoading = false,
                            hasMore = page?.hasMore ?: false,
                            isLoadingMore = false,
                            errorMessage = null,
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val message = e.userMessage()
                _state.update { state ->
                    if (state.pane(paneId).location != location) state
                    else state.updatePane(paneId) {
                        it.copy(
                            entries = emptyList(),
                            isLoading = false,
                            hasMore = false,
                            isLoadingMore = false,
                            errorMessage = message,
                        )
                    }
                }
            }
        }
    }

    override fun onCleared() {
        placesJob.cancel()
        drawerPlaceOrderJob?.cancel()
        mtpExpandedJob?.cancel()
        mtpShortcutOrderJob?.cancel()
        loadJobs.values.forEach { it.cancel() }
        loadJobs.clear()
        watchJobs.values.forEach { it.cancel() }
        watchJobs.clear()
        moreJobs.values.forEach { it.cancel() }
        moreJobs.clear()
        fileInfoJobs.values.forEach { it.cancel() }
        fileInfoJobs.clear()
        super.onCleared()
    }

    private suspend fun Throwable.userMessage(): String =
        (this as? FileBrowserException)?.message
            ?: message
            ?: getString(Res.string.text_read_failed)

    private companion object {
        const val PAGE_SIZE = 300
        val ENTRY_ORDER = compareByDescending<FileEntry> { it.isDirectory }
            .thenBy { it.name.lowercase() }
    }
}