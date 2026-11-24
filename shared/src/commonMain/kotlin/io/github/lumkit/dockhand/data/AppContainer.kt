package io.github.lumkit.dockhand.data

import androidx.compose.runtime.Composable
import io.github.lumkit.dockhand.data.datastore.SettingsRepository
import io.github.lumkit.dockhand.data.repository.TrashHistoryRepository
import io.github.lumkit.dockhand.domain.repository.FileBrowserRepository
import io.github.lumkit.dockhand.task.NoopTaskNotifier
import io.github.lumkit.dockhand.task.TaskNotifier

/**
 * 手动依赖容器：DataStore / Room / Repository 都在这里组装。
 * 只有这一个容器，暂时不需要 DI 框架。
 */
class AppContainer(
    val settings: SettingsRepository,
    val files: FileBrowserRepository,
    val trashHistory: TrashHistoryRepository? = null,
    val taskNotifier: TaskNotifier = NoopTaskNotifier,
    private val onClose: () -> Unit = {},
) {
    fun close() = onClose()
}

/**
 * 组装容器。实现放在各平台的 source set（JVM 上是 jvmMain），
 * 因为 DataStore 的文件路径和 SQLite driver 都是平台相关的。
 */
@Composable
expect fun rememberAppContainer(): AppContainer
