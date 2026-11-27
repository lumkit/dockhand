package io.github.lumkit.dockhand.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import dockhand.shared.generated.resources.Res
import dockhand.shared.generated.resources.action_cancel
import dockhand.shared.generated.resources.action_confirm
import dockhand.shared.generated.resources.compress_dialog_background
import dockhand.shared.generated.resources.extract_delete_source
import dockhand.shared.generated.resources.extract_dialog_title
import dockhand.shared.generated.resources.extract_folder_name
import dockhand.shared.generated.resources.extract_to_current
import dockhand.shared.generated.resources.extract_to_folder
import dockhand.shared.generated.resources.extract_to_other_pane
import io.github.lumkit.dockhand.ui.screen.home.ExtractDestinationMode
import io.github.lumkit.dockhand.ui.screen.home.ExtractRequestState
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.RadioButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.verticalScrollBar
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

@OptIn(ExperimentalScrollBarApi::class)
@Composable
fun ExtractRequestDialog(
    request: ExtractRequestState?,
    onModeChange: (ExtractDestinationMode) -> Unit,
    onFolderNameChange: (String) -> Unit,
    onToOtherPaneChange: (Boolean) -> Unit,
    onDeleteSourceChange: (Boolean) -> Unit,
    onBackgroundChange: (Boolean) -> Unit,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
) {
    val current = request ?: return
    val folderState = rememberTextFieldState(current.folderName)
    val scrollState = rememberScrollState()

    LaunchedEffect(folderState.text) { onFolderNameChange(folderState.text.toString()) }
    LaunchedEffect(current.folderName) {
        if (folderState.text != current.folderName) {
            folderState.setTextAndPlaceCursorAtEnd(current.folderName)
        }
    }

    OverlayDialog(
        show = true,
        title = stringResource(Res.string.extract_dialog_title),
        summary = current.entry.name,
        onDismissRequest = onDismissRequest,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(
                modifier = Modifier.fillMaxWidth()
                    .weight(1f, false)
                    .verticalScrollBar(scrollState)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                RadioRow(
                    title = stringResource(Res.string.extract_to_current),
                    selected = current.mode == ExtractDestinationMode.CurrentDirectory,
                    onClick = { onModeChange(ExtractDestinationMode.CurrentDirectory) },
                )
                RadioRow(
                    title = stringResource(Res.string.extract_to_folder),
                    selected = current.mode == ExtractDestinationMode.Folder,
                    onClick = { onModeChange(ExtractDestinationMode.Folder) },
                )
                AnimatedVisibility(visible = current.mode == ExtractDestinationMode.Folder) {
                    TextField(
                        state = folderState,
                        label = stringResource(Res.string.extract_folder_name),
                        modifier = Modifier.fillMaxWidth(),
                        lineLimits = TextFieldLineLimits.SingleLine,
                    )
                }
                CheckboxRow(
                    title = stringResource(Res.string.extract_to_other_pane),
                    checked = current.toOtherPane,
                    onCheckedChange = onToOtherPaneChange,
                )
                CheckboxRow(
                    title = stringResource(Res.string.extract_delete_source),
                    checked = current.deleteSource,
                    onCheckedChange = onDeleteSourceChange,
                )
                CheckboxRow(
                    title = stringResource(Res.string.compress_dialog_background),
                    checked = current.background,
                    onCheckedChange = onBackgroundChange,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(
                    text = stringResource(Res.string.action_confirm),
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    enabled = current.mode != ExtractDestinationMode.Folder || current.folderName.isNotBlank(),
                )
                TextButton(
                    text = stringResource(Res.string.action_cancel),
                    onClick = onDismissRequest,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun RadioRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                indication = null,
                interactionSource = null,
                onClick = onClick,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(
            text = title,
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun CheckboxRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                indication = null,
                interactionSource = null,
            ) { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Checkbox(
            state = if (checked) ToggleableState.On else ToggleableState.Off,
            onClick = null,
        )
        Text(
            text = title,
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurface,
        )
    }
}