package io.github.dockhand.ui.screen.home

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import dockhand.shared.generated.resources.Res
import dockhand.shared.generated.resources.ic_storage
import dockhand.shared.generated.resources.ic_user
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
import io.github.dockhand.domain.model.DEFAULT_FILE_PLACE_GROUP_ORDER
import io.github.dockhand.domain.model.FileLocation
import io.github.dockhand.domain.model.FilePlace
import io.github.dockhand.domain.model.FilePlaceGroup
import io.github.dockhand.domain.model.FilePlaceKind
import io.github.dockhand.domain.model.orderedByIds
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Download
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
import top.yukonga.miuix.kmp.icon.extended.Sort
import top.yukonga.miuix.kmp.squircle.squircleClip
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
    onPlaceMove: (FilePlace, Int) -> Unit,
    onPlaceClick: (FilePlace) -> Unit,
    modifier: Modifier = Modifier,
) {
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

    LazyColumn(modifier = modifier) {
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
            } else {
                items(groupPlaces, key = { it.id }) { place ->
                    PlaceRow(
                        place = place,
                        selected = place.id == activePlaceId,
                        onClick = { onPlaceClick(place) },
                        onMove = onPlaceMove,
                    )
                }
            }
        }
    }
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
    onClick: () -> Unit,
    onMove: (FilePlace, Int) -> Unit,
) {
    val threshold = with(LocalDensity.current) { 28.dp.toPx() }
    var dragOffset by remember(place.id) { mutableFloatStateOf(0f) }
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
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                translationY = dragOffset
                alpha = if (dragging) 0.72f else 1f
            }
            .padding(horizontal = 8.dp, vertical = 1.dp)
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

        Text(
            text = place.displayLabel(),
            modifier = Modifier.weight(1f),
            style = MiuixTheme.textStyles.footnote1,
            color = contentColor,
        )

        Icon(
            imageVector = MiuixIcons.Sort,
            contentDescription = null,
            modifier = Modifier
                .size(16.dp)
                .pointerInput(place.id, threshold) {
                    detectDragGestures(
                        onDragStart = { dragging = true },
                        onDrag = { change, amount ->
                            change.consume()
                            dragOffset += amount.y
                        },
                        onDragEnd = {
                            val direction = when {
                                dragOffset > threshold -> 1
                                dragOffset < -threshold -> -1
                                else -> 0
                            }
                            if (direction != 0) currentOnMove(place, direction)
                            dragOffset = 0f
                            dragging = false
                        },
                        onDragCancel = {
                            dragOffset = 0f
                            dragging = false
                        },
                    )
                },
            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
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
    FilePlaceKind.MtpStorage -> MiuixIcons.Phone
}