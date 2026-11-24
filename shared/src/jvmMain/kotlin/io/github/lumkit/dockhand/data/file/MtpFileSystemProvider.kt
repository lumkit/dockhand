package io.github.lumkit.dockhand.data.file

import io.github.lumkit.dockhand.domain.model.FileBrowserException
import io.github.lumkit.dockhand.domain.model.FileEntry
import io.github.lumkit.dockhand.domain.model.FileListPage
import io.github.lumkit.dockhand.domain.model.FileLocation
import io.github.lumkit.dockhand.domain.model.FilePlace
import io.github.lumkit.dockhand.domain.model.FilePlaceGroup
import io.github.lumkit.dockhand.domain.model.FilePlaceKind
import io.github.lumkit.dockhand.domain.model.FileSource
import io.github.lumkit.dockhand.domain.model.FileSourceKind
import io.github.lumkit.dockhand.domain.repository.FileSystemProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.nio.file.Files
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration.Companion.milliseconds

/**
 * MTP 文件后端，基于内嵌的 mtp-rs Rust bridge。
 *
 * 不依赖 OpenMTP / libmtp / libusb；原生桥由 native/mtp-bridge 构建，
 * JVM 侧通过 FFM 调用。MTP 没有目录事件，因此列表通过轮询检测变化。
 */
