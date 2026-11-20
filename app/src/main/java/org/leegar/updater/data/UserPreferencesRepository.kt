package org.leegar.updater.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.leegar.updater.R
import org.leegar.updater.deviceinfo.DeviceInfoUtils
import org.leegar.updater.updatescheck.UpdatesCheckWorker
import java.io.IOException

private const val USER_PREFERENCES_DATASTORE_NAME = "user_prefs"

private val Context.userPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = USER_PREFERENCES_DATASTORE_NAME,
    corruptionHandler = ReplaceFileCorruptionHandler {
        emptyPreferences()
    },
)

enum class ThemeMode(val storageValue: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    companion object {
        fun fromStorageValue(value: String?): ThemeMode =
            entries.find { it.storageValue == value } ?: SYSTEM
    }
}

private object UserPreferencesKeys {
    val AB_PERF_MODE = booleanPreferencesKey("ab_perf_mode")
    val AUTO_DELETE = booleanPreferencesKey("auto_delete_updates")
    val CHECK_INTERVAL = stringPreferencesKey("check_interval")
    val METERED_NETWORK_WARNING = booleanPreferencesKey("metered_network_warning")
    val PERIODIC_CHECK_ENABLED = booleanPreferencesKey("periodic_check_enabled")
    val SHOW_LOGO = booleanPreferencesKey("show_logo")
    val SHOW_RELEASE_BANNER = booleanPreferencesKey("show_release_banner")
    val STREAM_UPDATES = booleanPreferencesKey("stream_updates")
    val UNSAFE_INSTALL = booleanPreferencesKey("unsafe_install_bypass")
    val FORCE_RECOVERY_INSTALL = booleanPreferencesKey("force_recovery_install")
    val VERIFY_HASHES = booleanPreferencesKey("verify_download_hashes")
    val CUSTOM_OTA_URL = stringPreferencesKey("custom_ota_url")
    val WAVY_PROGRESS = booleanPreferencesKey("wavy_progress_bar")
    val WAVY_AMPLITUDE = floatPreferencesKey("wavy_progress_amplitude")
    val BLUR_RADIUS = floatPreferencesKey("blur_radius_dp")
    val BLUR_TINT = floatPreferencesKey("blur_tint_alpha")
    val PARALLEL_SPLIT = booleanPreferencesKey("parallel_split_download")
    val THEME_MODE = stringPreferencesKey("theme_mode")
}

class UserPreferencesRepository(context: Context) {
    private val appContext = context.applicationContext
    private val userPreferences = appContext.userPreferencesDataStore
    private val userPreferencesFlow = userPreferences.data.catch { exception ->
        if (exception is IOException) emit(emptyPreferences()) else throw exception
    }

    val abPerfModeFlow: Flow<Boolean> = userPreferencesFlow.map {
        it[UserPreferencesKeys.AB_PERF_MODE] ?: appContext.resources.getBoolean(R.bool.def_ab_perf_mode)
    }
    suspend fun getAbPerfMode(): Boolean = abPerfModeFlow.first()
    fun getAbPerfModeBlocking(): Boolean = runBlocking { getAbPerfMode() }
    suspend fun setAbPerfMode(value: Boolean) {
        userPreferences.edit { it[UserPreferencesKeys.AB_PERF_MODE] = value }
    }

    val autoDeleteFlow: Flow<Boolean> = userPreferencesFlow.map {
        it[UserPreferencesKeys.AUTO_DELETE] ?: appContext.resources.getBoolean(R.bool.def_auto_delete_updates)
    }
    suspend fun getAutoDelete(): Boolean = autoDeleteFlow.first()
    fun getAutoDeleteBlocking(): Boolean = runBlocking { getAutoDelete() }
    suspend fun setAutoDelete(value: Boolean) {
        userPreferences.edit { it[UserPreferencesKeys.AUTO_DELETE] = value }
    }

    val meteredNetworkWarningFlow: Flow<Boolean> = userPreferencesFlow.map {
        it[UserPreferencesKeys.METERED_NETWORK_WARNING] ?: appContext.resources.getBoolean(R.bool.def_metered_network_warning)
    }
    suspend fun getMeteredNetworkWarning(): Boolean = meteredNetworkWarningFlow.first()
    fun getMeteredNetworkWarningBlocking(): Boolean = runBlocking { getMeteredNetworkWarning() }
    suspend fun setMeteredNetworkWarning(value: Boolean) {
        userPreferences.edit { it[UserPreferencesKeys.METERED_NETWORK_WARNING] = value }
    }

