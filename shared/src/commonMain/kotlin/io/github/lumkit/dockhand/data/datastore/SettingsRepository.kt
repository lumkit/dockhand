package io.github.lumkit.dockhand.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import io.github.lumkit.dockhand.domain.model.DEFAULT_MTP_SHORTCUT_ORDER
import io.github.lumkit.dockhand.core.app.AppLanguage
import io.github.lumkit.dockhand.domain.model.FileLayoutMode
import io.github.lumkit.dockhand.domain.model.FilePlaceGroup
import io.github.lumkit.dockhand.domain.model.FileSortMode
import io.github.lumkit.dockhand.domain.model.WindowSize
import io.github.lumkit.dockhand.shortcut.DefaultShortcutBindings
import io.github.lumkit.dockhand.shortcut.ShortcutAction
import io.github.lumkit.dockhand.shortcut.ShortcutKey
import io.github.lumkit.dockhand.shortcut.decodeShortcuts
import io.github.lumkit.dockhand.shortcut.encodeShortcuts
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import java.io.IOException

/** DataStore 的 key 集中在这里，避免散落在各处。 */
internal object SettingsKeys {
    val ColorSchemeMode = stringPreferencesKey("color_scheme_mode")

    fun drawerPlaceOrder(group: FilePlaceGroup): Preferences.Key<String> =
        stringPreferencesKey("drawer_place_order_${group.name.lowercase()}")

    val MtpExpandedSources = stringPreferencesKey("mtp_expanded_sources")
    val MtpShortcutOrder = stringPreferencesKey("mtp_shortcut_order")
    val RestoreWindowSize = booleanPreferencesKey("restore_window_size")
    val WindowWidth = intPreferencesKey("window_width_dp")
    val WindowHeight = intPreferencesKey("window_height_dp")
    val InterfaceScale = intPreferencesKey("interface_scale")
    val KeyColor = intPreferencesKey("key_color")
    val ShowHiddenItems = booleanPreferencesKey("show_hidden_items")
    val AppLanguage = stringPreferencesKey("app_language")
    val FileLayoutMode = stringPreferencesKey("file_layout_mode")
    val FileSortMode = stringPreferencesKey("file_sort_mode")

    fun startupPath(pane: String): Preferences.Key<String> =
        stringPreferencesKey("startup_path_${pane.lowercase()}")

    fun lastLocation(pane: String): Preferences.Key<String> =
        stringPreferencesKey("last_location_${pane.lowercase()}")

    fun shortcut(action: ShortcutAction): Preferences.Key<String> =
        stringPreferencesKey("shortcut_${action.name.lowercase()}")
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

    /** 启动路径：STARTUP_LAST 表示恢复上次路径，否则为 FilePlace.id。 */
    fun startupPath(pane: String): Flow<String> = dataStore.data
        .catch { throwable -> if (throwable is IOException) emit(emptyPreferences()) else throw throwable }
        .map { prefs -> prefs[SettingsKeys.startupPath(pane)] ?: STARTUP_DEFAULT }

    suspend fun setStartupPath(pane: String, value: String) {
        dataStore.edit { prefs -> prefs[SettingsKeys.startupPath(pane)] = value }
    }

    /** 上次离开时的 location，编码为 sourceId + 分隔符 + path。 */
    fun lastLocation(pane: String): Flow<SavedLocation?> = dataStore.data
        .catch { throwable -> if (throwable is IOException) emit(emptyPreferences()) else throw throwable }
        .map { prefs ->
            val raw = prefs[SettingsKeys.lastLocation(pane)] ?: return@map null
            val separator = raw.indexOf(PLACE_ORDER_SEPARATOR)
            if (separator <= 0) return@map null
            SavedLocation(
                sourceId = raw.substring(0, separator),
                path = raw.substring(separator + 1),
            )
        }

    val showHiddenItems: Flow<Boolean> = dataStore.data
        .catch { throwable -> if (throwable is IOException) emit(emptyPreferences()) else throw throwable }
        .map { prefs -> prefs[SettingsKeys.ShowHiddenItems] ?: false }

