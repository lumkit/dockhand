package io.github.dockhand.ui.screen.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.dockhand.domain.model.FileBreadcrumb
import io.github.dockhand.domain.model.FileBrowserException
import io.github.dockhand.domain.model.FileEntry
import io.github.dockhand.domain.model.FileLocation
import io.github.dockhand.domain.model.FilePlace
import io.github.dockhand.domain.model.FilePlaceKind
import io.github.dockhand.domain.model.FileSourceKind
import io.github.dockhand.domain.model.parentBreadcrumb
import io.github.dockhand.domain.repository.FileBrowserRepository
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone

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
    /** 列表排序 + summary 格式化的线程；测试里可注入 Unconfined。 */
    private val computeDispatcher: CoroutineDispatcher = Dispatchers.Default,
    /** 测试用；默认绑定 [viewModelScope]。 */
    externalScope: CoroutineScope? = null,
) : ViewModel() {

    private val scope: CoroutineScope = externalScope ?: viewModelScope
    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private val loadJobs = mutableMapOf<PaneId, Job>()
    private val watchJobs = mutableMapOf<PaneId, Job>()
    private val placesJob: Job
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
                        bootstrap(places)
                    }
                }
        }
    }

    private fun bootstrap(places: List<FilePlace>) {
        val home = places.firstOrNull { it.kind == FilePlaceKind.Home }?.location
            ?: places.firstOrNull { it.location.source.kind == FileSourceKind.Local }?.location
            ?: places.first().location
        // 侧边栏不再暴露“根目录”，但右面板仍从本机根目录开始。
        val root = if (home.source.kind == FileSourceKind.Local) {
            home.copy(path = "/")
        } else {
            home
        }

        navigate(PaneId.Left, home, recordHistory = false)
        navigate(PaneId.Right, root, recordHistory = false)
    }

    // ---- 面板焦点 ----

    fun onPaneFocus(paneId: PaneId) {
        _state.update {
            if (it.focusedPane == paneId) it else it.copy(focusedPane = paneId)
        }
    }

    /**
     * 列表项单击：
     *  - 先把该面板设为焦点，HomeTopBar 立刻切到它的路径；
     *  - 目录：在焦点面板中进入该路径；
     *  - 文件：只更新选中项，交给后续的打开/传输动作处理。
     */
    fun onEntryClick(paneId: PaneId, entry: FileEntry) {
        onPaneFocus(paneId)
        if (entry.isDirectory) {
            navigate(paneId, entry.location)
        } else {
            _state.update { state ->
                state.updatePane(paneId) { it.copy(selectedIds = setOf(entry.id)) }
            }
        }
    }

    // ---- 导航 ----

    fun onPlaceClick(place: FilePlace) {
        val paneId = _state.value.focusedPane
        navigate(paneId, place.location)
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
            state.updatePane(paneId) { it.copy(isLoading = true, errorMessage = null) }
        }
        watch(paneId, location)
        load(paneId, location)
    }

    // ---- 文件操作 ----

    /**
     * 在焦点面板当前目录新建文件夹或空文件。
     * [createFile] 来自新建对话框里的 CheckboxPreference：勾选创建文件，未勾选创建文件夹。
     */
    fun onCreate(paneId: PaneId, name: String, createFile: Boolean) {
        val location = _state.value.pane(paneId).location ?: return
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            setOperationError("名称不能为空")
            return
        }
        if ('/' in trimmed || trimmed == "." || trimmed == "..") {
            setOperationError("名称不合法：$name")
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
                    errorMessage = null,
                    backStack = backStack,
                    forwardStack = if (recordHistory) emptyList() else pane.forwardStack,
                )
            ).copy(operationError = null)
        }
        watch(paneId, target)
        load(paneId, target)
    }

    /**
     * 监听面板当前目录，外部（Finder、其他 App、本 App 的复制）发生变化时自动刷新。
     * 每个面板一个 watcher；路径切换时旧 watcher 会被取消。
     */
    private fun watch(paneId: PaneId, location: FileLocation) {
        watchJobs[paneId]?.cancel()
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
        loadJobs[paneId] = scope.launch {
            try {
                val entries = files.list(location)

                // 列表加载阶段一次性完成排序 + summary 格式化。
                // LazyColumn 滚动/重组时只读 FileRowUi.summary，不再做任何时间或大小计算。
                val rows = withContext(computeDispatcher) {
                    entries.sortedWith(ENTRY_ORDER)
                        .toRowUi(TimeZone.currentSystemDefault())
                }

                _state.update { state ->
                    if (state.pane(paneId).location != location) state
                    else state.updatePane(paneId) {
                        it.copy(
                            entries = rows,
                            isLoading = false,
                            errorMessage = null,
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { state ->
                    if (state.pane(paneId).location != location) state
                    else state.updatePane(paneId) {
                        it.copy(
                            entries = emptyList(),
                            isLoading = false,
                            errorMessage = e.userMessage(),
                        )
                    }
                }
            }
        }
    }

    override fun onCleared() {
        placesJob.cancel()
        loadJobs.values.forEach { it.cancel() }
        loadJobs.clear()
        watchJobs.values.forEach { it.cancel() }
        watchJobs.clear()
        super.onCleared()
    }

    private fun Throwable.userMessage(): String =
        (this as? FileBrowserException)?.message ?: message ?: "读取失败"

    private companion object {
        val ENTRY_ORDER = compareByDescending<FileEntry> { it.isDirectory }
            .thenBy { it.name.lowercase() }
    }
}