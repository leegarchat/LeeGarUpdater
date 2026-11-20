package org.leegar.updater.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.leegar.updater.deviceinfo.DeviceInfoUtils
import kotlin.time.Duration.Companion.seconds

private const val APP_STATE_DATASTORE_NAME = "app_state"

private val Context.appStatePreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = APP_STATE_DATASTORE_NAME,
    corruptionHandler = ReplaceFileCorruptionHandler {
        emptyPreferences()
    },
)

private object AppStatePreferencesKeys {
    val LAST_CHECKED_TIMESTAMP = longPreferencesKey("last_checked_timestamp")
    val SUPPORT_FIRMWARE = stringPreferencesKey("support_firmware")
    val SUPPORT_FORUM = stringPreferencesKey("support_forum")
    val SUPPORT_MAINTAINER = stringPreferencesKey("support_maintainer")
    val SUPPORT_PAYPAL = stringPreferencesKey("support_paypal")
    val PROJECT_INFO_JSON = stringPreferencesKey("project_info_json")
}

class AppStateRepository(context: Context) {
    private val appState = context.applicationContext.appStatePreferencesDataStore
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    val hasEverCheckedFlow: Flow<Boolean> = appState.data.map { preferences ->
        preferences[AppStatePreferencesKeys.LAST_CHECKED_TIMESTAMP] != null
    }

    val lastCheckedTimestampFlow: Flow<Long> = appState.data.map { preferences ->
        preferences[AppStatePreferencesKeys.LAST_CHECKED_TIMESTAMP]
            ?: DeviceInfoUtils.buildDateTimestamp.seconds.inWholeMilliseconds
    }

    suspend fun setLastCheckedTimestamp(timestamp: Long) {
        appState.edit { preferences ->
            preferences[AppStatePreferencesKeys.LAST_CHECKED_TIMESTAMP] = timestamp
        }
    }

    val supportInfoFlow: Flow<SupportInfo> = appState.data.map { preferences ->
        SupportInfo(
            maintainer = preferences[AppStatePreferencesKeys.SUPPORT_MAINTAINER],
            forum = preferences[AppStatePreferencesKeys.SUPPORT_FORUM],
            firmware = preferences[AppStatePreferencesKeys.SUPPORT_FIRMWARE],
            paypal = preferences[AppStatePreferencesKeys.SUPPORT_PAYPAL],
        )
    }

    suspend fun setSupportInfo(supportInfo: SupportInfo) {
        appState.edit { preferences ->
            preferences.setOrRemove(AppStatePreferencesKeys.SUPPORT_MAINTAINER, supportInfo.maintainer)
            preferences.setOrRemove(AppStatePreferencesKeys.SUPPORT_FORUM, supportInfo.forum)
            preferences.setOrRemove(AppStatePreferencesKeys.SUPPORT_FIRMWARE, supportInfo.firmware)
            preferences.setOrRemove(AppStatePreferencesKeys.SUPPORT_PAYPAL, supportInfo.paypal)
        }
    }

    val projectInfoFlow: Flow<ProjectInfo> = appState.data.map { preferences ->
        val raw = preferences[AppStatePreferencesKeys.PROJECT_INFO_JSON]
        if (raw.isNullOrBlank()) {
            ProjectInfo(
                maintainer = preferences[AppStatePreferencesKeys.SUPPORT_MAINTAINER]
            )
        } else {
            runCatching { json.decodeFromString<ProjectInfo>(raw) }
                .getOrDefault(ProjectInfo())
        }
    }

    suspend fun setProjectInfo(projectInfo: ProjectInfo) {
        appState.edit { preferences ->
            preferences[AppStatePreferencesKeys.PROJECT_INFO_JSON] =
                json.encodeToString(projectInfo)
        }
    }

    private fun MutablePreferences.setOrRemove(key: Preferences.Key<String>, value: String?) {
        if (value != null) {
            this[key] = value
        } else {
            remove(key)
        }
    }
}