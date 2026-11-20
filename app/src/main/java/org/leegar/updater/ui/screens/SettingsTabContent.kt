package org.leegar.updater.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.leegar.updater.data.CheckInterval
import org.leegar.updater.data.ThemeMode
import org.leegar.updater.data.UserPreferencesRepository
import org.leegar.updater.R
import org.leegar.updater.deviceinfo.DeviceInfoUtils
import org.leegar.updater.ui.media.UpdaterMediaCache
import org.leegar.updater.ui.pep.SliderSetting
import org.leegar.updater.ui.pep.SecondaryActionPill
import org.leegar.updater.ui.pep.UpdaterProgressIndicator
import org.leegar.updater.ui.pep.UpdaterSettingsGroupCard
import org.leegar.updater.ui.pep.UpdaterSwitchRow
import org.leegar.updater.ui.pep.UpdaterTabs
import androidx.compose.ui.res.stringResource
import org.leegar.updater.ui.pep.edgeOutline
import kotlin.math.roundToInt
import org.leegar.updater.util.BatteryMonitor
import java.io.File

@Composable
fun SettingsTabContent(
    repository: UserPreferencesRepository,
    batteryMonitor: BatteryMonitor,
    listState: LazyListState,
    pagePadding: PaddingValues,
    onOtaUrlChanged: () -> Unit,
    onClearCaches: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val isABDevice = remember { DeviceInfoUtils.isABDevice }
    val showRecoveryUpdate = remember { File("/vendor/bin/install-recovery.sh").exists() }

    val periodicCheckEnabled by repository.periodicCheckEnabledFlow.collectAsStateWithLifecycle(context.resources.getBoolean(R.bool.def_periodic_check_enabled))
    val checkInterval by repository.checkIntervalFlow.collectAsStateWithLifecycle(CheckInterval.fromStorageValue(context.resources.getString(R.string.def_check_interval)))
    val verifyHashes by repository.verifyHashesFlow.collectAsStateWithLifecycle(context.resources.getBoolean(R.bool.def_verify_download_hashes))
    val meteredWarning by repository.meteredNetworkWarningFlow.collectAsStateWithLifecycle(context.resources.getBoolean(R.bool.def_metered_network_warning))
    val streamUpdates by repository.streamUpdatesFlow.collectAsStateWithLifecycle(context.resources.getBoolean(R.bool.def_stream_updates))
    val autoDelete by repository.autoDeleteFlow.collectAsStateWithLifecycle(context.resources.getBoolean(R.bool.def_auto_delete_updates))
    val abPerfMode by repository.abPerfModeFlow.collectAsStateWithLifecycle(context.resources.getBoolean(R.bool.def_ab_perf_mode))
    val showLogo by repository.showLogoFlow.collectAsStateWithLifecycle(context.resources.getBoolean(R.bool.def_show_logo))
    val showReleaseBanner by repository.showReleaseBannerFlow.collectAsStateWithLifecycle(context.resources.getBoolean(R.bool.def_show_release_banner))
    val wavyProgress by repository.wavyProgressFlow.collectAsStateWithLifecycle(context.resources.getBoolean(R.bool.def_wavy_progress))
    val wavyAmplitude by repository.wavyAmplitudeFlow.collectAsStateWithLifecycle(context.resources.getInteger(R.integer.def_wavy_amplitude_percent) / 100f)
    val blurRadius by repository.blurRadiusFlow.collectAsStateWithLifecycle(context.resources.getInteger(R.integer.def_blur_radius_dp).toFloat())
    val blurTint by repository.blurTintFlow.collectAsStateWithLifecycle(context.resources.getInteger(R.integer.def_blur_tint_percent) / 100f)
    val parallelSplit by repository.parallelSplitFlow.collectAsStateWithLifecycle(context.resources.getBoolean(R.bool.def_parallel_split_download))
    val themeMode by repository.themeModeFlow.collectAsStateWithLifecycle(ThemeMode.fromStorageValue(context.resources.getString(R.string.def_theme_mode)))
    val unsafeInstall by repository.unsafeInstallFlow.collectAsStateWithLifecycle(context.resources.getBoolean(R.bool.def_unsafe_install))
    val forceRecovery by repository.forceRecoveryInstallFlow.collectAsStateWithLifecycle(context.resources.getBoolean(R.bool.def_force_recovery_install))
    val savedOtaUrl by repository.customOtaUrlFlow.collectAsStateWithLifecycle(context.resources.getString(R.string.def_custom_ota_url))
    val batteryState by batteryMonitor.batteryState.collectAsStateWithLifecycle(
        batteryMonitor.currentBatteryState,
    )
    var recoveryUpdateEnabled by remember { mutableStateOf(repository.getRecoveryUpdateEnabled()) }

    var urlDraft by remember { mutableStateOf("") }
    LaunchedEffect(savedOtaUrl) {
        if (urlDraft.isEmpty() || urlDraft == savedOtaUrl) {
            urlDraft = savedOtaUrl
        }
    }

    var unsafeConfirmVisible by remember { mutableStateOf(false) }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = pagePadding,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // 1. Background Sync
        item(key = "pref_sync") {
            UpdaterSettingsGroupCard(title = stringResource(R.string.pref_category_background_sync)) {
                UpdaterSwitchRow(
                    title = stringResource(R.string.menu_auto_updates_check),
                    summary = stringResource(R.string.menu_auto_updates_check_summary),
                    checked = periodicCheckEnabled,
                    onCheckedChange = { scope.launch { repository.setPeriodicCheckEnabled(it) } },
                )
                AnimatedVisibility(
                    visible = periodicCheckEnabled,
                    enter = expandVertically(tween(220, easing = FastOutSlowInEasing)) + fadeIn(tween(180)),
                    exit = shrinkVertically(tween(220, easing = FastOutSlowInEasing)) + fadeOut(tween(140)),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.settings_check_interval),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 20.dp),
                        )
                        UpdaterTabs(
                            titles = listOf(stringResource(R.string.interval_daily), stringResource(R.string.interval_weekly), stringResource(R.string.interval_monthly)),
                            position = checkInterval.ordinal.toFloat(),
                            onTabClick = { idx ->
                                val next = CheckInterval.entries.getOrElse(idx) { CheckInterval.default }
                                scope.launch { repository.setCheckInterval(next) }
                            },
                        )
                    }
                }
            }
        }

        // 2. Downloads & Verification
        item(key = "pref_downloads") {
            UpdaterSettingsGroupCard(title = stringResource(R.string.settings_group_downloads)) {
                UpdaterSwitchRow(
                    title = stringResource(R.string.settings_parallel_title),
                    summary = stringResource(R.string.settings_parallel_summary),
                    checked = parallelSplit,
                    onCheckedChange = { scope.launch { repository.setParallelSplit(it) } },
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                UpdaterSwitchRow(
                    title = stringResource(R.string.settings_verify_title),
                    summary = stringResource(R.string.settings_verify_summary),
                    checked = verifyHashes,
                    onCheckedChange = { scope.launch { repository.setVerifyHashes(it) } },
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                UpdaterSwitchRow(
                    title = stringResource(R.string.menu_metered_network_warning),
                    summary = stringResource(R.string.menu_metered_network_warning_summary),
                    checked = meteredWarning,
                    onCheckedChange = { scope.launch { repository.setMeteredNetworkWarning(it) } },
                )
                if (isABDevice) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                    UpdaterSwitchRow(
                        title = stringResource(R.string.menu_stream_updates),
                        summary = stringResource(R.string.menu_stream_updates_summary),
                        checked = streamUpdates,
                        onCheckedChange = { scope.launch { repository.setStreamUpdates(it) } },
                    )
                }
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                UpdaterSwitchRow(
                    title = stringResource(R.string.menu_auto_delete_updates),
                    summary = stringResource(R.string.settings_autodelete_summary),
                    checked = autoDelete,
                    onCheckedChange = { scope.launch { repository.setAutoDelete(it) } },
                )
            }
        }

        // 3. Installation Options
        if (isABDevice || showRecoveryUpdate) {
            item(key = "pref_install") {
                UpdaterSettingsGroupCard(title = stringResource(R.string.settings_group_installation)) {
                    if (isABDevice) {
                        UpdaterSwitchRow(
                            title = stringResource(R.string.menu_ab_perf_mode),
                            summary = if (batteryState.isAcCharging) {
                                stringResource(R.string.settings_ab_perf_charging)
                            } else {
                                stringResource(R.string.settings_ab_perf_summary)
                            },
                            checked = batteryState.isAcCharging || abPerfMode,
                            enabled = !batteryState.isAcCharging,
                            onCheckedChange = { scope.launch { repository.setAbPerfMode(it) } },
                        )
                    }
                    if (showRecoveryUpdate) {
                        if (isABDevice) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                                modifier = Modifier.padding(horizontal = 20.dp),
                            )
                        }
                        UpdaterSwitchRow(
                            title = stringResource(R.string.menu_update_recovery),
                            summary = stringResource(R.string.menu_update_recovery_summary),
                            checked = recoveryUpdateEnabled,
                            onCheckedChange = {
                                recoveryUpdateEnabled = it
                                repository.setRecoveryUpdateEnabled(it)
                            },
                        )
                    }
                }
            }
        }

        // 4. OTA Feed URL override
        item(key = "pref_ota_source") {
            UpdaterSettingsGroupCard(title = stringResource(R.string.settings_group_ota)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = stringResource(R.string.settings_ota_url_title),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.settings_ota_url_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val inputBg = MaterialTheme.colorScheme.surfaceContainerLow
                    val inputShape = RoundedCornerShape(14.dp)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(inputShape)
                            .background(inputBg)
                            .border(1.dp, inputBg.edgeOutline(), inputShape)
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                    ) {
                        val defaultOtaHint = remember {
                            val template = context.getString(R.string.updater_server_url)
                            val device = DeviceInfoUtils.device.trim()
                            if (device.isNotEmpty()) template.replace("{device}", device) else template
                        }
                        if (urlDraft.isEmpty()) {
                            Text(
                                text = defaultOtaHint,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        BasicTextField(
                            value = urlDraft,
                            onValueChange = { urlDraft = it },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SecondaryActionPill(
                            icon = Icons.Rounded.RestartAlt,
                            label = stringResource(R.string.action_reset),
                            enabled = savedOtaUrl.isNotEmpty() || urlDraft.isNotEmpty(),
                            onClick = {
                                urlDraft = ""
                                scope.launch {
                                    repository.setCustomOtaUrl("")
                                    onOtaUrlChanged()
                                }
                                Toast.makeText(context, context.getString(R.string.toast_ota_reset), Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f),
                        )
                        SecondaryActionPill(
                            icon = Icons.Rounded.Check,
                            label = stringResource(R.string.settings_ota_apply),
                            enabled = urlDraft.trim() != savedOtaUrl,
                            onClick = {
                                val clean = urlDraft.trim()
                                scope.launch {
                                    repository.setCustomOtaUrl(clean)
                                    onOtaUrlChanged()
                                }
                                Toast.makeText(context, context.getString(R.string.toast_ota_updated), Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1.3f),
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }
        }

        // 5. Appearance & Cache
        item(key = "pref_appearance") {
            UpdaterSettingsGroupCard(title = stringResource(R.string.settings_group_appearance)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.settings_theme_title),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                    UpdaterTabs(
                        titles = listOf(stringResource(R.string.theme_system), stringResource(R.string.theme_light), stringResource(R.string.theme_dark)),
                        position = themeMode.ordinal.toFloat(),
                        onTabClick = { idx ->
                            val next = ThemeMode.entries.getOrElse(idx) { ThemeMode.SYSTEM }
                            scope.launch { repository.setThemeMode(next) }
                        },
                    )
                }
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                UpdaterSwitchRow(
                    title = stringResource(R.string.settings_wavy_title),
                    summary = stringResource(R.string.settings_wavy_summary),
                    checked = wavyProgress,
                    onCheckedChange = { scope.launch { repository.setWavyProgress(it) } },
                )
                AnimatedVisibility(
                    visible = wavyProgress,
                    enter = expandVertically(tween(220, easing = FastOutSlowInEasing)) + fadeIn(tween(180)),
                    exit = shrinkVertically(tween(220, easing = FastOutSlowInEasing)) + fadeOut(tween(140)),
                ) {
                    val wavyAmplitudePercent = (wavyAmplitude * 100f).roundToInt().coerceIn(0, 100)
                    val defAmplitudePercent = remember { context.resources.getInteger(R.integer.def_wavy_amplitude_percent) }
                    Column(modifier = Modifier.fillMaxWidth()) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                            modifier = Modifier.padding(horizontal = 20.dp),
                        )
                        SliderSetting(
                            title = stringResource(R.string.settings_amplitude_title),
                            value = wavyAmplitudePercent,
                            range = 0..100,
                            unit = stringResource(R.string.settings_unit_percent),
                            enabled = wavyProgress,
                            valueText = stringResource(R.string.settings_percent_format, wavyAmplitudePercent),
                            defaultValue = defAmplitudePercent,
                            defaultValueText = stringResource(R.string.settings_percent_format, defAmplitudePercent),
                            onValueChange = { v -> scope.launch { repository.setWavyAmplitude(v / 100f) } },
                            onDefault = { scope.launch { repository.setWavyAmplitude(defAmplitudePercent / 100f) } },
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
                        ) {
                            UpdaterProgressIndicator(
                                progress = 0.65f,
                                wavy = wavyProgress,
                                isActive = true,
                                wavyAmplitude = wavyAmplitude,
                            )
                        }
                    }
                }
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                val blurRadiusInt = blurRadius.roundToInt().coerceIn(0, 150)
                val defBlurDp = remember { context.resources.getInteger(R.integer.def_blur_radius_dp) }
                SliderSetting(
                    title = stringResource(R.string.settings_blur_title),
                    value = blurRadiusInt,
                    range = 0..150,
                    unit = stringResource(R.string.settings_unit_dp),
                    enabled = true,
                    valueText = if (blurRadiusInt == 0) stringResource(R.string.settings_value_off) else stringResource(R.string.settings_dp_format, blurRadiusInt),
                    defaultValue = defBlurDp,
                    defaultValueText = stringResource(R.string.settings_dp_format, defBlurDp),
                    onValueChange = { v -> scope.launch { repository.setBlurRadius(v.toFloat()) } },
                    onDefault = { scope.launch { repository.setBlurRadius(defBlurDp.toFloat()) } },
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                val blurTintPercent = (blurTint * 100f).roundToInt().coerceIn(0, 100)
                val defTintPercent = remember { context.resources.getInteger(R.integer.def_blur_tint_percent) }
                SliderSetting(
                    title = stringResource(R.string.settings_tint_title),
                    value = blurTintPercent,
                    range = 0..100,
                    unit = stringResource(R.string.settings_unit_percent),
                    enabled = true,
                    valueText = stringResource(R.string.settings_percent_format, blurTintPercent),
                    defaultValue = defTintPercent,
                    defaultValueText = stringResource(R.string.settings_percent_format, defTintPercent),
                    onValueChange = { v -> scope.launch { repository.setBlurTint(v / 100f) } },
                    onDefault = { scope.launch { repository.setBlurTint(defTintPercent / 100f) } },
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                UpdaterSwitchRow(
                    title = stringResource(R.string.settings_logo_title),
                    summary = stringResource(R.string.settings_logo_summary),
                    checked = showLogo,
                    onCheckedChange = { scope.launch { repository.setShowLogo(it) } },
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                UpdaterSwitchRow(
                    title = stringResource(R.string.settings_banner_title),
                    summary = stringResource(R.string.settings_banner_summary),
                    checked = showReleaseBanner,
                    onCheckedChange = { scope.launch { repository.setShowReleaseBanner(it) } },
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                ) {
                    SecondaryActionPill(
                        icon = Icons.Rounded.CleaningServices,
                        label = stringResource(R.string.settings_clear_cache),
                        onClick = {
                            UpdaterMediaCache.clearAll(context)
                            onClearCaches()
                            Toast.makeText(context, context.getString(R.string.toast_cache_cleared), Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        height = 46.dp,
                    )
                }
            }
        }

        // 6. CRITICAL DANGER ZONE: Force / Unsafe Install (Variant C: bypass + Recovery option)
        item(key = "pref_danger_zone") {
            val dangerBg = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
            Card(
                colors = CardDefaults.cardColors(containerColor = dangerBg),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(22.dp),
                        )
                        Column {
                            Text(
                                text = stringResource(R.string.settings_danger_title),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error,
                            )
                            Text(
                                text = stringResource(R.string.settings_danger_warning),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    }

                    UpdaterSwitchRow(
                        title = stringResource(R.string.settings_unsafe_title),
                        summary = stringResource(R.string.settings_unsafe_summary),
                        checked = unsafeInstall,
                        accentDanger = true,
                        onCheckedChange = { wantEnable ->
                            if (wantEnable && !unsafeInstall) {
                                unsafeConfirmVisible = true
                            } else {
                                unsafeConfirmVisible = false
                                scope.launch { repository.setUnsafeInstall(false) }
                            }
                        },
                    )

                    AnimatedVisibility(
                        visible = unsafeConfirmVisible && !unsafeInstall,
                        enter = expandVertically(tween(220, easing = FastOutSlowInEasing)) + fadeIn(tween(180)),
                        exit = shrinkVertically(tween(220, easing = FastOutSlowInEasing)) + fadeOut(tween(140)),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.settings_unsafe_confirm),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.error,
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                SecondaryActionPill(
                                    icon = null,
                                    label = stringResource(R.string.action_cancel),
                                    onClick = { unsafeConfirmVisible = false },
                                    modifier = Modifier.weight(1f),
                                )
                                SecondaryActionPill(
                                    icon = Icons.Rounded.Warning,
                                    label = stringResource(R.string.danger_accept),
                                    onClick = {
                                        unsafeConfirmVisible = false
                                        scope.launch { repository.setUnsafeInstall(true) }
                                    },
                                    modifier = Modifier.weight(1.3f),
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError,
                                )
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible = unsafeInstall,
                        enter = expandVertically(tween(220, easing = FastOutSlowInEasing)) + fadeIn(tween(180)),
                        exit = shrinkVertically(tween(220, easing = FastOutSlowInEasing)) + fadeOut(tween(140)),
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.error.copy(alpha = 0.25f),
                                modifier = Modifier.padding(horizontal = 20.dp),
                            )
                            UpdaterSwitchRow(
                                title = stringResource(R.string.settings_recovery_title),
                                summary = stringResource(R.string.settings_recovery_summary),
                                checked = forceRecovery,
                                accentDanger = true,
                                onCheckedChange = {
                                    scope.launch { repository.setForceRecoveryInstall(it) }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}