package org.leegar.updater.ui.overlays

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.SystemUpdateAlt
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.util.lerp as lerpFloat
import androidx.compose.ui.zIndex
import androidx.core.net.toUri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.leegar.updater.data.ChangelogState
import org.leegar.updater.data.Update
import org.leegar.updater.R
import org.leegar.updater.data.UpdateStatus
import org.leegar.updater.ui.markdown.ComposeMarkdown
import org.leegar.updater.ui.media.ReleaseBannerView
import org.leegar.updater.ui.media.ReleaseIconBannerView
import org.leegar.updater.ui.pep.AnimatedInertialFlowRow
import org.leegar.updater.ui.pep.AutoSizeSingleLineText
import org.leegar.updater.ui.pep.GraphicsLayerRecordingState
import org.leegar.updater.ui.pep.LocalBlurConfig
import org.leegar.updater.ui.pep.LocalContentHorizontalInset
import org.leegar.updater.ui.pep.MorphingDownloadControlRow
import org.leegar.updater.ui.pep.SecondaryActionPill
import org.leegar.updater.ui.pep.StudioCoordsRef
import org.leegar.updater.ui.pep.UpdaterBadgePill
import org.leegar.updater.ui.pep.UpdaterPrimaryPillButton
import org.leegar.updater.ui.pep.UpdaterTwoTapDeleteButton
import org.leegar.updater.ui.pep.cardFlightBlurMask
import org.leegar.updater.ui.pep.edgeOutline
import org.leegar.updater.ui.pep.recordLayer
import org.leegar.updater.ui.pep.rememberGraphicsLayerRecordingState
import org.leegar.updater.ui.pep.sstepE
import org.leegar.updater.ui.pep.tactilePressScale
import org.leegar.updater.ui.screens.ReleaseProgressRow
import org.leegar.updater.ui.screens.ReleaseTagFlowRow
import org.leegar.updater.ui.screens.releaseCardContainerColor
import org.leegar.updater.ui.screens.resolveBuildRelation
import org.leegar.updater.ui.screens.resolveLinkIcon
import org.leegar.updater.ui.screens.resolveReleaseVisual
import org.leegar.updater.ui.screens.resolveSavedVisual
import org.leegar.updater.ui.screens.shouldShowReleaseProgress
import org.leegar.updater.util.InstallUtils
import org.leegar.updater.util.StringUtil
import kotlin.math.roundToInt

enum class ReleaseFlightOrigin { UPDATES, SAVED }

@Stable
data class ReleaseFlightTarget(
    val updateId: String,
    val origin: ReleaseFlightOrigin = ReleaseFlightOrigin.UPDATES,
    val source: Rect = Rect.Zero,
    val sourceBoundsProvider: () -> Rect = { source },
    val sessionId: Long = System.nanoTime(),
)

private enum class ReleaseFooterMode {
    REBOOT,
    ACTIVE_DOWNLOAD,
    VERIFIED_OR_SAVED,
    NOT_DOWNLOADED,
}

