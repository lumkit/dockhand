package io.github.lumkit.dockhand.core.app

/** 应用显示语言。System 使用当前系统语言，缺少翻译时回退到默认中文。 */
enum class AppLanguage {
    System,
    ChineseSimplified,
    EnglishUS,
}

expect fun applyAppLanguage(language: AppLanguage)
