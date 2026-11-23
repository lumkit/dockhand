package io.github.dockhand.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import okio.Path.Companion.toPath
import java.io.File

/**
 * 创建 DataStore 实例。
 *
 * scope 由调用方持有（容器生命周期），不要在这里传 GlobalScope：
 * DataStore 在协程里做读写与 fsync，协程被取消就再也不会写入磁盘。
 */
fun createSettingsDataStore(
    file: File,
    scope: CoroutineScope,
): DataStore<Preferences> = PreferenceDataStoreFactory.createWithPath(
    scope = scope,
    produceFile = { file.absolutePath.toPath() },
)
