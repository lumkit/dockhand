package io.github.lumkit.dockhand.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dockhand.shared.generated.resources.Res
import dockhand.shared.generated.resources.action_cancel
import dockhand.shared.generated.resources.action_confirm
import dockhand.shared.generated.resources.extract_password
import dockhand.shared.generated.resources.extract_password_title
import io.github.lumkit.dockhand.ui.screen.home.ExtractPasswordState
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.overlay.OverlayDialog

@Composable
fun ExtractPasswordDialog(
    request: ExtractPasswordState?,
    onPasswordChange: (String) -> Unit,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
) {
    val current = request ?: return
    val state = rememberTextFieldState(current.password)
    val mask = OutputTransformation {
        if (length > 0) replace(0, length, "•".repeat(length))
    }
    LaunchedEffect(state.text) { onPasswordChange(state.text.toString()) }
    LaunchedEffect(current.password) {
        if (state.text != current.password) {
            state.setTextAndPlaceCursorAtEnd(current.password)
        }
    }

    OverlayDialog(
        show = true,
        title = stringResource(Res.string.extract_password_title),
        summary = current.archiveName,
        onDismissRequest = onDismissRequest,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TextField(
                state = state,
                label = stringResource(Res.string.extract_password),
                modifier = Modifier.fillMaxWidth(),
                useLabelAsPlaceholder = true,
                lineLimits = TextFieldLineLimits.SingleLine,
                outputTransformation = mask,
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
                    enabled = current.password.isNotBlank(),
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
