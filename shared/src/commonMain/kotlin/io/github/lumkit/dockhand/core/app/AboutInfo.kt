package io.github.lumkit.dockhand.core.app

const val APP_VERSION = "1.0.0"
const val PROJECT_URL = "https://github.com/lumkit/dockhand"

data class OpenSourceLibrary(
    val name: String,
    val version: String,
    val description: String,
    val author: String,
    val url: String,
)

val OPEN_SOURCE_LIBRARIES = listOf(
    OpenSourceLibrary(
        name = "Compose Multiplatform",
        version = "1.13.0-rc01",
        description = "Kotlin 跨平台声明式 UI 框架，用于构建桌面界面。",
        author = "JetBrains",
        url = "https://github.com/JetBrains/compose-multiplatform",
    ),
    OpenSourceLibrary(
        name = "Kotlin",
        version = "2.4.21",
        description = "项目使用的 Kotlin 语言与标准库。",
        author = "JetBrains",
        url = "https://github.com/JetBrains/kotlin",
    ),
    OpenSourceLibrary(
        name = "Miuix",
        version = "0.9.5-beta03",
        description = "面向 Compose Multiplatform 的 MIUI 风格组件库。",
        author = "YuKongA",
        url = "https://github.com/YuKongA/Miuix",
    ),
    OpenSourceLibrary(
        name = "Coil",
        version = "3.6.3",
        description = "Compose Multiplatform 图片加载库，用于缩略图。",
        author = "Coil Contributors",
        url = "https://github.com/coil-kt/coil",
    ),
    OpenSourceLibrary(
        name = "Room",
        version = "2.8.5",
        description = "AndroidX SQLite ORM，用于废纸篓历史等本地数据。",
        author = "Google / AndroidX",
        url = "https://github.com/androidx/androidx",
    ),
    OpenSourceLibrary(
        name = "DataStore",
        version = "1.3.0",
        description = "AndroidX 类型安全持久化存储，用于应用设置。",
        author = "Google / AndroidX",
        url = "https://github.com/androidx/androidx",
    ),
    OpenSourceLibrary(
        name = "kotlinx.coroutines",
        version = "1.11.0",
        description = "Kotlin 协程与异步任务调度库。",
        author = "JetBrains",
        url = "https://github.com/Kotlin/kotlinx.coroutines",
    ),
    OpenSourceLibrary(
        name = "kotlinx.datetime",
        version = "0.8.0",
        description = "Kotlin 跨平台日期时间库。",
        author = "JetBrains",
        url = "https://github.com/Kotlin/kotlinx-datetime",
    ),
    OpenSourceLibrary(
        name = "kotlinx.serialization",
        version = "1.13.0",
        description = "Kotlin 序列化库，用于结构化数据交换。",
        author = "JetBrains",
        url = "https://github.com/Kotlin/kotlinx.serialization",
    ),
    OpenSourceLibrary(
        name = "mtp-rs",
        version = "0.38",
        description = "Rust MTP 实现，用于 Android 设备文件传输。",
        author = "rust-mtp",
        url = "https://github.com/rust-mtp/mtp-rs",
    ),
)
