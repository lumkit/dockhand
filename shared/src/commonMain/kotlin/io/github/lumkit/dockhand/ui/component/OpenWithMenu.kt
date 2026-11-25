package io.github.lumkit.dockhand.ui.component

import androidx.compose.runtime.staticCompositionLocalOf
import io.github.lumkit.dockhand.core.platform.ExternalApplication
import io.github.lumkit.dockhand.domain.model.FileEntry
import io.github.lumkit.dockhand.ui.screen.home.OpenWithState

val LocalOpenWithState = staticCompositionLocalOf<OpenWithState?> { null }

val LocalOpenWithSelect = staticCompositionLocalOf<(FileEntry, ExternalApplication) -> Unit> {
    { _, _ -> }
}
