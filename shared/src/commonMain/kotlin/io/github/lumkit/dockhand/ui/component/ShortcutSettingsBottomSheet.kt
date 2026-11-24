package io.github.lumkit.dockhand.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import dockhand.shared.generated.resources.Res
import dockhand.shared.generated.resources.action_cancel
import dockhand.shared.generated.resources.action_confirm
import dockhand.shared.generated.resources.shortcut_action_clear_selection
import dockhand.shared.generated.resources.shortcut_action_context_menu
import dockhand.shared.generated.resources.shortcut_action_copy
import dockhand.shared.generated.resources.shortcut_action_delete
import dockhand.shared.generated.resources.shortcut_action_move
import dockhand.shared.generated.resources.shortcut_action_next
import dockhand.shared.generated.resources.shortcut_action_previous
import dockhand.shared.generated.resources.shortcut_action_rename
import dockhand.shared.generated.resources.shortcut_action_trash
import dockhand.shared.generated.resources.shortcut_action_select_all
import dockhand.shared.generated.resources.shortcut_add
import dockhand.shared.generated.resources.shortcut_conflict
import dockhand.shared.generated.resources.shortcut_delete
import dockhand.shared.generated.resources.shortcut_dialog_hint
import dockhand.shared.generated.resources.shortcut_dialog_prompt
import dockhand.shared.generated.resources.shortcut_dialog_title
import dockhand.shared.generated.resources.shortcut_reset_tooltip
import dockhand.shared.generated.resources.shortcut_sheet_title
import io.github.lumkit.dockhand.shortcut.ShortcutAction
import io.github.lumkit.dockhand.shortcut.ShortcutDispatcher
import io.github.lumkit.dockhand.shortcut.ShortcutKey
import io.github.lumkit.dockhand.shortcut.display
import io.github.lumkit.dockhand.shortcut.isModifierKey
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TooltipBox
import top.yukonga.miuix.kmp.basic.verticalScrollBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

