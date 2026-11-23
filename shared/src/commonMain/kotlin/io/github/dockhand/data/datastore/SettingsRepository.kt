package io.github.dockhand.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import java.io.IOException

/** DataStore 的 key 集中在这里，避免散落在各处。 */
internal object SettingsKeys {
    val ColorSchemeMode = stringPreferencesKey("color_scheme_mode")
}

class SettingsRepository(
    private val dataStore: DataStore<Preferences>,
) {
    /** 持久化的是 [ColorSchemeMode] 的枚举名（System / Light / Dark / Monet*）。 */
    val colorSchemeMode: Flow<ColorSchemeMode> = dataStore.data
        .catch { throwable -> if (throwable is IOException) emit(emptyPreferences()) else throw throwable }
        .map { prefs ->
            ColorSchemeMode.entries.firstOrNull { it.name == prefs[SettingsKeys.ColorSchemeMode] }
                ?: ColorSchemeMode.System
        }

    suspend fun setColorSchemeMode(mode: ColorSchemeMode) {
        dataStore.edit { prefs -> prefs[SettingsKeys.ColorSchemeMode] = mode.name }
    }
}
