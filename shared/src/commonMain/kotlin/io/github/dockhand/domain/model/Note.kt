package io.github.dockhand.domain.model

/** 领域层的笔记模型，与 Room 的实体解耦。 */
data class Note(
    val id: Long,
    val text: String,
)
