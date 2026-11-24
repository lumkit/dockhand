package io.github.lumkit.dockhand.ui.component

/** 文件列表右键菜单的抽象动作；具体实现由上层接入。 */
enum class FileContextAction {
    ViewInfo,
    Copy,
    Move,
    Trash,
    Restore,
    Delete,
    Rename,
    Compress,
    TerminalTab,
    TerminalWindow,
}
