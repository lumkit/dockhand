package io.github.dockhand.data.file

import io.github.dockhand.domain.model.FileBrowserException
import io.github.dockhand.domain.model.FileEntry
import io.github.dockhand.domain.model.FileInfo
import io.github.dockhand.domain.model.FileLocation
import io.github.dockhand.domain.model.FileUsage
import io.github.dockhand.domain.model.FilePlace
import io.github.dockhand.domain.model.FilePlaceGroup
import io.github.dockhand.domain.model.FilePlaceKind
import io.github.dockhand.domain.model.FileSource
import io.github.dockhand.domain.model.FileSourceKind
import io.github.dockhand.domain.repository.FileSystemProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.file.AccessDeniedException
import java.nio.file.ClosedWatchServiceException
import java.nio.file.DirectoryStream
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.StandardWatchEventKinds
import java.nio.file.WatchService
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.PosixFileAttributes
import java.nio.file.attribute.PosixFilePermission
import java.util.concurrent.TimeUnit

/**
 * macOS 本地文件系统。
 *
 * 负责三件事：
 *  1. 生成侧边栏入口（个人目录、常用目录、挂载磁盘）；
 *  2. 列出目录内容（默认隐藏 dotfile，和 Finder 的默认行为一致）；
 *  3. 提供同设备内的文件/目录复制，供后续双栏互传使用。
 *
 * 所有阻塞 IO 都在 Dispatchers.IO 上执行。
 */
