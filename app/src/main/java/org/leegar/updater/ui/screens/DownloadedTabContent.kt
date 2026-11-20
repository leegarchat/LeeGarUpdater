package org.leegar.updater.ui.screens

import android.os.SystemClock
import android.text.format.Formatter
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.SystemUpdateAlt
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import org.leegar.updater.ui.pep.UpdaterProgressIndicator
import org.leegar.updater.ui.pep.tactilePressScale
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.leegar.updater.data.ChangelogState
import org.leegar.updater.data.Update
import org.leegar.updater.R
import org.leegar.updater.data.UpdateStatus
import org.leegar.updater.ui.media.ReleaseBannerView
import org.leegar.updater.ui.media.ReleaseIconBannerView
import org.leegar.updater.ui.pep.AnimatedInertialFlowRow
import org.leegar.updater.ui.pep.AutoSizeSingleLineText
import org.leegar.updater.ui.pep.MorphingDownloadControlRow
import org.leegar.updater.ui.pep.SecondaryActionPill
import org.leegar.updater.ui.pep.StudioCoordsRef
import org.leegar.updater.ui.pep.UpdaterBadgePill
import org.leegar.updater.ui.pep.UpdaterEmptyCard
import org.leegar.updater.ui.pep.UpdaterPrimaryPillButton
import org.leegar.updater.ui.pep.UpdaterSectionHeader
import org.leegar.updater.ui.pep.UpdaterTwoTapDeleteButton
import org.leegar.updater.ui.pep.edgeOutline
import org.leegar.updater.ui.pep.unclippedBoundsInRoot
import org.leegar.updater.util.InstallUtils
import org.leegar.updater.util.StringUtil

