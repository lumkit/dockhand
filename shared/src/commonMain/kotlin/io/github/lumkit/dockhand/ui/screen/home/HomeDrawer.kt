package io.github.lumkit.dockhand.ui.screen.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import dockhand.shared.generated.resources.Res
import dockhand.shared.generated.resources.ic_camara
import dockhand.shared.generated.resources.ic_mtp
import dockhand.shared.generated.resources.ic_storage
import dockhand.shared.generated.resources.ic_user
import dockhand.shared.generated.resources.text_camera
import dockhand.shared.generated.resources.text_capacity_unknown
import dockhand.shared.generated.resources.text_desktop
import dockhand.shared.generated.resources.text_documents
import dockhand.shared.generated.resources.text_downloads
import dockhand.shared.generated.resources.text_local
import dockhand.shared.generated.resources.text_mounted_disks
import dockhand.shared.generated.resources.text_no_local_places
import dockhand.shared.generated.resources.text_movies
import dockhand.shared.generated.resources.text_mtp_devices
import dockhand.shared.generated.resources.text_music
import dockhand.shared.generated.resources.text_no_mounted_disks
import dockhand.shared.generated.resources.text_no_mtp_devices
import dockhand.shared.generated.resources.text_pictures
import dockhand.shared.generated.resources.text_settings
import dockhand.shared.generated.resources.text_storage_usage
import io.github.lumkit.dockhand.domain.model.DEFAULT_FILE_PLACE_GROUP_ORDER
import io.github.lumkit.dockhand.domain.model.DEFAULT_MTP_SHORTCUT_ORDER
import io.github.lumkit.dockhand.domain.model.FileLocation
import io.github.lumkit.dockhand.domain.model.FilePlace
import io.github.lumkit.dockhand.domain.model.FilePlaceGroup
import io.github.lumkit.dockhand.domain.model.FilePlaceKind
import io.github.lumkit.dockhand.domain.model.orderedByIds
import io.github.lumkit.dockhand.ui.component.SettingsBottomSheet
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.ExpandLess
import top.yukonga.miuix.kmp.icon.extended.ExpandMore
import top.yukonga.miuix.kmp.icon.extended.FileDownloads
import top.yukonga.miuix.kmp.icon.extended.GridView
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Layers
import top.yukonga.miuix.kmp.icon.extended.Music
import top.yukonga.miuix.kmp.icon.extended.Notes
import top.yukonga.miuix.kmp.icon.extended.Phone
import top.yukonga.miuix.kmp.icon.extended.Photos
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.icon.extended.Folder
import top.yukonga.miuix.kmp.icon.extended.Image
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 侧边导航面板。
 *
 * 数据完全来自 [FilePlace]：本地/外接磁盘入口由 LocalFileSystemProvider 扫描产生，
 * MTP 入口由 MtpFileSystemProvider 产生。这里没有写死的路径。
 */