private data class PendingConfirmBanner(
    val message: String,
    val isDanger: Boolean,
    val onConfirm: () -> Unit,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReleaseDetailFlightOverlay(
    target: ReleaseFlightTarget?,
    liveUpdate: Update?,
    changelogState: ChangelogState,
    serverError: String? = null,
    onDismissServerError: () -> Unit = {},
    isOnline: Boolean,
    isMetered: Boolean,
    meteredWarningEnabled: Boolean,
    isBatteryOk: Boolean,
    isBusy: Boolean,
    showReleaseBanner: Boolean,
    unsafeMode: Boolean,
    forceRecovery: Boolean,
    streamUpdatesEnabled: Boolean,
    wavyProgress: Boolean,
    wavyAmplitude: Float,
    scrollBlur: GraphicsLayerRecordingState,
    topInsetPx: Float,
    bottomReservedPx: Float,
    closeSignal: Int,
    backBlocked: Boolean = false,
    activeFlightImageUrl: String?,
    onProgress: (Float) -> Unit,
    onRequestChangelog: (Update, Boolean) -> Unit,
    onDownloadOnly: (Update) -> Unit,
    onDownloadAndInstall: (Update) -> Unit,
    onInstall: (Update) -> Unit,
    onPauseDownload: (Update) -> Unit,
    onResumeDownload: (Update) -> Unit,
    onCancelDownload: (Update) -> Unit,
    onExport: (Update) -> Unit,
    onDelete: (Update) -> Unit,
    onReboot: () -> Unit,
    onImageClick: (String, String, Rect, () -> Rect) -> Unit,
    onClose: () -> Unit,
) {
    val flight = target ?: return
    var lastValidUpdate by remember(flight.sessionId) { mutableStateOf(liveUpdate) }
    if (liveUpdate != null) lastValidUpdate = liveUpdate
    val update = liveUpdate ?: lastValidUpdate ?: return

    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val blurConfig = LocalBlurConfig.current
    val horizontalInset = LocalContentHorizontalInset.current
    val blurEnabled = with(density) { blurConfig.radiusDp.coerceIn(0f, 150f).dp.toPx() } > 0.5f

    val flightAnim = remember(flight.sessionId) { Animatable(0f) }
    val collapseAnim = remember(flight.sessionId) { Animatable(1f) }
    val backProgress = remember(flight.sessionId) { Animatable(0f) }
    var backEdge by remember(flight.sessionId) { mutableIntStateOf(BackEventCompat.EDGE_LEFT) }
    var backDyNorm by remember(flight.sessionId) { mutableFloatStateOf(0f) }
    var closing by remember(flight.sessionId) { mutableStateOf(false) }
    var closingForRemoval by remember(flight.sessionId) { mutableStateOf(false) }

    val hostCoordsRef = remember(flight.sessionId) { StudioCoordsRef() }
    val cardCoordsRef = remember(flight.sessionId) { StudioCoordsRef() }
    val headerCoordsRef = remember(flight.sessionId) { StudioCoordsRef() }
    val footerCoordsRef = remember(flight.sessionId) { StudioCoordsRef() }
    val innerScrollBlur = rememberGraphicsLayerRecordingState()
    var headerHeightPx by remember(flight.sessionId) { mutableIntStateOf(0) }
    var footerHeightPx by remember(flight.sessionId) { mutableIntStateOf(0) }

    var partsExpanded by remember(flight.sessionId) { mutableStateOf(false) }
    var pendingConfirm by remember(flight.sessionId) { mutableStateOf<PendingConfirmBanner?>(null) }
    var lastConfirm by remember(flight.sessionId) { mutableStateOf<PendingConfirmBanner?>(null) }
    if (pendingConfirm != null) lastConfirm = pendingConfirm
    var deleteArmedAt by remember(flight.sessionId) { mutableLongStateOf(0L) }

    LaunchedEffect(deleteArmedAt) {
        if (deleteArmedAt <= 0L) return@LaunchedEffect
        val ts = deleteArmedAt
        delay(3000L)
        if (deleteArmedAt == ts) deleteArmedAt = 0L
    }

    LaunchedEffect(flight.sessionId) {
        onRequestChangelog(update, false)
        withFrameNanos { }
        if (!closing) {
            haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
            flightAnim.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    stiffness = 260f,
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    visibilityThreshold = 0.002f,
                ),
            )
        }
    }

    fun closeAnimated(afterClose: (() -> Unit)? = null) {
        if (closing) return
        closing = true
        scope.launch {
            if (backProgress.value > 0.001f) {
                launch {
                    backProgress.animateTo(
                        targetValue = 0f,
                        animationSpec = spring(
                            stiffness = 340f,
                            dampingRatio = Spring.DampingRatioNoBouncy,
                        ),
                    )
                }
            }
            flightAnim.stop()
            try {
                flightAnim.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(
                        stiffness = 300f,
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        visibilityThreshold = 0.004f,
                    ),
                )
            } finally {
                onProgress(0f)
                afterClose?.invoke()
                onClose()
            }
        }
    }

    fun closeForRemoval(afterClose: (() -> Unit)? = null) {
        if (closing) return
        closing = true
        closingForRemoval = true
        scope.launch {
            flightAnim.stop()
            try {
                collapseAnim.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(220, easing = FastOutSlowInEasing),
                )
            } finally {
                onProgress(0f)
                afterClose?.invoke()
                onClose()
            }
        }
    }

    val isSavedOrigin = flight.origin == ReleaseFlightOrigin.SAVED
    val stillInSaved = liveUpdate != null && (
        liveUpdate.isLocalUpdate ||
            liveUpdate.hasVerifiedPackage() ||
            liveUpdate.status.isInProgress ||
            liveUpdate.status == UpdateStatus.UPDATED_NEED_REBOOT ||
            liveUpdate.status == UpdateStatus.VERIFICATION_FAILED ||
            (liveUpdate.file != null && liveUpdate.file.exists())
        )
    LaunchedEffect(liveUpdate, stillInSaved) {
        if (!closing && (liveUpdate == null || (isSavedOrigin && !stillInSaved))) {
            closeForRemoval()
        }
    }

    PredictiveBackHandler(enabled = !closing && !backBlocked) { progress ->
        var startY = Float.NaN
        val dismissConfirmOnly = pendingConfirm != null
        try {
            progress.collect { event ->
                if (!dismissConfirmOnly) {
                    if (startY.isNaN()) startY = event.touchY
                    backEdge = event.swipeEdge
                    backDyNorm = ((event.touchY - startY) / 420f).coerceIn(-1f, 1f)
                    backProgress.snapTo(event.progress.coerceIn(0f, 1f))
                }
            }
            if (pendingConfirm != null) {
                pendingConfirm = null
            } else {
                closeAnimated()
            }
        } catch (_: CancellationException) {
            if (backProgress.value > 0f) {
                scope.launch {
                    backProgress.animateTo(
                        targetValue = 0f,
                        animationSpec = spring(
                            stiffness = 380f,
                            dampingRatio = Spring.DampingRatioNoBouncy,
                        ),
                    )
                }
            }
        }
    }

    val initialCloseSignal = remember(flight.sessionId) { closeSignal }
    LaunchedEffect(closeSignal) {
        if (closeSignal > initialCloseSignal && !closing) {
            closeAnimated()
        }
    }

    // Single unified physical curve `t` for geometry so position & size stay locked 1:1
    val t = flightAnim.value.coerceIn(0f, 1f)
    val collapseT = collapseAnim.value.coerceIn(0f, 1f)
    val bp = FastOutSlowInEasing.transform(backProgress.value.coerceIn(0f, 1f))
    val backScale = lerpFloat(1f, 0.88f, bp)
    val edgeSign = if (backEdge == BackEventCompat.EDGE_LEFT) 1f else -1f
    val backTx = with(density) { 18.dp.toPx() } * bp * edgeSign
    val backTy = with(density) { 12.dp.toPx() } * bp * backDyNorm
    DisposableEffect(t, collapseT, bp) {
        onProgress(t * collapseT * (1f - 0.35f * bp))
        onDispose {}
    }

    val relation = remember(update.timestamp, update.osSdkLevel) { resolveBuildRelation(update) }
    val releaseVisual = resolveReleaseVisual(update, relation)
    val savedVisual = resolveSavedVisual(update)

    val startBg = if (isSavedOrigin) {
        MaterialTheme.colorScheme.surface
    } else {
        releaseCardContainerColor(update, relation)
    }
    val endBg = MaterialTheme.colorScheme.surfaceContainerHigh
    val containerColor = lerpColor(startBg, endBg, sstepE(t, 0.10f, 0.78f))
    val scrimAlpha = 0.42f * sstepE(t, 0.25f, 0.92f) * collapseT * (1f - 0.35f * bp)
    val blurReveal = sstepE(t, 0.35f, 0.95f)
    val modalShape = RoundedCornerShape(lerp(24.dp, 28.dp, t) + (4.dp * bp))
    val cardBorderColor = containerColor.edgeOutline()
    val contentReveal = sstepE(t, 0.22f, 0.88f)
    val chevronReveal = sstepE(t, 0.15f, 0.78f)
    val titleGrow = sstepE(t, 0.08f, 0.85f)

    val topInsetDp = with(density) { topInsetPx.coerceAtLeast(0f).toDp() }
    val navBottomDp = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val targetBottomDp = maxOf(navBottomDp, with(density) { bottomReservedPx.coerceAtLeast(0f).toDp() })
    val effectiveBottomDp by animateDpAsState(
        targetValue = targetBottomDp,
        animationSpec = spring(
            stiffness = Spring.StiffnessMediumLow,
            dampingRatio = Spring.DampingRatioNoBouncy,
        ),
        label = "ReleaseOverlayBottomReserve",
    )
    val effectiveBottomPx = with(density) { effectiveBottomDp.toPx() }

    val actualSize = update.file?.takeIf { it.exists() }?.length() ?: update.fileSize
    val buildDate = remember(update.timestamp) {
        if (update.timestamp > 0L) StringUtil.formatBuildDate(context, update.timestamp) else context.getString(R.string.unknown_date)
    }
    val sizeText = remember(actualSize) {
        if (actualSize > 0L) Formatter.formatShortFileSize(context, actualSize) else context.getString(R.string.unknown_size)
    }
    val blockedReason = InstallUtils.getBlockedReason(update, unsafeMode)
    val canStream = InstallUtils.canStreamUpdate(update, streamUpdatesEnabled, forceRecovery)

    fun guardAndRunDownload(autoInstall: Boolean) {
        if (autoInstall && blockedReason != InstallUtils.BlockedReason.NONE) {
            pendingConfirm = PendingConfirmBanner(
                message = when (blockedReason) {
                    InstallUtils.BlockedReason.DOWNGRADE ->
                        context.getString(R.string.overlay_downgrade_blocked)
                    InstallUtils.BlockedReason.VERSION_UNSUPPORTED ->
                        context.getString(R.string.overlay_major_blocked)
                    InstallUtils.BlockedReason.NONE -> ""
                },
                isDanger = true,
                onConfirm = { pendingConfirm = null },
            )
            return
        }
        if (autoInstall && !isBatteryOk && !unsafeMode) {
            Toast.makeText(context, context.getString(R.string.overlay_battery_low), Toast.LENGTH_LONG).show()
            return
        }
        if (isMetered && meteredWarningEnabled) {
            pendingConfirm = PendingConfirmBanner(
                message = context.getString(R.string.overlay_metered_format, sizeText),
                isDanger = false,
                onConfirm = {
                    pendingConfirm = null
                    if (autoInstall) onDownloadAndInstall(update) else onDownloadOnly(update)
                },
            )
            return
        }
        if (autoInstall && unsafeMode) {
            pendingConfirm = PendingConfirmBanner(
                message = if (forceRecovery) {
                    context.getString(R.string.overlay_unsafe_recovery)
                } else {
                    context.getString(R.string.overlay_unsafe_force)
                },
                isDanger = true,
                onConfirm = {
                    pendingConfirm = null
                    onDownloadAndInstall(update)
                },
            )
            return
        }
        if (autoInstall) onDownloadAndInstall(update) else onDownloadOnly(update)
    }

    fun guardAndRunInstall() {
        if (blockedReason != InstallUtils.BlockedReason.NONE) {
            Toast.makeText(
                context,
                context.getString(R.string.overlay_blocked_format, blockedReason.toString()),
                Toast.LENGTH_LONG,
            ).show()
            return
        }
        if (!isBatteryOk && !unsafeMode) {
            Toast.makeText(context, context.getString(R.string.overlay_battery_low), Toast.LENGTH_LONG).show()
            return
        }
        if (InstallUtils.isScratchMounted() && !unsafeMode) {
            Toast.makeText(context, context.getString(R.string.overlay_scratch_mounted), Toast.LENGTH_LONG).show()
            return
        }
        pendingConfirm = PendingConfirmBanner(
            message = when {
                forceRecovery -> context.getString(R.string.overlay_install_recovery)
                unsafeMode -> context.getString(R.string.overlay_install_force)
                else -> context.getString(R.string.install_confirm_now, update.version.ifBlank { update.name })
            },
            isDanger = unsafeMode,
            onConfirm = {
                pendingConfirm = null
                onInstall(update)
            },
        )
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(1f),
    ) {
        val maxW = maxWidth
        val maxH = maxHeight
        val fallbackWpx = with(density) { maxWidth.toPx() }
        val fallbackHpx = with(density) { maxHeight.toPx() }

        val liveSource = flight.sourceBoundsProvider().let {
            if (it.width > 1f && it.height > 1f) it else flight.source
        }
        val endW = (maxW - 16.dp - horizontalInset * 2)
            .coerceAtLeast(240.dp)
            .coerceAtMost(maxW)
        val startW = if (liveSource.width > 1f) {
            with(density) { liveSource.width.roundToInt().toDp() }
        } else endW
        val availH = (maxH - topInsetDp - effectiveBottomDp).coerceAtLeast(160.dp)
        val endH = (availH - 14.dp).coerceAtLeast(140.dp)
        val startH = if (liveSource.height > 1f) {
            with(density) { liveSource.height.roundToInt().toDp() }
        } else 116.dp

        val cardWidth: Dp = lerp(startW.coerceIn(0.dp, maxW), endW, t)
        val cardHeight: Dp = lerp(startH.coerceAtLeast(64.dp), endH, t)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = scrimAlpha))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { if (!closing) closeAnimated() },
                ),
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = topInsetDp, bottom = effectiveBottomDp)
                .onPlaced { hostCoordsRef.value = it }
                .onGloballyPositioned { hostCoordsRef.value = it },
            contentAlignment = Alignment.Center,
        ) {
            val hostBounds = hostCoordsRef.value?.boundsInRoot()
            val targetHost = when {
                hostBounds != null && hostBounds.width > 1f && hostBounds.height > 1f -> hostBounds
                else -> Rect(
                    0f,
                    topInsetPx.coerceAtMost(fallbackHpx),
                    fallbackWpx,
                    (fallbackHpx - effectiveBottomPx).coerceAtLeast(topInsetPx + 1f),
                )
            }
            val src = liveSource
            val dockW = 1f - t
            val cardWpx = with(density) { cardWidth.toPx() }.roundToInt()
            val cardHpx = with(density) { cardHeight.toPx() }.roundToInt().coerceAtLeast(1)
            val hostWpx = targetHost.width.roundToInt()
            val hostHpx = targetHost.height.roundToInt()
            val e0x = (hostWpx - cardWpx) / 2 + cardWpx / 2f - targetHost.width / 2f
            val e0y = (hostHpx - cardHpx) / 2 + cardHpx / 2f - targetHost.height / 2f

            val dx = if (targetHost.width > 1f && src.width > 1f) {
                (src.center.x - targetHost.center.x - e0x) * dockW
            } else 0f
            val dy = if (targetHost.height > 1f && src.height > 1f) {
                (src.center.y - targetHost.center.y - e0y) * dockW
            } else 0f

            val clipViewport = scrollBlur.contentBounds
                .takeIf { it.width > 1f && it.height > 1f }
                ?: Rect(0f, 0f, fallbackWpx, fallbackHpx)

            val removalScale = lerpFloat(0.88f, 1f, collapseT)
            Box(
                modifier = Modifier
                    .requiredWidth(cardWidth)
                    .requiredHeight(cardHeight)
                    .graphicsLayer {
                        translationX = dx + backTx
                        translationY = dy + backTy
                        scaleX = removalScale * backScale
                        scaleY = removalScale * backScale
                        alpha = collapseT
                    }
                    .drawWithContent {
                        if (clipViewport.width > 1f && t < 0.999f) {
                            val w = size.width
                            val h = size.height
                            val rL = src.center.x - w / 2f
                            val rT = src.center.y - h / 2f
                            val keep = 1f - t
                            clipRect(
                                left = (clipViewport.left - rL).coerceIn(0f, w) * keep,
                                top = (clipViewport.top - rT).coerceIn(0f, h) * keep,
                                right = w - (w - (clipViewport.right - rL).coerceIn(0f, w)) * keep,
                                bottom = h - (h - (clipViewport.bottom - rT).coerceIn(0f, h)) * keep,
                            ) {
                                this@drawWithContent.drawContent()
                            }
                        } else {
                            drawContent()
                        }
                    },
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(modalShape)
                        .border(1.dp, cardBorderColor, modalShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            enabled = !closing,
                            onClick = {},
                        ),
                ) {
                    Box(Modifier.matchParentSize().background(containerColor))
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .graphicsLayer {
                                    clip = true
                                    alpha = blurReveal
                                }
                                .onPlaced { cardCoordsRef.value = it }
                                .onGloballyPositioned { cardCoordsRef.value = it }
                                .cardFlightBlurMask(
                                    state = scrollBlur,
                                    coordsProvider = { cardCoordsRef.value },
                                ),
                        )
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(
                                    containerColor.copy(
                                        alpha = lerpFloat(1f, blurConfig.tintAlpha, blurReveal),
                                    ),
                                ),
                        )
                    }

                    val loadedMd = (changelogState as? ChangelogState.Loaded)?.markdown
                    val bannerSpec = if (showReleaseBanner) update.resolvedBannerSpec(loadedMd) else null
                    val bannerHide = (t / 0.65f).coerceIn(0f, 1f)
                    val iconOut = sstepE(t, 0.05f, 0.62f)
                    val showProgress = shouldShowReleaseProgress(update)
                    val headerHeightDp = with(density) { headerHeightPx.toDp() }
                    val footerHeightDp = with(density) { footerHeightPx.toDp() }
                    val activeErrorText = serverError?.takeIf { it.isNotBlank() }
                        ?: update.errorMessage?.takeIf { it.isNotBlank() }
                    var lastErrorText by remember(flight.sessionId) { mutableStateOf(activeErrorText.orEmpty()) }
                    if (!activeErrorText.isNullOrBlank()) lastErrorText = activeErrorText
                    val morphErrorFromSaved = isSavedOrigin && !update.errorMessage.isNullOrBlank()
                    val showLayerA = contentReveal > 0.01f || morphErrorFromSaved

                    Box(modifier = Modifier.fillMaxSize()) {
                        // Layer A: Scrollable body recorded into innerScrollBlur so it blurs cleanly under Header & Footer
                        if (showLayerA) {
                            val slideUpPx = with(density) { 14.dp.toPx() } * (1f - contentReveal)
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .then(
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && blurEnabled && t > 0.35f) {
                                            Modifier.recordLayer(
                                                state = innerScrollBlur,
                                                backgroundColor = Color.Transparent,
                                                topEdgeFadeHeight = 0.dp,
                                                bottomEdgeFadeHeight = 0.dp,
                                            )
                                        } else {
                                            Modifier
                                        },
                                    ),
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(rememberScrollState(), enabled = t > 0.65f)
                                        .padding(
                                            start = 20.dp,
                                            end = 20.dp,
                                            top = headerHeightDp + if (isSavedOrigin) lerp(0.dp, 4.dp, t) else 4.dp,
                                            bottom = footerHeightDp + 12.dp,
                                        ),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    // Smooth inline server / download error banner (stays locked 1:1 from t=0 on Saved morph)
                                    AnimatedVisibility(
                                        visible = !activeErrorText.isNullOrBlank(),
                                        enter = expandVertically(tween(240, easing = FastOutSlowInEasing)) + fadeIn(tween(180)),
                                        exit = shrinkVertically(tween(220, easing = FastOutSlowInEasing)) + fadeOut(tween(140)),
                                    ) {
                                        val errAlpha = if (morphErrorFromSaved) 1f else contentReveal
                                        val errSlideY = if (morphErrorFromSaved) 0f else slideUpPx
                                        val errBg = MaterialTheme.colorScheme.errorContainer.copy(
                                            alpha = if (morphErrorFromSaved) lerpFloat(0.65f, 1f, t) else 1f,
                                        )
                                        val errShape = RoundedCornerShape(
                                            if (morphErrorFromSaved) lerp(14.dp, 16.dp, t) else 16.dp,
                                        )
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .alpha(errAlpha)
                                                .graphicsLayer { translationY = errSlideY }
                                                .clip(errShape)
                                                .background(errBg)
                                                .border(1.dp, errBg.edgeOutline(), errShape)
                                                .padding(
                                                    start = if (morphErrorFromSaved) lerp(12.dp, 14.dp, t) else 14.dp,
                                                    end = 8.dp,
                                                    top = if (morphErrorFromSaved) lerp(8.dp, 10.dp, t) else 10.dp,
                                                    bottom = if (morphErrorFromSaved) lerp(8.dp, 10.dp, t) else 10.dp,
                                                ),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Warning,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                                modifier = Modifier.size(18.dp),
                                            )
                                            Text(
                                                text = lastErrorText,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onErrorContainer,
                                                modifier = Modifier.weight(1f),
                                            )
                                            if (!serverError.isNullOrBlank()) {
                                                IconButton(
                                                    onClick = onDismissServerError,
                                                    enabled = !activeErrorText.isNullOrBlank(),
                                                    modifier = Modifier.size(28.dp),
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.Close,
                                                        contentDescription = stringResource(R.string.desc_close),
                                                        tint = MaterialTheme.colorScheme.onErrorContainer,
                                                        modifier = Modifier.size(16.dp),
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    if (contentReveal > 0.01f) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .alpha(contentReveal)
                                            .graphicsLayer { translationY = slideUpPx },
                                        verticalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                    // 2.1 Release files block
                                    ReleaseFilesCard(
                                        update = update,
                                        partsExpanded = partsExpanded,
                                        onToggleParts = {
                                            haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                                            partsExpanded = !partsExpanded
                                        },
                                    )

                                    // 2.2 Link buttons
                                    if (update.links.isNotEmpty()) {
                                        FlowRow(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            update.links.forEach { link ->
                                                SecondaryActionPill(
                                                    icon = resolveLinkIcon(link),
                                                    label = link.title,
                                                    height = 40.dp,
                                                    onClick = {
                                                        runCatching {
                                                            context.startActivity(
                                                                Intent(Intent.ACTION_VIEW, link.url.toUri()),
                                                            )
                                                        }
                                                    },
                                                )
                                            }
                                        }
                                    }

                                    // 2.3 Changelog section
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                    val isChangelogBusy = changelogState is ChangelogState.Loading ||
                                        (changelogState as? ChangelogState.Loaded)?.isRefreshing == true
                                    val spinTransition = rememberInfiniteTransition(label = "ChangelogRefreshSpin")
                                    val spinAngle by spinTransition.animateFloat(
                                        initialValue = 0f,
                                        targetValue = 360f,
                                        animationSpec = infiniteRepeatable(
                                            animation = tween(750, easing = LinearEasing),
                                            repeatMode = RepeatMode.Restart,
                                        ),
                                        label = "ChangelogSpinAngle",
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text(
                                            text = stringResource(R.string.overlay_changelog_title),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                        IconButton(
                                            onClick = {
                                                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                                                onRequestChangelog(update, true)
                                            },
                                            enabled = !isChangelogBusy,
                                            modifier = Modifier.size(34.dp),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Refresh,
                                                contentDescription = stringResource(R.string.desc_reload_changelog),
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier
                                                    .size(18.dp)
                                                    .rotate(if (isChangelogBusy) spinAngle else 0f),
                                            )
                                        }
                                    }

                                    val loadedAlpha by animateFloatAsState(
                                        targetValue = if ((changelogState as? ChangelogState.Loaded)?.isRefreshing == true) {
                                            0.55f
                                        } else {
                                            1f
                                        },
                                        animationSpec = tween(220),
                                        label = "ChangelogDim",
                                    )

                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .animateContentSize(
                                                animationSpec = spring(
                                                    stiffness = Spring.StiffnessMediumLow,
                                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                                ),
                                            ),
                                    ) {
                                        when (changelogState) {
                                            ChangelogState.Idle, ChangelogState.Loading -> {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(vertical = 16.dp),
                                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                ) {
                                                    CircularProgressIndicator(
                                                        strokeWidth = 2.5.dp,
                                                        modifier = Modifier.size(18.dp),
                                                    )
                                                    Text(
                                                        text = stringResource(R.string.overlay_changelog_loading),
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    )
                                                }
                                            }

                                            is ChangelogState.Error -> {
                                                val errBg = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
                                                val errShape = RoundedCornerShape(14.dp)
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clip(errShape)
                                                        .background(errBg)
                                                        .border(1.dp, errBg.edgeOutline(), errShape)
                                                        .padding(horizontal = 12.dp, vertical = 10.dp),
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
                                                        text = changelogState.message?.takeIf { it.isNotBlank() }
                                                            ?: stringResource(R.string.overlay_changelog_error),
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                                    )
                                                }
                                            }

                                            is ChangelogState.Loaded -> {
                                                Box(modifier = Modifier.alpha(loadedAlpha)) {
                                                    if (changelogState.markdown.isBlank()) {
                                                        Text(
                                                            text = stringResource(R.string.overlay_changelog_empty),
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        )
                                                    } else {
                                                        ComposeMarkdown(
                                                            markdown = changelogState.markdown,
                                                            activeFlightImageUrl = activeFlightImageUrl,
                                                            onImageClick = onImageClick,
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // 2.4 Remaining technical metadata at the very bottom
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                                    BuildInfoGridCard(
                                        update = update,
                                        buildDate = buildDate,
                                        sizeText = sizeText,
                                    )

                                    if (!update.sha256.isNullOrBlank()) {
                                        val hashBg = MaterialTheme.colorScheme.surface
                                        val hashShape = RoundedCornerShape(16.dp)
                                        val hashInteraction = remember { MutableInteractionSource() }
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .tactilePressScale(hashInteraction, pressedScale = 0.98f)
                                                .clip(hashShape)
                                                .background(hashBg)
                                                .border(1.dp, hashBg.edgeOutline(), hashShape)
                                                .clickable(
                                                    interactionSource = hashInteraction,
                                                    indication = androidx.compose.material3.ripple(),
                                                ) {
                                                    haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                                                    val cm = context.getSystemService(ClipboardManager::class.java)
                                                    cm?.setPrimaryClip(ClipData.newPlainText("SHA-256", update.sha256))
                                                    Toast.makeText(context, context.getString(R.string.toast_sha_copied), Toast.LENGTH_SHORT).show()
                                                }
                                                .padding(horizontal = 14.dp, vertical = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = stringResource(R.string.overlay_archive_sha),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary,
                                                )
                                                Text(
                                                    text = update.sha256,
                                                    style = MaterialTheme.typography.bodySmall.copy(
                                                        fontFamily = FontFamily.Monospace,
                                                    ),
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                            Icon(
                                                imageVector = Icons.Rounded.ContentCopy,
                                                contentDescription = stringResource(R.string.desc_copy_sha),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(16.dp),
                                            )
                                        }
                                    }

                                    Spacer(Modifier.height(4.dp))
                                    }
                                    }
                                }
                            }
                        }

                        // Layer B: Pinned Top Header with inner scroll blur frost
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.TopCenter)
                                .onPlaced { headerCoordsRef.value = it }
                                .onGloballyPositioned { headerCoordsRef.value = it }
                                .onSizeChanged { headerHeightPx = it.height },
                        ) {
                            if (contentReveal > 0.01f) {
                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .graphicsLayer { alpha = contentReveal },
                                ) {
                                    Box(Modifier.matchParentSize().background(containerColor))
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && blurEnabled) {
                                        Box(
                                            modifier = Modifier
                                                .matchParentSize()
                                                .graphicsLayer {
                                                    clip = true
                                                    alpha = blurReveal
                                                }
                                                .cardFlightBlurMask(
                                                    state = scrollBlur,
                                                    coordsProvider = { headerCoordsRef.value },
                                                ),
                                        )
                                        Box(
                                            modifier = Modifier
                                                .matchParentSize()
                                                .graphicsLayer { clip = true }
                                                .cardFlightBlurMask(
                                                    state = innerScrollBlur,
                                                    coordsProvider = { headerCoordsRef.value },
                                                ),
                                        )
                                        Box(
                                            modifier = Modifier
                                                .matchParentSize()
                                                .background(
                                                    containerColor.copy(
                                                        alpha = lerpFloat(1f, blurConfig.tintAlpha, blurReveal),
                                                    ),
                                                ),
                                        )
                                    }
                                }
                            }

                            Column(modifier = Modifier.fillMaxWidth()) {
                                // 0. Morphing Release Banner (flies down into text section & collapses strictly clipped)
                                if (!bannerSpec.isNullOrBlank() && bannerHide < 0.999f) {
                                    val keep = 1f - bannerHide
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .graphicsLayer { clip = true }
                                            .clipToBounds()
                                            .layout { measurable, constraints ->
                                                val p = measurable.measure(
                                                    constraints.copy(
                                                        minHeight = 0,
                                                        maxHeight = Constraints.Infinity,
                                                    ),
                                                )
                                                val visH = (p.height * keep).roundToInt().coerceAtLeast(0)
                                                val diveY = (bannerHide * p.height * 0.35f).roundToInt()
                                                layout(p.width, visH) {
                                                    p.placeRelative(0, (visH - p.height) + diveY)
                                                }
                                            },
                                    ) {
                                        ReleaseBannerView(
                                            bannerSpec = bannerSpec,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .graphicsLayer {
                                                    alpha = 1f - sstepE(bannerHide, 0.05f, 0.85f)
                                                },
                                        )
                                    }
                                }

                                // 1. Header + Capsules + Progress Bar (continuous 1:1 morph from collapsed card)
                                val headerIconBg = if (isSavedOrigin) savedVisual.iconBg else releaseVisual.iconBg
                                val headerIconFg = if (isSavedOrigin) savedVisual.iconFg else releaseVisual.iconFg
                                val headerIcon = if (isSavedOrigin) savedVisual.icon else releaseVisual.icon
                                val headerPadBottom = if (isSavedOrigin) lerp(10.dp, 12.dp, t) else lerp(16.dp, 12.dp, t)

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                            enabled = !closing && t > 0.5f,
                                            onClick = { closeAnimated() },
                                        )
                                        .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = headerPadBottom),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                // Icon slides left & fades out, freeing horizontal space for title
                                if (iconOut < 0.999f) {
                                    val iconShiftPx = with(density) { 44.dp.toPx() } * iconOut
                                    val iconSlotW = lerp(44.dp, 0.dp, iconOut)
                                    val iconGapW = lerp(12.dp, 0.dp, iconOut)
                                    Box(
                                        modifier = Modifier
                                            .width(iconSlotW)
                                            .height(44.dp)
                                            .clipToBounds(),
                                        contentAlignment = Alignment.CenterStart,
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(44.dp)
                                                .graphicsLayer {
                                                    translationX = -iconShiftPx
                                                    alpha = 1f - sstepE(iconOut, 0f, 0.78f)
                                                }
                                                .clip(CircleShape)
                                                .background(headerIconBg)
                                                .border(1.dp, headerIconBg.edgeOutline(), CircleShape),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            ReleaseIconBannerView(
                                                icoSpec = update.icoBanner,
                                                tint = headerIconFg,
                                                iconSize = 24.dp,
                                            ) {
                                                Icon(
                                                    imageVector = headerIcon,
                                                    contentDescription = null,
                                                    tint = headerIconFg,
                                                    modifier = Modifier.size(22.dp),
                                                )
                                            }
                                        }
                                    }
                                    if (iconGapW > 0.2.dp) {
                                        Spacer(Modifier.width(iconGapW))
                                    }
                                }

                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    val titleScale = lerpFloat(1f, 1.12f, titleGrow)
                                    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
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
                                            modifier = Modifier
                                                .weight(1f, fill = false)
                                                .layout { measurable, constraints ->
                                                    val unscaledMaxW = if (constraints.hasBoundedWidth) {
                                                        (constraints.maxWidth / titleScale).toInt().coerceAtLeast(0)
                                                    } else {
                                                        constraints.maxWidth
                                                    }
                                                    val placeable = measurable.measure(
                                                        constraints.copy(
                                                            minWidth = 0,
                                                            maxWidth = unscaledMaxW,
                                                        ),
                                                    )
                                                    val scaledW = (placeable.width * titleScale).roundToInt()
                                                        .coerceIn(constraints.minWidth, constraints.maxWidth)
                                                    val scaledH = (placeable.height * titleScale).roundToInt()
                                                        .coerceIn(constraints.minHeight, constraints.maxHeight)
                                                    layout(scaledW, scaledH) {
                                                        placeable.placeRelative(0, (scaledH - placeable.height) / 2)
                                                    }
                                                }
                                                .graphicsLayer {
                                                    scaleX = titleScale
                                                    scaleY = titleScale
                                                    transformOrigin = TransformOrigin(if (isRtl) 1f else 0f, 0.5f)
                                                },
                                        )
                                        if (!isSavedOrigin) {
                                            UpdaterBadgePill(
                                                text = stringResource(relation.labelRes),
                                                containerColor = releaseVisual.relationBg,
                                                contentColor = releaseVisual.relationFg,
                                            )
                                        }
                                    }
                                    if (isSavedOrigin) {
                                        AutoSizeSingleLineText(
                                            text = update.name,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    } else {
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

                                val chevronSlotW = lerp(0.dp, 28.dp, chevronReveal)
                                if (chevronSlotW > 0.5.dp) {
                                    Spacer(Modifier.width(lerp(0.dp, 8.dp, chevronReveal)))
                                    Box(
                                        modifier = Modifier
                                            .width(chevronSlotW)
                                            .height(28.dp)
                                            .alpha(chevronReveal),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.KeyboardArrowDown,
                                            contentDescription = stringResource(R.string.desc_close),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier
                                                .size(24.dp)
                                                .rotate(lerpFloat(0f, 180f, t)),
                                        )
                                    }
                                }
                            }

                            // Capsules directly under header (smooth inertial reflow on width change)
                            if (isSavedOrigin) {
                                AnimatedInertialFlowRow(
                                    horizontalSpacing = 6.dp,
                                    verticalSpacing = 6.dp,
                                ) {
                                    if (sizeText.isNotEmpty()) UpdaterBadgePill(sizeText)
                                    if (buildDate.isNotEmpty()) UpdaterBadgePill(buildDate)
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
                            } else {
                                ReleaseTagFlowRow(update = update)
                            }

                            // Progress bar (status pill left + bar right) continuously morphed
                                    AnimatedVisibility(
                                        visible = showProgress,
                                        enter = expandVertically(tween(220, easing = FastOutSlowInEasing)) + fadeIn(tween(180)),
                                        exit = shrinkVertically(tween(220, easing = FastOutSlowInEasing)) + fadeOut(tween(140)),
                                    ) {
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            if (!isSavedOrigin) {
                                                HorizontalDivider(
                                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                                                )
                                            }
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

                        // Layer C: Pinned Bottom Footer with inner scroll blur frost
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                                .onPlaced { footerCoordsRef.value = it }
                                .onGloballyPositioned { footerCoordsRef.value = it }
                                .onSizeChanged { footerHeightPx = it.height },
                        ) {
                            if (contentReveal > 0.01f) {
                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .graphicsLayer { alpha = contentReveal },
                                ) {
                                    Box(Modifier.matchParentSize().background(containerColor))
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && blurEnabled) {
                                        Box(
                                            modifier = Modifier
                                                .matchParentSize()
                                                .graphicsLayer {
                                                    clip = true
                                                    alpha = blurReveal
                                                }
                                                .cardFlightBlurMask(
                                                    state = scrollBlur,
                                                    coordsProvider = { footerCoordsRef.value },
                                                ),
                                        )
                                        Box(
                                            modifier = Modifier
                                                .matchParentSize()
                                                .graphicsLayer { clip = true }
                                                .cardFlightBlurMask(
                                                    state = innerScrollBlur,
                                                    coordsProvider = { footerCoordsRef.value },
                                                ),
                                        )
                                        Box(
                                            modifier = Modifier
                                                .matchParentSize()
                                                .background(
                                                    containerColor.copy(
                                                        alpha = lerpFloat(1f, blurConfig.tintAlpha, blurReveal),
                                                    ),
                                                ),
                                        )
                                    }
                                }
                            }

                            Column(modifier = Modifier.fillMaxWidth()) {
                                // 3. Inline confirmation banner (cached lastConfirm so exit shrink/fade animates cleanly)
                                AnimatedVisibility(
                                    visible = pendingConfirm != null && contentReveal > 0.01f,
                                    enter = expandVertically(tween(240, easing = FastOutSlowInEasing)) + fadeIn(tween(180)),
                                    exit = shrinkVertically(tween(220, easing = FastOutSlowInEasing)) + fadeOut(tween(150)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .alpha(contentReveal),
                                ) {
                                    lastConfirm?.let { conf ->
                                        val isActiveConfirm = pendingConfirm != null
                                        val cBg = if (conf.isDanger) {
                                            MaterialTheme.colorScheme.errorContainer
                                        } else {
                                            MaterialTheme.colorScheme.primaryContainer
                                        }
                                        val cFg = if (conf.isDanger) {
                                            MaterialTheme.colorScheme.onErrorContainer
                                        } else {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        }
                                        val cShape = RoundedCornerShape(16.dp)
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 20.dp, vertical = 6.dp)
                                                .clip(cShape)
                                                .background(cBg)
                                                .border(1.dp, cBg.edgeOutline(), cShape)
                                                .padding(14.dp),
                                            verticalArrangement = Arrangement.spacedBy(10.dp),
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Warning,
                                                    contentDescription = null,
                                                    tint = cFg,
                                                    modifier = Modifier.size(18.dp),
                                                )
                                                Text(
                                                    text = conf.message,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = FontWeight.Medium,
                                                    color = cFg,
                                                )
                                            }
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            ) {
                                                SecondaryActionPill(
                                                    icon = Icons.Rounded.Close,
                                                    label = stringResource(R.string.action_cancel),
                                                    enabled = isActiveConfirm,
                                                    height = 46.dp,
                                                    onClick = { pendingConfirm = null },
                                                    modifier = Modifier.weight(1f),
                                                )
                                                UpdaterPrimaryPillButton(
                                                    label = stringResource(R.string.action_proceed),
                                                    icon = Icons.Rounded.Check,
                                                    enabled = isActiveConfirm,
                                                    isDanger = conf.isDanger,
                                                    height = 46.dp,
                                                    onClick = conf.onConfirm,
                                                    modifier = Modifier.weight(1f),
                                                )
                                            }
                                        }
                                    }
                                }

                        // 4. Sticky Action Footer (1:1 seamless morph for Saved buttons)
                        if (isSavedOrigin || contentReveal > 0.01f) {
                            val footerAlpha = if (isSavedOrigin) 1f else contentReveal
                            val btnMorph = if (isSavedOrigin) sstepE(t, 0.14f, 0.88f) else 1f
                            val footerPadBottom = if (isSavedOrigin) lerp(16.dp, 14.dp, t) else 14.dp
                            val footerPadTop = if (isSavedOrigin) lerp(0.dp, 14.dp, t) else 14.dp

                            val footerMode = when {
                                update.status == UpdateStatus.UPDATED_NEED_REBOOT -> ReleaseFooterMode.REBOOT
                                update.status == UpdateStatus.DOWNLOADING ||
                                    update.status == UpdateStatus.STARTING ||
                                    update.status == UpdateStatus.PAUSED ||
                                    update.status == UpdateStatus.PAUSED_ERROR -> ReleaseFooterMode.ACTIVE_DOWNLOAD
                                update.hasVerifiedPackage() || isSavedOrigin -> ReleaseFooterMode.VERIFIED_OR_SAVED
                                else -> ReleaseFooterMode.NOT_DOWNLOADED
                            }

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .alpha(footerAlpha)
                                    .padding(start = 20.dp, end = 20.dp, top = footerPadTop, bottom = footerPadBottom),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                AnimatedContent(
                                    targetState = footerMode,
                                    transitionSpec = {
                                        (fadeIn(tween(200)) + scaleIn(initialScale = 0.94f, animationSpec = tween(200))) togetherWith
                                            (fadeOut(tween(140)) + scaleOut(targetScale = 0.94f, animationSpec = tween(140))) using
                                            SizeTransform(clip = false)
                                    },
                                    label = "ReleaseFooterModeMorph",
                                ) { mode ->
                                    when (mode) {
                                        ReleaseFooterMode.REBOOT -> {
                                            UpdaterPrimaryPillButton(
                                                label = if (btnMorph > 0.5f) stringResource(R.string.overlay_reboot_to_apply) else stringResource(R.string.action_reboot_now),
                                                icon = Icons.Rounded.RestartAlt,
                                                onClick = onReboot,
                                                modifier = Modifier.fillMaxWidth(),
                                            )
                                        }

                                        ReleaseFooterMode.ACTIVE_DOWNLOAD -> {
                                            val isPaused = update.status == UpdateStatus.PAUSED ||
                                                update.status == UpdateStatus.PAUSED_ERROR
                                            MorphingDownloadControlRow(
                                                isPaused = isPaused,
                                                pauseLabel = if (btnMorph > 0.5f) stringResource(R.string.overlay_pause_format, update.progress) else stringResource(R.string.action_pause),
                                                resumeLabel = if (btnMorph > 0.5f) stringResource(R.string.overlay_resume_format, update.progress) else stringResource(R.string.action_resume),
                                                cancelLabel = stringResource(R.string.action_cancel),
                                                deleteLabel = stringResource(R.string.action_delete),
                                                confirmDeleteLabel = stringResource(R.string.action_confirm),
                                                resumeEnabled = isOnline,
                                                morphToExpanded = btnMorph,
                                                deleteArmed = deleteArmedAt > 0L,
                                                onPause = {
                                                    deleteArmedAt = 0L
                                                    onPauseDownload(update)
                                                },
                                                onResume = {
                                                    deleteArmedAt = 0L
                                                    onResumeDownload(update)
                                                },
                                                onCancel = {
                                                    if (deleteArmedAt > 0L) {
                                                        deleteArmedAt = 0L
                                                        if (isSavedOrigin || update.isLocalUpdate) {
                                                            closeForRemoval { onCancelDownload(update) }
                                                        } else {
                                                            onCancelDownload(update)
                                                        }
                                                    } else {
                                                        deleteArmedAt = SystemClock.uptimeMillis()
                                                    }
                                                },
                                                onDelete = {
                                                    if (deleteArmedAt > 0L) {
                                                        deleteArmedAt = 0L
                                                        if (isSavedOrigin || update.isLocalUpdate) {
                                                            closeForRemoval { onCancelDownload(update) }
                                                        } else {
                                                            onCancelDownload(update)
                                                        }
                                                    } else {
                                                        deleteArmedAt = SystemClock.uptimeMillis()
                                                    }
                                                },
                                            )
                                        }

                                        ReleaseFooterMode.VERIFIED_OR_SAVED -> {
                                            val canInstallNow = blockedReason == InstallUtils.BlockedReason.NONE && !isBusy
                                            val installLabel = when {
                                                blockedReason == InstallUtils.BlockedReason.DOWNGRADE -> stringResource(R.string.blocked_update_dialog_title_downgrade)
                                                blockedReason == InstallUtils.BlockedReason.VERSION_UNSUPPORTED -> stringResource(R.string.badge_major_upgrade)
                                                forceRecovery -> stringResource(R.string.install_label_recovery)
                                                unsafeMode -> stringResource(R.string.install_label_force)
                                                else -> stringResource(R.string.action_install)
                                            }
                                            MorphingInstallExportTrashBlock(
                                                installLabel = installLabel,
                                                installEnabled = canInstallNow && update.file?.exists() == true,
                                                isDanger = unsafeMode,
                                                hasExport = update.file?.exists() == true,
                                                isBusy = isBusy,
                                                morph = btnMorph,
                                                deleteArmed = deleteArmedAt > 0L,
                                                onInstall = { guardAndRunInstall() },
                                                onExport = { onExport(update) },
                                                onDelete = {
                                                    if (deleteArmedAt > 0L) {
                                                        deleteArmedAt = 0L
                                                        if (isSavedOrigin || update.isLocalUpdate) {
                                                            closeForRemoval { onDelete(update) }
                                                        } else {
                                                            onDelete(update)
                                                        }
                                                    } else {
                                                        deleteArmedAt = SystemClock.uptimeMillis()
                                                    }
                                                },
                                            )
                                        }

                                        ReleaseFooterMode.NOT_DOWNLOADED -> {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            ) {
                                                SecondaryActionPill(
                                                    icon = Icons.Rounded.Download,
                                                    label = stringResource(R.string.action_download),
                                                    enabled = isOnline && !update.downloadUrl.isNullOrBlank(),
                                                    height = 46.dp,
                                                    onClick = { guardAndRunDownload(autoInstall = false) },
                                                    modifier = Modifier.weight(1f),
                                                )
                                                UpdaterPrimaryPillButton(
                                                    label = if (canStream) stringResource(R.string.action_stream_install) else stringResource(R.string.action_download_install_new),
                                                    icon = Icons.Rounded.SystemUpdateAlt,
                                                    enabled = isOnline && !update.downloadUrl.isNullOrBlank() && !isBusy,
                                                    isDanger = unsafeMode,
                                                    height = 46.dp,
                                                    onClick = {
                                                        if (canStream) {
                                                            guardAndRunInstall()
                                                        } else {
                                                            guardAndRunDownload(autoInstall = true)
                                                        }
                                                    },
                                                    modifier = Modifier.weight(1.35f),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReleaseFilesCard(
    update: Update,
    partsExpanded: Boolean,
    onToggleParts: () -> Unit,
) {
    val context = LocalContext.current
    val bg = MaterialTheme.colorScheme.surface
    val shape = RoundedCornerShape(18.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(bg)
            .border(1.dp, bg.edgeOutline(), shape)
            .padding(14.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.overlay_package_file),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
            AutoSizeSingleLineText(
                text = update.name,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (update.parts.size > 1) {
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleParts)
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.overlay_parts_title, update.parts.size),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                val hashedCount = update.parts.count { !it.sha256.isNullOrBlank() }
                if (hashedCount > 0) {
                    UpdaterBadgePill(stringResource(R.string.overlay_hashed_format, hashedCount))
                    Spacer(Modifier.width(6.dp))
                }
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier
                        .size(18.dp)
                        .rotate(if (partsExpanded) 180f else 0f),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AnimatedVisibility(
                visible = partsExpanded,
                enter = expandVertically(tween(220, easing = FastOutSlowInEasing)) + fadeIn(tween(180)),
                exit = shrinkVertically(tween(220, easing = FastOutSlowInEasing)) + fadeOut(tween(140)),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    update.parts.forEachIndexed { idx, part ->
                        val partName = part.url.substringAfterLast('/').substringBefore('?')
                        val partSizeStr = part.size?.let { Formatter.formatShortFileSize(context, it) } ?: ""
                        val lineTitle = buildString {
                            append("#${idx + 1}: ")
                            append(partName)
                            if (partSizeStr.isNotEmpty()) append(" ($partSizeStr)")
                        }
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            AutoSizeSingleLineText(
                                text = lineTitle,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            if (!part.sha256.isNullOrBlank()) {
                                Text(
                                    text = stringResource(R.string.overlay_part_sha_format, part.sha256.take(16)),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MorphingInstallExportTrashBlock(
    installLabel: String,
    installEnabled: Boolean,
    isDanger: Boolean,
    hasExport: Boolean,
    isBusy: Boolean,
    morph: Float,
    deleteArmed: Boolean,
    onInstall: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val btnH = 46.dp
    val rowGap = 10.dp

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val totalW = maxWidth
        val colGap = 10.dp
        val halfW = ((totalW - colGap) / 2f).coerceAtLeast(46.dp)
        val containerH = lerp(btnH, btnH * 2 + rowGap, morph)

        val startIconsW = if (hasExport) btnH * 2 + 16.dp else btnH + 8.dp
        val startInstallW = (totalW - startIconsW).coerceAtLeast(64.dp)
        val installW = lerp(startInstallW, totalW, sstepE(morph, 0f, 0.72f))

        val row2Y = lerp(0.dp, btnH + rowGap, sstepE(morph, 0.05f, 0.80f))
        val textAlpha = sstepE(morph, 0.38f, 0.92f)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(containerH),
        ) {
            UpdaterPrimaryPillButton(
                label = installLabel,
                icon = Icons.Rounded.SystemUpdateAlt,
                enabled = installEnabled,
                isDanger = isDanger,
                height = btnH,
                onClick = onInstall,
                modifier = Modifier
                    .width(installW)
                    .align(Alignment.TopStart),
            )

            if (hasExport) {
                val startExpX = (totalW - btnH * 2 - 8.dp).coerceAtLeast(0.dp)
                val expX = lerp(startExpX, 0.dp, morph)
                val expW = lerp(btnH, halfW, sstepE(morph, 0.12f, 0.95f))
                val expBg = MaterialTheme.colorScheme.surfaceContainerHighest
                val expFg = MaterialTheme.colorScheme.onSurface
                val expShape = RoundedCornerShape(16.dp)

                Row(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                x = with(density) { expX.roundToPx() },
                                y = with(density) { row2Y.roundToPx() },
                            )
                        }
                        .width(expW)
                        .height(btnH)
                        .alpha(if (!isBusy) 1f else 0.45f)
                        .clip(expShape)
                        .background(expBg)
                        .border(1.dp, expBg.edgeOutline(), expShape)
                        .clickable(enabled = !isBusy) {
                            haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                            onExport()
                        }
                        .padding(horizontal = lerp(0.dp, 12.dp, morph)),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SaveAlt,
                        contentDescription = stringResource(R.string.action_export),
                        tint = expFg,
                        modifier = Modifier.size(18.dp),
                    )
                    if (textAlpha > 0.01f) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clipToBounds()
                                .layout { measurable, constraints ->
                                    val p = measurable.measure(constraints.copy(minWidth = 0))
                                    val w = (p.width * textAlpha).roundToInt().coerceAtLeast(0)
                                    layout(w, p.height) {
                                        p.placeRelative(0, 0)
                                    }
                                }
                                .alpha(textAlpha),
                        ) {
                            Spacer(Modifier.width(7.dp))
                            Text(
                                text = stringResource(R.string.action_export),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = expFg,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            val startTrashX = (totalW - btnH).coerceAtLeast(0.dp)
            val targetTrashW = if (hasExport) halfW else totalW
            val targetTrashX = if (hasExport) halfW + colGap else 0.dp
            val trashW = lerp(btnH, targetTrashW, sstepE(morph, 0.15f, 0.98f))
            val trashX = lerp(startTrashX, targetTrashX, morph)

            val trashBg by animateColorAsState(
                targetValue = if (deleteArmed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.errorContainer,
                animationSpec = spring(stiffness = Spring.StiffnessMedium),
                label = "InstallTrashBg",
            )
            val trashFg by animateColorAsState(
                targetValue = if (deleteArmed) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onErrorContainer,
                animationSpec = spring(stiffness = Spring.StiffnessMedium),
                label = "InstallTrashFg",
            )
            val trashCorner by animateDpAsState(
                targetValue = if (deleteArmed) 23.dp else 16.dp,
                animationSpec = spring(
                    stiffness = Spring.StiffnessMedium,
                    dampingRatio = Spring.DampingRatioNoBouncy,
                ),
                label = "InstallTrashCorner",
            )
            val trashShape = RoundedCornerShape(trashCorner)

            Row(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            x = with(density) { trashX.roundToPx() },
                            y = with(density) { row2Y.roundToPx() },
                        )
                    }
                    .width(trashW)
                    .height(btnH)
                    .alpha(if (!isBusy) 1f else 0.45f)
                    .clip(trashShape)
                    .background(trashBg)
                    .border(1.dp, trashBg.edgeOutline(), trashShape)
                    .clickable(enabled = !isBusy) {
                        haptics.performHapticFeedback(
                            if (deleteArmed) HapticFeedbackType.LongPress else HapticFeedbackType.ContextClick,
                        )
                        onDelete()
                    }
                    .padding(horizontal = lerp(0.dp, 12.dp, morph)),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AnimatedContent(
                    targetState = deleteArmed,
                    transitionSpec = {
                        (scaleIn(initialScale = 0.65f, animationSpec = spring(stiffness = Spring.StiffnessMedium)) + fadeIn(tween(150))) togetherWith
                            (scaleOut(targetScale = 0.65f, animationSpec = tween(110)) + fadeOut(tween(110)))
                    },
                    label = "InstallTrashIconMorph",
                ) { armed ->
                    Icon(
                        imageVector = if (armed) Icons.Rounded.Check else Icons.Rounded.Delete,
                        contentDescription = if (armed) stringResource(R.string.overlay_confirm_delete) else stringResource(R.string.action_delete),
                        tint = trashFg,
                        modifier = Modifier.size(18.dp),
                    )
                }
                if (textAlpha > 0.01f) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clipToBounds()
                            .layout { measurable, constraints ->
                                val p = measurable.measure(constraints.copy(minWidth = 0))
                                val w = (p.width * textAlpha).roundToInt().coerceAtLeast(0)
                                layout(w, p.height) {
                                    p.placeRelative(0, 0)
                                }
                            }
                            .alpha(textAlpha),
                    ) {
                        Spacer(Modifier.width(7.dp))
                        AnimatedContent(
                            targetState = deleteArmed,
                            transitionSpec = {
                                (fadeIn(tween(160)) + scaleIn(initialScale = 0.90f, animationSpec = tween(160))) togetherWith
                                    (fadeOut(tween(110)) + scaleOut(targetScale = 0.90f, animationSpec = tween(110))) using
                                    SizeTransform(clip = false)
                            },
                            label = "InstallTrashTextMorph",
                        ) { armed ->
                            Text(
                                text = if (armed) stringResource(R.string.action_confirm) else stringResource(R.string.action_delete),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = trashFg,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BuildInfoGridCard(
    update: Update,
    buildDate: String,
    sizeText: String,
) {
    val context = LocalContext.current
    val bg = MaterialTheme.colorScheme.surface
    val shape = RoundedCornerShape(18.dp)
    val cells = remember(update, buildDate, sizeText) {
        buildList {
            add(context.getString(R.string.build_date) to buildDate)
            add(context.getString(R.string.info_package_size) to sizeText)
            if (update.osSdkLevel > 0) add(context.getString(R.string.info_android_api) to context.getString(R.string.info_api_format, update.osSdkLevel))
            update.osPatchLevel?.takeIf { it.isNotBlank() }?.let { add(context.getString(R.string.security_update) to it) }
            update.type?.takeIf { it.isNotBlank() }?.let { add(context.getString(R.string.info_build_type) to it) }
            if (update.isSplitDownload) {
                val cnt = update.parts.size.takeIf { it > 0 }
                    ?: update.downloadUrl?.lines()?.count { it.isNotBlank() }
                    ?: 0
                add(context.getString(R.string.info_delivery) to context.getString(R.string.info_delivery_split, cnt))
            }
        }
    }

    if (cells.isEmpty()) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(bg)
            .border(1.dp, bg.edgeOutline(), shape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (cells.isNotEmpty()) {
            cells.chunked(2).forEach { pair ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    pair.forEach { (k, v) ->
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = k,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = v,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}