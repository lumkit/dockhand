package io.github.lumkit.dockhand.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import dockhand.shared.generated.resources.Res
import dockhand.shared.generated.resources.action_cancel
import dockhand.shared.generated.resources.action_confirm
import dockhand.shared.generated.resources.appearance_section
import dockhand.shared.generated.resources.cache_clear_dialog_summary
import dockhand.shared.generated.resources.cache_clear_dialog_title
import dockhand.shared.generated.resources.settings_clear_cache
import dockhand.shared.generated.resources.settings_clear_cache_summary
import dockhand.shared.generated.resources.interface_scale
import dockhand.shared.generated.resources.interface_scale_range
import dockhand.shared.generated.resources.interface_scale_summary
import dockhand.shared.generated.resources.main_color
import dockhand.shared.generated.resources.theme_dark
import dockhand.shared.generated.resources.theme_light
import dockhand.shared.generated.resources.theme_mode
import dockhand.shared.generated.resources.theme_mode_summary
import dockhand.shared.generated.resources.theme_monet_dark
import dockhand.shared.generated.resources.theme_monet_light
import dockhand.shared.generated.resources.theme_monet_system
import dockhand.shared.generated.resources.theme_system
import dockhand.shared.generated.resources.startup_last_path
import dockhand.shared.generated.resources.startup_left_path
import dockhand.shared.generated.resources.startup_right_path
import dockhand.shared.generated.resources.settings_restore_window_size
import dockhand.shared.generated.resources.settings_restore_window_size_summary
import dockhand.shared.generated.resources.settings_show_hidden_items
import dockhand.shared.generated.resources.settings_show_hidden_items_summary
import dockhand.shared.generated.resources.framework_section
import dockhand.shared.generated.resources.shortcut_settings
import dockhand.shared.generated.resources.shortcut_settings_summary
import dockhand.shared.generated.resources.startup_section
import dockhand.shared.generated.resources.text_desktop
import dockhand.shared.generated.resources.text_documents
import dockhand.shared.generated.resources.text_downloads
import dockhand.shared.generated.resources.text_movies
import dockhand.shared.generated.resources.text_music
import dockhand.shared.generated.resources.text_trash
import dockhand.shared.generated.resources.text_pictures
import dockhand.shared.generated.resources.text_settings
import io.github.lumkit.dockhand.core.cache.CacheBreakdown
import io.github.lumkit.dockhand.domain.model.FilePlace
import io.github.lumkit.dockhand.domain.model.FilePlaceGroup
import io.github.lumkit.dockhand.domain.model.FilePlaceKind
import io.github.lumkit.dockhand.shortcut.ShortcutAction
import io.github.lumkit.dockhand.shortcut.ShortcutKey
import io.github.lumkit.dockhand.ui.screen.home.STARTUP_PATH_LAST
import io.github.lumkit.dockhand.ui.screen.home.formatFileSize
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.ColorPalette
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SliderDefaults
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.menu.OverlayDropdownMenu
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.basic.verticalScrollBar
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SliderPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

/**
 * 设置底部弹窗。
 */