@Composable
fun HomeDrawerContent(
    places: List<FilePlace>,
    activeLocation: FileLocation?,
    placeOrder: Map<FilePlaceGroup, List<String>>,
    mtpExpandedSources: Set<String>,
    mtpShortcutOrder: List<String>,
    onPlaceMove: (FilePlace, Int) -> Boolean,
    onMtpStorageClick: (FilePlace, Boolean) -> Unit,
    onMtpShortcutMove: (String, Int) -> Boolean,
    startupLeftPath: String,
    startupRightPath: String,
    onStartupPathChange: (PaneId, String) -> Unit,
    restoreWindowSize: Boolean,
    onRestoreWindowSizeChange: (Boolean) -> Unit,
    colorSchemeMode: ColorSchemeMode,
    keyColor: Color,
    interfaceScale: Int,
    onColorSchemeModeChange: (ColorSchemeMode) -> Unit,
    onKeyColorChange: (Color) -> Unit,
    onInterfaceScaleChange: (Int) -> Unit,
    onPlaceClick: (FilePlace) -> Unit,
    modifier: Modifier = Modifier,
) {
    var draggingPlaceId by remember { mutableStateOf<String?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    val placesByGroup = remember(places, placeOrder) {
        DEFAULT_FILE_PLACE_GROUP_ORDER.associateWith { group ->
            places.filter { it.group == group }
                .orderedByIds(placeOrder[group].orEmpty())
        }
    }
    val activePlaceId = remember(places, activeLocation) {
        if (activeLocation == null) {
            null
        } else {
            places.filter { it.matches(activeLocation) }
                .maxByOrNull { it.location.path.length }
                ?.id
        }
    }
    val shortcuts = remember(mtpShortcutOrder) { orderedMtpShortcuts(mtpShortcutOrder) }

    Column(modifier = modifier) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth()
            .weight(1f)
        ) {
            DEFAULT_FILE_PLACE_GROUP_ORDER.forEach { group ->
                item(key = "header:${group.name}") {
                    SmallTitle(
                        text = when (group) {
                            FilePlaceGroup.Local -> stringResource(Res.string.text_local)
                            FilePlaceGroup.Mounted -> stringResource(Res.string.text_mounted_disks)
                            FilePlaceGroup.Mtp -> stringResource(Res.string.text_mtp_devices)
                        },
                    )
                }

                val groupPlaces = placesByGroup[group].orEmpty()
                if (groupPlaces.isEmpty()) {
                    item(key = "empty:${group.name}") {
                        PlaceGroupHint(
                            when (group) {
                                FilePlaceGroup.Local -> stringResource(Res.string.text_no_local_places)
                                FilePlaceGroup.Mounted -> stringResource(Res.string.text_no_mounted_disks)
                                FilePlaceGroup.Mtp -> stringResource(Res.string.text_no_mtp_devices)
                            }
                        )
                    }
                } else if (group == FilePlaceGroup.Mtp) {
                    groupPlaces.forEach { storage ->
                        val expanded = storage.id in mtpExpandedSources
                        val activeShortcut = activeLocation?.let { location ->
                            MTP_SHORTCUT_SPECS.any { shortcutMatches(location, storage, it) }
                        } == true
                        val storageDragging = draggingPlaceId == storage.id
                        val storageSelected = storage.id == activePlaceId && !activeShortcut

                        item(key = "place:${storage.id}") {
                            PlaceRow(
                                place = storage,
                                selected = storageSelected,
                                expanded = expanded,
                                modifier = if (storageDragging) Modifier else Modifier.animateItem(),
                                onClick = { onMtpStorageClick(storage, storageSelected) },
                                onMove = onPlaceMove,
                                onDragStateChange = { dragging ->
                                    draggingPlaceId = if (dragging) storage.id else null
                                },
                            )
                        }

                        if (expanded) {
                            shortcuts.forEach { spec ->
                                val shortcut = spec.toPlace(storage)
                                val selected = activeLocation?.let {
                                    shortcutMatches(it, storage, spec)
                                } == true
                                val shortcutDragging = draggingPlaceId == shortcut.id

                                item(key = "shortcut:${shortcut.id}") {
                                    PlaceRow(
                                        place = shortcut,
                                        selected = selected,
                                        modifier = if (shortcutDragging) Modifier else Modifier.animateItem(),
                                        indent = true,
                                        onClick = { onPlaceClick(shortcut) },
                                        onMove = { _, direction ->
                                            onMtpShortcutMove(spec.id, direction)
                                        },
                                        onDragStateChange = { dragging ->
                                            draggingPlaceId = if (dragging) shortcut.id else null
                                        },
                                    )
                                }
                            }
                        }
                    }
                } else {
                    items(groupPlaces, key = { it.id }) { place ->
                        val isDragging = draggingPlaceId == place.id
                        PlaceRow(
                            place = place,
                            selected = place.id == activePlaceId,
                            modifier = if (isDragging) Modifier else Modifier.animateItem(),
                            onClick = { onPlaceClick(place) },
                            onMove = onPlaceMove,
                            onDragStateChange = { dragging ->
                                draggingPlaceId = if (dragging) place.id else null
                            },
                        )
                    }
                }

                item {
                    Spacer(Modifier.height(8.dp))
                }
            }
        }

        HorizontalDivider()

        Button(
            onClick = { showSettings = true },
            modifier = Modifier.fillMaxWidth()
                .padding(8.dp),
        ) {
            Icon(
                imageVector = MiuixIcons.Settings,
                contentDescription = null,
            )

            Spacer(Modifier.width(8.dp))

            Text(
                text = stringResource(Res.string.text_settings)
            )
        }
    }

    SettingsBottomSheet(
        show = showSettings,
        onDismissRequest = { showSettings = false },
        places = places,
        startupLeftPath = startupLeftPath,
        startupRightPath = startupRightPath,
        onStartupLeftPathChange = { onStartupPathChange(PaneId.Left, it) },
        onStartupRightPathChange = { onStartupPathChange(PaneId.Right, it) },
        restoreWindowSize = restoreWindowSize,
        onRestoreWindowSizeChange = onRestoreWindowSizeChange,
        colorSchemeMode = colorSchemeMode,
        keyColor = keyColor,
        interfaceScale = interfaceScale,
        onColorSchemeModeChange = onColorSchemeModeChange,
        onKeyColorChange = onKeyColorChange,
        onInterfaceScaleChange = onInterfaceScaleChange,
    )
}

