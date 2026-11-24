package io.github.dockhand.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dockhand.shared.generated.resources.Res
import dockhand.shared.generated.resources.startup_last_path
import dockhand.shared.generated.resources.startup_left_path
import dockhand.shared.generated.resources.startup_right_path
import dockhand.shared.generated.resources.settings_restore_window_size
import dockhand.shared.generated.resources.settings_restore_window_size_summary
import dockhand.shared.generated.resources.startup_section
import dockhand.shared.generated.resources.text_desktop
import dockhand.shared.generated.resources.text_documents
import dockhand.shared.generated.resources.text_downloads
import dockhand.shared.generated.resources.text_movies
import dockhand.shared.generated.resources.text_music
import dockhand.shared.generated.resources.text_pictures
import dockhand.shared.generated.resources.text_settings
import io.github.dockhand.domain.model.FilePlace
import io.github.dockhand.domain.model.FilePlaceGroup
import io.github.dockhand.domain.model.FilePlaceKind
import io.github.dockhand.ui.screen.home.STARTUP_PATH_LAST
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.menu.OverlayDropdownMenu
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 设置底部弹窗。
 */
@Composable
fun SettingsBottomSheet(
    show: Boolean,
    onDismissRequest: () -> Unit,
    places: List<FilePlace>,
    startupLeftPath: String,
    startupRightPath: String,
    onStartupLeftPathChange: (String) -> Unit,
    onStartupRightPathChange: (String) -> Unit,
    restoreWindowSize: Boolean,
    onRestoreWindowSizeChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val localPlaces = remember(places) {
        places.filter { it.group == FilePlaceGroup.Local }
    }
    OverlayBottomSheet(
        show = show,
        modifier = modifier,
        title = stringResource(Res.string.text_settings),
        onDismissRequest = onDismissRequest,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            SmallTitle(stringResource(Res.string.startup_section))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.defaultColors(
                    color = MiuixTheme.colorScheme.secondaryContainer,
                ),
            ) {
                OverlayDropdownMenu(
                    entries = listOf(
                        DropdownEntry(
                            items = startupDropdownItems(
                                places = localPlaces,
                                selectedId = startupLeftPath,
                                onSelected = onStartupLeftPathChange,
                            )
                        )
                    ),
                    title = stringResource(Res.string.startup_left_path),
                    summary = startupPathLabel(localPlaces, startupLeftPath),
                )
                OverlayDropdownMenu(
                    entries = listOf(
                        DropdownEntry(
                            items = startupDropdownItems(
                                places = localPlaces,
                                selectedId = startupRightPath,
                                onSelected = onStartupRightPathChange,
                            )
                        )
                    ),
                    title = stringResource(Res.string.startup_right_path),
                    summary = startupPathLabel(localPlaces, startupRightPath),
                )
                SwitchPreference(
                    checked = restoreWindowSize,
                    onCheckedChange = onRestoreWindowSizeChange,
                    title = stringResource(Res.string.settings_restore_window_size),
                    summary = stringResource(Res.string.settings_restore_window_size_summary),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun startupDropdownItems(
    places: List<FilePlace>,
    selectedId: String,
    onSelected: (String) -> Unit,
): List<DropdownItem> {
    val items = mutableListOf(
        DropdownItem(
            text = stringResource(Res.string.startup_last_path),
            selected = selectedId == STARTUP_PATH_LAST,
            onClick = { onSelected(STARTUP_PATH_LAST) },
        )
    )
    places.forEach { place ->
        items += DropdownItem(
            text = place.settingsLabel(),
            selected = selectedId == place.id,
            onClick = { onSelected(place.id) },
        )
    }
    return items
}

@Composable
private fun startupPathLabel(places: List<FilePlace>, selectedId: String): String {
    if (selectedId == STARTUP_PATH_LAST) return stringResource(Res.string.startup_last_path)
    return places.firstOrNull { it.id == selectedId }?.settingsLabel()
        ?: stringResource(Res.string.startup_last_path)
}

@Composable
private fun FilePlace.settingsLabel(): String = when (kind) {
    FilePlaceKind.Desktop -> stringResource(Res.string.text_desktop)
    FilePlaceKind.Documents -> stringResource(Res.string.text_documents)
    FilePlaceKind.Downloads -> stringResource(Res.string.text_downloads)
    FilePlaceKind.Pictures -> stringResource(Res.string.text_pictures)
    FilePlaceKind.Music -> stringResource(Res.string.text_music)
    FilePlaceKind.Movies -> stringResource(Res.string.text_movies)
    FilePlaceKind.Home,
    FilePlaceKind.Volume,
    FilePlaceKind.MtpDevice,
    FilePlaceKind.MtpStorage -> label
    FilePlaceKind.MtpCamera,
    FilePlaceKind.MtpDownloads,
    FilePlaceKind.MtpMovies,
    FilePlaceKind.MtpMusic,
    FilePlaceKind.MtpPictures -> label
}
