package org.leegar.updater.ui.screens

import android.content.Intent
import android.text.format.DateFormat
import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import androidx.core.net.toUri
import org.leegar.updater.data.ChangelogState
import org.leegar.updater.data.ProjectInfo
import org.leegar.updater.data.ProjectLink
import org.leegar.updater.data.Update
import org.leegar.updater.R
import org.leegar.updater.data.UpdateStatus
import org.leegar.updater.deviceinfo.DeviceInfoUtils
import org.leegar.updater.ui.markdown.ComposeMarkdown
import org.leegar.updater.ui.media.ProjectLogoView
import org.leegar.updater.ui.media.ReleaseBannerView
import org.leegar.updater.ui.media.ReleaseIconBannerView
import org.leegar.updater.ui.pep.AnimatedInertialFlowRow
import org.leegar.updater.ui.pep.SecondaryActionPill
import org.leegar.updater.ui.pep.StudioCoordsRef
import org.leegar.updater.ui.pep.UpdaterBadgePill
import org.leegar.updater.ui.pep.UpdaterEmptyCard
import org.leegar.updater.ui.pep.UpdaterProgressIndicator
import org.leegar.updater.ui.pep.UpdaterSectionHeader
import org.leegar.updater.ui.pep.edgeOutline
import org.leegar.updater.ui.pep.tactilePressScale
import org.leegar.updater.ui.pep.unclippedBoundsInRoot
import org.leegar.updater.updatescheck.UpdatesCheckState
import org.leegar.updater.util.StringUtil
import java.util.Date

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UpdatesTabContent(
    updates: List<Update>,
    changelogs: Map<String, ChangelogState>,
    projectInfo: ProjectInfo,
    checkState: UpdatesCheckState,
    lastError: String?,
    downloadError: String?,
    lastCheckedTimestamp: Long,
    showLogo: Boolean,
    showReleaseBanner: Boolean,
    unsafeMode: Boolean,
    wavyProgress: Boolean,
    wavyAmplitude: Float,
    listState: LazyListState,
    pagePadding: PaddingValues,
    activeFlightUpdateId: String?,
    onRefreshClick: () -> Unit,
    onDismissError: () -> Unit,
    onOpenRelease: (Update, Rect, () -> Rect) -> Unit,
    onProjectImageClick: (String, String, Rect, () -> Rect) -> Unit,
) {
    val onlineUpdates = remember(updates) {
        updates.filter { !it.isLocalUpdate }
            .sortedWith(compareBy<Update> { it.orderIndex }.thenByDescending { it.timestamp })
    }
    val newerCount = remember(onlineUpdates) {
        onlineUpdates.count { it.timestamp > DeviceInfoUtils.buildDateTimestamp }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = pagePadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "hero_project_card") {
            ProjectHeroCard(
                projectInfo = projectInfo,
                showLogo = showLogo,
                newerCount = newerCount,
                totalCount = onlineUpdates.size,
                lastCheckedTimestamp = lastCheckedTimestamp,
                isChecking = checkState is UpdatesCheckState.Checking,
                onCheckClick = onRefreshClick,
                onImageClick = onProjectImageClick,
            )
        }

        item(key = "check_error_banner") {
            val bannerVisible = checkState is UpdatesCheckState.NoInternet ||
                checkState is UpdatesCheckState.Error ||
                !downloadError.isNullOrBlank()
            val liveTitle = when {
                checkState is UpdatesCheckState.NoInternet -> stringResource(R.string.banner_no_internet)
                !downloadError.isNullOrBlank() -> stringResource(R.string.toast_download_failed)
                else -> stringResource(R.string.banner_check_failed)
            }
            val liveDetail = when {
                checkState is UpdatesCheckState.NoInternet -> null
                !downloadError.isNullOrBlank() -> downloadError
                else -> lastError
            }
            var cachedTitle by remember { mutableStateOf(liveTitle) }
            var cachedDetail by remember { mutableStateOf(liveDetail) }
            var cachedDismissible by remember { mutableStateOf(checkState !is UpdatesCheckState.NoInternet) }
            if (bannerVisible) {
                cachedTitle = liveTitle
                cachedDetail = liveDetail
                cachedDismissible = checkState !is UpdatesCheckState.NoInternet
            }

            AnimatedVisibility(
                visible = bannerVisible,
                enter = expandVertically(tween(240, easing = FastOutSlowInEasing)) + fadeIn(tween(180)),
                exit = shrinkVertically(tween(220, easing = FastOutSlowInEasing)) + fadeOut(tween(140)),
            ) {
                val bg = MaterialTheme.colorScheme.errorContainer
                Card(
                    colors = CardDefaults.cardColors(containerColor = bg),
                    shape = RoundedCornerShape(22.dp),
                    border = BorderStroke(1.dp, bg.edgeOutline()),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 18.dp, end = 10.dp, top = 14.dp, bottom = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(22.dp),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = cachedTitle,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                            if (!cachedDetail.isNullOrBlank()) {
                                Text(
                                    text = cachedDetail.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f),
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        if (cachedDismissible) {
                            androidx.compose.material3.IconButton(
                                onClick = onDismissError,
                                enabled = bannerVisible,
                                modifier = Modifier.size(34.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = stringResource(R.string.desc_close),
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        if (checkState is UpdatesCheckState.Checking && onlineUpdates.isEmpty()) {
            items(3, key = { "upd_skeleton_$it" }) {
                ReleaseSkeletonCard()
            }
            return@LazyColumn
        }

        item(key = "releases_header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                UpdaterSectionHeader(
                    title = if (onlineUpdates.isEmpty()) {
                        stringResource(R.string.section_releases)
                    } else {
                        stringResource(R.string.section_releases_count, onlineUpdates.size)
                    },
                )
                AnimatedVisibility(
                    visible = unsafeMode,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    UpdaterBadgePill(
                        text = stringResource(R.string.badge_unsafe_mode),
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        }

        if (onlineUpdates.isEmpty()) {
            item(key = "releases_empty") {
                UpdaterEmptyCard(
                    icon = Icons.Rounded.CheckCircle,
                    title = stringResource(R.string.empty_releases_title),
                    description = stringResource(R.string.empty_releases_desc),
                )
            }
        } else {
            items(onlineUpdates, key = { it.downloadId }) { update ->
                val loadedMd = (changelogs[update.downloadId] as? ChangelogState.Loaded)?.markdown
                ReleaseCard(
                    update = update,
                    bannerSpec = if (showReleaseBanner) update.resolvedBannerSpec(loadedMd) else null,
                    wavyProgress = wavyProgress,
                    wavyAmplitude = wavyAmplitude,
                    hiddenForFlight = activeFlightUpdateId == update.downloadId,
                    onClick = { bounds, provider ->
                        onOpenRelease(update, bounds, provider)
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProjectHeroCard(
    projectInfo: ProjectInfo,
    showLogo: Boolean,
    newerCount: Int,
    totalCount: Int,
    lastCheckedTimestamp: Long,
    isChecking: Boolean,
    onCheckClick: () -> Unit,
    onImageClick: (String, String, Rect, () -> Rect) -> Unit,
) {
    val context = LocalContext.current
    val bg = MaterialTheme.colorScheme.surface

    val securityPatch = remember {
        runCatching {
            StringUtil.formatSecurityPatchFullDate(context, DeviceInfoUtils.buildSecurityPatch)
        }.getOrDefault(DeviceInfoUtils.buildSecurityPatch)
    }

    val lastCheckedFormatted = remember(lastCheckedTimestamp) {
        if (lastCheckedTimestamp <= 0L) null
        else {
            val date = DateUtils.formatDateTime(
                context,
                lastCheckedTimestamp,
                DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH or DateUtils.FORMAT_NO_YEAR,
            )
            val time = DateFormat.getTimeFormat(context).format(Date(lastCheckedTimestamp))
            "$date, $time"
        }
    }

    val hasRemoteLogo = !projectInfo.logo?.url.isNullOrBlank()
    Card(
        colors = CardDefaults.cardColors(containerColor = bg),
        shape = RoundedCornerShape(26.dp),
        border = BorderStroke(1.dp, bg.edgeOutline()),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp)),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            AnimatedVisibility(
                visible = showLogo && hasRemoteLogo,
                enter = expandVertically(tween(220, easing = FastOutSlowInEasing)) + fadeIn(tween(180)),
                exit = shrinkVertically(tween(220, easing = FastOutSlowInEasing)) + fadeOut(tween(140)),
            ) {
                ProjectLogoView(
                    logo = projectInfo.logo,
                    showDefaultFallback = true,
                    cornerRadius = 0.dp,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AnimatedVisibility(
                    visible = showLogo && !hasRemoteLogo,
                    enter = expandVertically(tween(220, easing = FastOutSlowInEasing)) + fadeIn(tween(180)),
                    exit = shrinkVertically(tween(220, easing = FastOutSlowInEasing)) + fadeOut(tween(140)),
                ) {
                    ProjectLogoView(
                        logo = null,
                        showDefaultFallback = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = projectInfo.name?.takeIf { it.isNotBlank() } ?: stringResource(R.string.display_name),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                val newerLabel = pluralStringResource(R.plurals.hero_newer_builds, newerCount, newerCount)
                val upToDateLabel = pluralStringResource(R.plurals.hero_up_to_date, totalCount, totalCount)
                val subtitle = projectInfo.tagline?.takeIf { it.isNotBlank() } ?: when {
                    newerCount > 0 -> newerLabel
                    totalCount > 0 -> upToDateLabel
                    else -> stringResource(R.string.hero_device_fallback, DeviceInfoUtils.androidVersion, DeviceInfoUtils.device.ifBlank { stringResource(R.string.device_fallback_name) })
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (newerCount > 0) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontWeight = if (newerCount > 0) FontWeight.SemiBold else FontWeight.Normal,
                )
            }

            if (!projectInfo.description.isNullOrBlank()) {
                ComposeMarkdown(
                    markdown = projectInfo.description,
                    onImageClick = onImageClick,
                )
            }

            val infoBg = MaterialTheme.colorScheme.surfaceContainerLow
            val infoShape = RoundedCornerShape(16.dp)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(infoShape)
                    .background(infoBg)
                    .border(1.dp, infoBg.edgeOutline(), infoShape)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                val installedVer = DeviceInfoUtils.buildVersion.ifBlank {
                    stringResource(R.string.header_android_version, DeviceInfoUtils.androidVersion)
                }
                Text(
                    text = stringResource(R.string.installed_label_format, installedVer),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.device_info_format, DeviceInfoUtils.androidVersion, DeviceInfoUtils.sdkLevel, securityPatch),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val extraLine = listOfNotNull(
                    projectInfo.maintainer?.let { stringResource(R.string.maintainer_name, it) },
                    lastCheckedFormatted?.let { stringResource(R.string.checked_format, it) },
                ).joinToString(" • ")
                if (extraLine.isNotEmpty()) {
                    Text(
                        text = extraLine,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                    )
                }
            }

            if (projectInfo.links.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    projectInfo.links.forEach { link ->
                        SecondaryActionPill(
                            icon = resolveLinkIcon(link),
                            label = link.title,
                            height = 40.dp,
                            onClick = {
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, link.url.toUri()))
                                }
                            },
                        )
                    }
                }
            }

            SecondaryActionPill(
                    icon = Icons.Rounded.Refresh,
                    label = if (isChecking) stringResource(R.string.checking_for_updates) else stringResource(R.string.check_for_update),
                    enabled = !isChecking,
                    onClick = onCheckClick,
                    modifier = Modifier.fillMaxWidth(),
                    height = 46.dp,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReleaseCard(
    update: Update,
    bannerSpec: String?,
    wavyProgress: Boolean,
    wavyAmplitude: Float,
    hiddenForFlight: Boolean,
    onClick: (Rect, () -> Rect) -> Unit,
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val coordsRef = remember(update.downloadId) { StudioCoordsRef() }
    val interaction = remember(update.downloadId) { MutableInteractionSource() }

    val buildDate = remember(update.timestamp) {
        if (update.timestamp > 0L) StringUtil.formatBuildDate(context, update.timestamp) else context.getString(R.string.unknown_date)
    }
    val sizeText = remember(update.fileSize) {
        if (update.fileSize > 0L) Formatter.formatShortFileSize(context, update.fileSize) else ""
    }
    val relation = remember(update.timestamp, update.osSdkLevel) { resolveBuildRelation(update) }
    val cardBg by animateColorAsState(
        targetValue = releaseCardContainerColor(update, relation),
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "ReleaseCardBg",
    )
    val visual = resolveReleaseVisual(update, relation)

    val cardShape = RoundedCornerShape(24.dp)
    Card(
        colors = CardDefaults.cardColors(containerColor = cardBg),
        shape = cardShape,
        border = BorderStroke(1.dp, cardBg.edgeOutline()),
        modifier = Modifier
            .fillMaxWidth()
            .onPlaced { coordsRef.value = it }
            .onGloballyPositioned { coordsRef.value = it }
            .tactilePressScale(interaction, enabled = !hiddenForFlight, pressedScale = 0.975f)
            .alpha(if (hiddenForFlight) 0f else 1f)
            .clip(cardShape)
            .clickable(
                interactionSource = interaction,
                indication = androidx.compose.material3.ripple(),
                enabled = !hiddenForFlight,
            ) {
                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                val rect = coordsRef.value
                    ?.takeIf { it.isAttached }
                    ?.unclippedBoundsInRoot()
                    ?: Rect.Zero
                onClick(rect) {
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
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(visual.iconBg)
                            .border(1.dp, visual.iconBg.edgeOutline(), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        ReleaseIconBannerView(
                            icoSpec = update.icoBanner,
                            tint = visual.iconFg,
                            iconSize = 24.dp,
                        ) {
                            Icon(
                                imageVector = visual.icon,
                                contentDescription = null,
                                tint = visual.iconFg,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = update.version.ifBlank { update.name },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            UpdaterBadgePill(
                                text = stringResource(relation.labelRes),
                                containerColor = visual.relationBg,
                                contentColor = visual.relationFg,
                            )
                        }
                        Text(
                            text = listOfNotNull(
                                buildDate,
                                sizeText.takeIf { it.isNotEmpty() },
                                update.type?.takeIf { it.isNotBlank() },
                            ).joinToString(" • "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))
                ReleaseTagFlowRow(update = update)

                AnimatedVisibility(
                    visible = shouldShowReleaseProgress(update),
                    enter = expandVertically(tween(220, easing = FastOutSlowInEasing)) + fadeIn(tween(180)),
                    exit = shrinkVertically(tween(220, easing = FastOutSlowInEasing)) + fadeOut(tween(140)),
                ) {
                    Column(
                        modifier = Modifier.padding(top = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                        ReleaseProgressRow(
                            update = update,
                            wavyProgress = wavyProgress,
                            wavyAmplitude = wavyAmplitude,
                        )
                    }
                }
            }
        }
    }
}

internal fun shouldShowReleaseProgress(update: Update): Boolean =
    update.status == UpdateStatus.DOWNLOADING ||
        update.status == UpdateStatus.PAUSED ||
        update.status == UpdateStatus.PAUSED_ERROR ||
        update.status == UpdateStatus.INSTALLING ||
        update.status == UpdateStatus.INSTALLATION_SUSPENDED ||
        update.status == UpdateStatus.VERIFYING ||
        update.status == UpdateStatus.STARTING

@Composable
internal fun ReleaseProgressRow(
    update: Update,
    wavyProgress: Boolean,
    wavyAmplitude: Float,
    modifier: Modifier = Modifier,
) {
    val isRunning = update.status == UpdateStatus.DOWNLOADING ||
        update.status == UpdateStatus.INSTALLING ||
        update.status == UpdateStatus.VERIFYING ||
        update.status == UpdateStatus.STARTING
    val progressFraction: Float? = when (update.status) {
        UpdateStatus.INSTALLING, UpdateStatus.INSTALLATION_SUSPENDED -> update.installProgress / 100f
        UpdateStatus.DOWNLOADING, UpdateStatus.PAUSED, UpdateStatus.PAUSED_ERROR ->
            if (update.downloadStage == Update.STAGE_VERIFYING_PART ||
                update.downloadStage == Update.STAGE_VERIFYING_FINAL
            ) null else update.progress / 100f
        else -> null
    }
    val statusText = when (update.status) {
        UpdateStatus.STARTING -> stringResource(R.string.status_starting)
        UpdateStatus.DOWNLOADING -> when (update.downloadStage) {
            Update.STAGE_VERIFYING_PART, Update.STAGE_VERIFYING_FINAL -> stringResource(R.string.status_verifying)
            Update.STAGE_MERGING -> stringResource(R.string.status_merging, update.partProgress)
            else -> stringResource(R.string.status_progress_percent, update.progress)
        }
        UpdateStatus.PAUSED -> stringResource(R.string.status_paused_percent, update.progress)
        UpdateStatus.PAUSED_ERROR -> stringResource(R.string.status_error)
        UpdateStatus.VERIFYING -> stringResource(R.string.status_verifying)
        UpdateStatus.INSTALLING -> stringResource(R.string.status_installing, update.installProgress)
        UpdateStatus.INSTALLATION_SUSPENDED -> stringResource(R.string.status_suspended, update.installProgress)
        else -> ""
    }
    val statusBg = when (update.status) {
        UpdateStatus.PAUSED -> MaterialTheme.colorScheme.tertiaryContainer
        UpdateStatus.PAUSED_ERROR -> MaterialTheme.colorScheme.errorContainer
        UpdateStatus.INSTALLING, UpdateStatus.DOWNLOADING, UpdateStatus.VERIFYING, UpdateStatus.STARTING ->
            MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val statusFg = when (update.status) {
        UpdateStatus.PAUSED -> MaterialTheme.colorScheme.onTertiaryContainer
        UpdateStatus.PAUSED_ERROR -> MaterialTheme.colorScheme.onErrorContainer
        UpdateStatus.INSTALLING, UpdateStatus.DOWNLOADING, UpdateStatus.VERIFYING, UpdateStatus.STARTING ->
            MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val animStatusBg by animateColorAsState(
        targetValue = statusBg,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "ProgressPillBg",
    )
    val animStatusFg by animateColorAsState(
        targetValue = statusFg,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "ProgressPillFg",
    )
    val statusPhase = Pair(update.status, update.downloadStage)

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (statusText.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(animStatusBg)
                    .border(1.dp, animStatusBg.edgeOutline(), CircleShape)
                    .animateContentSize(
                        animationSpec = spring(
                            stiffness = Spring.StiffnessMediumLow,
                            dampingRatio = Spring.DampingRatioNoBouncy,
                        ),
                    )
                    .padding(horizontal = 9.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                AnimatedContent(
                    targetState = statusPhase,
                    transitionSpec = {
                        (fadeIn(tween(180)) + scaleIn(initialScale = 0.90f, animationSpec = tween(180))) togetherWith
                            (fadeOut(tween(120)) + scaleOut(targetScale = 0.90f, animationSpec = tween(120))) using
                            SizeTransform(clip = false) { _, _ -> snap() }
                    },
                    contentAlignment = Alignment.Center,
                    label = "ProgressStatusPhase",
                ) { _ ->
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = animStatusFg,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
        UpdaterProgressIndicator(
            progress = progressFraction,
            wavy = wavyProgress,
            isActive = isRunning,
            wavyAmplitude = wavyAmplitude,
            height = 6.dp,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
internal fun ReleaseTagFlowRow(
    update: Update,
    modifier: Modifier = Modifier,
) {
    val isDownloaded = update.hasVerifiedPackage()
    AnimatedInertialFlowRow(
        modifier = modifier,
        horizontalSpacing = 6.dp,
        verticalSpacing = 6.dp,
    ) {
        update.tag?.takeIf { it.isNotBlank() }?.let { tag ->
            UpdaterBadgePill(
                text = tag,
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
        if (isDownloaded) {
            UpdaterBadgePill(
                text = stringResource(R.string.badge_downloaded),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        } else if (update.status == UpdateStatus.UPDATED_NEED_REBOOT) {
            UpdaterBadgePill(
                text = stringResource(R.string.badge_reboot_required),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

internal data class ReleaseVisualStyle(
    val icon: ImageVector,
    val iconBg: Color,
    val iconFg: Color,
    val relationBg: Color,
    val relationFg: Color,
)

@Composable
internal fun releaseCardContainerColor(update: Update, relation: BuildRelation): Color {
    val isActiveTask = update.status.isInProgress || update.status == UpdateStatus.UPDATED_NEED_REBOOT
    return when {
        isActiveTask -> MaterialTheme.colorScheme.surfaceContainerHigh
        relation == BuildRelation.NEWER -> MaterialTheme.colorScheme.surfaceContainerHigh
        else -> MaterialTheme.colorScheme.surface
    }
}

@Composable
internal fun resolveReleaseVisual(update: Update, relation: BuildRelation): ReleaseVisualStyle {
    val isDownloaded = update.hasVerifiedPackage()
    val iconBg = when {
        isDownloaded || relation == BuildRelation.NEWER -> MaterialTheme.colorScheme.primaryContainer
        relation == BuildRelation.INSTALLED -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val iconFg = when {
        isDownloaded || relation == BuildRelation.NEWER -> MaterialTheme.colorScheme.onPrimaryContainer
        relation == BuildRelation.INSTALLED -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val icon = when {
        isDownloaded -> Icons.Rounded.CloudDone
        relation == BuildRelation.NEWER -> Icons.Rounded.NewReleases
        relation == BuildRelation.INSTALLED -> Icons.Rounded.CheckCircle
        relation == BuildRelation.DOWNGRADE -> Icons.Rounded.History
        else -> Icons.Rounded.SystemUpdate
    }
    val relBg = when (relation) {
        BuildRelation.NEWER -> MaterialTheme.colorScheme.primaryContainer
        BuildRelation.INSTALLED -> MaterialTheme.colorScheme.secondaryContainer
        BuildRelation.DOWNGRADE -> MaterialTheme.colorScheme.tertiaryContainer
        BuildRelation.OTHER -> MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val relFg = when (relation) {
        BuildRelation.NEWER -> MaterialTheme.colorScheme.onPrimaryContainer
        BuildRelation.INSTALLED -> MaterialTheme.colorScheme.onSecondaryContainer
        BuildRelation.DOWNGRADE -> MaterialTheme.colorScheme.onTertiaryContainer
        BuildRelation.OTHER -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    return ReleaseVisualStyle(icon, iconBg, iconFg, relBg, relFg)
}

@Composable
private fun ReleaseSkeletonCard() {
    val pulse = rememberInfiniteTransition(label = "ReleaseSkel")
    val alpha by pulse.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "ReleaseSkelAlpha",
    )
    val bg = MaterialTheme.colorScheme.surface
    val block = MaterialTheme.colorScheme.surfaceContainerHighest
    Card(
        colors = CardDefaults.cardColors(containerColor = bg),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, bg.edgeOutline()),
        modifier = Modifier
            .fillMaxWidth()
            .height(116.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
                .alpha(alpha),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(block))
                Spacer(Modifier.width(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.width(150.dp).height(14.dp).clip(CircleShape).background(block))
                    Box(Modifier.width(100.dp).height(10.dp).clip(CircleShape).background(block))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.width(72.dp).height(20.dp).clip(CircleShape).background(block))
                Box(Modifier.width(64.dp).height(20.dp).clip(CircleShape).background(block))
            }
        }
    }
}

internal enum class BuildRelation(val labelRes: Int) {
    NEWER(R.string.relation_newer),
    INSTALLED(R.string.relation_installed),
    DOWNGRADE(R.string.relation_downgrade),
    OTHER(R.string.relation_build),
}

internal fun resolveBuildRelation(update: Update): BuildRelation {
    val curTs = DeviceInfoUtils.buildDateTimestamp
    if (curTs <= 0L || update.timestamp <= 0L) return BuildRelation.OTHER
    return when {
        update.timestamp > curTs -> BuildRelation.NEWER
        update.timestamp == curTs -> BuildRelation.INSTALLED
        else -> BuildRelation.DOWNGRADE
    }
}

internal fun resolveLinkIcon(link: ProjectLink): ImageVector =
    when (link.icon?.lowercase()?.trim()) {
        "forum", "xda", "4pda" -> Icons.Rounded.Forum
        "firmware", "download", "mirror" -> Icons.Rounded.Download
        "donate", "paypal", "favorite", "sponsor" -> Icons.Rounded.Favorite
        "telegram", "chat" -> Icons.AutoMirrored.Rounded.Send
        "github", "source", "code" -> Icons.Rounded.Code
        "device", "kernel" -> Icons.Rounded.Memory
        "web", "site" -> Icons.Rounded.Language
        else -> Icons.AutoMirrored.Rounded.OpenInNew
    }