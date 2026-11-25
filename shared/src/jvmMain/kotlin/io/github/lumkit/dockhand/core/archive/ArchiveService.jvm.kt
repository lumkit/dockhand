package io.github.lumkit.dockhand.core.archive

import io.github.lumkit.dockhand.domain.model.FileBrowserException
import io.github.lumkit.dockhand.domain.model.FileEntry
import io.github.lumkit.dockhand.domain.model.FileSourceKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.AesKeyStrength
import net.lingala.zip4j.model.enums.CompressionLevel as ZipCompressionLevel
import net.lingala.zip4j.model.enums.CompressionMethod
import net.lingala.zip4j.model.enums.EncryptionMethod
import org.apache.commons.compress.PasswordRequiredException
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.apache.commons.compress.compressors.gzip.GzipParameters
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream
import com.github.luben.zstd.ZstdInputStream
import com.github.luben.zstd.ZstdOutputStream
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.Date

class LocalArchiveService : ArchiveService {
    override suspend fun compress(request: CompressRequest, callbacks: CompressCallbacks) =
        withContext(Dispatchers.IO) {
            if (request.entries.isEmpty()) return@withContext
            if (request.destination.source.kind != FileSourceKind.Local) {
                throw FileBrowserException("压缩目标必须是本地目录")
            }
            val destination = File(request.destination.path)
            if (!destination.isDirectory) throw FileBrowserException("目标目录不存在")

            val roots = request.entries.map { entry ->
                if (entry.location.source.kind != FileSourceKind.Local) {
                    throw FileBrowserException("暂不支持压缩 MTP 文件")
                }
                File(entry.location.path).also {
                    if (!it.exists()) throw FileBrowserException("源文件不存在：" + it.path)
                }
            }
            if (request.format.singleFile && !request.separateArchives) {
                if (roots.size != 1 || !roots.first().isFile) {
                    throw FileBrowserException("该格式只能压缩单个文件")
                }
            }

            val jobs = buildJobs(roots, request)
            val totalFiles = jobs.sumOf { it.files.size }.coerceAtLeast(1)
            val totalBytes = jobs.sumOf { it.totalBytes }.coerceAtLeast(1L)
            var completedFiles = 0L
            var completedBytes = 0L
            val createdTargets = jobs.map { it.target }

            fun report(size: Long) {
                completedFiles++
                completedBytes += size
                callbacks.onProgress(completedFiles, totalFiles.toLong())
                callbacks.onBytes(completedBytes.coerceAtMost(totalBytes), totalBytes)
            }

            try {
                jobs.forEach { job ->
                    if (callbacks.isCancelled()) throw CancellationException("压缩已取消")
                    when (request.format) {
                        ArchiveFormat.Zip -> writeZip(job, request, callbacks) { report(it) }
                        ArchiveFormat.SevenZ -> writeSevenZ(job, request, callbacks) { report(it) }
                        ArchiveFormat.Tar,
                        ArchiveFormat.TarGz,
                        ArchiveFormat.TarXz,
                        ArchiveFormat.TarBz2,
                        ArchiveFormat.TarZst,
                        -> writeTar(job, request, callbacks) { report(it) }
                        ArchiveFormat.Gzip,
                        ArchiveFormat.Xz,
                        ArchiveFormat.Zstd,
                        -> writeSingleStream(job, request, callbacks) { report(it) }
                    }
                    if (request.deleteSource) {
                        job.roots.forEach { root ->
                            if (callbacks.isCancelled()) throw CancellationException("压缩已取消")
                            root.deleteRecursively()
                        }
                    }
                }
            } catch (error: Throwable) {
                createdTargets.forEach { target ->
                    target.delete()
                }
                throw error
            }
        }

