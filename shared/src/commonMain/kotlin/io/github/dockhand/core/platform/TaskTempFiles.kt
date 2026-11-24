package io.github.dockhand.core.platform

/** 创建任务临时目录，返回绝对路径。 */
expect fun createTaskTempDirectory(prefix: String): String

/** 删除任务临时目录或文件。 */
expect fun deleteTaskTempPath(path: String)
