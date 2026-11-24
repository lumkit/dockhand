package io.github.lumkit.dockhand.core.thumbnail

import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.key.Keyer
import coil3.request.Options
import coil3.size.pxOrElse
import io.github.lumkit.dockhand.core.platform.defaultAppDataDirectory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path.Companion.toPath
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

actual fun ImageLoader.Builder.installVideoThumbnailSupport() {
    components {
        add(VideoThumbnailFetcher.Factory())
        add(VideoThumbnailKeyer())
    }
}

internal class VideoThumbnailFetcher(
    private val request: VideoThumbnailRequest,
    private val requestedWidth: Int,
) : Fetcher {
    override suspend fun fetch(): FetchResult? {
        val cacheFile = ensureThumbnail()
        return SourceFetchResult(
            source = ImageSource(
                file = cacheFile.absolutePath.toPath(),
                fileSystem = FileSystem.SYSTEM,
                diskCacheKey = cacheFile.name,
            ),
            mimeType = "image/png",
            dataSource = DataSource.DISK,
        )
    }

    private suspend fun ensureThumbnail(): File = withContext(Dispatchers.IO) {
        val source = File(request.path)
        if (!source.isFile) error("视频文件不存在")
        val maxSize = requestedWidth.coerceIn(64, 256)
        val key = cacheKey(source, request.frameMillis, maxSize)
        val cacheDirectory = File(defaultAppDataDirectory(), "video-thumbnails").apply { mkdirs() }
        val target = File(cacheDirectory, key + ".png")
        if (target.isFile && target.length() > 0) return@withContext target

        val lock = locks.getOrPut(key) { Mutex() }
        try {
            lock.withLock {
                if (target.isFile && target.length() > 0) return@withLock
                val helper = extractHelper()
                val process = ProcessBuilder(
                    helper.absolutePath,
                    source.absolutePath,
                    target.absolutePath,
                    request.frameMillis.toString(),
                    maxSize.toString(),
                ).redirectErrorStream(true).start()
                if (!process.waitFor(15, TimeUnit.SECONDS)) {
                    process.destroyForcibly()
                    target.delete()
                    error("视频缩略图生成超时")
                }
                val output = process.inputStream.bufferedReader().use { it.readText() }
                val exit = process.exitValue()
                if (exit != 0 || !target.isFile || target.length() == 0L) {
                    target.delete()
                    error(output.trim().ifBlank { "视频缩略图生成失败，退出码 " + exit })
                }
            }
        } finally {
            locks.remove(key, lock)
        }
        target
    }

    private fun extractHelper(): File {
        val directory = File(defaultAppDataDirectory(), "native").apply { mkdirs() }
        val helper = File(directory, "video-thumbnailer")
        if (helper.isFile && helper.length() > 0 && helper.canExecute()) return helper
        val input = javaClass.getResourceAsStream("/native/video-thumbnailer")
            ?: error("缺少视频缩略图组件")
        val temp = File(directory, "video-thumbnailer.tmp")
        input.use { stream ->
            temp.outputStream().use { output -> stream.copyTo(output) }
        }
        temp.setExecutable(true, true)
        if (helper.exists()) helper.delete()
        if (!temp.renameTo(helper)) {
            temp.copyTo(helper, overwrite = true)
            temp.delete()
            helper.setExecutable(true, true)
        }
        return helper
    }

    private fun cacheKey(source: File, frameMillis: Long, maxSize: Int): String {
        val raw = source.absolutePath + "|" + source.length() + "|" +
            source.lastModified() + "|" + frameMillis + "|" + maxSize
        return MessageDigest.getInstance("SHA-256")
            .digest(raw.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    class Factory : Fetcher.Factory<VideoThumbnailRequest> {
        override fun create(
            data: VideoThumbnailRequest,
            options: Options,
            imageLoader: ImageLoader,
        ): Fetcher = VideoThumbnailFetcher(
            data,
            options.size.width.pxOrElse { 64 },
        )
    }

    companion object {
        private val locks = ConcurrentHashMap<String, Mutex>()
    }
}

internal class VideoThumbnailKeyer : Keyer<VideoThumbnailRequest> {
    override fun key(data: VideoThumbnailRequest, options: Options): String =
        data.path + "|" + data.frameMillis + "|" +
            options.size.width.pxOrElse { 64 }
}