    val checkIntervalFlow: Flow<CheckInterval> = userPreferencesFlow.map {
        CheckInterval.fromStorageValue(
            it[UserPreferencesKeys.CHECK_INTERVAL]
                ?: appContext.resources.getString(R.string.def_check_interval),
        )
    }
    suspend fun getCheckInterval(): CheckInterval = checkIntervalFlow.first()
    suspend fun setCheckInterval(interval: CheckInterval) {
        userPreferences.edit { it[UserPreferencesKeys.CHECK_INTERVAL] = interval.storageValue }
        if (getPeriodicCheckEnabled()) {
            UpdatesCheckWorker.reschedulePeriodicCheck(appContext)
        }
    }

    val periodicCheckEnabledFlow: Flow<Boolean> = userPreferencesFlow.map {
        it[UserPreferencesKeys.PERIODIC_CHECK_ENABLED] ?: appContext.resources.getBoolean(R.bool.def_periodic_check_enabled)
    }
    suspend fun getPeriodicCheckEnabled(): Boolean = periodicCheckEnabledFlow.first()
    suspend fun setPeriodicCheckEnabled(enabled: Boolean) {
        userPreferences.edit { it[UserPreferencesKeys.PERIODIC_CHECK_ENABLED] = enabled }
        if (enabled) {
            UpdatesCheckWorker.schedulePeriodicCheck(appContext)
        } else {
            UpdatesCheckWorker.cancelPeriodicCheck(appContext)
        }
    }

    val streamUpdatesFlow: Flow<Boolean> = userPreferencesFlow.map {
        it[UserPreferencesKeys.STREAM_UPDATES] ?: appContext.resources.getBoolean(R.bool.def_stream_updates)
    }
    suspend fun getStreamUpdates(): Boolean = streamUpdatesFlow.first()
    fun getStreamUpdatesBlocking(): Boolean = runBlocking { getStreamUpdates() }
    suspend fun setStreamUpdates(value: Boolean) {
        userPreferences.edit { it[UserPreferencesKeys.STREAM_UPDATES] = value }
    }

    val showLogoFlow: Flow<Boolean> = userPreferencesFlow.map {
        it[UserPreferencesKeys.SHOW_LOGO] ?: appContext.resources.getBoolean(R.bool.def_show_logo)
    }
    suspend fun setShowLogo(value: Boolean) {
        userPreferences.edit { it[UserPreferencesKeys.SHOW_LOGO] = value }
    }

    val showReleaseBannerFlow: Flow<Boolean> = userPreferencesFlow.map {
        it[UserPreferencesKeys.SHOW_RELEASE_BANNER] ?: appContext.resources.getBoolean(R.bool.def_show_release_banner)
    }
    suspend fun setShowReleaseBanner(value: Boolean) {
        userPreferences.edit { it[UserPreferencesKeys.SHOW_RELEASE_BANNER] = value }
    }

    /** Critical option: bypasses downgrade/SDK checks and RecoverySystem.verifyPackage. */
    val unsafeInstallFlow: Flow<Boolean> = userPreferencesFlow.map {
        it[UserPreferencesKeys.UNSAFE_INSTALL] ?: appContext.resources.getBoolean(R.bool.def_unsafe_install)
    }
    suspend fun getUnsafeInstall(): Boolean = unsafeInstallFlow.first()
    fun getUnsafeInstallBlocking(): Boolean = runBlocking { getUnsafeInstall() }
    suspend fun setUnsafeInstall(value: Boolean) {
        userPreferences.edit {
            it[UserPreferencesKeys.UNSAFE_INSTALL] = value
            if (!value) it[UserPreferencesKeys.FORCE_RECOVERY_INSTALL] = false
        }
    }

    /** When unsafe install is ON, optionally flash via Recovery instead of update_engine. */
    val forceRecoveryInstallFlow: Flow<Boolean> = userPreferencesFlow.map {
        it[UserPreferencesKeys.FORCE_RECOVERY_INSTALL] ?: appContext.resources.getBoolean(R.bool.def_force_recovery_install)
    }
    suspend fun getForceRecoveryInstall(): Boolean = forceRecoveryInstallFlow.first()
    fun getForceRecoveryInstallBlocking(): Boolean = runBlocking { getForceRecoveryInstall() }
    suspend fun setForceRecoveryInstall(value: Boolean) {
        userPreferences.edit { it[UserPreferencesKeys.FORCE_RECOVERY_INSTALL] = value }
    }

