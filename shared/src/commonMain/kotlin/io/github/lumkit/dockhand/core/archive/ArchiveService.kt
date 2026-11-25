package io.github.lumkit.dockhand.core.archive

import io.github.lumkit.dockhand.domain.model.FileEntry
import io.github.lumkit.dockhand.domain.model.FileLocation

enum class CompressionLevel(val zipLevel: Int) {
    Store(0),
    Fastest(1),
    Fast(3),
    Normal(5),
    Maximum(7),
    Ultra(9),
}

enum class ArchiveFormat(
    val extension: String,
    val supportsPassword: Boolean = false,
    val supportsSplit: Boolean = false,
    val singleFile: Boolean = false,
) {
    Zip("zip", supportsPassword = true, supportsSplit = true),
    SevenZ("7z", supportsPassword = true),
    Tar("tar"),
    TarGz("tar.gz"),
    TarXz("tar.xz"),
    TarBz2("tar.bz2"),
    TarZst("tar.zst"),
    Gzip("gz", singleFile = true),
    Xz("xz", singleFile = true),
    Zstd("zst", singleFile = true),
}

fun archiveBaseName(name: String): String {
    val lower = name.lowercase()
    val suffixes = listOf(
        ".tar.gz", ".tgz", ".tar.xz", ".txz", ".tar.bz2", ".tbz2", ".tbz",
        ".tar.zst", ".tzst", ".tar", ".7z", ".zip", ".gz", ".xz", ".zst",
    )
    val suffix = suffixes.firstOrNull { lower.endsWith(it) }
    return if (suffix != null) name.dropLast(suffix.length).ifBlank { name } else {
        name.substringBeforeLast('.', name).ifBlank { name }
    }
}

fun archiveFormatForName(name: String): ArchiveFormat? {
    val lower = name.lowercase()
    return when {
        lower.endsWith(".tar.gz") || lower.endsWith(".tgz") -> ArchiveFormat.TarGz
        lower.endsWith(".tar.xz") || lower.endsWith(".txz") -> ArchiveFormat.TarXz
        lower.endsWith(".tar.bz2") || lower.endsWith(".tbz2") || lower.endsWith(".tbz") -> ArchiveFormat.TarBz2
        lower.endsWith(".tar.zst") || lower.endsWith(".tzst") -> ArchiveFormat.TarZst
        lower.endsWith(".tar") -> ArchiveFormat.Tar
        lower.endsWith(".7z") -> ArchiveFormat.SevenZ
        lower.endsWith(".zip") -> ArchiveFormat.Zip
        lower.endsWith(".gz") -> ArchiveFormat.Gzip
        lower.endsWith(".xz") -> ArchiveFormat.Xz
        lower.endsWith(".zst") -> ArchiveFormat.Zstd
        else -> null
    }
}

data class ExtractRequest(
    val archive: FileEntry,
    val destinationDirectory: FileLocation,
    val createSubfolder: Boolean,
    val subfolderName: String,
    val password: String = "",
    val deleteSource: Boolean = false,
)

data class ExtractCallbacks(
    val onProgress: (completed: Long?, total: Long?) -> Unit = { _, _ -> },
    val onBytes: (completed: Long, total: Long) -> Unit = { _, _ -> },
    val isCancelled: () -> Boolean = { false },
)

data class CompressRequest(
    val entries: List<FileEntry>,
    val destination: FileLocation,
    val archiveName: String,
    val format: ArchiveFormat = ArchiveFormat.Zip,
    val level: CompressionLevel = CompressionLevel.Normal,
    val password: String = "",
    val splitSizeMb: Long? = null,
    val separateArchives: Boolean = false,
    val deleteSource: Boolean = false,
)

data class CompressCallbacks(
    val onProgress: (completed: Long?, total: Long?) -> Unit = { _, _ -> },
    val onBytes: (completed: Long, total: Long) -> Unit = { _, _ -> },
    val isCancelled: () -> Boolean = { false },
)

interface ArchiveService {
    suspend fun compress(request: CompressRequest, callbacks: CompressCallbacks)

    suspend fun extract(request: ExtractRequest, callbacks: ExtractCallbacks)

    /** 判断压缩包是否需要密码；用于在解压前决定是否弹出密码框。 */
    suspend fun isPasswordProtected(archive: FileEntry): Boolean = false
}

object NoopArchiveService : ArchiveService {
    override suspend fun compress(request: CompressRequest, callbacks: CompressCallbacks) {
        error("当前平台不支持压缩")
    }

    override suspend fun extract(request: ExtractRequest, callbacks: ExtractCallbacks) {
        error("当前平台不支持解压")
    }
}
