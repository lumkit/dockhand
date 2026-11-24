package io.github.dockhand.data

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import io.github.dockhand.core.platform.cleanupTaskTempDirectories
import io.github.dockhand.core.platform.defaultAppDataDirectory
import io.github.dockhand.data.database.createDatabase
import io.github.dockhand.data.datastore.SettingsRepository
import io.github.dockhand.data.datastore.createSettingsDataStore
import io.github.dockhand.data.file.LocalFileSystemProvider
import io.github.dockhand.data.file.MtpFileSystemProvider
import io.github.dockhand.data.repository.NoteRepository
import io.github.dockhand.domain.repository.DefaultFileBrowserRepository
import io.github.dockhand.task.MacTaskNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File

@Composable
actual fun rememberAppContainer(): AppContainer {
    val container = remember { createAppContainer() }
    DisposableEffect(container) {
        onDispose { container.close() }
    }
    return container
}

private fun createAppContainer(): AppContainer {
    cleanupTaskTempDirectories()
    val dataDir = defaultAppDataDirectory().apply { mkdirs() }
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val dataStore = createSettingsDataStore(File(dataDir, "settings.preferences_pb"), scope)
    val database = createDatabase(File(dataDir, "dockhand.db"))

    return AppContainer(
        settings = SettingsRepository(dataStore),
        notes = NoteRepository(database.noteDao()),
        files = DefaultFileBrowserRepository(
            providers = listOf(
                LocalFileSystemProvider(),
                MtpFileSystemProvider(),
            )
        ),
        taskNotifier = MacTaskNotifier(),
        onClose = { database.close() },
    )
}