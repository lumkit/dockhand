package io.github.lumkit.dockhand.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import dockhand.shared.generated.resources.Res
import dockhand.shared.generated.resources.action_cancel
import dockhand.shared.generated.resources.action_confirm
import dockhand.shared.generated.resources.copy_dialog_background
import dockhand.shared.generated.resources.rename_dialog_summary
import dockhand.shared.generated.resources.rename_dialog_summary_multiple
import dockhand.shared.generated.resources.rename_dialog_title_multiple
import dockhand.shared.generated.resources.rename_dialog_title
import io.github.lumkit.dockhand.ui.screen.home.RenameRequestState
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.verticalScrollBar
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

@OptIn(ExperimentalScrollBarApi::class)
@Composable
fun RenameRequestDialog(
    request: RenameRequestState?,
    onNameChange: (String) -> Unit,
    onBackgroundChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    if (request == null) return
    val scrollState = rememberScrollState()
    val nameState = rememberTextFieldState(request.newName)
    LaunchedEffect(request.entry.id) {
        nameState.setTextAndPlaceCursorAtEnd(request.newName)
    }
    LaunchedEffect(nameState) {
        snapshotFlow { nameState.text.toString() }.collect(onNameChange)
    }
    OverlayDialog(
        show = true,
        title = stringResource(
            if (request.entries.size > 1) {
                Res.string.rename_dialog_title_multiple
            } else {
                Res.string.rename_dialog_title
            }
        ),
        summary = if (request.entries.size > 1) {
            stringResource(Res.string.rename_dialog_summary_multiple, request.entries.size)
        } else {
            stringResource(Res.string.rename_dialog_summary, request.entry.name)
        },
        onDismissRequest = onDismiss,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(
                modifier = Modifier.fillMaxWidth()
                    .weight(1f, false)
                    .verticalScrollBar(scrollState)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextField(
                    state = nameState,
                    modifier = Modifier.fillMaxWidth(),
                    lineLimits = TextFieldLineLimits.SingleLine,
                )
                BackgroundTaskRow(
                    checked = request.background,
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
                )
                TextButton(
                    text = stringResource(Res.string.action_cancel),
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}