    /** Verify per-part and final SHA-256 checksums when present in JSON. */
    val verifyHashesFlow: Flow<Boolean> = userPreferencesFlow.map {
        it[UserPreferencesKeys.VERIFY_HASHES] ?: appContext.resources.getBoolean(R.bool.def_verify_download_hashes)
    }
    suspend fun getVerifyHashes(): Boolean = verifyHashesFlow.first()
    fun getVerifyHashesBlocking(): Boolean = runBlocking { getVerifyHashes() }
    suspend fun setVerifyHashes(value: Boolean) {
        userPreferences.edit { it[UserPreferencesKeys.VERIFY_HASHES] = value }
    }

    val customOtaUrlFlow: Flow<String> = userPreferencesFlow.map {
        it[UserPreferencesKeys.CUSTOM_OTA_URL] ?: appContext.resources.getString(R.string.def_custom_ota_url)
    }
    suspend fun getCustomOtaUrl(): String = customOtaUrlFlow.first()
    fun getCustomOtaUrlBlocking(): String = runBlocking { getCustomOtaUrl() }
    suspend fun setCustomOtaUrl(url: String) {
        userPreferences.edit { it[UserPreferencesKeys.CUSTOM_OTA_URL] = url.trim() }
    }

    val wavyProgressFlow: Flow<Boolean> = userPreferencesFlow.map {
        it[UserPreferencesKeys.WAVY_PROGRESS] ?: appContext.resources.getBoolean(R.bool.def_wavy_progress)
    }
    suspend fun setWavyProgress(value: Boolean) {
        userPreferences.edit { it[UserPreferencesKeys.WAVY_PROGRESS] = value }
    }

    val wavyAmplitudeFlow: Flow<Float> = userPreferencesFlow.map {
        (it[UserPreferencesKeys.WAVY_AMPLITUDE] ?: appContext.resources.getInteger(R.integer.def_wavy_amplitude_percent) / 100f).coerceIn(0f, 1f)
    }
    suspend fun setWavyAmplitude(value: Float) {
        userPreferences.edit { it[UserPreferencesKeys.WAVY_AMPLITUDE] = value.coerceIn(0f, 1f) }
    }

    val blurRadiusFlow: Flow<Float> = userPreferencesFlow.map {
        (it[UserPreferencesKeys.BLUR_RADIUS] ?: appContext.resources.getInteger(R.integer.def_blur_radius_dp).toFloat()).coerceIn(0f, 150f)
    }
    suspend fun setBlurRadius(value: Float) {
        userPreferences.edit { it[UserPreferencesKeys.BLUR_RADIUS] = value.coerceIn(0f, 150f) }
    }

    val blurTintFlow: Flow<Float> = userPreferencesFlow.map {
        (it[UserPreferencesKeys.BLUR_TINT] ?: appContext.resources.getInteger(R.integer.def_blur_tint_percent) / 100f).coerceIn(0f, 1f)
    }
    suspend fun setBlurTint(value: Float) {
        userPreferences.edit { it[UserPreferencesKeys.BLUR_TINT] = value.coerceIn(0f, 1f) }
    }

    val parallelSplitFlow: Flow<Boolean> = userPreferencesFlow.map {
        it[UserPreferencesKeys.PARALLEL_SPLIT] ?: appContext.resources.getBoolean(R.bool.def_parallel_split_download)
    }
    suspend fun getParallelSplit(): Boolean = parallelSplitFlow.first()
    fun getParallelSplitBlocking(): Boolean = runBlocking { getParallelSplit() }
    suspend fun setParallelSplit(value: Boolean) {
        userPreferences.edit { it[UserPreferencesKeys.PARALLEL_SPLIT] = value }
    }

    val themeModeFlow: Flow<ThemeMode> = userPreferencesFlow.map {
        ThemeMode.fromStorageValue(
            it[UserPreferencesKeys.THEME_MODE]
                ?: appContext.resources.getString(R.string.def_theme_mode),
        )
    }
    suspend fun setThemeMode(mode: ThemeMode) {
        userPreferences.edit { it[UserPreferencesKeys.THEME_MODE] = mode.storageValue }
    }

    fun getRecoveryUpdateEnabled(): Boolean = DeviceInfoUtils.isRecoveryUpdateEnabled

    fun setRecoveryUpdateEnabled(enabled: Boolean) {
        DeviceInfoUtils.isRecoveryUpdateEnabled = enabled
    }
}