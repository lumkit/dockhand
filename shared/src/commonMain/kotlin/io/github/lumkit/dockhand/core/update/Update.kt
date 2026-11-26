package io.github.lumkit.dockhand.core.update

/** GitHub Release 中与更新提示相关的字段。 */
data class AppUpdate(
    /** 去掉前导 v 的版本号，用于比较。 */
    val version: String,
    /** 原始 tag，例如 v0.2.0。 */
    val tagName: String,
    /** Release 标题；可能为空。 */
    val name: String?,
    /** Release 正文，可能为空。 */
    val body: String,
    /** Release 页面地址。 */
    val pageUrl: String,
    /** 优先指向 macOS 安装包，找不到时回退 Release 页面。 */
    val downloadUrl: String,
)

/** 检测更新；失败时返回 null，不抛出异常。 */
interface UpdateRepository {
    suspend fun latestUpdate(): AppUpdate?
}

object NoopUpdateRepository : UpdateRepository {
    override suspend fun latestUpdate(): AppUpdate? = null
}

/**
 * 比较版本号，忽略前导 v，支持 x.y.z 和 -prerelease。
 *
 * 返回负数表示 left 更旧，0 表示相同，正数表示 left 更新；无法解析时按相同处理。
 * 常见结果：0.1.0 > 0.1.0-beta.1；0.2.0 > 0.1.9；1.0 == 1.0.0。
 */
fun compareVersions(left: String, right: String): Int {
    val parsedLeft = parseVersion(left) ?: return 0
    val parsedRight = parseVersion(right) ?: return 0
    val coreSize = maxOf(parsedLeft.core.size, parsedRight.core.size)
    for (index in 0 until coreSize) {
        val leftPart = parsedLeft.core.getOrElse(index) { 0 }
        val rightPart = parsedRight.core.getOrElse(index) { 0 }
        if (leftPart != rightPart) return leftPart.compareTo(rightPart)
    }
    val leftPre = parsedLeft.prerelease
    val rightPre = parsedRight.prerelease
    return when {
        leftPre == null && rightPre == null -> 0
        leftPre == null -> 1
        rightPre == null -> -1
        else -> comparePrerelease(leftPre, rightPre)
    }
}

private data class ParsedVersion(
    val core: List<Int>,
    val prerelease: List<String>?,
)

private fun parseVersion(raw: String): ParsedVersion? {
    val trimmed = raw.trim()
        .removePrefix("v")
        .removePrefix("V")
        .substringBefore('+')
    if (trimmed.isEmpty()) return null
    val corePart = trimmed.substringBefore('-')
    val prereleasePart = trimmed.substringAfter('-', missingDelimiterValue = "")
    val core = corePart.split('.').map { part -> part.toIntOrNull() ?: return null }
    if (core.isEmpty()) return null
    val prerelease = if (prereleasePart.isEmpty()) null else prereleasePart.split('.')
    return ParsedVersion(core = core, prerelease = prerelease)
}

private fun comparePrerelease(left: List<String>, right: List<String>): Int {
    val size = maxOf(left.size, right.size)
    for (index in 0 until size) {
        val leftPart = left.getOrNull(index) ?: return -1
        val rightPart = right.getOrNull(index) ?: return 1
        val leftNumber = leftPart.toIntOrNull()
        val rightNumber = rightPart.toIntOrNull()
        val result = when {
            leftNumber != null && rightNumber != null -> leftNumber.compareTo(rightNumber)
            leftNumber != null -> -1
            rightNumber != null -> 1
            else -> leftPart.compareTo(rightPart)
        }
        if (result != 0) return result
    }
    return 0
}