class MtpFileSystemProvider(
    private val placesRefreshMs: Long = 5_000L,
    private val changesRefreshMs: Long = 4_000L,
) : FileSystemProvider {

    private val requestIds = AtomicLong()
    private val transferIds = AtomicLong()
    private var showHiddenItems = false
    private val deviceTransferLocks = mutableMapOf<Long, Mutex>()
    private val deviceCooldowns = mutableMapOf<Long, Long>()
    private val indexMutex = Mutex()
    private val indexedDirectories = object : LinkedHashMap<FileLocation, List<FileEntry>>(4, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<FileLocation, List<FileEntry>>): Boolean =
            size > 2
    }

    override val kind: FileSourceKind = FileSourceKind.Mtp

    override fun setShowHiddenItems(show: Boolean) {
        showHiddenItems = show
    }

    override fun observePlaces(): Flow<List<FilePlace>> = flow {
        while (true) {
            emit(readPlaces())
            delay(placesRefreshMs.milliseconds)
        }
    }.flowOn(Dispatchers.IO)

    override fun observeChanges(location: FileLocation): Flow<Unit> = flow {
        var previous: List<FileEntry>? = null
        while (true) {
            // 首次加载由 ViewModel 的 load() 完成，watcher 从下一轮开始。
            delay(changesRefreshMs.milliseconds)
            val current = runCatching { list(location) }.onFailure {
                if (it is CancellationException) {
                    currentCoroutineContext().ensureActive()
                }
            }.getOrNull()
            if (current != null) {
                if (previous != null && current != previous) {
                    emit(Unit)
                }
                previous = current
            }
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun list(location: FileLocation): List<FileEntry> = withContext(Dispatchers.IO) {
        val target = targetOf(location.source)
        val requestId = requestIds.incrementAndGet()
        val entries = awaitList(
            requestId = requestId,
            deviceKey = target.deviceKey,
            storageId = target.storageId,
            path = location.path,
            fast = false,
            location = location,
        )
        entries
    }

    /**
     * MTP 大目录分页：
     * 1. 第一次请求用轻量 prop-list 建立全量索引；
     * 2. 按“目录优先 + 名称”排序后缓存；
     * 3. 后续页直接从缓存切片，不再访问 USB。
     */
    override suspend fun listPage(
        location: FileLocation,
        offset: Int,
        limit: Int,
    ): FileListPage = withContext(Dispatchers.IO) {
        val all = indexMutex.withLock {
            val cached = indexedDirectories[location]
            if (offset == 0 || cached == null) {
                val target = targetOf(location.source)
                val requestId = requestIds.incrementAndGet()
                val fastEntries = awaitList(
                    requestId = requestId,
                    deviceKey = target.deviceKey,
                    storageId = target.storageId,
                    path = location.path,
                    fast = true,
                    location = location,
                ).sortedWith(ENTRY_ORDER)

                // 小目录继续走完整元数据，保留修改时间；大目录不做 5 万次
                // GetObjectInfo，时间先留空，优先保证分页可用。
                val entries = if (fastEntries.size <= DETAILED_METADATA_LIMIT) {
                    awaitList(
                        requestId = requestIds.incrementAndGet(),
                        deviceKey = target.deviceKey,
                        storageId = target.storageId,
                        path = location.path,
                        fast = false,
                        location = location,
                    ).sortedWith(ENTRY_ORDER)
                } else {
                    fastEntries
                }

                indexedDirectories[location] = entries
                entries
            } else {
                cached
            }
        }

        all.toPage(offset, limit)
    }

    private fun List<FileEntry>.toPage(offset: Int, limit: Int): FileListPage {
        val page = drop(offset).take(limit)
        return FileListPage(
            entries = page,
            hasMore = offset + page.size < size,
            total = size,
        )
    }

    private suspend fun awaitList(
        requestId: Long,
        deviceKey: Long,
        storageId: Long,
        path: String,
        fast: Boolean,
        location: FileLocation,
    ): List<FileEntry> = suspendCancellableCoroutine { continuation ->
        // 原生 list 是阻塞调用，不能依赖 Job.invokeOnCompletion：
        // 那要等阻塞返回后才触发。invokeOnCancellation 在取消时立即触发，
        // 可以把 requestId 传给 Rust，让 list 在下一个 USB roundtrip 退出。
        continuation.invokeOnCancellation {
            runCatching { MtpNativeBridge.cancelList(requestId) }
        }

        val result = runCatching {
            when (
                val bridge = if (fast) {
                    invokeFastList(requestId, deviceKey, storageId, path)
                } else {
                    invokeList(requestId, deviceKey, storageId, path)
                }
            ) {
                is BridgeResult.Failure -> throw FileBrowserException("MTP 读取失败：${bridge.message}")
                is BridgeResult.Success -> bridge.value
                    .asSequence()
                    .filter { it.name.isNotEmpty() }
                    .filter { showHiddenItems || !it.name.startsWith(".") }
                    .map { entry -> entry.toFileEntry(location.source, location.path) }
                    .toList()
            }
        }

        if (continuation.isActive) {
            result
                .onSuccess { continuation.resume(it) }
                .onFailure { continuation.resumeWithException(it) }
        }
    }

    override suspend fun exists(location: FileLocation): Boolean = withContext(Dispatchers.IO) {
        val parentPath = location.path.substringBeforeLast('/', missingDelimiterValue = "").ifBlank { "/" }
        val name = location.path.substringAfterLast('/')
        val parent = FileLocation(location.source, parentPath)
        val cached = indexMutex.withLock { indexedDirectories[parent] }
        val entries = cached ?: list(parent)
        entries.any { it.name == name }
    }

    override suspend fun copyNative(
        source: FileLocation,
        destinationDirectory: FileLocation,
    ): Boolean = withContext(Dispatchers.IO) {
        val sourceTarget = targetOf(source.source)
        val destinationTarget = targetOf(destinationDirectory.source)
        if (sourceTarget.deviceKey != destinationTarget.deviceKey) {
            return@withContext false
        }
        val name = source.path.substringAfterLast('/')
        val destinationPath = appendPath(destinationDirectory.path, name)
        when (val result = MtpNativeBridge.copyObject(
            deviceKey = sourceTarget.deviceKey,
            sourceStorageId = sourceTarget.storageId,
            sourcePath = source.path,
            destinationStorageId = destinationTarget.storageId,
            destinationPath = destinationPath,
        )) {
            is BridgeResult.Failure -> false
            is BridgeResult.Success -> true
        }
    }

    override suspend fun exportToLocal(
        source: FileLocation,
        localPath: String,
        onProgress: (completed: Long, total: Long) -> Unit,
        onWaiting: (deviceName: String?) -> Unit,
    ): Unit = withContext(Dispatchers.IO) {
        val target = targetOf(source.source)
        val requestId = transferIds.incrementAndGet()
        val result = runTransferWithProgress(
            requestId = requestId,
            deviceKey = target.deviceKey,
            deviceName = source.source.name,
            onProgress = onProgress,
            onWaiting = onWaiting,
        ) {
            MtpNativeBridge.downloadWithProgress(
                requestId = requestId,
                deviceKey = target.deviceKey,
                storageId = target.storageId,
                remotePath = source.path,
                localPath = localPath,
            )
        }
        when (result) {
            is BridgeResult.Failure -> throw FileBrowserException("MTP 下载失败：${result.message}")
            is BridgeResult.Success -> Unit
        }
    }

    override suspend fun importFromLocal(
        localPath: String,
        destination: FileLocation,
        overwrite: Boolean,
        onProgress: (completed: Long, total: Long) -> Unit,
        onWaiting: (deviceName: String?) -> Unit,
    ): Unit = withContext(Dispatchers.IO) {
        val target = targetOf(destination.source)
        if (overwrite) {
            delete(destination, recursive = true)
        }
        val requestId = transferIds.incrementAndGet()
        val result = runTransferWithProgress(
            requestId = requestId,
            deviceKey = target.deviceKey,
            deviceName = destination.source.name,
            onProgress = onProgress,
            onWaiting = onWaiting,
        ) {
            MtpNativeBridge.uploadWithProgress(
                requestId = requestId,
                deviceKey = target.deviceKey,
                storageId = target.storageId,
                localPath = localPath,
                remotePath = destination.path,
            )
        }
        when (result) {
            is BridgeResult.Failure -> throw FileBrowserException("MTP 上传失败：${result.message}")
            is BridgeResult.Success -> Unit
        }
    }

    @OptIn(InternalCoroutinesApi::class)
    private suspend fun <T> runTransferWithProgress(
        requestId: Long,
        deviceKey: Long,
        deviceName: String,
        onProgress: (Long, Long) -> Unit,
        onWaiting: (String?) -> Unit,
        transfer: suspend () -> BridgeResult<T>,
    ): BridgeResult<T> = coroutineScope {
        val deviceLock = synchronized(deviceTransferLocks) {
            deviceTransferLocks.getOrPut(deviceKey) { Mutex() }
        }
        var locked = deviceLock.tryLock()
        if (!locked) {
            // 在 JVM 侧排队，取消等待中的任务时不需要进入阻塞的原生调用。
            onWaiting(deviceName)
            deviceLock.lock()
            locked = true
        }
        val cooldownUntil = synchronized(deviceCooldowns) {
            deviceCooldowns[deviceKey] ?: 0L
        }
        val cooldownRemaining = cooldownUntil - System.currentTimeMillis()
        if (cooldownRemaining > 0) {
            delay(cooldownRemaining)
        }
        try {
            onWaiting(null)
            coroutineScope {
                var cancelled = false
                val deferred = async(Dispatchers.IO) { transfer() }
                val cancellation = currentCoroutineContext()[Job]?.invokeOnCompletion(
                    onCancelling = true,
                ) { cause ->
                    if (cause != null) {
                        cancelled = true
                        MtpNativeBridge.cancelTransfer(requestId)
                    }
                }
                var lastWaiting = false
                try {
                    while (!deferred.isCompleted) {
                        val waiting = MtpNativeBridge.transferWaiting(requestId)
                        if (waiting != lastWaiting) {
                            lastWaiting = waiting
                            onWaiting(if (waiting) deviceName else null)
                        }
                        MtpNativeBridge.transferProgress(requestId)?.let { (completed, total) ->
                            if (total > 0) onProgress(completed, total)
                        }
                        delay(100)
                    }
                    deferred.await()
                } finally {
                    cancellation?.dispose()
                    MtpNativeBridge.clearTransferProgress(requestId)
                    if (cancelled) {
                        synchronized(deviceCooldowns) {
                            // 取消上传后设备需要一点时间结束会话，避免下一个任务立刻收到 GeneralError。
                            deviceCooldowns[deviceKey] =
                                System.currentTimeMillis() + MTP_CANCEL_COOLDOWN_MS
                        }
                    }
                }
            }
        } finally {
            if (locked) deviceLock.unlock()
            onWaiting(null)
        }
    }

    override suspend fun rename(
        location: FileLocation,
        newName: String,
        isCancelled: () -> Boolean,
        onWaiting: (deviceName: String?) -> Unit,
    ): Unit = withContext(Dispatchers.IO) {
        val name = newName.trim()
        if (name.isEmpty() || '/' in name || name == "." || name == "..") {
            throw FileBrowserException("名称无效：$newName")
        }
        if (name == location.path.substringAfterLast('/')) {
            throw FileBrowserException("名称没有变化")
        }
        val target = targetOf(location.source)
        val parentPath = location.path.substringBeforeLast('/', missingDelimiterValue = "").ifBlank { "/" }
        val parent = FileLocation(location.source, parentPath)
        if (list(parent).any { it.name == name }) {
            throw FileBrowserException("同名项目已存在：$name")
        }
        val deviceLock = synchronized(deviceTransferLocks) {
            deviceTransferLocks.getOrPut(target.deviceKey) { Mutex() }
        }
        var locked = deviceLock.tryLock()
        if (!locked) {
            onWaiting(location.source.name)
            deviceLock.lock()
            locked = true
        }
        try {
            if (isCancelled()) throw CancellationException("重命名已取消")
            onWaiting(null)
            when (val result = MtpNativeBridge.rename(
                deviceKey = target.deviceKey,
                storageId = target.storageId,
                remotePath = location.path,
                newName = name,
            )) {
                is BridgeResult.Failure -> throw FileBrowserException("MTP 重命名失败：${result.message}")
                is BridgeResult.Success -> Unit
            }
        } finally {
            if (locked) deviceLock.unlock()
            onWaiting(null)
        }
    }

    override suspend fun delete(
        location: FileLocation,
        recursive: Boolean,
        isCancelled: () -> Boolean,
        onWaiting: (deviceName: String?) -> Unit,
    ): Unit = withContext(Dispatchers.IO) {
        val target = targetOf(location.source)
        val deviceLock = synchronized(deviceTransferLocks) {
            deviceTransferLocks.getOrPut(target.deviceKey) { Mutex() }
        }
        var locked = deviceLock.tryLock()
        if (!locked) {
            onWaiting(location.source.name)
            deviceLock.lock()
            locked = true
        }
        try {
            onWaiting(null)
            deleteRecursive(location, recursive, isCancelled)
        } finally {
            if (locked) deviceLock.unlock()
            onWaiting(null)
        }
    }

    private suspend fun deleteRecursive(
        location: FileLocation,
        recursive: Boolean,
        isCancelled: () -> Boolean,
    ) {
        val target = targetOf(location.source)
        val parentPath = location.path.substringBeforeLast('/', missingDelimiterValue = "").ifBlank { "/" }
        val name = location.path.substringAfterLast('/')
        val parent = FileLocation(location.source, parentPath)
        val entry = runCatching { list(parent).firstOrNull { it.name == name } }.getOrNull()
        if (recursive && entry?.isDirectory == true) {
            list(location).forEach { child ->
                if (isCancelled()) throw CancellationException("MTP 删除已取消")
                deleteRecursive(child.location, recursive = true, isCancelled = isCancelled)
            }
        }
        if (isCancelled()) throw CancellationException("MTP 删除已取消")
        when (val result = MtpNativeBridge.delete(
            deviceKey = target.deviceKey,
            storageId = target.storageId,
            remotePath = location.path,
        )) {
            is BridgeResult.Failure -> throw FileBrowserException("MTP 删除失败：${result.message}")
            is BridgeResult.Success -> Unit
        }
    }

    override suspend fun createDirectory(parent: FileLocation, name: String) = withContext(Dispatchers.IO) {
        val mtpTarget = targetOf(parent.source)
        val validName = validateNewName(name)
        if (list(parent).any { it.name == validName }) {
            throw FileBrowserException("同名项目已存在：$validName")
        }
        val target = appendPath(parent.path, validName)
        when (val result = invokeMkdir(mtpTarget.deviceKey, mtpTarget.storageId, target)) {
            is BridgeResult.Failure -> throw FileBrowserException("MTP 新建文件夹失败：${result.message}")
            is BridgeResult.Success -> Unit
        }
    }

    override suspend fun createFile(parent: FileLocation, name: String) = withContext(Dispatchers.IO) {
        val mtpTarget = targetOf(parent.source)
        val validName = validateNewName(name)
        if (list(parent).any { it.name == validName }) {
            throw FileBrowserException("同名项目已存在：$validName")
        }
        val target = appendPath(parent.path, validName)
        val tempDir = Files.createTempDirectory("dockhand-mtp-")
        val tempFile = tempDir.resolve(validName)
        try {
            Files.write(tempFile, ByteArray(0))
            when (val result = invokeUpload(mtpTarget.deviceKey, mtpTarget.storageId, tempFile.toString(), target)) {
                is BridgeResult.Failure -> throw FileBrowserException("MTP 新建文件失败：${result.message}")
                is BridgeResult.Success -> Unit
            }
        } finally {
            runCatching { tempDir.toFile().deleteRecursively() }
        }
    }

    override suspend fun copy(
        source: FileLocation,
        destinationDirectory: FileLocation,
        overwrite: Boolean,
    ) {
        throw FileBrowserException("MTP 内部复制尚未接入")
    }

    private fun readPlaces(): List<FilePlace> {
        return when (val result = invokeStorages()) {
            is BridgeResult.Failure -> emptyList()
            is BridgeResult.Success -> result.value.devices.flatMap { device ->
                val deviceLabel = device.model.ifBlank {
                    device.serial.ifBlank { "MTP 设备" }
                }
                device.storages.map { storage ->
                    val sourceId = "${SOURCE_PREFIX}${device.key}:${storage.id}"
                    val label = "$deviceLabel · ${storage.description}"
                    val source = FileSource(
                        id = sourceId,
                        name = label,
                        kind = FileSourceKind.Mtp,
                    )
                    FilePlace(
                        id = sourceId,
                        label = label,
                        location = FileLocation(source, "/"),
                        kind = FilePlaceKind.MtpStorage,
                        group = FilePlaceGroup.Mtp,
                        totalBytes = storage.totalCapacity.takeIf { it > 0 },
                        freeBytes = storage.freeSpace.takeIf { it >= 0 },
                    )
                }
            }
        }
    }

    private fun invokeStorages(): BridgeResult<NativeMtpDevices> = try {
        MtpNativeBridge.storages()
    } catch (error: Throwable) {
        BridgeResult.Failure("MTP 原生桥不可用：${error.message ?: error.javaClass.simpleName}")
    }

    private fun invokeList(
        requestId: Long,
        deviceKey: Long,
        storageId: Long,
        path: String,
    ): BridgeResult<List<NativeMtpEntry>> = try {
        MtpNativeBridge.list(requestId, deviceKey, storageId, path)
    } catch (error: Throwable) {
        BridgeResult.Failure("MTP 原生桥不可用：${error.message ?: error.javaClass.simpleName}")
    }

    private fun invokeFastList(
        requestId: Long,
        deviceKey: Long,
        storageId: Long,
        path: String,
    ): BridgeResult<List<NativeMtpEntry>> = try {
        MtpNativeBridge.listFast(requestId, deviceKey, storageId, path)
    } catch (error: Throwable) {
        BridgeResult.Failure("MTP 原生桥不可用：${error.message ?: error.javaClass.simpleName}")
    }

    private suspend fun invokeUpload(
        deviceKey: Long,
        storageId: Long,
        localPath: String,
        remotePath: String,
    ): BridgeResult<Long> = try {
        MtpNativeBridge.upload(deviceKey, storageId, localPath, remotePath)
    } catch (error: Throwable) {
        BridgeResult.Failure("MTP 原生桥不可用：${error.message ?: error.javaClass.simpleName}")
    }

    private fun invokeMkdir(
        deviceKey: Long,
        storageId: Long,
        path: String,
    ): BridgeResult<Long> = try {
        MtpNativeBridge.mkdir(deviceKey, storageId, path)
    } catch (error: Throwable) {
        BridgeResult.Failure("MTP 原生桥不可用：${error.message ?: error.javaClass.simpleName}")
    }

    private fun NativeMtpEntry.toFileEntry(source: FileSource, parentPath: String): FileEntry {
        val entryPath = appendPath(parentPath, name)
        val extension = if (!isDirectory && name.contains('.')) {
            name.substringAfterLast('.').takeIf { it.isNotEmpty() }
        } else {
            null
        }
        return FileEntry(
            id = "${source.id}:${handle}",
            name = name,
            location = FileLocation(source, entryPath),
            isDirectory = isDirectory,
            size = if (isDirectory) null else size,
            modifiedAt = parseModifiedLocal(modifiedLocal),
            extension = extension,
            isHidden = name.startsWith("."),
        )
    }

    /**
     * MTP 的 DateModified 通常不带时区，是设备本地墙钟时间；
     * 按本机时区解释，避免直接当 UTC 后显示偏移。
     */
    private fun parseModifiedLocal(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        return runCatching {
            LocalDateTime.parse(value)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        }.getOrNull()
    }

    private fun appendPath(parentPath: String, name: String): String {
        val base = parentPath.trimEnd('/')
        return if (base.isEmpty()) "/$name" else "$base/$name"
    }

    private fun validateNewName(name: String): String {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) throw FileBrowserException("名称不能为空")
        if (
            '/' in trimmed ||
            trimmed == "." ||
            trimmed == ".." ||
            '\n' in trimmed ||
            '\r' in trimmed
        ) {
            throw FileBrowserException("名称不合法：$name")
        }
        return trimmed
    }

    private fun targetOf(source: FileSource): MtpTarget {
        if (source.kind != FileSourceKind.Mtp || !source.id.startsWith(SOURCE_PREFIX)) {
            throw FileBrowserException("不是 MTP 存储：${source.name}")
        }
        val parts = source.id.removePrefix(SOURCE_PREFIX).split(':')
        if (parts.size != 2) {
            throw FileBrowserException("无效的 MTP 存储 ID：${source.id}")
        }
        val deviceKey = parts[0].toLongOrNull()
            ?: throw FileBrowserException("无效的 MTP 设备 ID：${source.id}")
        val storageId = parts[1].toLongOrNull()
            ?: throw FileBrowserException("无效的 MTP 存储 ID：${source.id}")
        return MtpTarget(deviceKey, storageId)
    }

    private data class MtpTarget(
        val deviceKey: Long,
        val storageId: Long,
    )

    private companion object {
        const val SOURCE_PREFIX = "mtp:"
        const val DETAILED_METADATA_LIMIT = 1_000
        const val MTP_CANCEL_COOLDOWN_MS = 2_000L
        val ENTRY_ORDER = compareByDescending<FileEntry> { it.isDirectory }
            .thenBy { it.name.lowercase() }
    }
}