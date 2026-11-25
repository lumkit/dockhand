package io.github.lumkit.dockhand.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import dockhand.shared.generated.resources.Res
import dockhand.shared.generated.resources.action_cancel
import dockhand.shared.generated.resources.action_confirm
import dockhand.shared.generated.resources.copy_dialog_background
import dockhand.shared.generated.resources.trash_dialog_summary
import dockhand.shared.generated.resources.trash_dialog_summary_multiple
import dockhand.shared.generated.resources.trash_dialog_title
import io.github.lumkit.dockhand.ui.screen.home.TrashRequestState
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun TrashRequestDialog(
    request: TrashRequestState?,
    onBackgroundChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    if (request == null) return
    OverlayDialog(
        show = true,
        title = stringResource(Res.string.trash_dialog_title),
        summary = if (request.entries.size > 1) {
            stringResource(Res.string.trash_dialog_summary_multiple, request.entries.size)
        } else {
            stringResource(Res.string.trash_dialog_summary, request.entry.name)
        },
        onDismissRequest = onDismiss,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            BackgroundTaskRow(
                checked = request.background,
                onCheckedChange = onBackgroundChange,
            )
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
