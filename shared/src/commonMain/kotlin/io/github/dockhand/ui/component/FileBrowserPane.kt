package io.github.dockhand.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.innerShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dockhand.shared.generated.resources.Res
import dockhand.shared.generated.resources.text_empty_folder
import dockhand.shared.generated.resources.text_read_failed
import dockhand.shared.generated.resources.text_retry
import io.github.dockhand.domain.model.FileEntry
import io.github.dockhand.ui.screen.home.FilePaneState
import io.github.dockhand.ui.screen.home.FileRowUi
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.File
import top.yukonga.miuix.kmp.icon.extended.Folder
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val INACTIVE_PANE_ALPHA = 0.55f

/**
 * 一个文件浏览面板。
 *
 * 焦点通过两层表达：
 *  - 失焦面板整体降低透明度；
 *  - 失焦面板盖一层 innerShadow，明确“当前输入只作用于另一侧”。
 *
 * 鼠标进入面板时立即请求焦点；点击面板空白处也会请求焦点；
 * 点击条目由上层决定是否导航。
 */
@Composable
fun FileBrowserPane(
    pane: FilePaneState,
    isFocused: Boolean,
    onFocus: () -> Unit,
    onEntryClick: (FileEntry) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    overlayShape: Shape = RoundedCornerShape(0.dp),
) {
    val hoverInteractionSource = remember { MutableInteractionSource() }
    val currentOnFocus by rememberUpdatedState(onFocus)

    // 鼠标一进入面板就切换焦点；hoverable 只在桌面端产生 HoverInteraction。
    LaunchedEffect(hoverInteractionSource) {
        hoverInteractionSource.interactions.collect { interaction ->
            if (interaction is HoverInteraction.Enter) {
                currentOnFocus()
            }
        }
    }

    Box(
        modifier = modifier
            .hoverable(hoverInteractionSource)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { currentOnFocus() })
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .alpha(if (isFocused) 1f else INACTIVE_PANE_ALPHA)
        ) {
            if (pane.isLoading && pane.entries.isNotEmpty()) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            Box(modifier = Modifier.weight(1f)) {
                when {
                    pane.isLoading && pane.entries.isEmpty() -> {
                        PaneStateBox {
                            CircularProgressIndicator()
                        }
                    }

                    pane.errorMessage != null -> {
                        PaneStateBox {
                            ErrorState(
                                message = pane.errorMessage,
                                onRetry = onRetry,
                            )
                        }
                    }

                    pane.isEmpty -> {
                        PaneStateBox {
                            EmptyState()
                        }
                    }

                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                        ) {
                            items(pane.entries, key = { it.id }) { row ->
                                FileEntryRow(
                                    row = row,
                                    selected = row.id in pane.selectedIds,
                                    onClick = { onEntryClick(row.entry) },
                                )
                            }
                        }
                    }
                }
            }
        }

        if (!isFocused) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .innerShadow(
                        shape = overlayShape,
                        shadow = Shadow(
                            radius = 4.dp,
                            color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                        )
                    )
            )
        }
    }
}

@Composable
private fun FileEntryRow(
    row: FileRowUi,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val rowColor = if (selected) {
        MiuixTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
    } else {
        Color.Transparent
    }

    BasicComponent(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 1.dp)
            .squircleClip(10.dp)
            .background(rowColor),
        startAction = {
            Icon(
                imageVector = if (row.isDirectory) MiuixIcons.Folder else MiuixIcons.File,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = if (row.isDirectory) {
                    MiuixTheme.colorScheme.primary
                } else {
                    MiuixTheme.colorScheme.onSurfaceVariantSummary
                },
            )
        },
        title = row.name,
        // summary 在列表加载时已经格式化好，LazyColumn 里只读字符串。
        summary = row.summary,
        onClick = onClick,
    )
}

@Composable
private fun PaneStateBox(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = MiuixIcons.Folder,
            contentDescription = null,
            modifier = Modifier.size(28.dp),
            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Text(
            text = stringResource(Res.string.text_empty_folder),
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

@Composable
private fun ErrorState(
    message: String,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(Res.string.text_read_failed),
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.error,
        )
        Text(
            text = message,
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            textAlign = TextAlign.Center,
        )
        TextButton(
            text = stringResource(Res.string.text_retry),
            onClick = onRetry,
        )
    }
}