@OptIn(ExperimentalScrollBarApi::class)
@Composable
fun ShortcutSettingsBottomSheet(
    show: Boolean,
    bindings: Map<ShortcutAction, List<ShortcutKey>>,
    onDismissRequest: () -> Unit,
    onShortcutAdd: (ShortcutAction, ShortcutKey) -> Unit,
    onShortcutRemove: (ShortcutAction, ShortcutKey) -> Unit,
    onResetShortcuts: () -> Unit,
) {
    var editingAction by remember { mutableStateOf<ShortcutAction?>(null) }

    OverlayBottomSheet(
        show = show,
        title = stringResource(Res.string.shortcut_sheet_title),
        endAction = {
            TooltipBox(text = stringResource(Res.string.shortcut_reset_tooltip)) {
                IconButton(
                    onClick = onResetShortcuts,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = MiuixIcons.Refresh,
                        contentDescription = stringResource(Res.string.shortcut_reset_tooltip),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        },
        onDismissRequest = onDismissRequest,
    ) {
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .verticalScrollBar(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ShortcutAction.entries.forEach { action ->
                SmallTitle(text = action.label())
                Card(
                    modifier = Modifier.fillMaxWidth()
                        .padding(bottom = 16.dp),
                    colors = CardDefaults.defaultColors(
                        color = MiuixTheme.colorScheme.secondaryContainer,
                    ),
                ) {
                    val keys = bindings[action].orEmpty()
                    keys.forEach { key ->
                        BasicComponent(
                            title = key.display(),
                            endActions = {
                                IconButton(
                                    onClick = { onShortcutRemove(action, key) },
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Icon(
                                        imageVector = MiuixIcons.Delete,
                                        contentDescription = stringResource(
                                            Res.string.shortcut_delete
                                        ),
                                        modifier = Modifier.size(18.dp),
                                        tint = MiuixTheme.colorScheme.error,
                                    )
                                }
                            },
                        )
                    }
                    BasicComponent(
                        title = stringResource(Res.string.shortcut_add),
                        startAction = {
                            Icon(
                                imageVector = MiuixIcons.Add,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MiuixTheme.colorScheme.primary,
                            )
                        },
                        onClick = { editingAction = action },
                    )
                }
            }
        }
    }

    val action = editingAction
    if (action != null) {
        ShortcutCaptureDialog(
            action = action,
            current = bindings[action]?.firstOrNull(),
            bindings = bindings,
            onDismiss = { editingAction = null },
            onConfirm = { key ->
                onShortcutAdd(action, key)
                editingAction = null
            },
        )
    }
}

@Composable
private fun ShortcutCaptureDialog(
    action: ShortcutAction,
    current: ShortcutKey?,
    bindings: Map<ShortcutAction, List<ShortcutKey>>,
    onDismiss: () -> Unit,
    onConfirm: (ShortcutKey) -> Unit,
) {
    var captured by remember(action) { mutableStateOf<ShortcutKey?>(null) }
    val conflictAction = captured?.let { key ->
        ShortcutAction.entries.firstOrNull { other ->
            bindings[other].orEmpty().contains(key)
        }
    }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(action) {
        withFrameNanos { }
        focusRequester.requestFocus()
    }
    DisposableEffect(action) {
        ShortcutDispatcher.setCaptureHandler { event ->
            if (event.type != KeyEventType.KeyDown || event.key.isModifierKey()) {
                false
            } else {
                captured = ShortcutKey(
                    key = event.key,
                    meta = event.isMetaPressed,
                    ctrl = event.isCtrlPressed,
                    shift = event.isShiftPressed,
                    alt = event.isAltPressed,
                )
                true
            }
        }
        onDispose { ShortcutDispatcher.setCaptureHandler(null) }
    }

    OverlayDialog(
        show = true,
        title = stringResource(Res.string.shortcut_dialog_title),
        summary = action.label(),
        onDismissRequest = onDismiss,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .background(
                        color = MiuixTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(10.dp),
                    )
                    .focusRequester(focusRequester)
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown || event.key.isModifierKey()) {
                            false
                        } else {
                            captured = ShortcutKey(
                                key = event.key,
                                meta = event.isMetaPressed,
                                ctrl = event.isCtrlPressed,
                                shift = event.isShiftPressed,
                                alt = event.isAltPressed,
                            )
                            true
                        }
                    }
                    .focusable()
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = captured?.display()
                        ?: current?.display()
                        ?: stringResource(Res.string.shortcut_dialog_prompt),
                    style = MiuixTheme.textStyles.body1,
                )
            }
            if (conflictAction != null) {
                Text(
                    text = stringResource(
                        Res.string.shortcut_conflict,
                        conflictAction.label(),
                    ),
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.error,
                )
            } else {
                Text(
                    text = stringResource(Res.string.shortcut_dialog_hint),
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(
                    text = stringResource(Res.string.action_confirm),
                    onClick = { captured?.let(onConfirm) },
                    enabled = captured != null && conflictAction == null,
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

@Composable
private fun ShortcutAction.label(): String = when (this) {
    ShortcutAction.SelectAll -> stringResource(Res.string.shortcut_action_select_all)
    ShortcutAction.ClearSelection -> stringResource(Res.string.shortcut_action_clear_selection)
    ShortcutAction.SelectPrevious -> stringResource(Res.string.shortcut_action_previous)
    ShortcutAction.SelectNext -> stringResource(Res.string.shortcut_action_next)
    ShortcutAction.OpenContextMenu -> stringResource(Res.string.shortcut_action_context_menu)
    ShortcutAction.Copy -> stringResource(Res.string.shortcut_action_copy)
    ShortcutAction.Move -> stringResource(Res.string.shortcut_action_move)
    ShortcutAction.Delete -> stringResource(Res.string.shortcut_action_delete)
    ShortcutAction.Rename -> stringResource(Res.string.shortcut_action_rename)
    ShortcutAction.Trash -> stringResource(Res.string.shortcut_action_trash)
}
