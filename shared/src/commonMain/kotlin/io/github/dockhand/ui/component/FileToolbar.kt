package io.github.dockhand.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dockhand.shared.generated.resources.Res
import dockhand.shared.generated.resources.action_cancel
import dockhand.shared.generated.resources.action_close
import dockhand.shared.generated.resources.action_confirm
import dockhand.shared.generated.resources.checkbox_create_file
import dockhand.shared.generated.resources.dialog_new
import dockhand.shared.generated.resources.field_name
import dockhand.shared.generated.resources.tooltip_back
import dockhand.shared.generated.resources.tooltip_forward
import dockhand.shared.generated.resources.tooltip_new
import dockhand.shared.generated.resources.tooltip_refresh
import dockhand.shared.generated.resources.tooltip_sync
import dockhand.shared.generated.resources.tooltip_up
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TooltipBox
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Replace
import top.yukonga.miuix.kmp.preference.CheckboxPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

/**
 * 双列表顶部的文件工具栏。
 *
 * 自身只负责“动作入口 + 新建弹窗 + 操作错误提示”，
 * 具体业务由调用方（HomeViewModel）执行，方便后续复用到其他文件视图。
 */
@Composable
fun FileToolbar(
    canGoBack: Boolean,
    canGoForward: Boolean,
    canGoUp: Boolean,
    canCreate: Boolean,
    canSync: Boolean,
    operationError: String?,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onGoUp: () -> Unit,
    onCreate: (String, Boolean) -> Unit,
    onSync: () -> Unit,
    onRefresh: () -> Unit,
    onDismissOperationError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var createFile by remember { mutableStateOf(false) }
    val itemName = rememberTextFieldState()

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToolbarAction(
                icon = MiuixIcons.ChevronBackward,
                tooltip = stringResource(Res.string.tooltip_back),
                enabled = canGoBack,
                modifier = Modifier.weight(1f),
                onClick = onBack,
            )
            ToolbarAction(
                icon = MiuixIcons.ChevronForward,
                tooltip = stringResource(Res.string.tooltip_forward),
                enabled = canGoForward,
                modifier = Modifier.weight(1f),
                onClick = onForward,
            )
            ToolbarAction(
                icon = MiuixIcons.Add,
                tooltip = stringResource(Res.string.tooltip_new),
                enabled = canCreate,
                modifier = Modifier.weight(1f),
                onClick = {
                    itemName.clearText()
                    createFile = false
                    showCreateDialog = true
                },
            )
            ToolbarAction(
                icon = MiuixIcons.Replace,
                tooltip = stringResource(Res.string.tooltip_sync),
                enabled = canSync,
                modifier = Modifier.weight(1f),
                onClick = onSync,
            )
            ToolbarAction(
                icon = MiuixIcons.Back,
                tooltip = stringResource(Res.string.tooltip_up),
                enabled = canGoUp,
                modifier = Modifier.weight(1f),
                iconRotate = 90f,
                onClick = onGoUp,
            )
            ToolbarAction(
                icon = MiuixIcons.Refresh,
                tooltip = stringResource(Res.string.tooltip_refresh),
                enabled = canCreate,
                modifier = Modifier.weight(1f),
                iconRotate = 90f,
                onClick = onRefresh,
            )
        }

        if (operationError != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = operationError,
                    modifier = Modifier.weight(1f),
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                TextButton(
                    text = stringResource(Res.string.action_close),
                    onClick = onDismissOperationError,
                )
            }
        }
    }

    CreateItemDialog(
        show = showCreateDialog,
        itemName = itemName,
        createFile = createFile,
        onCheckedChange = { createFile = it },
        onDismiss = { showCreateDialog = false },
        onCreate = {
            val name = itemName.text.toString().trim()
            val file = createFile
            showCreateDialog = false
            onCreate(name, file)
        },
    )
}

@Composable
private fun ToolbarAction(
    icon: ImageVector,
    tooltip: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    iconRotate: Float = 0f,
    onClick: () -> Unit,
) {
    TooltipBox(
        modifier = modifier.fillMaxHeight(),
        text = tooltip,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = tooltip,
                modifier = Modifier.size(20.dp).rotate(iconRotate),
                tint = if (enabled) {
                    MiuixTheme.colorScheme.onSurface
                } else {
                    MiuixTheme.colorScheme.disabledOnSurface
                },
            )
        }
    }
}

@Composable
private fun CreateItemDialog(
    show: Boolean,
    itemName: TextFieldState,
    createFile: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onCreate: () -> Unit,
) {
    val name = itemName.text.toString().trim()
    val canCreate = name.isNotEmpty() && name != "." && name != ".." && '/' !in name

    WindowDialog(
        show = show,
        title = stringResource(Res.string.dialog_new),
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            TextField(
                state = itemName,
                modifier = Modifier.fillMaxWidth(),
                label = stringResource(Res.string.field_name),
                useLabelAsPlaceholder = true,
            )
            CheckboxPreference(
                title = stringResource(Res.string.checkbox_create_file),
                checked = createFile,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    text = stringResource(Res.string.action_confirm),
                    onClick = onCreate,
                    enabled = canCreate,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
                TextButton(
                    text = stringResource(Res.string.action_cancel),
                    onClick = onDismiss,
                    enabled = true,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}