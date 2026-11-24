package io.github.lumkit.dockhand.domain.model

/** 文件系统层可以展示给用户的错误。 */
class FileBrowserException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
