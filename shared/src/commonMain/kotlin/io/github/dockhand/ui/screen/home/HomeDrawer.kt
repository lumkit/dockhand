package io.github.dockhand.ui.screen.home

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dockhand.shared.generated.resources.Res
import dockhand.shared.generated.resources.ic_home
import dockhand.shared.generated.resources.ic_storage
import dockhand.shared.generated.resources.ic_user
import dockhand.shared.generated.resources.text_desktop
import dockhand.shared.generated.resources.text_documents
import dockhand.shared.generated.resources.text_downloads
import dockhand.shared.generated.resources.text_local
import dockhand.shared.generated.resources.text_movies
import dockhand.shared.generated.resources.text_mtp_devices
import dockhand.shared.generated.resources.text_music
import dockhand.shared.generated.resources.text_no_mtp_devices
import dockhand.shared.generated.resources.text_pictures
import dockhand.shared.generated.resources.text_root
import io.github.dockhand.domain.model.FileLocation
import io.github.dockhand.domain.model.FilePlace
import io.github.dockhand.domain.model.FilePlaceGroup
import io.github.dockhand.domain.model.FilePlaceKind
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
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 侧边导航面板。
 *
 * 数据完全来自 [FilePlace]：本地入口由 LocalFileSystemProvider 扫描产生，
 * MTP 入口由未来的 MTP provider 产生。这里没有写死的路径。
 */
@Composable
fun HomeDrawerContent(
    places: List<FilePlace>,
    activeLocation: FileLocation?,
    onPlaceClick: (FilePlace) -> Unit,
    modifier: Modifier = Modifier,
) {
    val localPlaces = remember(places) { places.filter { it.group == FilePlaceGroup.Local } }
    val mtpPlaces = remember(places) { places.filter { it.group == FilePlaceGroup.Mtp } }
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
        item {
            SmallTitle(text = stringResource(Res.string.text_local))
        }
        items(localPlaces, key = { it.id }) { place ->
            PlaceRow(
                place = place,
                selected = place.id == activePlaceId,
                onClick = { onPlaceClick(place) },
            )
        }

        item {
            SmallTitle(text = stringResource(Res.string.text_mtp_devices))
        }
        if (mtpPlaces.isEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = stringResource(Res.string.text_no_mtp_devices),
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
        } else {
            items(mtpPlaces, key = { it.id }) { place ->
                PlaceRow(
                    place = place,
                    selected = place.id == activePlaceId,
                    onClick = { onPlaceClick(place) },
                )
            }
        }
    }
}

@Composable
private fun PlaceRow(
    place: FilePlace,
    selected: Boolean,
    onClick: () -> Unit,
) {
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
            style = MiuixTheme.textStyles.footnote1,
            color = contentColor,
        )
    }
}

/**
 * 当前路径是否属于这个地点。
 *
 * 根目录只在精确命中时高亮，否则任何本地路径都会把“根目录”点亮；
 * 其他地点允许前缀命中，并配合最长路径优先，选中更具体的入口。
 */
private fun FilePlace.matches(active: FileLocation): Boolean {
    if (active.source.id != location.source.id) return false
    if (active.path == location.path) return true
    if (kind == FilePlaceKind.Root) return false
    return active.path.startsWith(location.path.trimEnd('/') + "/")
}

@Composable
private fun FilePlace.displayLabel(): String = when (kind) {
    FilePlaceKind.Root -> stringResource(Res.string.text_root)
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
    FilePlaceKind.Root -> painterResource(Res.drawable.ic_home)
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