package io.github.lumkit.dockhand.core.archive

import io.github.lumkit.dockhand.domain.model.FileBrowserException
import io.github.lumkit.dockhand.domain.model.FileSourceKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.AesKeyStrength
import net.lingala.zip4j.model.enums.CompressionLevel as ZipCompressionLevel
import net.lingala.zip4j.model.enums.CompressionMethod
import net.lingala.zip4j.model.enums.EncryptionMethod
import java.io.File

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

            val jobs = buildJobs(roots, request)
            val totalFiles = jobs.sumOf { it.files.size }.coerceAtLeast(1)
            var completedFiles = 0L
            var completedBytes = 0L
            val totalBytes = jobs.sumOf { it.totalBytes }.coerceAtLeast(1L)

            try {
                jobs.forEach { job ->
                    if (callbacks.isCancelled()) throw kotlinx.coroutines.CancellationException("压缩已取消")
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
                    if (job.files.isNotEmpty()) {
                        val splitBytes = request.splitSizeMb
                            ?.takeIf { it > 0 && request.password.isEmpty() && job.roots.size == 1 }
                            ?.times(1024L * 1024L)
                        if (splitBytes != null && job.roots.all { it.isFile }) {
                            zip.createSplitZipFile(job.roots, params, true, splitBytes)
                            completedFiles += job.files.size
                            completedBytes += job.totalBytes
                            callbacks.onProgress(completedFiles, totalFiles.toLong())
                            callbacks.onBytes(completedBytes.coerceAtMost(totalBytes), totalBytes)
                        } else {
                            job.roots.forEach { root ->
                                if (root.isFile) {
                                    if (callbacks.isCancelled()) throw kotlinx.coroutines.CancellationException("压缩已取消")
                                    zip.addFile(root, params.copyWithName(root.name))
                                    completedFiles++
                                    completedBytes += root.length()
                                    callbacks.onProgress(completedFiles, totalFiles.toLong())
                                    callbacks.onBytes(completedBytes.coerceAtMost(totalBytes), totalBytes)
                                } else {
                                    val rootPath = root.toPath()
                                    root.walkTopDown().filter { it.isFile }.forEach { file ->
                                        if (callbacks.isCancelled()) throw kotlinx.coroutines.CancellationException("压缩已取消")
                                        val relative = root.name + "/" +
                                            rootPath.relativize(file.toPath()).toString()
                                                .replace(File.separatorChar, '/')
                                        zip.addFile(file, params.copyWithName(relative))
                                        completedFiles++
                                        completedBytes += file.length()
                                        callbacks.onProgress(completedFiles, totalFiles.toLong())
                                        callbacks.onBytes(completedBytes.coerceAtMost(totalBytes), totalBytes)
                                    }
                                }
                            }
                        }
                    } else {
                        job.target.delete()
                    }
                    if (request.deleteSource) {
                        job.roots.forEach { root ->
                            if (callbacks.isCancelled()) throw kotlinx.coroutines.CancellationException("压缩已取消")
                            root.deleteRecursively()
                        }
                    }
                }
            } catch (error: Throwable) {
                jobs.forEach { job -> job.target.delete() }
                throw error
            }
        }

    private fun buildJobs(roots: List<File>, request: CompressRequest): List<Job> {
        val baseName = request.archiveName.trim().ifEmpty { "archive" }
            .removeSuffix(".zip")
            .replace('/', '_')
            .replace('\\', '_')
            .ifBlank { "archive" }
        val destination = File(request.destination.path)
        val selectedRoots = if (request.separateArchives) {
            roots.map { root -> listOf(root) }
        } else {
            listOf(roots)
        }
        return selectedRoots.map { jobRoots ->
            val name = if (request.separateArchives) {
                val root = jobRoots.first()
                if (root.isDirectory) root.name else root.nameWithoutExtension
            } else {
                baseName
            }
            val target = uniqueTarget(destination, name)
            val files = jobRoots.flatMap { collectFiles(it) }
            Job(jobRoots, target, files, files.sumOf { it.length() })
        }
    }

    private fun ZipParameters.copyWithName(name: String): ZipParameters =
        ZipParameters(this).apply { fileNameInZip = name }

    private fun uniqueTarget(destination: File, baseName: String): File {
        var target = File(destination, baseName + ".zip")
        var index = 1
        while (target.exists()) {
            target = File(destination, baseName + " (" + index + ").zip")
            index++
        }
        return target
    }

    private fun collectFiles(root: File): List<File> =
        if (root.isFile) listOf(root)
        else root.walkTopDown().filter { it.isFile }.toList()

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