@OptIn(ExperimentalScrollBarApi::class)
@Composable
fun SettingsBottomSheet(
    show: Boolean,
    onDismissRequest: () -> Unit,
    places: List<FilePlace>,
    startupLeftPath: String,
    startupRightPath: String,
    onStartupLeftPathChange: (String) -> Unit,
    onStartupRightPathChange: (String) -> Unit,
    colorSchemeMode: ColorSchemeMode,
    onColorSchemeModeChange: (ColorSchemeMode) -> Unit,
    keyColor: Color,
    onKeyColorChange: (Color) -> Unit,
    interfaceScale: Int,
    onInterfaceScaleChange: (Int) -> Unit,
    showHiddenItems: Boolean,
    onShowHiddenItemsChange: (Boolean) -> Unit,
    shortcutBindings: Map<ShortcutAction, List<ShortcutKey>>,
    onShortcutAdd: (ShortcutAction, ShortcutKey) -> Unit,
    onShortcutRemove: (ShortcutAction, ShortcutKey) -> Unit,
    onResetShortcuts: () -> Unit,
    restoreWindowSize: Boolean,
    onRestoreWindowSizeChange: (Boolean) -> Unit,
    cacheBreakdown: CacheBreakdown,
    onClearCache: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val localPlaces = remember(places) {
        places.filter { it.group == FilePlaceGroup.Local }
    }
    var scale by remember(interfaceScale) { mutableFloatStateOf(interfaceScale / 100f) }
    LaunchedEffect(interfaceScale) { scale = interfaceScale / 100f }
    var showScaleDialog by remember { mutableStateOf(false) }
    val scaleInput = rememberTextFieldState()
    var showColorDialog by remember { mutableStateOf(false) }
    var showShortcutSheet by remember { mutableStateOf(false) }
    var showCacheDialog by remember { mutableStateOf(false) }
    var dialogColor by remember { mutableStateOf(keyColor) }
    val themeModes = ColorSchemeMode.entries
    val scrollState = rememberScrollState()
    OverlayBottomSheet(
        show = show,
        modifier = modifier,
        title = stringResource(Res.string.text_settings),
        onDismissRequest = onDismissRequest,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .verticalScrollBar(scrollState)
                .padding(16.dp),
        ) {
            SmallTitle(stringResource(Res.string.appearance_section))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.defaultColors(
                    color = MiuixTheme.colorScheme.secondaryContainer,
                ),
            ) {
                OverlayDropdownPreference(
                    items = themeModes.map { themeModeLabel(it) },
                    selectedIndex = colorSchemeMode.ordinal,
                    title = stringResource(Res.string.theme_mode),
                    summary = stringResource(Res.string.theme_mode_summary),
                    onSelectedIndexChange = { onColorSchemeModeChange(themeModes[it]) },
                )
                AnimatedVisibility(visible = colorSchemeMode >= ColorSchemeMode.MonetSystem) {
                    ArrowPreference(
                        title = stringResource(Res.string.main_color),
                        summary = keyColor.toHexString(),
                        endActions = {
                            Surface(
                                modifier = Modifier.size(32.dp)
                                    .squircleClip(12.dp),
                                color = keyColor,
                            ) {}
                        },
                        onClick = {
                            dialogColor = keyColor
                            showColorDialog = true
                        },
                    )
                }

                SliderPreference(
                    value = scale,
                    onValueChange = { scale = it },
                    onValueChangeFinished = {
                        onInterfaceScaleChange((scale * 100).roundToInt())
                    },
                    title = stringResource(Res.string.interface_scale),
                    summary = stringResource(Res.string.interface_scale_summary),
                    valueRange = 0.8f..1.1f,
                    showKeyPoints = true,
                    keyPoints = listOf(0.8f, 0.9f, 1.0f, 1.1f),
                    endActions = {
                        Text(text = "${(scale * 100).roundToInt()}%")
                    },
                    hapticEffect = SliderDefaults.SliderHapticEffect.Step,
                    onClick = {
                        scaleInput.setTextAndPlaceCursorAtEnd(
                            (scale * 100).roundToInt().toString()
                        )
                        showScaleDialog = true
                    },
                )
            }

            Spacer(Modifier.height(16.dp))
            SmallTitle(stringResource(Res.string.framework_section))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.defaultColors(
                    color = MiuixTheme.colorScheme.secondaryContainer,
                ),
            ) {
                SwitchPreference(
                    checked = showHiddenItems,
                    onCheckedChange = onShowHiddenItemsChange,
                    title = stringResource(Res.string.settings_show_hidden_items),
                    summary = stringResource(Res.string.settings_show_hidden_items_summary),
                    modifier = Modifier.fillMaxWidth(),
                )
                ArrowPreference(
                    title = stringResource(Res.string.shortcut_settings),
                    summary = stringResource(Res.string.shortcut_settings_summary),
                    onClick = { showShortcutSheet = true },
                )
                ArrowPreference(
                    title = stringResource(Res.string.settings_clear_cache),
                    summary = stringResource(
                        Res.string.settings_clear_cache_summary,
                        formatFileSize(cacheBreakdown.totalBytes),
                    ),
                    onClick = { showCacheDialog = true },
                )
            }

            Spacer(Modifier.height(16.dp))
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

    if (showColorDialog) {
        OverlayDialog(
            show = true,
            title = stringResource(Res.string.main_color),
            summary = dialogColor.toHexString(),
            onDismissRequest = { showColorDialog = false },
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ColorPalette(
                    color = dialogColor,
                    onColorChanged = { dialogColor = it },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        onClick = {
                            onKeyColorChange(dialogColor)
                            showColorDialog = false
                        },
                        colors = ButtonDefaults.buttonColorsPrimary(),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(text = stringResource(Res.string.action_confirm))
                    }
                    Button(
                        onClick = { showColorDialog = false },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(text = stringResource(Res.string.action_cancel))
                    }
                }
            }
        }
    }

    if (showCacheDialog) {
        OverlayDialog(
            show = true,
            title = stringResource(Res.string.cache_clear_dialog_title),
            summary = stringResource(
                Res.string.cache_clear_dialog_summary,
                formatFileSize(cacheBreakdown.preferencesBytes),
                formatFileSize(cacheBreakdown.databaseBytes),
                formatFileSize(cacheBreakdown.thumbnailBytes),
            ),
            onDismissRequest = { showCacheDialog = false },
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = {
                        onClearCache()
                        showCacheDialog = false
                    },
                    colors = ButtonDefaults.buttonColorsPrimary(),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(text = stringResource(Res.string.action_confirm))
                }
                Button(
                    onClick = { showCacheDialog = false },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(text = stringResource(Res.string.action_cancel))
                }
            }
        }
    }

    ShortcutSettingsBottomSheet(
        show = showShortcutSheet,
        bindings = shortcutBindings,
        onDismissRequest = { showShortcutSheet = false },
        onShortcutAdd = onShortcutAdd,
        onShortcutRemove = onShortcutRemove,
        onResetShortcuts = onResetShortcuts,
    )

    if (showScaleDialog) {
        OverlayDialog(
            show = true,
            title = stringResource(Res.string.interface_scale),
            summary = stringResource(Res.string.interface_scale_range),
            onDismissRequest = { showScaleDialog = false },
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextField(
                    state = scaleInput,
                    modifier = Modifier.fillMaxWidth(),
                    lineLimits = TextFieldLineLimits.SingleLine,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        onClick = {
                            val percent = scaleInput.text.toString().toIntOrNull()
                                ?: (scale * 100).roundToInt()
                            val safeScale = (percent / 100f).coerceIn(0.8f, 1.1f)
                            scale = safeScale
                            onInterfaceScaleChange((safeScale * 100).roundToInt())
                            showScaleDialog = false
                        },
                        colors = ButtonDefaults.buttonColorsPrimary(),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(text = stringResource(Res.string.action_confirm))
                    }
                    Button(
                        onClick = { showScaleDialog = false },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(text = stringResource(Res.string.action_cancel))
                    }
                }
            }
        }
    }
}

private fun Color.toHexString(): String {
    val rgb = toArgb() and 0xFFFFFF
    return "#" + rgb.toString(16).uppercase().padStart(6, '0')
}

@Composable
private fun themeModeLabel(mode: ColorSchemeMode): String = when (mode) {
    ColorSchemeMode.System -> stringResource(Res.string.theme_system)
    ColorSchemeMode.Light -> stringResource(Res.string.theme_light)
    ColorSchemeMode.Dark -> stringResource(Res.string.theme_dark)
    ColorSchemeMode.MonetSystem -> stringResource(Res.string.theme_monet_system)
    ColorSchemeMode.MonetLight -> stringResource(Res.string.theme_monet_light)
    ColorSchemeMode.MonetDark -> stringResource(Res.string.theme_monet_dark)
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
