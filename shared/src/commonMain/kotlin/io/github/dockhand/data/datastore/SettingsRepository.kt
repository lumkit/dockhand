package io.github.dockhand.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import io.github.dockhand.domain.model.DEFAULT_MTP_SHORTCUT_ORDER
import io.github.dockhand.domain.model.FilePlaceGroup
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import java.io.IOException

/** DataStore 的 key 集中在这里，避免散落在各处。 */
internal object SettingsKeys {
    val ColorSchemeMode = stringPreferencesKey("color_scheme_mode")

    fun drawerPlaceOrder(group: FilePlaceGroup): Preferences.Key<String> =
        stringPreferencesKey("drawer_place_order_${group.name.lowercase()}")

    val MtpExpandedSources = stringPreferencesKey("mtp_expanded_sources")
    val MtpShortcutOrder = stringPreferencesKey("mtp_shortcut_order")
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

    /**
     * 每个侧边栏分组内部的地点顺序，值为保存的 place id 列表。
     */
    val drawerPlaceOrder: Flow<Map<FilePlaceGroup, List<String>>> = dataStore.data
        .catch { throwable -> if (throwable is IOException) emit(emptyPreferences()) else throw throwable }
        .map { prefs ->
            FilePlaceGroup.entries.associateWith { group ->
                prefs[SettingsKeys.drawerPlaceOrder(group)]
                    ?.split(PLACE_ORDER_SEPARATOR)
                    ?.filter { it.isNotEmpty() }
                    .orEmpty()
            }
        }

    suspend fun setDrawerPlaceOrder(group: FilePlaceGroup, placeIds: List<String>) {
        dataStore.edit { prefs ->
            prefs[SettingsKeys.drawerPlaceOrder(group)] = placeIds.joinToString(PLACE_ORDER_SEPARATOR)
        }
    }

    /** 已展开的 MTP 存储 source id 集合；默认全部折叠。 */
    val mtpExpandedSources: Flow<Set<String>> = dataStore.data
        .catch { throwable -> if (throwable is IOException) emit(emptyPreferences()) else throw throwable }
        .map { prefs ->
            prefs[SettingsKeys.MtpExpandedSources]
                ?.split(PLACE_ORDER_SEPARATOR)
                ?.filter { it.isNotEmpty() }
                ?.toSet()
                .orEmpty()
        }

    suspend fun setMtpExpandedSources(sources: Set<String>) {
        dataStore.edit { prefs ->
            prefs[SettingsKeys.MtpExpandedSources] = sources.joinToString(PLACE_ORDER_SEPARATOR)
        }
    }

    /** MTP 快捷入口的全局顺序；新增入口自动补在末尾。 */
    val mtpShortcutOrder: Flow<List<String>> = dataStore.data
        .catch { throwable -> if (throwable is IOException) emit(emptyPreferences()) else throw throwable }
        .map { prefs ->
            val saved = prefs[SettingsKeys.MtpShortcutOrder]
                ?.split(PLACE_ORDER_SEPARATOR)
                ?.filter { it.isNotEmpty() }
                .orEmpty()
                .distinct()
            saved + DEFAULT_MTP_SHORTCUT_ORDER.filterNot { it in saved }
        }

    suspend fun setMtpShortcutOrder(order: List<String>) {
        dataStore.edit { prefs ->
            prefs[SettingsKeys.MtpShortcutOrder] = order.joinToString(PLACE_ORDER_SEPARATOR)
        }
    }

    private companion object {
        const val PLACE_ORDER_SEPARATOR = "\u001F"
    }
}
