package io.github.lumkit.dockhand.core.platform

import io.github.lumkit.dockhand.domain.model.FileLocation

/** 用系统默认程序打开本地文件。 */
data class ExternalApplication(
    val name: String,
    val path: String,
    val iconPath: String? = null,
)

interface FileOpener {
    suspend fun open(location: FileLocation)

    suspend fun applications(location: FileLocation): List<ExternalApplication> = emptyList()

    suspend fun openWith(location: FileLocation, application: ExternalApplication) {
        error("当前平台不支持指定应用打开文件")
    }

    suspend fun icon(application: ExternalApplication): String? = null
}

object NoopFileOpener : FileOpener {
    override suspend fun open(location: FileLocation) {
        error("当前平台不支持打开文件")
    }
}