private data class MtpShortcutSpec(
    val id: String,
    val kind: FilePlaceKind,
    val path: String,
)

private val MTP_SHORTCUT_SPECS = listOf(
    MtpShortcutSpec("camera", FilePlaceKind.MtpCamera, "/DCIM/Camera"),
    MtpShortcutSpec("downloads", FilePlaceKind.MtpDownloads, "/Download"),
    MtpShortcutSpec("movies", FilePlaceKind.MtpMovies, "/Movies"),
    MtpShortcutSpec("music", FilePlaceKind.MtpMusic, "/Music"),
    MtpShortcutSpec("pictures", FilePlaceKind.MtpPictures, "/Pictures"),
)

private fun orderedMtpShortcuts(order: List<String>): List<MtpShortcutSpec> {
    val rank = order.withIndex().associate { (index, id) -> id to index }
    return MTP_SHORTCUT_SPECS.withIndex()
        .sortedWith(
            compareBy(
                { rank[it.value.id] ?: Int.MAX_VALUE },
                { it.index },
            )
        )
        .map { it.value }
}

private fun MtpShortcutSpec.toPlace(storage: FilePlace): FilePlace = FilePlace(
    id = "${storage.id}:shortcut:${id}",
    label = "",
    location = FileLocation(storage.location.source, path),
    kind = kind,
    group = FilePlaceGroup.Mtp,
)

private fun shortcutMatches(
    active: FileLocation,
    storage: FilePlace,
    shortcut: MtpShortcutSpec,
): Boolean {
    if (active.source.id != storage.location.source.id) return false
    val root = shortcut.path.trimEnd('/')
    return active.path == root || active.path.startsWith("$root/")
}