    suspend fun setShowHiddenItems(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[SettingsKeys.ShowHiddenItems] = enabled }
    }

    val language: Flow<AppLanguage> = dataStore.data
        .catch { throwable -> if (throwable is IOException) emit(emptyPreferences()) else throw throwable }
        .map { prefs ->
            AppLanguage.entries.firstOrNull { it.name == prefs[SettingsKeys.AppLanguage] }
                ?: AppLanguage.System
        }

    suspend fun setLanguage(language: AppLanguage) {
        dataStore.edit { prefs -> prefs[SettingsKeys.AppLanguage] = language.name }
    }

    val fileLayoutMode: Flow<FileLayoutMode> = dataStore.data
        .catch { throwable -> if (throwable is IOException) emit(emptyPreferences()) else throw throwable }
        .map { prefs ->
            FileLayoutMode.entries.firstOrNull { it.name == prefs[SettingsKeys.FileLayoutMode] }
                ?: FileLayoutMode.List
        }

    suspend fun setFileLayoutMode(mode: FileLayoutMode) {
        dataStore.edit { prefs -> prefs[SettingsKeys.FileLayoutMode] = mode.name }
    }

    val fileSortMode: Flow<FileSortMode> = dataStore.data
        .catch { throwable -> if (throwable is IOException) emit(emptyPreferences()) else throw throwable }
        .map { prefs ->
            FileSortMode.entries.firstOrNull { it.name == prefs[SettingsKeys.FileSortMode] }
                ?: FileSortMode.NameAsc
        }

    suspend fun setFileSortMode(mode: FileSortMode) {
        dataStore.edit { prefs -> prefs[SettingsKeys.FileSortMode] = mode.name }
    }

    val shortcuts: Flow<Map<ShortcutAction, List<ShortcutKey>>> = dataStore.data
        .catch { throwable -> if (throwable is IOException) emit(emptyPreferences()) else throw throwable }
        .map { prefs ->
            ShortcutAction.entries.associateWith { action ->
                val raw = prefs[SettingsKeys.shortcut(action)]
                if (raw == null) {
                    listOf(DefaultShortcutBindings.getValue(action))
                } else {
                    decodeShortcuts(raw)
                }
            }
        }

    suspend fun setShortcuts(action: ShortcutAction, keys: List<ShortcutKey>) {
        dataStore.edit { prefs ->
            prefs[SettingsKeys.shortcut(action)] = encodeShortcuts(keys)
        }
    }

    suspend fun resetShortcuts() {
        dataStore.edit { prefs ->
            ShortcutAction.entries.forEach { prefs.remove(SettingsKeys.shortcut(it)) }
        }
    }

    val interfaceScale: Flow<Int> = dataStore.data
        .catch { throwable -> if (throwable is IOException) emit(emptyPreferences()) else throw throwable }
        .map { prefs -> (prefs[SettingsKeys.InterfaceScale] ?: 100).coerceIn(80, 110) }

    suspend fun setInterfaceScale(value: Int) {
        dataStore.edit { prefs -> prefs[SettingsKeys.InterfaceScale] = value.coerceIn(80, 110) }
    }

    val keyColor: Flow<Color?> = dataStore.data
        .catch { throwable -> if (throwable is IOException) emit(emptyPreferences()) else throw throwable }
        .map { prefs -> prefs[SettingsKeys.KeyColor]?.let { Color(it) } }

    suspend fun setKeyColor(color: Color) {
        dataStore.edit { prefs -> prefs[SettingsKeys.KeyColor] = color.toArgb() }
    }

    val restoreWindowSize: Flow<Boolean> = dataStore.data
        .catch { throwable -> if (throwable is IOException) emit(emptyPreferences()) else throw throwable }
        .map { prefs -> prefs[SettingsKeys.RestoreWindowSize] ?: true }

    suspend fun setRestoreWindowSize(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[SettingsKeys.RestoreWindowSize] = enabled }
    }

    val windowSize: Flow<WindowSize?> = dataStore.data
        .catch { throwable -> if (throwable is IOException) emit(emptyPreferences()) else throw throwable }
        .map { prefs ->
            val width = prefs[SettingsKeys.WindowWidth] ?: return@map null
            val height = prefs[SettingsKeys.WindowHeight] ?: return@map null
            if (width <= 0 || height <= 0) null else WindowSize(width, height)
        }

    suspend fun setWindowSize(size: WindowSize) {
        dataStore.edit { prefs ->
            prefs[SettingsKeys.WindowWidth] = size.widthDp
            prefs[SettingsKeys.WindowHeight] = size.heightDp
        }
    }

    suspend fun setLastLocation(pane: String, sourceId: String, path: String) {
        dataStore.edit { prefs ->
            prefs[SettingsKeys.lastLocation(pane)] =
                sourceId + PLACE_ORDER_SEPARATOR + path
        }
    }

    companion object {
        const val STARTUP_LAST = "__last__"
        const val STARTUP_DEFAULT = "local:desktop"
        private const val PLACE_ORDER_SEPARATOR = "\u001F"
    }
}

data class SavedLocation(
    val sourceId: String,
    val path: String,
)