class LocalFileSystemProvider(
    private val homeDirectory: Path = Path.of(System.getProperty("user.home") ?: "."),
    private val volumesDirectory: Path = Path.of("/Volumes"),
    sourceName: String = "本机",
) : FileSystemProvider {

    override val kind: FileSourceKind = FileSourceKind.Local

    private val source = FileSource(
        id = FileSource.LOCAL_ID,
        name = sourceName,
        kind = FileSourceKind.Local,
    )

    private val quickAccess = listOf(
        FilePlaceKind.Desktop to "Desktop",
        FilePlaceKind.Documents to "Documents",
        FilePlaceKind.Downloads to "Downloads",
        FilePlaceKind.Pictures to "Pictures",
        FilePlaceKind.Music to "Music",
        FilePlaceKind.Movies to "Movies",
    )

    override fun observePlaces(): Flow<List<FilePlace>> = combine(
        watchDirectory(volumesDirectory).onStart { emit(Unit) },
        watchDirectory(homeDirectory).onStart { emit(Unit) },
        tickerFlow(REFRESH_INTERVAL_MS),
    ) { _, _, _ ->
        runCatching { scanPlaces() }.getOrDefault(emptyList())
    }.flowOn(Dispatchers.IO)

    override fun observeChanges(location: FileLocation): Flow<Unit> = when (location.source.kind) {
        FileSourceKind.Local -> watchDirectory(location.toLocalPath())
        FileSourceKind.Mtp -> emptyFlow()
    }

    override suspend fun list(location: FileLocation): List<FileEntry> = withContext(Dispatchers.IO) {
        ensureLocal(location)
        val directory = location.toLocalPath()
        if (!Files.exists(directory)) {
            throw FileBrowserException("路径不存在：${location.path}")
        }
        if (!Files.isDirectory(directory)) {
            throw FileBrowserException("不是文件夹：${location.path}")
        }

        val entries = try {
            Files.newDirectoryStream(directory).use { stream ->
                stream.mapNotNull { readEntry(it) }
            }
        } catch (e: AccessDeniedException) {
            throw FileBrowserException("没有权限读取：${location.path}", e)
        } catch (e: IOException) {
            throw FileBrowserException("读取文件夹失败：${location.path}", e)
        }

        entries.sortedWith(ENTRY_ORDER)
    }

    override suspend fun info(entry: FileEntry): FileInfo = withContext(Dispatchers.IO) {
        ensureLocal(entry.location)
        val path = entry.location.toLocalPath()
        if (!Files.exists(path)) {
            throw FileBrowserException("路径不存在：${entry.location.path}")
        }

        val attributes = Files.readAttributes(path, BasicFileAttributes::class.java)
        val isDirectory = attributes.isDirectory
        val posix = runCatching {
            Files.readAttributes(path, PosixFileAttributes::class.java)
        }.getOrNull()
        val permissionSymbol = posix?.let {
            permissionSymbol(it.isDirectory, it.permissions())
        }
        val permissionCode = posix?.let { permissionCode(it.permissions()) }

        FileInfo(
            name = entry.name,
            parentPath = path.parent?.toString() ?: "/",
            isDirectory = isDirectory,
            sizeBytes = if (isDirectory) null else attributes.size(),
            itemCount = null,
            createdAt = attributes.creationTime().toMillis(),
            modifiedAt = attributes.lastModifiedTime().toMillis(),
            permissionSymbol = permissionSymbol,
            permissionCode = permissionCode,
            owner = posix?.owner()?.name,
            group = posix?.group()?.name,
        )
    }

    override suspend fun exportToLocal(
        source: FileLocation,
        localPath: String,
    ): Unit = withContext(Dispatchers.IO) {
        ensureLocal(source)
        Files.copy(
            source.toLocalPath(),
            Path.of(localPath),
            StandardCopyOption.REPLACE_EXISTING,
        )
    }

    override suspend fun importFromLocal(
        localPath: String,
        destination: FileLocation,
        overwrite: Boolean,
    ): Unit = withContext(Dispatchers.IO) {
        ensureLocal(destination)
        val target = destination.toLocalPath()
        if (Files.exists(target) && !overwrite) {
            throw FileBrowserException("目标已存在：${destination.path}")
        }
        target.parent?.let { Files.createDirectories(it) }
        Files.copy(
            Path.of(localPath),
            target,
            if (overwrite) StandardCopyOption.REPLACE_EXISTING else StandardCopyOption.COPY_ATTRIBUTES,
        )
    }

    override suspend fun exists(location: FileLocation): Boolean = withContext(Dispatchers.IO) {
        ensureLocal(location)
        Files.exists(location.toLocalPath())
    }

    override suspend fun delete(
        location: FileLocation,
        recursive: Boolean,
    ): Unit = withContext(Dispatchers.IO) {
        ensureLocal(location)
        val target = location.toLocalPath()
        if (!Files.exists(target)) return@withContext
        if (Files.isDirectory(target)) {
            if (!recursive) {
                Files.delete(target)
            } else {
                Files.walk(target).use { paths ->
                    paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
                }
            }
        } else {
            Files.deleteIfExists(target)
        }
    }

    override suspend fun usage(entry: FileEntry): FileUsage? = withContext(Dispatchers.IO) {
        ensureLocal(entry.location)
        val path = entry.location.toLocalPath()
        if (!Files.isDirectory(path)) {
            val size = runCatching { Files.size(path) }.getOrNull() ?: return@withContext null
            FileUsage(size)
        } else {
            val usage = directoryUsage(path)
            FileUsage(usage.first, usage.second)
        }
    }

    override suspend fun createDirectory(
        parent: FileLocation,
        name: String,
    ): Unit = withContext(Dispatchers.IO) {
        val target = newItemTarget(parent, name)
        try {
            Files.createDirectory(target)
        } catch (e: AccessDeniedException) {
            throw FileBrowserException("没有权限新建文件夹：$name", e)
        } catch (e: IOException) {
            throw FileBrowserException("新建文件夹失败：$name", e)
        }
    }

    override suspend fun createFile(
        parent: FileLocation,
        name: String,
    ): Unit = withContext(Dispatchers.IO) {
        val target = newItemTarget(parent, name)
        try {
            Files.createFile(target)
        } catch (e: AccessDeniedException) {
            throw FileBrowserException("没有权限新建文件：$name", e)
        } catch (e: IOException) {
            throw FileBrowserException("新建文件失败：$name", e)
        }
    }

    override suspend fun copy(
        source: FileLocation,
        destinationDirectory: FileLocation,
        overwrite: Boolean,
    ): Unit = withContext(Dispatchers.IO) {
        ensureLocal(source)
        ensureLocal(destinationDirectory)

        val from = source.toLocalPath()
        val into = destinationDirectory.toLocalPath()
        if (!Files.exists(from)) throw FileBrowserException("源文件不存在：${source.path}")
        if (!Files.isDirectory(into)) throw FileBrowserException("目标不是文件夹：${destinationDirectory.path}")

        val name = from.fileName?.toString()
            ?: throw FileBrowserException("无法复制根目录")
        val target = into.resolve(name)

        if (Files.exists(target) && !overwrite) {
            throw FileBrowserException("目标已存在：${target}")
        }
        if (Files.isDirectory(from)) {
            val fromReal = runCatching { from.toRealPath() }.getOrDefault(from.toAbsolutePath().normalize())
            val intoReal = runCatching { into.toRealPath() }.getOrDefault(into.toAbsolutePath().normalize())
            if (intoReal.startsWith(fromReal)) {
                throw FileBrowserException("不能把文件夹复制到自身内部")
            }
        }

        try {
            copyRecursively(from, target, overwrite)
        } catch (e: AccessDeniedException) {
            throw FileBrowserException("没有权限复制：${source.path}", e)
        } catch (e: IOException) {
            throw FileBrowserException("复制失败：${source.path}", e)
        }
    }

    /** 校验名称并解析出待创建的新项目路径；文件/文件夹共用。 */
    private fun newItemTarget(parent: FileLocation, name: String): Path {
        ensureLocal(parent)
        val directory = parent.toLocalPath()
        if (!Files.isDirectory(directory)) {
            throw FileBrowserException("目标不是文件夹：${parent.path}")
        }

        val trimmed = name.trim()
        if (trimmed.isEmpty()) throw FileBrowserException("名称不能为空")
        if ('/' in trimmed || trimmed == "." || trimmed == "..") {
            throw FileBrowserException("名称不合法：$name")
        }

        val target = directory.resolve(trimmed)
        if (Files.exists(target)) {
            throw FileBrowserException("同名项目已存在：$trimmed")
        }
        return target
    }

    /**
     * 监听目录的直接子项变化。
     *
     * 使用 WatchService 的 poll 循环而不是无限阻塞的 take()，这样协程取消后最多
     * [WATCH_POLL_MS] 内就能关闭服务；隐藏文件（如 .DS_Store）的变化会被忽略。
     */
    private fun watchDirectory(directory: Path): Flow<Unit> = flow {
        if (!Files.isDirectory(directory)) return@flow

        val watchService: WatchService = try {
            FileSystems.getDefault().newWatchService()
        } catch (e: IOException) {
            return@flow
        }

        try {
            val registered = runCatching {
                directory.register(
                    watchService,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_DELETE,
                    StandardWatchEventKinds.ENTRY_MODIFY,
                )
            }.isSuccess
            if (!registered) return@flow

            while (currentCoroutineContext().isActive) {
                val key = watchService.poll(WATCH_POLL_MS, TimeUnit.MILLISECONDS) ?: continue
                var changed = false
                for (event in key.pollEvents()) {
                    if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
                        changed = true
                        continue
                    }
                    val name = (event.context() as? Path)?.fileName?.toString()
                    if (name != null && name.startsWith(".")) continue
                    changed = true
                }

                val valid = key.reset()
                if (changed) {
                    emit(Unit)
                    // 合并 Finder/复制操作产生的一批事件。
                    delay(WATCH_COALESCE_MS)
                }
                if (!valid) break
            }
        } catch (e: ClosedWatchServiceException) {
            // flow 被取消或服务关闭，属于正常退出。
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        } finally {
            runCatching { watchService.close() }
        }
    }.flowOn(Dispatchers.IO)

    private fun tickerFlow(intervalMs: Long): Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            delay(intervalMs)
        }
    }

    private fun scanPlaces(): List<FilePlace> {
        val places = mutableListOf<FilePlace>()

        addPlace(
            places = places,
            kind = FilePlaceKind.Home,
            path = homeDirectory,
            label = homeDirectory.fileName?.toString()?.takeIf { it.isNotBlank() } ?: "Home",
        )

        for ((placeKind, directoryName) in quickAccess) {
            addPlace(
                places = places,
                kind = placeKind,
                path = homeDirectory.resolve(directoryName),
                label = directoryName,
            )
        }

        // 单个挂载点异常不应该让整个侧边栏停更。
        places += runCatching { scanVolumes() }.getOrDefault(emptyList())
        return places
    }

    private fun addPlace(
        places: MutableList<FilePlace>,
        kind: FilePlaceKind,
        path: Path,
        label: String,
    ) {
        val normalized = path.toAbsolutePath().normalize()
        if (!Files.isDirectory(normalized)) return
        places += FilePlace(
            id = "local:${kind.name.lowercase()}",
            label = label,
            location = FileLocation(source, normalized.toString()),
            kind = kind,
            group = FilePlaceGroup.Local,
        )
    }

    private fun scanVolumes(): List<FilePlace> {
        if (!Files.isDirectory(volumesDirectory)) return emptyList()
        val rootStoreName = runCatching { Files.getFileStore(Path.of("/")).name() }.getOrNull()
        val stream: DirectoryStream<Path> = runCatching {
            Files.newDirectoryStream(volumesDirectory)
        }.getOrNull() ?: return emptyList()

        return stream.use { entries ->
            entries.mapNotNull { path ->
                if (!Files.isDirectory(path)) return@mapNotNull null

                val name = path.fileName?.toString() ?: return@mapNotNull null
                if (name in IGNORED_VOLUME_NAMES) return@mapNotNull null

                // Macintosh HD 是指向 / 的符号链接，但它本身就是启动盘，
                // 在“挂载磁盘”分组里必须保留。其他符号链接仍然跳过。
                val isStartupVolume = name == "Macintosh HD"
                if (!isStartupVolume && Files.isSymbolicLink(path)) return@mapNotNull null

                // /Volumes 下也可能只是普通文件夹（例如残留的 dmg 目录）。
                // 只有挂载点拥有不同的 FileStore 时才算独立磁盘；
                // 启动盘与 / 共用同一个 FileStore，单独放行。
                if (!isStartupVolume) {
                    val storeName = runCatching { Files.getFileStore(path).name() }.getOrNull()
                    if (storeName != null && storeName == rootStoreName) return@mapNotNull null
                }

                val capacity = runCatching {
                    Files.getFileStore(path).let { store -> store.totalSpace to store.usableSpace }
                }.getOrNull()
                val totalBytes = capacity?.first?.takeIf { it > 0 }
                val freeBytes = capacity?.second?.takeIf { it >= 0 }

                FilePlace(
                    id = "local:volume:$name",
                    label = name,
                    location = FileLocation(source, path.toAbsolutePath().normalize().toString()),
                    kind = FilePlaceKind.Volume,
                    group = FilePlaceGroup.Mounted,
                    totalBytes = totalBytes,
                    freeBytes = freeBytes,
                )
            }.sortedBy { it.label.lowercase() }
        }
    }

    private fun readEntry(path: Path): FileEntry? {
        val name = path.fileName?.toString() ?: return null
        if (name.startsWith(".")) return null

        val attributes = runCatching {
            Files.readAttributes(path, BasicFileAttributes::class.java)
        }.getOrNull() ?: return null

        val normalized = path.toAbsolutePath().normalize()
        val isDirectory = attributes.isDirectory
        val extension = if (!isDirectory && name.contains('.')) {
            name.substringAfterLast('.').takeIf { it.isNotEmpty() }
        } else {
            null
        }

        return FileEntry(
            id = normalized.toString(),
            name = name,
            location = FileLocation(source, normalized.toString()),
            isDirectory = isDirectory,
            size = if (isDirectory) null else attributes.size(),
            childCount = if (isDirectory) countVisibleChildren(path) else null,
            modifiedAt = attributes.lastModifiedTime().toMillis(),
            extension = extension,
            isHidden = false,
        )
    }

    /**
     * 本地文件系统没有目录项计数的元数据，只能扫一层目录。
     * 这里和列表保持一致：不统计以 “.” 开头的隐藏项；失败时返回 null。
     */
    private fun countVisibleChildren(directory: Path): Int? = runCatching {
        Files.newDirectoryStream(directory).use { children ->
            children.count { child ->
                child.fileName?.toString()?.startsWith(".") != true
            }
        }
    }.getOrNull()

    private fun copyRecursively(source: Path, target: Path, overwrite: Boolean) {
        if (Files.isSymbolicLink(source)) {
            Files.deleteIfExists(target)
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING, LinkOption.NOFOLLOW_LINKS)
            return
        }

        if (Files.isDirectory(source)) {
            Files.createDirectories(target)
            Files.newDirectoryStream(source).use { children ->
                for (child in children) {
                    val name = child.fileName?.toString() ?: continue
                    copyRecursively(child, target.resolve(name), overwrite)
                }
            }
        } else {
            target.parent?.let { Files.createDirectories(it) }
            if (overwrite) {
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES)
            } else {
                Files.copy(source, target, StandardCopyOption.COPY_ATTRIBUTES)
            }
        }
    }

    private fun directoryUsage(directory: Path): Pair<Long, Int> {
        var bytes = 0L
        var count = 0
        Files.walkFileTree(
            directory,
            object : SimpleFileVisitor<Path>() {
                override fun preVisitDirectory(
                    dir: Path,
                    attrs: BasicFileAttributes,
                ): java.nio.file.FileVisitResult {
                    if (dir != directory) count++
                    return java.nio.file.FileVisitResult.CONTINUE
                }

                override fun visitFile(
                    file: Path,
                    attrs: BasicFileAttributes,
                ): java.nio.file.FileVisitResult {
                    if (attrs.isRegularFile) bytes += attrs.size()
                    count++
                    return java.nio.file.FileVisitResult.CONTINUE
                }

                override fun visitFileFailed(
                    file: Path,
                    exc: IOException,
                ): java.nio.file.FileVisitResult = java.nio.file.FileVisitResult.CONTINUE
            }
        )
        return bytes to count
    }

    private fun permissionSymbol(
        isDirectory: Boolean,
        permissions: Set<PosixFilePermission>,
    ): String = buildString {
        append(if (isDirectory) 'd' else '-')
        val bits = listOf(
            PosixFilePermission.OWNER_READ to 'r',
            PosixFilePermission.OWNER_WRITE to 'w',
            PosixFilePermission.OWNER_EXECUTE to 'x',
            PosixFilePermission.GROUP_READ to 'r',
            PosixFilePermission.GROUP_WRITE to 'w',
            PosixFilePermission.GROUP_EXECUTE to 'x',
            PosixFilePermission.OTHERS_READ to 'r',
            PosixFilePermission.OTHERS_WRITE to 'w',
            PosixFilePermission.OTHERS_EXECUTE to 'x',
        )
        bits.forEach { (permission, symbol) ->
            append(if (permission in permissions) symbol else '-')
        }
    }

    private fun permissionCode(permissions: Set<PosixFilePermission>): String {
        val bits = listOf(
            PosixFilePermission.OWNER_READ to 0b100_000_000,
            PosixFilePermission.OWNER_WRITE to 0b010_000_000,
            PosixFilePermission.OWNER_EXECUTE to 0b001_000_000,
            PosixFilePermission.GROUP_READ to 0b000_100_000,
            PosixFilePermission.GROUP_WRITE to 0b000_010_000,
            PosixFilePermission.GROUP_EXECUTE to 0b000_001_000,
            PosixFilePermission.OTHERS_READ to 0b000_000_100,
            PosixFilePermission.OTHERS_WRITE to 0b000_000_010,
            PosixFilePermission.OTHERS_EXECUTE to 0b000_000_001,
        )
        val value = bits.fold(0) { result, (permission, bit) ->
            if (permission in permissions) result or bit else result
        }
        return value.toString(8).padStart(3, '0')
    }

    private fun ensureLocal(location: FileLocation) {
        if (location.source.kind != FileSourceKind.Local) {
            throw FileBrowserException("本地 provider 不能处理：${location.source.name}")
        }
    }

    private fun FileLocation.toLocalPath(): Path =
        Path.of(path).toAbsolutePath().normalize()

    private companion object {
        const val REFRESH_INTERVAL_MS = 3_000L
        const val WATCH_POLL_MS = 250L
        const val WATCH_COALESCE_MS = 150L
        val IGNORED_VOLUME_NAMES = setOf("Recovery")
        val ENTRY_ORDER = compareByDescending<FileEntry> { it.isDirectory }
            .thenBy { it.name.lowercase() }
    }
}