@Composable
fun DownloadedTabContent(
    updates: List<Update>,
    changelogs: Map<String, ChangelogState>,
    showReleaseBanner: Boolean,
    isBusy: Boolean,
    unsafeMode: Boolean,
    forceRecovery: Boolean,
    wavyProgress: Boolean,
    wavyAmplitude: Float,
    listState: LazyListState,
    pagePadding: PaddingValues,
    activeFlightUpdateId: String?,
    onImportLocalZip: () -> Unit,
    onInstall: (Update) -> Unit,
    onExport: (Update) -> Unit,
    onDelete: (Update) -> Unit,
    onPauseDownload: (Update) -> Unit,
    onResumeDownload: (Update) -> Unit,
    onCancelDownload: (Update) -> Unit,
    onReboot: () -> Unit,
    onOpenDetails: (Update, Rect, () -> Rect) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val armedDeletes = remember { mutableStateMapOf<String, Long>() }

    val downloadedList = remember(updates) {
        updates.filter { u ->
            u.isLocalUpdate ||
                u.hasVerifiedPackage() ||
                u.status.isInProgress ||
                u.status == UpdateStatus.UPDATED_NEED_REBOOT ||
                u.status == UpdateStatus.VERIFICATION_FAILED ||
                (u.file != null && u.file.exists())
        }.sortedByDescending { it.timestamp }
    }

    val displayedList = remember { mutableStateListOf<Update>() }
    val itemVisibility = remember { mutableStateMapOf<String, Boolean>() }

    LaunchedEffect(downloadedList) {
        val incomingById = downloadedList.associateBy { it.downloadId }
        val exiting = displayedList.filter { it.downloadId !in incomingById }
        val merged = ArrayList<Update>(downloadedList.size + exiting.size)
        merged.addAll(downloadedList)
        exiting.forEach { old ->
            merged.add(old)
            if (itemVisibility[old.downloadId] != false) {
                itemVisibility[old.downloadId] = false
                scope.launch {
                    delay(260L)
                    if (itemVisibility[old.downloadId] == false) {
                        displayedList.removeAll { it.downloadId == old.downloadId }
                        itemVisibility.remove(old.downloadId)
                    }
                }
            }
        }
        downloadedList.forEach { itemVisibility[it.downloadId] = true }
        displayedList.clear()
        displayedList.addAll(merged)
    }

    val totalBytes = remember(downloadedList) {
        downloadedList.sumOf { it.file?.takeIf { f -> f.exists() }?.length() ?: 0L }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = pagePadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Overview + Import Local ZIP card
        item(key = "downloaded_overview_card") {
            val bg = MaterialTheme.colorScheme.surface
            Card(
                colors = CardDefaults.cardColors(containerColor = bg),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, bg.edgeOutline()),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = pluralStringResource(R.plurals.downloaded_title, downloadedList.size, downloadedList.size),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = if (totalBytes > 0L) {
                                context.getString(R.string.downloaded_subtitle_used, Formatter.formatShortFileSize(context, totalBytes))
                            } else {
                                context.getString(R.string.downloaded_subtitle_hint)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    SecondaryActionPill(
                        icon = Icons.Rounded.FileOpen,
                        label = stringResource(R.string.action_import_zip),
                        enabled = true,
                        onClick = onImportLocalZip,
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }

        if (displayedList.isEmpty()) {
            item(key = "downloaded_empty") {
                UpdaterEmptyCard(
                    icon = Icons.Rounded.FolderZip,
                    title = stringResource(R.string.empty_downloaded_title),
                    description = stringResource(R.string.empty_downloaded_desc),
                )
            }
            return@LazyColumn
        }

        item(key = "downloaded_header") {
            UpdaterSectionHeader(stringResource(R.string.section_downloaded))
        }

        items(displayedList, key = { "dl_${it.downloadId}" }) { update ->
            val isItemVisible = itemVisibility[update.downloadId] ?: true
            val armedAt = armedDeletes[update.downloadId] ?: 0L
            val deleteArmed = armedAt > 0L
            LaunchedEffect(armedAt) {
                if (armedAt <= 0L) return@LaunchedEffect
                delay(3000L)
                if (armedDeletes[update.downloadId] == armedAt) {
                    armedDeletes.remove(update.downloadId)
                }
            }

            val loadedMd = (changelogs[update.downloadId] as? ChangelogState.Loaded)?.markdown
            AnimatedVisibility(
                visible = isItemVisible,
                enter = expandVertically(tween(240, easing = FastOutSlowInEasing)) + fadeIn(tween(180)),
                exit = shrinkVertically(tween(240, easing = FastOutSlowInEasing)) + fadeOut(tween(160)),
                modifier = Modifier.animateItem(),
            ) {
                DownloadedPackageCard(
                    update = update,
                    bannerSpec = if (showReleaseBanner) update.resolvedBannerSpec(loadedMd) else null,
                    isBusy = isBusy,
                    unsafeMode = unsafeMode,
                    forceRecovery = forceRecovery,
                    wavyProgress = wavyProgress,
                    wavyAmplitude = wavyAmplitude,
                    deleteArmed = deleteArmed,
                    hiddenForFlight = activeFlightUpdateId == update.downloadId,
                    onInstall = { onInstall(update) },
                    onExport = { onExport(update) },
                    onDeleteClick = {
                        if (deleteArmed) {
                            armedDeletes.remove(update.downloadId)
                            if (update.status.isInProgress) {
                                onCancelDownload(update)
                            } else {
                                onDelete(update)
                            }
                        } else {
                            armedDeletes[update.downloadId] = SystemClock.uptimeMillis()
                        }
                    },
                    onPause = {
                        armedDeletes.remove(update.downloadId)
                        onPauseDownload(update)
                    },
                    onResume = {
                        armedDeletes.remove(update.downloadId)
                        onResumeDownload(update)
                    },
                    onCancel = {
                        if (deleteArmed) {
                            armedDeletes.remove(update.downloadId)
                            onCancelDownload(update)
                        } else {
                            armedDeletes[update.downloadId] = SystemClock.uptimeMillis()
                        }
                    },
                    onReboot = onReboot,
                    onOpenDetails = { bounds, provider -> onOpenDetails(update, bounds, provider) },
                )
            }
        }
    }
}

internal data class SavedVisualStyle(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val iconBg: androidx.compose.ui.graphics.Color,
    val iconFg: androidx.compose.ui.graphics.Color,
)

@Composable
internal fun resolveSavedVisual(update: Update): SavedVisualStyle {
    val iconBg = when (update.status) {
        UpdateStatus.VERIFIED, UpdateStatus.UPDATED_NEED_REBOOT ->
            MaterialTheme.colorScheme.primaryContainer
        UpdateStatus.VERIFICATION_FAILED, UpdateStatus.INSTALLATION_FAILED, UpdateStatus.PAUSED_ERROR ->
            MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val iconFg = when (update.status) {
        UpdateStatus.VERIFIED, UpdateStatus.UPDATED_NEED_REBOOT ->
            MaterialTheme.colorScheme.onPrimaryContainer
        UpdateStatus.VERIFICATION_FAILED, UpdateStatus.INSTALLATION_FAILED, UpdateStatus.PAUSED_ERROR ->
            MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val icon = if (update.hasVerifiedPackage()) Icons.Rounded.DownloadDone else Icons.Rounded.Archive
    return SavedVisualStyle(icon, iconBg, iconFg)
}

@Composable
private fun DownloadedPackageCard(
    update: Update,
    bannerSpec: String?,
    isBusy: Boolean,
    unsafeMode: Boolean,
    forceRecovery: Boolean,
    wavyProgress: Boolean,
    wavyAmplitude: Float,
    deleteArmed: Boolean,
    hiddenForFlight: Boolean,
    onInstall: () -> Unit,
    onExport: () -> Unit,
    onDeleteClick: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onReboot: () -> Unit,
    onOpenDetails: (Rect, () -> Rect) -> Unit,
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val coordsRef = remember(update.downloadId) { StudioCoordsRef() }
    val cardInteraction = remember(update.downloadId) { MutableInteractionSource() }
    var confirmInstallOpen by remember(update.downloadId) { mutableStateOf(false) }
    var lastErrorMsg by remember(update.downloadId) { mutableStateOf(update.errorMessage.orEmpty()) }
    if (!update.errorMessage.isNullOrBlank()) lastErrorMsg = update.errorMessage

    LaunchedEffect(update.status) {
        if (update.status.isInProgress || update.status == UpdateStatus.UPDATED_NEED_REBOOT) {
            confirmInstallOpen = false
        }
    }

    val actualSize = update.file?.takeIf { it.exists() }?.length() ?: update.fileSize
    val sizeStr = if (actualSize > 0L) Formatter.formatShortFileSize(context, actualSize) else ""
    val dateStr = if (update.timestamp > 0L) StringUtil.formatBuildDate(context, update.timestamp) else ""
    val blockedReason = InstallUtils.getBlockedReason(update, unsafeMode)
    val canInstallNow = blockedReason == InstallUtils.BlockedReason.NONE && !isBusy

    val cardBg = MaterialTheme.colorScheme.surface
    val cardShape = RoundedCornerShape(24.dp)
    Card(
        colors = CardDefaults.cardColors(containerColor = cardBg),
        shape = cardShape,
        border = BorderStroke(1.dp, cardBg.edgeOutline()),
        modifier = Modifier
            .fillMaxWidth()
            .onPlaced { coordsRef.value = it }
            .onGloballyPositioned { coordsRef.value = it }
            .tactilePressScale(cardInteraction, enabled = !hiddenForFlight, pressedScale = 0.98f)
            .alpha(if (hiddenForFlight) 0f else 1f)
            .clip(cardShape)
            .clickable(
                interactionSource = cardInteraction,
                indication = androidx.compose.material3.ripple(),
                enabled = !hiddenForFlight,
            ) {
                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                if (confirmInstallOpen) {
                    confirmInstallOpen = false
                    return@clickable
                }
                val rect = coordsRef.value
                    ?.takeIf { it.isAttached }
                    ?.unclippedBoundsInRoot()
                    ?: Rect.Zero
                onOpenDetails(rect) {
                    coordsRef.value
                        ?.takeIf { it.isAttached }
                        ?.unclippedBoundsInRoot()
                        ?: rect
                }
            },
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (!bannerSpec.isNullOrBlank()) {
                ReleaseBannerView(
                    bannerSpec = bannerSpec,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    val savedVisual = resolveSavedVisual(update)
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(savedVisual.iconBg)
                            .border(1.dp, savedVisual.iconBg.edgeOutline(), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        ReleaseIconBannerView(
                            icoSpec = update.icoBanner,
                            tint = savedVisual.iconFg,
                            iconSize = 24.dp,
                        ) {
                            Icon(
                                imageVector = savedVisual.icon,
                                contentDescription = null,
                                tint = savedVisual.iconFg,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = update.version.ifBlank { update.name },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        AutoSizeSingleLineText(
                            text = update.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))
                AnimatedInertialFlowRow(
                    horizontalSpacing = 6.dp,
                    verticalSpacing = 6.dp,
                ) {
                    if (sizeStr.isNotEmpty()) UpdaterBadgePill(sizeStr)
                    if (dateStr.isNotEmpty()) UpdaterBadgePill(dateStr)
                    if (update.isLocalUpdate) {
                        UpdaterBadgePill(
                            text = stringResource(R.string.badge_local),
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                    if (update.isSplitDownload) {
                        val count = update.parts.size.takeIf { it > 0 }
                            ?: update.downloadUrl?.lines()?.count { it.isNotBlank() }
                            ?: 0
                        UpdaterBadgePill(text = pluralStringResource(R.plurals.badge_split_parts, count, count))
                    }
                    if (!update.sha256.isNullOrBlank()) {
                        UpdaterBadgePill(text = stringResource(R.string.badge_sha256))
                    }
                }

                AnimatedVisibility(
                    visible = shouldShowReleaseProgress(update),
                    enter = expandVertically(tween(220, easing = FastOutSlowInEasing)) + fadeIn(tween(180)),
                    exit = shrinkVertically(tween(220, easing = FastOutSlowInEasing)) + fadeOut(tween(140)),
                ) {
                    Box(modifier = Modifier.padding(top = 10.dp)) {
                        ReleaseProgressRow(
                            update = update,
                            wavyProgress = wavyProgress,
                            wavyAmplitude = wavyAmplitude,
                        )
                    }
                }

                AnimatedVisibility(
                    visible = !update.errorMessage.isNullOrBlank(),
                    enter = expandVertically(tween(240, easing = FastOutSlowInEasing)) + fadeIn(tween(180)),
                    exit = shrinkVertically(tween(220, easing = FastOutSlowInEasing)) + fadeOut(tween(140)),
                ) {
                    val errBg = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f)
                    val errShape = RoundedCornerShape(14.dp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp)
                            .clip(errShape)
                            .background(errBg)
                            .border(1.dp, errBg.edgeOutline(), errShape)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = lastErrorMsg,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }

                AnimatedVisibility(
                    visible = confirmInstallOpen,
                    enter = expandVertically(tween(220, easing = FastOutSlowInEasing)) + fadeIn(tween(180)),
                    exit = shrinkVertically(tween(220, easing = FastOutSlowInEasing)) + fadeOut(tween(140)),
                ) {
                    val confBg = if (unsafeMode) {
                        MaterialTheme.colorScheme.errorContainer
                    } else {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                    }
                    val confFg = if (unsafeMode) {
                        MaterialTheme.colorScheme.onErrorContainer
                    } else {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    }
                    val confShape = RoundedCornerShape(16.dp)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp)
                            .clip(confShape)
                            .background(confBg)
                            .border(1.dp, confBg.edgeOutline(), confShape)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = when {
                                forceRecovery -> stringResource(R.string.install_confirm_recovery)
                                unsafeMode -> stringResource(R.string.install_confirm_force)
                                else -> stringResource(R.string.install_confirm_now, update.version.ifBlank { update.name })
                            },
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = confFg,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            SecondaryActionPill(
                                icon = Icons.Rounded.Close,
                                label = stringResource(R.string.action_cancel),
                                height = 46.dp,
                                onClick = { confirmInstallOpen = false },
                                modifier = Modifier.weight(1f),
                            )
                            UpdaterPrimaryPillButton(
                                label = stringResource(R.string.action_confirm),
                                icon = Icons.Rounded.Check,
                                isDanger = unsafeMode,
                                onClick = {
                                    confirmInstallOpen = false
                                    onInstall()
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                val cardActionMode = when (update.status) {
                    UpdateStatus.UPDATED_NEED_REBOOT -> 0
                    UpdateStatus.DOWNLOADING,
                    UpdateStatus.STARTING,
                    UpdateStatus.PAUSED,
                    UpdateStatus.PAUSED_ERROR -> 1
                    else -> 2
                }
                AnimatedContent(
                    targetState = cardActionMode,
                    transitionSpec = {
                        (fadeIn(tween(200)) + scaleIn(initialScale = 0.94f, animationSpec = tween(200))) togetherWith
                            (fadeOut(tween(140)) + scaleOut(targetScale = 0.94f, animationSpec = tween(140))) using
                            SizeTransform(clip = false)
                    },
                    label = "SavedCardActionModeMorph",
                ) { mode ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        when (mode) {
                            0 -> {
                                UpdaterPrimaryPillButton(
                                    label = stringResource(R.string.action_reboot_now),
                                    icon = Icons.Rounded.RestartAlt,
                                    onClick = onReboot,
                                    modifier = Modifier.weight(1f),
                                )
                            }

                            1 -> {
                                MorphingDownloadControlRow(
                                    isPaused = update.status == UpdateStatus.PAUSED ||
                                        update.status == UpdateStatus.PAUSED_ERROR,
                                    pauseLabel = stringResource(R.string.action_pause),
                                    resumeLabel = stringResource(R.string.action_resume),
                                    cancelLabel = stringResource(R.string.action_cancel),
                                    deleteLabel = stringResource(R.string.action_delete),
                                    confirmDeleteLabel = stringResource(R.string.action_confirm),
                                    resumeEnabled = true,
                                    morphToExpanded = 0f,
                                    deleteArmed = deleteArmed,
                                    onPause = onPause,
                                    onResume = onResume,
                                    onCancel = onCancel,
                                    onDelete = onDeleteClick,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }

                            else -> {
                                val installLabel = when {
                                    blockedReason == InstallUtils.BlockedReason.DOWNGRADE -> stringResource(R.string.blocked_update_dialog_title_downgrade)
                                    blockedReason == InstallUtils.BlockedReason.VERSION_UNSUPPORTED -> stringResource(R.string.badge_major_upgrade)
                                    forceRecovery -> stringResource(R.string.install_label_recovery)
                                    unsafeMode -> stringResource(R.string.install_label_force)
                                    else -> stringResource(R.string.action_install)
                                }
                                UpdaterPrimaryPillButton(
                                    label = installLabel,
                                    icon = Icons.Rounded.SystemUpdateAlt,
                                    enabled = canInstallNow && update.file?.exists() == true,
                                    isDanger = unsafeMode,
                                    onClick = { confirmInstallOpen = true },
                                    modifier = Modifier.weight(1f),
                                )

                                // Export to internal storage (SAF CreateDocument -> ExportUpdateService)
                                if (update.file?.exists() == true) {
                                    val expBg = MaterialTheme.colorScheme.surfaceContainerHighest
                                    val expShape = RoundedCornerShape(16.dp)
                                    val expInteraction = remember { MutableInteractionSource() }
                                    Box(
                                        modifier = Modifier
                                            .size(46.dp)
                                            .tactilePressScale(expInteraction, enabled = !isBusy, pressedScale = 0.92f)
                                            .alpha(if (!isBusy) 1f else 0.45f)
                                            .clip(expShape)
                                            .background(expBg)
                                            .border(1.dp, expBg.edgeOutline(), expShape)
                                            .clickable(
                                                interactionSource = expInteraction,
                                                indication = androidx.compose.material3.ripple(),
                                                enabled = !isBusy,
                                            ) {
                                                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                                                onExport()
                                            },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.SaveAlt,
                                            contentDescription = stringResource(R.string.menu_export_update),
                                            tint = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                }

                                // 2-Tap Delete (zero AlertDialog)
                                UpdaterTwoTapDeleteButton(
                                    armed = deleteArmed,
                                    enabled = !isBusy,
                                    onClick = onDeleteClick,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}