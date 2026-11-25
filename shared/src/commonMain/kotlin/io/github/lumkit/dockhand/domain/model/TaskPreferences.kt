package io.github.lumkit.dockhand.domain.model

/** 耗时任务弹窗中可复用的选择项。 */
data class TaskPreferences(
    val background: Boolean = false,
    val compressSeparateArchives: Boolean = false,
    val compressDeleteSource: Boolean = false,
    val compressToOtherPane: Boolean = false,
)
