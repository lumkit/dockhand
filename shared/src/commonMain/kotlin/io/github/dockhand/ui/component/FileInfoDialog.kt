package io.github.dockhand.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dockhand.shared.generated.resources.Res
import dockhand.shared.generated.resources.action_close
import dockhand.shared.generated.resources.file_info_basic
import dockhand.shared.generated.resources.file_info_created
import dockhand.shared.generated.resources.file_info_file
import dockhand.shared.generated.resources.file_info_folder
import dockhand.shared.generated.resources.file_info_group
import dockhand.shared.generated.resources.file_info_loading
import dockhand.shared.generated.resources.file_info_modified
import dockhand.shared.generated.resources.file_info_name
import dockhand.shared.generated.resources.file_info_owner
import dockhand.shared.generated.resources.file_info_parent
import dockhand.shared.generated.resources.file_info_permission
import dockhand.shared.generated.resources.file_info_permissions
import dockhand.shared.generated.resources.file_info_size
import dockhand.shared.generated.resources.file_info_size_loading
import dockhand.shared.generated.resources.file_info_size_with_count
import dockhand.shared.generated.resources.file_info_title
import dockhand.shared.generated.resources.file_info_type
import dockhand.shared.generated.resources.file_info_unknown
import io.github.dockhand.domain.model.FileInfo
import io.github.dockhand.domain.model.FileLocation
import io.github.dockhand.domain.model.displayPath
import io.github.dockhand.ui.screen.home.FileInfoDialogState
import io.github.dockhand.ui.screen.home.formatFileSize
import io.github.dockhand.ui.screen.home.formatModifiedTime
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.verticalScrollBar
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.MiuixCaptionBar

@OptIn(ExperimentalScrollBarApi::class)
@Composable
fun FileInfoDialogContent(
    state: FileInfoDialogState,
    onClose: () -> Unit,
) {
    val timeZone = TimeZone.currentSystemDefault()
    val unknown = stringResource(Res.string.file_info_unknown)
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            MiuixCaptionBar {
                SmallTitle(
                    text = stringResource(Res.string.file_info_title),
                    insideMargin = PaddingValues(),
                    modifier = Modifier.align(Alignment.CenterStart),
                )
            }
        }
    ) {
        Box(
            modifier = Modifier.padding(it)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .verticalScrollBar(scrollState)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {

                when {
                    state.isLoading -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            InfiniteProgressIndicator(size = 20.dp)
                            Text(
                                text = stringResource(Res.string.file_info_loading),
                                modifier = Modifier.padding(start = 8.dp),
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                    }

                    state.error != null -> {
                        Text(
                            text = state.error,
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.error,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }

                    state.info != null -> {
                        val parentPath = FileLocation(
                            source = state.entry.location.source,
                            path = state.info.parentPath,
                        ).displayPath()
                        SelectionContainer {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                FileInfoSections(
                                    info = state.info,
                                    parentPath = parentPath,
                                    sizeLoading = state.sizeLoading,
                                    timeZone = timeZone,
                                    unknown = unknown,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FileInfoSections(
    info: FileInfo,
    parentPath: String,
    sizeLoading: Boolean,
    timeZone: TimeZone,
    unknown: String,
) {
    SmallTitle(text = stringResource(Res.string.file_info_basic))
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        InfoRow(
            label = stringResource(Res.string.file_info_name),
            value = info.name,
        )
        InfoRow(
            label = stringResource(Res.string.file_info_parent),
            value = parentPath,
        )
        InfoRow(
            label = stringResource(Res.string.file_info_type),
            value = stringResource(
                if (info.isDirectory) Res.string.file_info_folder else Res.string.file_info_file
            ),
        )
        if (sizeLoading) {
            BasicComponent(
                modifier = Modifier.fillMaxWidth(),
                title = stringResource(Res.string.file_info_size),
                summary = stringResource(Res.string.file_info_size_loading),
                endActions = {
                    InfiniteProgressIndicator(size = 18.dp)
                },
            )
        } else {
            InfoRow(
                label = stringResource(Res.string.file_info_size),
                value = when {
                    info.sizeBytes == null -> unknown
                    info.isDirectory && info.itemCount != null -> stringResource(
                        Res.string.file_info_size_with_count,
                        formatFileSize(info.sizeBytes),
                        info.itemCount,
                    )
                    else -> formatFileSize(info.sizeBytes)
                },
            )
        }
        InfoRow(
            label = stringResource(Res.string.file_info_created),
            value = formatModifiedTime(info.createdAt, timeZone) ?: unknown,
        )
        InfoRow(
            label = stringResource(Res.string.file_info_modified),
            value = formatModifiedTime(info.modifiedAt, timeZone) ?: unknown,
        )
    }

    if (
        info.permissionSymbol != null ||
        info.permissionCode != null ||
        info.owner != null ||
        info.group != null
    ) {
        SmallTitle(text = stringResource(Res.string.file_info_permissions))
        Card(
            modifier = Modifier.fillMaxWidth(),
        ) {
            InfoRow(
                label = stringResource(Res.string.file_info_permission),
                value = when {
                    info.permissionSymbol != null && info.permissionCode != null ->
                        "${info.permissionSymbol} (${info.permissionCode})"
                    info.permissionSymbol != null -> info.permissionSymbol
                    info.permissionCode != null -> info.permissionCode
                    else -> unknown
                },
            )
            InfoRow(
                label = stringResource(Res.string.file_info_owner),
                value = info.owner ?: unknown,
            )
            InfoRow(
                label = stringResource(Res.string.file_info_group),
                value = info.group ?: unknown,
            )
        }
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
) {
    BasicComponent(
        modifier = Modifier.fillMaxWidth(),
        title = label,
        summary = value,
    )
}

@Composable
expect fun FileInfoDialogWindow(
    title: String,
    width: Dp = 400.dp,
    height: Dp = 300.dp,
    order: Int = 0,
    scale: Float = 1f,
    onCloseRequest: () -> Unit,
    content: @Composable () -> Unit,
)
