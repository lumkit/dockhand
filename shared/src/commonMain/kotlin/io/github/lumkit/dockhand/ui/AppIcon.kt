package io.github.lumkit.dockhand.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import dockhand.shared.generated.resources.Res
import dockhand.shared.generated.resources.ic_app_logo
import org.jetbrains.compose.resources.painterResource

/** 平台窗口图标；资源访问器只在 shared 模块内可见。 */
@Composable
fun appLogoPainter(): Painter = painterResource(Res.drawable.ic_app_logo)