@Composable
private fun PlaceGroupHint(text: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Text(
            text = text,
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

@Composable
private fun PlaceRow(
    place: FilePlace,
    selected: Boolean,
    modifier: Modifier,
    expanded: Boolean? = null,
    indent: Boolean = false,
    onClick: () -> Unit,
    onMove: (FilePlace, Int) -> Boolean,
    onDragStateChange: (Boolean) -> Unit,
) {
    val threshold = with(LocalDensity.current) { 28.dp.toPx() }
    var dragOffset by remember(place.id) { mutableFloatStateOf(0f) }
    var rowHeight by remember(place.id) { mutableFloatStateOf(0f) }
    var dragging by remember(place.id) { mutableStateOf(false) }
    val currentOnMove by rememberUpdatedState(onMove)

    val background = if (selected) {
        MiuixTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
    } else {
        Color.Transparent
    }
    val contentColor = if (selected) {
        MiuixTheme.colorScheme.primary
    } else {
        MiuixTheme.colorScheme.onSurface
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged { rowHeight = it.height.toFloat() }
            .graphicsLayer {
                translationY = dragOffset
                alpha = if (dragging) 0.72f else 1f
            }
            .pointerInput(place.id, threshold) {
                detectDragGestures(
                    onDragStart = {
                        dragging = true
                        onDragStateChange(true)
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        dragOffset += amount.y
                        val direction = when {
                            dragOffset > threshold -> 1
                            dragOffset < -threshold -> -1
                            else -> 0
                        }
                        if (direction != 0) {
                            if (currentOnMove(place, direction)) {
                                // 列表重排会把这一行搬到相邻槽位；反向补偿，
                                // 让被拖动的行继续停在鼠标下，只让其他行动画避让。
                                dragOffset += if (direction > 0) -rowHeight else rowHeight
                            } else {
                                dragOffset = dragOffset.coerceIn(-threshold, threshold)
                            }
                        }
                    },
                    onDragEnd = {
                        dragOffset = 0f
                        dragging = false
                        onDragStateChange(false)
                    },
                    onDragCancel = {
                        dragOffset = 0f
                        dragging = false
                        onDragStateChange(false)
                    },
                )
            }
            .padding(start = if (indent) 28.dp else 8.dp, end = 8.dp, top = 1.dp, bottom = 1.dp)
            .squircleClip(12.dp)
            .background(background)
            .combinedClickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {

        when (val icon = place.icon()) {
            is ImageVector -> {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = if (selected) MiuixTheme.colorScheme.primary else contentColor,
                )
            }

            is Painter -> {
                Icon(
                    painter = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = if (selected) MiuixTheme.colorScheme.primary else contentColor,
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = place.displayLabel(),
                style = MiuixTheme.textStyles.body2,
                color = contentColor,
            )

            if (place.kind in setOf(FilePlaceKind.Volume, FilePlaceKind.MtpStorage)) {
                val totalBytes = place.totalBytes
                val freeBytes = place.freeBytes

                if (totalBytes != null && freeBytes != null && totalBytes > 0) {
                    val usedBytes = (totalBytes - freeBytes).coerceAtLeast(0)
                    val progress = (usedBytes.toDouble() / totalBytes).toFloat().coerceIn(0f, 1f)
                    val percent = (progress * 100).roundToInt()

                    Spacer(modifier = Modifier.height(2.dp))

                    LinearProgressIndicator(
                        progress = progress,
                        modifier = Modifier.fillMaxWidth()
                            .height(4.dp)
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = stringResource(
                            Res.string.text_storage_usage,
                            percent,
                            formatFileSize(usedBytes),
                            formatFileSize(freeBytes),
                        ),
                        style = MiuixTheme.textStyles.footnote2,
                        color = contentColor.copy(.31f),
                    )
                } else {
                    Spacer(modifier = Modifier.height(2.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        InfiniteProgressIndicator(
                            color = contentColor,
                            size = 14.dp,
                        )
                        Text(
                            text = stringResource(Res.string.text_capacity_unknown),
                            style = MiuixTheme.textStyles.footnote2,
                            color = contentColor,
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))
                }
            }
        }

        if (expanded != null) {
            val rotate by animateFloatAsState(
                targetValue = if (expanded) 90f else 0f,
            )

            Icon(
                imageVector = MiuixIcons.ChevronForward,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
                    .rotate(rotate),
                tint = contentColor,
            )
        }
    }
}

/**
 * 当前路径是否属于这个地点。
 *
 * 地点允许前缀命中，并配合最长路径优先，选中更具体的入口。
 */
private fun FilePlace.matches(active: FileLocation): Boolean {
    if (active.source.id != location.source.id) return false
    if (active.path == location.path) return true
    return active.path.startsWith(location.path.trimEnd('/') + "/")
}

@Composable
private fun FilePlace.displayLabel(): String = when (kind) {
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
    FilePlaceKind.MtpCamera -> stringResource(Res.string.text_camera)
    FilePlaceKind.MtpDownloads -> stringResource(Res.string.text_downloads)
    FilePlaceKind.MtpMovies -> stringResource(Res.string.text_movies)
    FilePlaceKind.MtpMusic -> stringResource(Res.string.text_music)
    FilePlaceKind.MtpPictures -> stringResource(Res.string.text_pictures)
}

@Composable
private fun FilePlace.icon(): Any = when (kind) {
    FilePlaceKind.Home -> painterResource(Res.drawable.ic_user)
    FilePlaceKind.Desktop -> MiuixIcons.GridView
    FilePlaceKind.Documents -> MiuixIcons.Notes
    FilePlaceKind.Downloads -> MiuixIcons.Download
    FilePlaceKind.Pictures -> MiuixIcons.Image
    FilePlaceKind.Music -> MiuixIcons.Music
    FilePlaceKind.Movies -> MiuixIcons.Play
    FilePlaceKind.Volume -> painterResource(Res.drawable.ic_storage)
    FilePlaceKind.MtpDevice,
    FilePlaceKind.MtpStorage -> painterResource(Res.drawable.ic_mtp)
    FilePlaceKind.MtpCamera -> painterResource(Res.drawable.ic_camara)
    FilePlaceKind.MtpDownloads -> MiuixIcons.Download
    FilePlaceKind.MtpMovies -> MiuixIcons.Play
    FilePlaceKind.MtpMusic -> MiuixIcons.Music
    FilePlaceKind.MtpPictures -> MiuixIcons.Image
}