    override suspend fun extract(request: ExtractRequest, callbacks: ExtractCallbacks) =
        withContext(Dispatchers.IO) {
            val archiveEntry = request.archive
            if (archiveEntry.location.source.kind != FileSourceKind.Local) {
                throw FileBrowserException("暂不支持解压 MTP 文件")
            }
            val archive = File(archiveEntry.location.path)
            if (!archive.isFile) throw FileBrowserException("压缩包不存在")
            val format = archiveFormatForName(archive.name)
                ?: throw FileBrowserException("不支持的压缩格式")
            val destination = File(request.destinationDirectory.path)
            if (!destination.isDirectory) throw FileBrowserException("目标目录不存在")
            val root = if (request.createSubfolder) {
                val name = sanitizeFolderName(
                    request.subfolderName.ifBlank { archiveBaseName(archive.name) }
                )
                uniqueDirectory(destination, name).apply { mkdirs() }
            } else {
                destination
            }
            var completedFiles = 0L
            var completedBytes = 0L
            fun report(size: Long) {
                completedFiles++
                completedBytes += size.coerceAtLeast(0)
                callbacks.onProgress(completedFiles, null)
                callbacks.onBytes(completedBytes, archive.length().coerceAtLeast(1))
            }
            try {
                when (format) {
                    ArchiveFormat.Zip -> extractZip(archive, root, request.password, callbacks, ::report)
                    ArchiveFormat.SevenZ -> extractSevenZ(archive, root, request.password, callbacks, ::report)
                    ArchiveFormat.Tar,
                    ArchiveFormat.TarGz,
                    ArchiveFormat.TarXz,
                    ArchiveFormat.TarBz2,
                    ArchiveFormat.TarZst,
                    -> extractTar(archive, root, format, callbacks, ::report)
                    ArchiveFormat.Gzip,
                    ArchiveFormat.Xz,
                    ArchiveFormat.Zstd,
                    -> extractSingleStream(archive, root, format, callbacks, ::report)
                }
                if (request.deleteSource) archive.delete()
            } catch (error: Throwable) {
                throw error
            }
        }

    override suspend fun isPasswordProtected(archive: FileEntry): Boolean =
        withContext(Dispatchers.IO) {
            if (archive.location.source.kind != FileSourceKind.Local) return@withContext false
            val file = File(archive.location.path)
            when (archiveFormatForName(file.name)) {
                ArchiveFormat.Zip -> ZipFile(file).isEncrypted
                ArchiveFormat.SevenZ -> try {
                    SevenZFile(file).use { it.nextEntry }
                    false
                } catch (_: PasswordRequiredException) {
                    true
                } catch (_: Throwable) {
                    false
                }
                else -> false
            }
        }

    private fun extractZip(
        archive: File,
        root: File,
        password: String,
        callbacks: ExtractCallbacks,
        report: (Long) -> Unit,
    ) {
        val zip = ZipFile(archive)
        if (password.isNotEmpty()) zip.setPassword(password.toCharArray())
        zip.fileHeaders.forEach { header ->
            if (callbacks.isCancelled()) throw CancellationException("解压已取消")
            val target = safeTarget(root, header.fileName)
            if (header.isDirectory) {
                target.mkdirs()
            } else {
                target.parentFile?.mkdirs()
                zip.extractFile(header, target.parentFile.absolutePath, target.name)
                report(header.uncompressedSize)
            }
        }
    }

