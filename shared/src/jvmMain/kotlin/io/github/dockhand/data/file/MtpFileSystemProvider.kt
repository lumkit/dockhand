package io.github.dockhand.data.file

import io.github.dockhand.domain.model.FileBrowserException
import io.github.dockhand.domain.model.FileEntry
import io.github.dockhand.domain.model.FileLocation
import io.github.dockhand.domain.model.FilePlace
import io.github.dockhand.domain.model.FilePlaceGroup
import io.github.dockhand.domain.model.FilePlaceKind
import io.github.dockhand.domain.model.FileSource
import io.github.dockhand.domain.model.FileSourceKind
import io.github.dockhand.domain.repository.FileSystemProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.nio.file.Files

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

    override val kind: FileSourceKind = FileSourceKind.Mtp

    override fun observePlaces(): Flow<List<FilePlace>> = flow {
        while (true) {
            emit(readPlaces())
            delay(placesRefreshMs)
        }
    }.flowOn(Dispatchers.IO)

    override fun observeChanges(location: FileLocation): Flow<Unit> = flow {
        var previous: List<FileEntry>? = null
        while (true) {
            // 首次加载由 ViewModel 的 load() 完成，watcher 从下一轮开始。
            delay(changesRefreshMs)
            val current = runCatching { list(location) }.getOrNull()
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
        when (val result = invokeList(target.deviceKey, target.storageId, location.path)) {
            is BridgeResult.Failure -> throw FileBrowserException("MTP 读取失败：${result.message}")
            is BridgeResult.Success -> result.value
                .asSequence()
                .filter { it.name.isNotEmpty() }
                .map { entry -> entry.toFileEntry(location.source, location.path) }
                .toList()
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
        deviceKey: Long,
        storageId: Long,
        path: String,
    ): BridgeResult<List<NativeMtpEntry>> = try {
        MtpNativeBridge.list(deviceKey, storageId, path)
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

    private fun invokeUpload(
        deviceKey: Long,
        storageId: Long,
        localPath: String,
        remotePath: String,
    ): BridgeResult<Long> = try {
        MtpNativeBridge.upload(deviceKey, storageId, localPath, remotePath)
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
            modifiedAt = modifiedAt,
            extension = extension,
            isHidden = false,
        )
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
    }
}