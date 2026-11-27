package io.github.lumkit.dockhand.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dockhand.shared.generated.resources.Res
import dockhand.shared.generated.resources.task_cancelled
import dockhand.shared.generated.resources.task_completed
import dockhand.shared.generated.resources.task_current_progress
import dockhand.shared.generated.resources.task_dialog_background
import dockhand.shared.generated.resources.task_dialog_cancel
import dockhand.shared.generated.resources.task_overall_progress
import dockhand.shared.generated.resources.task_eta_days_hours
import dockhand.shared.generated.resources.task_eta_hours_minutes
import dockhand.shared.generated.resources.task_eta_less_than_second
import dockhand.shared.generated.resources.task_eta_minutes_seconds
import dockhand.shared.generated.resources.task_eta_seconds
import dockhand.shared.generated.resources.task_failed
import dockhand.shared.generated.resources.task_running
import dockhand.shared.generated.resources.task_speed
import dockhand.shared.generated.resources.task_speed_eta
import dockhand.shared.generated.resources.task_waiting
import io.github.lumkit.dockhand.task.AppTask
import io.github.lumkit.dockhand.task.TaskState
import io.github.lumkit.dockhand.ui.screen.home.formatFileSize
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.verticalScrollBar
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

@OptIn(ExperimentalScrollBarApi::class)
@Composable
fun TaskProgressDialog(
    task: AppTask?,
    onBackground: () -> Unit,
    onCancel: () -> Unit,
) {
    if (task == null) return
    val scrollState = rememberScrollState()
    val status = when (task.state) {
        TaskState.Waiting -> stringResource(Res.string.task_waiting)
        TaskState.Running -> stringResource(Res.string.task_running)
        TaskState.Completed -> stringResource(Res.string.task_completed)
        TaskState.Failed -> stringResource(Res.string.task_failed)
        TaskState.Cancelled -> stringResource(Res.string.task_cancelled)
    }
    val operation = task.title.substringBefore(' ')
    val summary = if (
        task.detail?.startsWith("到 ") == true ||
        task.detail?.startsWith("改为 ") == true
    ) {
        "${task.title} ${task.detail}"
    } else {
        task.title
    }
    OverlayDialog(
        show = true,
        title = operation,
        summary = summary,
        onDismissRequest = {},
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(
                modifier = Modifier.fillMaxWidth()
                    .weight(1f, false)
                    .verticalScrollBar(scrollState)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
            Text(
                text = task.error ?: status,
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            if (task.state == TaskState.Running) {
                if (task.isBatch) {
                    val index = (task.overallProgress.completed ?: 0L)
                        .toInt()
                        .coerceIn(0, task.itemNames.lastIndex.coerceAtLeast(0))
                    val currentItem = task.itemNames.getOrNull(index)
                    Text(
                        text = currentItem?.let { "$operation $it" }
                            ?: stringResource(Res.string.task_current_progress),
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    LinearProgressIndicator(
                        progress = task.currentProgress.fraction,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = stringResource(Res.string.task_overall_progress),
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    LinearProgressIndicator(
                        progress = task.overallProgress.fraction,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    LinearProgressIndicator(
                        progress = task.currentProgress.fraction ?: task.progress.fraction,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                val speed = task.speedBytesPerSecond
                if (speed != null) {
                    val speedText = formatFileSize(speed.toLong())
                    Text(
                        text = task.etaSeconds?.let { eta ->
                            stringResource(
                                Res.string.task_speed_eta,
                                speedText,
                                formatEta(eta),
                            )
                        } ?: stringResource(Res.string.task_speed, speedText),
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            } else if (task.state == TaskState.Waiting) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(
                    text = task.waitingMessage ?: stringResource(Res.string.task_waiting),
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(
                    text = stringResource(Res.string.task_dialog_background),
                    onClick = onBackground,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
                TextButton(
                    text = stringResource(Res.string.task_dialog_cancel),
                    onClick = onCancel,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun formatEta(seconds: Long): String {
    val value = seconds.coerceAtLeast(0)
    return when {
        value <= 0 -> stringResource(Res.string.task_eta_less_than_second)
        value < 60 -> stringResource(Res.string.task_eta_seconds, value)
        value < 3600 -> stringResource(
            Res.string.task_eta_minutes_seconds,
            value / 60,
            value % 60,
        )
        value < 86_400 -> stringResource(
            Res.string.task_eta_hours_minutes,
            value / 3600,
            (value % 3600) / 60,
        )
        else -> stringResource(
            Res.string.task_eta_days_hours,
            value / 86_400,
            (value % 86_400) / 3600,
        )
    }
}