    private fun extractSevenZ(
        archive: File,
        root: File,
        password: String,
        callbacks: ExtractCallbacks,
        report: (Long) -> Unit,
    ) {
        val passwordChars = password.takeIf { it.isNotEmpty() }?.toCharArray()
        SevenZFile(archive, passwordChars).use { seven ->
            var entry = seven.nextEntry
            while (entry != null) {
                if (callbacks.isCancelled()) throw CancellationException("解压已取消")
                val target = safeTarget(root, entry.name)
                if (entry.isDirectory) {
                    target.mkdirs()
                } else {
                    target.parentFile?.mkdirs()
                    target.outputStream().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            if (callbacks.isCancelled()) throw CancellationException("解压已取消")
                            val read = seven.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                        }
                    }
                    report(entry.size)
                }
                entry = seven.nextEntry
            }
        }
    }

    private fun extractTar(
        archive: File,
        root: File,
        format: ArchiveFormat,
        callbacks: ExtractCallbacks,
        report: (Long) -> Unit,
    ) {
        val fileInput = BufferedInputStream(FileInputStream(archive))
        val decompressed = when (format) {
            ArchiveFormat.TarGz -> GzipCompressorInputStream(fileInput)
            ArchiveFormat.TarXz -> XZCompressorInputStream(fileInput)
            ArchiveFormat.TarBz2 -> BZip2CompressorInputStream(fileInput)
            ArchiveFormat.TarZst -> ZstdInputStream(fileInput)
            else -> fileInput
        }
        TarArchiveInputStream(decompressed).use { tar ->
            var entry = tar.nextEntry
            while (entry != null) {
                if (callbacks.isCancelled()) throw CancellationException("解压已取消")
                val target = safeTarget(root, entry.name)
                if (entry.isDirectory) {
                    target.mkdirs()
                } else {
                    target.parentFile?.mkdirs()
                    target.outputStream().use { output -> tar.copyTo(output) }
                    report(entry.size)
                }
                entry = tar.nextEntry
            }
        }
    }

    private fun extractSingleStream(
        archive: File,
        root: File,
        format: ArchiveFormat,
        callbacks: ExtractCallbacks,
        report: (Long) -> Unit,
    ) {
        val baseName = when (format) {
            ArchiveFormat.Gzip -> archive.name.removeSuffix(".gz")
            ArchiveFormat.Xz -> archive.name.removeSuffix(".xz")
            ArchiveFormat.Zstd -> archive.name.removeSuffix(".zst")
            else -> archive.name
        }.ifBlank { "archive" }
        val target = safeTarget(root, baseName)
        target.parentFile?.mkdirs()
        val fileInput = BufferedInputStream(FileInputStream(archive))
        val input: InputStream = when (format) {
            ArchiveFormat.Gzip -> GzipCompressorInputStream(fileInput)
            ArchiveFormat.Xz -> XZCompressorInputStream(fileInput)
            ArchiveFormat.Zstd -> ZstdInputStream(fileInput)
            else -> fileInput
        }
        input.use { source ->
            target.outputStream().use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var total = 0L
                while (true) {
                    if (callbacks.isCancelled()) throw CancellationException("解压已取消")
                    val read = source.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    total += read
                    callbacks.onBytes(total, archive.length().coerceAtLeast(1))
                }
                report(total)
            }
        }
    }

    private fun safeTarget(root: File, entryName: String): File {
        val normalized = entryName.replace('\\', '/').trimStart('/')
        val target = File(root, normalized).canonicalFile
        val rootPath = root.canonicalFile
        if (target != rootPath && !target.path.startsWith(rootPath.path + File.separator)) {
            throw FileBrowserException("压缩包包含非法路径：" + entryName)
        }
        return target
    }

    private fun sanitizeFolderName(name: String): String =
        name.trim().replace('/', '_').replace('\\', '_').ifBlank { "archive" }

    private fun uniqueDirectory(destination: File, baseName: String): File {
        var target = File(destination, baseName)
        var index = 1
        while (target.exists()) {
            target = File(destination, baseName + " (" + index + ")")
            index++
        }
        return target
    }

    private fun writeZip(job: Job, request: CompressRequest, callbacks: CompressCallbacks, report: (Long) -> Unit) {
        val params = ZipParameters().apply {
            compressionMethod = if (request.level == CompressionLevel.Store) {
                CompressionMethod.STORE
            } else {
                CompressionMethod.DEFLATE
            }
            compressionLevel = request.level.toZipLevel()
            isIncludeRootFolder = true
            if (request.password.isNotEmpty()) {
                isEncryptFiles = true
                encryptionMethod = EncryptionMethod.AES
                aesKeyStrength = AesKeyStrength.KEY_STRENGTH_256
            }
        }
        val zip = ZipFile(job.target)
        if (request.password.isNotEmpty()) zip.setPassword(request.password.toCharArray())
        val splitBytes = request.splitSizeMb
            ?.takeIf { it > 0 && request.password.isEmpty() }
            ?.times(1024L * 1024L)
        if (splitBytes != null && job.roots.all { it.isFile }) {
            zip.createSplitZipFile(job.roots, params, true, splitBytes)
            job.files.forEach { report(it.length()) }
        } else {
            job.roots.forEach { root ->
                if (root.isFile) {
                    if (callbacks.isCancelled()) throw CancellationException("压缩已取消")
                    zip.addFile(root, params.copyWithName(root.name))
                    report(root.length())
                } else {
                    val rootPath = root.toPath()
                    root.walkTopDown().filter { it.isFile }.forEach { file ->
                        if (callbacks.isCancelled()) throw CancellationException("压缩已取消")
                        val relative = root.name + "/" +
                            rootPath.relativize(file.toPath()).toString().replace(File.separatorChar, '/')
                        zip.addFile(file, params.copyWithName(relative))
                        report(file.length())
                    }
                }
            }
        }
    }

    private fun writeSevenZ(job: Job, request: CompressRequest, callbacks: CompressCallbacks, report: (Long) -> Unit) {
        val password = request.password.takeIf { it.isNotEmpty() }?.toCharArray()
        SevenZOutputFile(job.target, password).use { output ->
            job.roots.forEach { root ->
                if (root.isFile) {
                    if (callbacks.isCancelled()) throw CancellationException("压缩已取消")
                    addSevenZEntry(output, root, root.name, report)
                } else {
                    addSevenZDirectory(output, root, root.name, callbacks, report)
                }
            }
        }
    }

    private fun addSevenZDirectory(
        output: SevenZOutputFile,
        directory: File,
        entryName: String,
        callbacks: CompressCallbacks,
        report: (Long) -> Unit,
    ) {
        val entry = SevenZArchiveEntry().apply {
            name = entryName + "/"
            isDirectory = true
            lastModifiedDate = Date(directory.lastModified())
        }
        output.putArchiveEntry(entry)
        output.closeArchiveEntry()
        directory.listFiles()?.sortedBy { it.name }?.forEach { child ->
            if (callbacks.isCancelled()) throw CancellationException("压缩已取消")
            if (child.isDirectory) {
                addSevenZDirectory(output, child, entryName + "/" + child.name, callbacks, report)
            } else {
                addSevenZEntry(output, child, entryName + "/" + child.name, report)
            }
        }
    }

    private fun addSevenZEntry(
        output: SevenZOutputFile,
        file: File,
        entryName: String,
        report: (Long) -> Unit,
    ) {
        val entry = SevenZArchiveEntry().apply {
            name = entryName
            size = file.length()
            lastModifiedDate = Date(file.lastModified())
        }
        output.putArchiveEntry(entry)
        file.inputStream().use { input -> output.write(input) }
        output.closeArchiveEntry()
        report(file.length())
    }

    private fun writeTar(job: Job, request: CompressRequest, callbacks: CompressCallbacks, report: (Long) -> Unit) {
        val fileOutput = BufferedOutputStream(FileOutputStream(job.target))
        val compressor: OutputStream = when (request.format) {
            ArchiveFormat.Tar -> fileOutput
            ArchiveFormat.TarGz -> GzipCompressorOutputStream(
                fileOutput,
                GzipParameters().apply { compressionLevel = request.level.zipLevel.coerceIn(1, 9) },
            )
            ArchiveFormat.TarXz -> XZCompressorOutputStream(fileOutput, request.level.zipLevel)
            ArchiveFormat.TarBz2 -> BZip2CompressorOutputStream(fileOutput)
            ArchiveFormat.TarZst -> ZstdOutputStream(fileOutput, request.level.zipLevel)
            else -> fileOutput
        }
        TarArchiveOutputStream(compressor).use { tar ->
            tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
            tar.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX)
            job.roots.forEach { root ->
                if (root.isFile) {
                    if (callbacks.isCancelled()) throw CancellationException("压缩已取消")
                    addTarEntry(tar, root, root.name, report)
                } else {
                    addTarDirectory(tar, root, root.name, callbacks, report)
                }
            }
        }
    }

    private fun addTarDirectory(
        tar: TarArchiveOutputStream,
        directory: File,
        entryName: String,
        callbacks: CompressCallbacks,
        report: (Long) -> Unit,
    ) {
        val dirEntry = TarArchiveEntry(entryName + "/").apply {
            mode = 0b111101101
            modTime = Date(directory.lastModified())
        }
        tar.putArchiveEntry(dirEntry)
        tar.closeArchiveEntry()
        directory.listFiles()?.sortedBy { it.name }?.forEach { child ->
            if (callbacks.isCancelled()) throw CancellationException("压缩已取消")
            if (child.isDirectory) {
                addTarDirectory(tar, child, entryName + "/" + child.name, callbacks, report)
            } else {
                addTarEntry(tar, child, entryName + "/" + child.name, report)
            }
        }
    }

    private fun addTarEntry(
        tar: TarArchiveOutputStream,
        file: File,
        entryName: String,
        report: (Long) -> Unit,
    ) {
        val entry = TarArchiveEntry(file, entryName).apply {
            size = file.length()
            modTime = Date(file.lastModified())
        }
        tar.putArchiveEntry(entry)
        file.inputStream().use { input -> input.copyTo(tar) }
        tar.closeArchiveEntry()
        report(file.length())
    }

    private fun writeSingleStream(job: Job, request: CompressRequest, callbacks: CompressCallbacks, report: (Long) -> Unit) {
        val root = job.roots.firstOrNull() ?: return
        if (!root.isFile) throw FileBrowserException("该格式只能压缩单个文件")
        val fileOutput = BufferedOutputStream(FileOutputStream(job.target))
        val output: OutputStream = when (request.format) {
            ArchiveFormat.Gzip -> GzipCompressorOutputStream(
                fileOutput,
                GzipParameters().apply {
                    compressionLevel = request.level.zipLevel.coerceIn(1, 9)
                    fileName = root.name
                },
            )
            ArchiveFormat.Xz -> XZCompressorOutputStream(fileOutput, request.level.zipLevel)
            ArchiveFormat.Zstd -> ZstdOutputStream(fileOutput, request.level.zipLevel)
            else -> fileOutput
        }
        output.use {
            if (callbacks.isCancelled()) throw CancellationException("压缩已取消")
            root.inputStream().use { input -> input.copyTo(output) }
        }
        report(root.length())
    }

    private fun buildJobs(roots: List<File>, request: CompressRequest): List<Job> {
        val destination = File(request.destination.path)
        val baseName = cleanBaseName(request.archiveName, request.format)
        val selectedRoots = if (request.separateArchives) {
            roots.map { root -> listOf(root) }
        } else {
            listOf(roots)
        }
        return selectedRoots.map { jobRoots ->
            val name = if (request.separateArchives) {
                val root = jobRoots.first()
                if (root.isDirectory) root.name else root.nameWithoutExtension.ifBlank { root.name }
            } else {
                baseName
            }
            val target = uniqueTarget(destination, name, request.format.extension)
            val files = jobRoots.flatMap { collectFiles(it) }
            Job(jobRoots, target, files, files.sumOf { it.length() })
        }
    }

    private fun cleanBaseName(name: String, format: ArchiveFormat): String {
        var base = name.trim().ifEmpty { "archive" }
        ArchiveFormat.entries
            .sortedByDescending { it.extension.length }
            .forEach { candidate ->
                base = base.removeSuffix("." + candidate.extension)
            }
        return base.replace('/', '_').replace('\\', '_').ifBlank { "archive" }
    }

    private fun uniqueTarget(destination: File, baseName: String, extension: String): File {
        var target = File(destination, baseName + "." + extension)
        var index = 1
        while (target.exists()) {
            target = File(destination, baseName + " (" + index + ")." + extension)
            index++
        }
        return target
    }

    private fun collectFiles(root: File): List<File> =
        if (root.isFile) listOf(root)
        else root.walkTopDown().filter { it.isFile }.toList()

    private fun ZipParameters.copyWithName(name: String): ZipParameters =
        ZipParameters(this).apply { fileNameInZip = name }

    private fun CompressionLevel.toZipLevel(): ZipCompressionLevel = when (this) {
        CompressionLevel.Store -> ZipCompressionLevel.NO_COMPRESSION
        CompressionLevel.Fastest -> ZipCompressionLevel.FASTEST
        CompressionLevel.Fast -> ZipCompressionLevel.FAST
        CompressionLevel.Normal -> ZipCompressionLevel.NORMAL
        CompressionLevel.Maximum -> ZipCompressionLevel.MAXIMUM
        CompressionLevel.Ultra -> ZipCompressionLevel.ULTRA
    }

    private data class Job(
        val roots: List<File>,
        val target: File,
        val files: List<File>,
        val totalBytes: Long,
    )
}
