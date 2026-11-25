package io.github.lumkit.dockhand.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import dockhand.shared.generated.resources.Res
import dockhand.shared.generated.resources.about_licenses_title
import io.github.lumkit.dockhand.core.app.OPEN_SOURCE_LIBRARIES
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.verticalScrollBar
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@OptIn(ExperimentalScrollBarApi::class)
@Composable
fun OpenSourceLicensesBottomSheet(
    show: Boolean,
    onDismissRequest: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    val scrollState = rememberScrollState()

    OverlayBottomSheet(
        show = show,
        title = stringResource(Res.string.about_licenses_title),
        onDismissRequest = onDismissRequest,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .verticalScrollBar(scrollState)
                .padding(16.dp),
        ) {
            OPEN_SOURCE_LIBRARIES.forEach { library ->
                ArrowPreference(
                    title = library.name + " " + library.version,
                    summary = library.description,
                    endActions = {
                        Text(
                            text = library.author,
                            color = MiuixTheme.colorScheme.primary,
                            style = MiuixTheme.textStyles.footnote2,
                        )
                    },
                    onClick = {
                        uriHandler.openUri(library.url)
                    }
                )
            }
        }
    }
}
