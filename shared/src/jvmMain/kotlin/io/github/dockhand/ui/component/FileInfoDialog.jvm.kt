package io.github.dockhand.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberDialogState
import androidx.compose.ui.window.rememberWindowState
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.MiuixDialogWindow
import top.yukonga.miuix.kmp.window.MiuixWindow

@OptIn(ExperimentalComposeUiApi::class)
@Composable
actual fun FileInfoDialogWindow(
    title: String,
    width: Dp,
    height: Dp,
    order: Int,
    onCloseRequest: () -> Unit,
    content: @Composable () -> Unit,
) {
    val offset = (order % 6) * 24
    val windowState = rememberWindowState(
        width = width,
        height = height,
        position = WindowPosition(
            x = (120 + offset).dp,
            y = (120 + offset).dp,
        ),
    )
    MiuixWindow(
        onCloseRequest = onCloseRequest,
        title = title,
        state = windowState,
        resizable = false,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MiuixTheme.colorScheme.surface)
        ) {
            content()
        }
    }
}
