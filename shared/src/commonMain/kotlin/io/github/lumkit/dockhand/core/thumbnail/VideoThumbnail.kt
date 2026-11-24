package io.github.lumkit.dockhand.core.thumbnail

import androidx.compose.runtime.Immutable
import coil3.ImageLoader

/** Coil 自定义视频缩略图请求。 */
@Immutable
data class VideoThumbnailRequest(
    val path: String,
    val frameMillis: Long = 1_000L,
)

/** 平台实现负责向 Coil 注册视频缩略图 Fetcher/Keyer。 */
expect fun ImageLoader.Builder.installVideoThumbnailSupport()
