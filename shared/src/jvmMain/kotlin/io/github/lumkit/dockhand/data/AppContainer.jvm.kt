package io.github.lumkit.dockhand.data

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import io.github.lumkit.dockhand.core.archive.LocalArchiveService
import io.github.lumkit.dockhand.core.cache.LocalCacheManager
import io.github.lumkit.dockhand.core.platform.DesktopFileOpener
import io.github.lumkit.dockhand.core.platform.MacTerminalLauncher
import io.github.lumkit.dockhand.core.platform.cleanupTaskTempDirectories
import io.github.lumkit.dockhand.core.platform.defaultAppDataDirectory
import io.github.lumkit.dockhand.data.database.createDatabase
import io.github.lumkit.dockhand.data.datastore.SettingsRepository
import io.github.lumkit.dockhand.data.datastore.createSettingsDataStore
import io.github.lumkit.dockhand.data.file.LocalFileSystemProvider
import io.github.lumkit.dockhand.data.file.MtpFileSystemProvider
import io.github.lumkit.dockhand.data.repository.TrashHistoryRepository
import io.github.lumkit.dockhand.data.update.GithubUpdateRepository
import io.github.lumkit.dockhand.domain.repository.DefaultFileBrowserRepository
import io.github.lumkit.dockhand.task.MacTaskNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.io.File

@Composable
actual fun rememberAppContainer(): AppContainer {
    val container = remember { createAppContainer() }
    DisposableEffect(container) {
        onDispose { container.close() }
    }
    return container
}

fun createAppContainer(): AppContainer {
    cleanupTaskTempDirectories()
    val dataDir = defaultAppDataDirectory().apply { mkdirs() }
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val dataStore = createSettingsDataStore(File(dataDir, "settings.preferences_pb"), scope)
    val database = createDatabase(File(dataDir, "dockhand.db"))
    val trashHistory = TrashHistoryRepository(database.trashHistoryDao())

    return AppContainer(
        settings = SettingsRepository(dataStore),
        files = DefaultFileBrowserRepository(
            providers = listOf(
                LocalFileSystemProvider(),
                MtpFileSystemProvider(),
            ),
            trashHistory = trashHistory,
        ),
        trashHistory = trashHistory,
        terminalLauncher = MacTerminalLauncher(),
        taskNotifier = MacTaskNotifier(),
        cacheManager = LocalCacheManager(dataDir),
        archiveService = LocalArchiveService(),
        fileOpener = DesktopFileOpener(),
        updates = GithubUpdateRepository(),
        onClose = {
            scope.cancel()
            database.close()
        },
    )
}