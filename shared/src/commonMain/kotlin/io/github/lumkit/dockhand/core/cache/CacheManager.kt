package io.github.lumkit.dockhand.core.cache

/** 可清理的本地缓存分类。 */
data class CacheBreakdown(
    val preferencesBytes: Long = 0,
    val databaseBytes: Long = 0,
    val thumbnailBytes: Long = 0,
) {
    val totalBytes: Long get() = preferencesBytes + databaseBytes + thumbnailBytes
}

interface CacheManager {
    suspend fun breakdown(): CacheBreakdown

    /** 标记清理；真正删除推迟到窗口关闭。 */
    fun markClearPending()

    /** 窗口关闭时调用；没有待处理请求时不做任何事。 */
    fun clearIfPending()
}

object NoopCacheManager : CacheManager {
    override suspend fun breakdown(): CacheBreakdown = CacheBreakdown()

    override fun markClearPending() = Unit

    override fun clearIfPending() = Unit
}
