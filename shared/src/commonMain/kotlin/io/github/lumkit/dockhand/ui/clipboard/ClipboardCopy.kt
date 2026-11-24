package io.github.lumkit.dockhand.ui.clipboard

import androidx.compose.ui.platform.Clipboard

/** 把纯文本写入系统剪切板。 */
expect suspend fun Clipboard.copyText(text: String)
