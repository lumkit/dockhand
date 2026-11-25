package io.github.lumkit.dockhand.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dockhand.shared.generated.resources.Res
import dockhand.shared.generated.resources.action_cancel
import dockhand.shared.generated.resources.action_confirm
import dockhand.shared.generated.resources.compress_dialog_background
import dockhand.shared.generated.resources.compress_dialog_summary
import dockhand.shared.generated.resources.compress_dialog_title
import dockhand.shared.generated.resources.compress_delete_source
import dockhand.shared.generated.resources.compress_field_format
import dockhand.shared.generated.resources.compress_field_level
import dockhand.shared.generated.resources.compress_field_name
import dockhand.shared.generated.resources.compress_field_password
import dockhand.shared.generated.resources.compress_field_split
import dockhand.shared.generated.resources.compress_format_7z
import dockhand.shared.generated.resources.compress_format_gzip
import dockhand.shared.generated.resources.compress_format_tar
import dockhand.shared.generated.resources.compress_format_tar_bz2
import dockhand.shared.generated.resources.compress_format_tar_gz
import dockhand.shared.generated.resources.compress_format_tar_xz
import dockhand.shared.generated.resources.compress_format_tar_zst
import dockhand.shared.generated.resources.compress_format_xz
import dockhand.shared.generated.resources.compress_format_zip
import dockhand.shared.generated.resources.compress_format_zstd
import dockhand.shared.generated.resources.compress_level_fast
import dockhand.shared.generated.resources.compress_level_fastest
import dockhand.shared.generated.resources.compress_level_maximum
import dockhand.shared.generated.resources.compress_level_normal
import dockhand.shared.generated.resources.compress_level_store
import dockhand.shared.generated.resources.compress_level_ultra
import dockhand.shared.generated.resources.compress_password_hint
import dockhand.shared.generated.resources.compress_separate
import dockhand.shared.generated.resources.compress_split_unit
import dockhand.shared.generated.resources.compress_to_other_pane
import io.github.lumkit.dockhand.core.archive.ArchiveFormat
import io.github.lumkit.dockhand.core.archive.CompressionLevel
import io.github.lumkit.dockhand.ui.screen.home.CompressRequestState
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.verticalScrollBar
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@OptIn(ExperimentalScrollBarApi::class)
@Composable
fun CompressRequestDialog(
    request: CompressRequestState?,
    onNameChange: (String) -> Unit,
    onFormatChange: (ArchiveFormat) -> Unit,
    onLevelChange: (CompressionLevel) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSplitChange: (String) -> Unit,
    onSeparateChange: (Boolean) -> Unit,
    onDeleteSourceChange: (Boolean) -> Unit,
    onToOtherPaneChange: (Boolean) -> Unit,
    onBackgroundChange: (Boolean) -> Unit,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
) {
    val current = request ?: return
    val nameState = rememberTextFieldState(current.archiveName)
    val passwordState = rememberTextFieldState(current.password)
    val splitState = rememberTextFieldState(current.splitSizeMb)
    val scrollState = rememberScrollState()
    val passwordMask = OutputTransformation {
        if (length > 0) replace(0, length, "•".repeat(length))
    }

    LaunchedEffect(nameState.text) { onNameChange(nameState.text.toString()) }
    LaunchedEffect(passwordState.text) { onPasswordChange(passwordState.text.toString()) }
    LaunchedEffect(splitState.text) { onSplitChange(splitState.text.toString()) }
    LaunchedEffect(current.archiveName) {
        if (nameState.text != current.archiveName) {
            nameState.setTextAndPlaceCursorAtEnd(current.archiveName)
        }
    }
    LaunchedEffect(current.password) {
        if (passwordState.text != current.password) {
            passwordState.setTextAndPlaceCursorAtEnd(current.password)
        }
    }
    LaunchedEffect(current.splitSizeMb) {
        if (splitState.text != current.splitSizeMb) {
            splitState.setTextAndPlaceCursorAtEnd(current.splitSizeMb)
        }
    }

    OverlayDialog(
        show = true,
        title = stringResource(Res.string.compress_dialog_title),
        summary = stringResource(Res.string.compress_dialog_summary, current.entries.size),
        onDismissRequest = onDismissRequest,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(scrollState)
                    .verticalScrollBar(scrollState),
            ) {
            TextField(
                state = nameState,
                label = stringResource(Res.string.compress_field_name),
                modifier = Modifier.fillMaxWidth(),
                lineLimits = TextFieldLineLimits.SingleLine,
            )
            Spacer(Modifier.height(10.dp))
            OverlayDropdownPreference(
                items = ArchiveFormat.entries.map { archiveFormatLabel(it) },
                selectedIndex = ArchiveFormat.entries.indexOf(current.format).coerceAtLeast(0),
                title = stringResource(Res.string.compress_field_format),
                summary = archiveFormatLabel(current.format),
                onSelectedIndexChange = { onFormatChange(ArchiveFormat.entries[it]) },
            )
            Spacer(Modifier.height(10.dp))
            OverlayDropdownPreference(
                items = CompressionLevel.entries.map { compressionLevelLabel(it) },
                selectedIndex = CompressionLevel.entries.indexOf(current.level).coerceAtLeast(0),
                title = stringResource(Res.string.compress_field_level),
                summary = compressionLevelLabel(current.level),
                onSelectedIndexChange = { onLevelChange(CompressionLevel.entries[it]) },
            )
            Spacer(Modifier.height(10.dp))
            AnimatedVisibility(visible = current.format.supportsPassword) {
                Column {
                    TextField(
                        state = passwordState,
                        label = stringResource(Res.string.compress_field_password),
                        modifier = Modifier.fillMaxWidth(),
                        useLabelAsPlaceholder = true,
                        lineLimits = TextFieldLineLimits.SingleLine,
                        outputTransformation = passwordMask,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(Res.string.compress_password_hint),
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    Spacer(Modifier.height(10.dp))
                }
            }
            AnimatedVisibility(visible = current.format.supportsSplit) {
                Column {
                    TextField(
                        state = splitState,
                        label = stringResource(Res.string.compress_field_split),
                        modifier = Modifier.fillMaxWidth(),
                        useLabelAsPlaceholder = true,
                        lineLimits = TextFieldLineLimits.SingleLine,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                        ),
                        trailingIcon = {
                            Text(
                                text = stringResource(Res.string.compress_split_unit),
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier.padding(horizontal = 12.dp)
                            )
                        },
                    )
                    Spacer(Modifier.height(10.dp))
                }
            }
            CheckboxRow(
                title = stringResource(Res.string.compress_separate),
                checked = current.separateArchives,
                onCheckedChange = onSeparateChange,
            )
            Spacer(Modifier.height(10.dp))
            CheckboxRow(
                title = stringResource(Res.string.compress_delete_source),
                checked = current.deleteSource,
                onCheckedChange = onDeleteSourceChange,
            )
            Spacer(Modifier.height(10.dp))
            CheckboxRow(
                title = stringResource(Res.string.compress_to_other_pane),
                checked = current.toOtherPane,
                onCheckedChange = onToOtherPaneChange,
            )
            Spacer(Modifier.height(10.dp))
            CheckboxRow(
                title = stringResource(Res.string.compress_dialog_background),
                checked = current.background,
                onCheckedChange = onBackgroundChange,
            )
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                top.yukonga.miuix.kmp.basic.Button(
                    onClick = onConfirm,
                    colors = top.yukonga.miuix.kmp.basic.ButtonDefaults.buttonColorsPrimary(),
                    modifier = Modifier.weight(1f),
                    enabled = current.archiveName.isNotBlank(),
                ) {
                    Text(text = stringResource(Res.string.action_confirm))
                }
                top.yukonga.miuix.kmp.basic.Button(
                    onClick = onDismissRequest,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(text = stringResource(Res.string.action_cancel))
                }
            }
        }
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
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun archiveFormatLabel(format: ArchiveFormat): String = when (format) {
    ArchiveFormat.Zip -> stringResource(Res.string.compress_format_zip)
    ArchiveFormat.SevenZ -> stringResource(Res.string.compress_format_7z)
    ArchiveFormat.Tar -> stringResource(Res.string.compress_format_tar)
    ArchiveFormat.TarGz -> stringResource(Res.string.compress_format_tar_gz)
    ArchiveFormat.TarXz -> stringResource(Res.string.compress_format_tar_xz)
    ArchiveFormat.TarBz2 -> stringResource(Res.string.compress_format_tar_bz2)
    ArchiveFormat.TarZst -> stringResource(Res.string.compress_format_tar_zst)
    ArchiveFormat.Gzip -> stringResource(Res.string.compress_format_gzip)
    ArchiveFormat.Xz -> stringResource(Res.string.compress_format_xz)
    ArchiveFormat.Zstd -> stringResource(Res.string.compress_format_zstd)
}

@Composable
private fun compressionLevelLabel(level: CompressionLevel): String = when (level) {
    CompressionLevel.Store -> stringResource(Res.string.compress_level_store)
    CompressionLevel.Fastest -> stringResource(Res.string.compress_level_fastest)
    CompressionLevel.Fast -> stringResource(Res.string.compress_level_fast)
    CompressionLevel.Normal -> stringResource(Res.string.compress_level_normal)
    CompressionLevel.Maximum -> stringResource(Res.string.compress_level_maximum)
    CompressionLevel.Ultra -> stringResource(Res.string.compress_level_ultra)
}
