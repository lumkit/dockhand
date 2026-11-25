package io.github.lumkit.dockhand.core.app

import java.util.Locale

private val systemLocale: Locale = Locale.getDefault()

actual fun applyAppLanguage(language: AppLanguage) {
    val locale = when (language) {
        AppLanguage.System -> systemLocale
        AppLanguage.ChineseSimplified -> Locale.SIMPLIFIED_CHINESE
        AppLanguage.EnglishUS -> Locale.US
    }
    Locale.setDefault(locale)
}
