package org.leegar.updater.ui.overlays

import android.os.Build
import android.os.SystemClock
import android.text.format.Formatter
import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring                                                                               
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.SystemUpdateAlt
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.util.lerp as lerpFloat
import androidx.compose.ui.zIndex
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.leegar.updater.R
import org.leegar.updater.data.PartDownloadState
import org.leegar.updater.data.PartStatus
import org.leegar.updater.data.Update
import org.leegar.updater.data.UpdateStatus
import org.leegar.updater.ui.pep.GraphicsLayerRecordingState
import org.leegar.updater.ui.pep.LocalBlurConfig
import org.leegar.updater.ui.pep.LocalContentHorizontalInset
import org.leegar.updater.ui.pep.SecondaryActionPill
import org.leegar.updater.ui.pep.StudioCoordsRef
import org.leegar.updater.ui.pep.UpdaterBadgePill
import org.leegar.updater.ui.pep.UpdaterPrimaryPillButton
import org.leegar.updater.ui.pep.UpdaterProgressIndicator
import org.leegar.updater.ui.pep.cardFlightBlurMask
import org.leegar.updater.ui.pep.edgeOutline
import org.leegar.updater.ui.pep.recordLayer
import org.leegar.updater.ui.pep.rememberGraphicsLayerRecordingState
import org.leegar.updater.ui.pep.sstepE
import org.leegar.updater.ui.pep.tactilePressScale
import org.leegar.updater.ui.pep.unclippedBoundsInRoot
import org.leegar.updater.util.StringUtil
import kotlin.math.roundToInt

/**
 * Two-layer 1:1 PEP Flight FloatBar:
 * 1) Stable bottom anchor pill (measures live bounds & hides when flight modal is active).
 * 2) Centered frosted modal flying directly from the anchor bounds on a single smooth spring
 *    (zero layout jumps, zero double-easing, detailed per-part live cards).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UpdaterFloatBar(
    activeUpdate: Update?,
    isImportingLocalZip: Boolean,
    isAutoInstallQueued: Boolean,
    isParallelSplitEnabled: Boolean,
    wavyProgress: Boolean,
    wavyAmplitude: Float,
    blurState: GraphicsLayerRecordingState,
    morphBlurState: GraphicsLayerRecordingState? = null,
    morphActive: Boolean = false,
    tintBoost: Float = 0f,
    topInsetPx: Float = 0f,
    backBlocked: Boolean = false,
    onInterceptAnchorTap: (() -> Boolean)? = null,
    onModalOpenChanged: (Boolean) -> Unit = {},
    onAnchorHeightPxChanged: (Float) -> Unit,
    onPauseDownload: (Update) -> Unit,
    onResumeDownload: (Update) -> Unit,
    onCancelDownload: (Update) -> Unit,
    onPauseInstall: () -> Unit,
    onResumeInstall: () -> Unit,
    onCancelInstall: () -> Unit,
    onReboot: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val blurConfig = LocalBlurConfig.current
    val horizontalInset = LocalContentHorizontalInset.current

    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val visible = activeUpdate != null || isImportingLocalZip

    val anchorCoordsRef = remember { StudioCoordsRef() }
    val modalHostCoordsRef = remember { StudioCoordsRef() }
    val modalCardCoordsRef = remember { StudioCoordsRef() }
    val modalHeaderCoordsRef = remember { StudioCoordsRef() }
    val modalFooterCoordsRef = remember { StudioCoordsRef() }
    val modalInnerScrollBlur = rememberGraphicsLayerRecordingState()
    var modalHeaderHeightPx by remember { mutableIntStateOf(0) }
    var modalBodyHeightPx by remember { mutableIntStateOf(0) }
    var modalFooterHeightPx by remember { mutableIntStateOf(0) }
    val backProgress = remember { Animatable(0f) }
    var backEdge by remember { mutableIntStateOf(BackEventCompat.EDGE_LEFT) }
    var backDyNorm by remember { mutableFloatStateOf(0f) }
    var cancelArmedAt by remember(activeUpdate?.downloadId) { mutableLongStateOf(0L) }
    val blurEnabled = with(density) { blurConfig.radiusDp.coerceIn(0f, 150f).dp.toPx() } > 0.5f

    LaunchedEffect(cancelArmedAt) {
        if (cancelArmedAt <= 0L) return@LaunchedEffect
        val ts = cancelArmedAt
        delay(3000L)
        if (cancelArmedAt == ts) cancelArmedAt = 0L
    }

    var lastRenderedUpdate by remember { mutableStateOf<Update?>(null) }
    var lastRenderedImporting by remember { mutableStateOf(false) }
    if (activeUpdate != null) {
        lastRenderedUpdate = activeUpdate
        lastRenderedImporting = false
    } else if (isImportingLocalZip) {
        lastRenderedImporting = true
    }

    var measuredAnchorBoxHeightPx by remember { mutableIntStateOf(0) }
    var modalOpen by remember(activeUpdate?.downloadId, isImportingLocalZip) { mutableStateOf(false) }
    var closing by remember(activeUpdate?.downloadId, isImportingLocalZip) { mutableStateOf(false) }
    var frozenSourceRect by remember { mutableStateOf(Rect.Zero) }
    val flightAnim = remember { Animatable(0f) }

    LaunchedEffect(modalOpen) {
        onModalOpenChanged(modalOpen)
    }

    LaunchedEffect(visible) {
        if (!visible) {
            modalOpen = false
            closing = false
            modalHeaderHeightPx = 0
            modalBodyHeightPx = 0
            modalFooterHeightPx = 0
            modalHostCoordsRef.value = null
            modalCardCoordsRef.value = null
            flightAnim.snapTo(0f)
            backProgress.snapTo(0f)
        }
    }

    fun openModal() {
        if (modalOpen || isImportingLocalZip || activeUpdate == null) return
        val rect = anchorCoordsRef.value
            ?.takeIf { it.isAttached }
            ?.unclippedBoundsInRoot()
            ?: Rect.Zero
        frozenSourceRect = rect
        modalHeaderHeightPx = 0
        modalBodyHeightPx = 0
        modalFooterHeightPx = 0
        closing = false
        modalOpen = true
        scope.launch {
            flightAnim.snapTo(0f)
            backProgress.snapTo(0f)
            withFrameNanos { }
            if (!closing) {
                flightAnim.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(
                        stiffness = 280f,
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        visibilityThreshold = 0.002f,
                    ),
                )
            }
        }
    }

    fun closeModal(afterClose: (() -> Unit)? = null) {
        if (!modalOpen || closing) return
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
                        stiffness = 320f,
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        visibilityThreshold = 0.001f,
                    ),
                )
            } finally {
                flightAnim.snapTo(0f)
                backProgress.snapTo(0f)
                modalOpen = false
                closing = false
                modalHeaderHeightPx = 0
                modalBodyHeightPx = 0
                modalFooterHeightPx = 0
                modalHostCoordsRef.value = null
                modalCardCoordsRef.value = null
                afterClose?.invoke()
            }
        }
    }

    val displayUpdate = activeUpdate ?: lastRenderedUpdate
    val displayImporting = if (visible) isImportingLocalZip else lastRenderedImporting
    val isRunning = activeUpdate?.let { isOperationActivelyRunning(it) } == true
    val determinateProgress: Float? = displayUpdate?.let { u ->
        when (u.status) {
            UpdateStatus.DOWNLOADING, UpdateStatus.PAUSED, UpdateStatus.PAUSED_ERROR ->
                if (u.downloadStage == Update.STAGE_VERIFYING_PART ||
                    u.downloadStage == Update.STAGE_VERIFYING_FINAL
                ) null else u.progress / 100f
            UpdateStatus.INSTALLING, UpdateStatus.INSTALLATION_SUSPENDED -> u.installProgress / 100f
            UpdateStatus.UPDATED_NEED_REBOOT -> 1f
            else -> null
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .zIndex(if (modalOpen) 6f else 2f),
    ) {
        val maxW = maxWidth
        val maxH = maxHeight
        val fallbackWpx = with(density) { maxWidth.toPx() }
        val fallbackHpx = with(density) { maxHeight.toPx() }
        val statusTopPx = with(density) { statusTop.toPx() }
        val navBottomPx = with(density) { navBottom.toPx() }
        val marginPx = with(density) { 18.dp.toPx() }
        val effectiveAnchorPx = if (measuredAnchorBoxHeightPx > 0) {
            measuredAnchorBoxHeightPx.toFloat() + navBottomPx + marginPx
        } else {
            0f
        }

        LaunchedEffect(visible, effectiveAnchorPx) {
            onAnchorHeightPxChanged(if (visible) effectiveAnchorPx else 0f)
        }

        // ============================================================
        // LAYER 1: Stable Bottom Anchor Pill (hidden 1:1 during flight)
        // ============================================================
        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(
                animationSpec = spring(
                    stiffness = Spring.StiffnessMediumLow,
                    dampingRatio = Spring.DampingRatioNoBouncy,
                ),
            ) { it } + fadeIn(tween(180)),
            exit = slideOutVertically(
                animationSpec = spring(
                    stiffness = Spring.StiffnessMediumLow,
                    dampingRatio = Spring.DampingRatioNoBouncy,
                ),
            ) { it } + fadeOut(tween(180)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(
                    start = 14.dp + horizontalInset,
                    end = 14.dp + horizontalInset,
                    bottom = navBottom + 12.dp,
                ),
        ) {
            val anchorShape = RoundedCornerShape(22.dp)
            val surfaceBg = MaterialTheme.colorScheme.surfaceContainerHigh
            val anchorInteraction = remember { MutableInteractionSource() }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .onPlaced { anchorCoordsRef.value = it }
                    .onGloballyPositioned { anchorCoordsRef.value = it }
                    .onSizeChanged { sz ->
                        if (sz.height > 0) {
                            measuredAnchorBoxHeightPx = sz.height
                        }
                    }
                    .tactilePressScale(
                        interactionSource = anchorInteraction,
                        enabled = !modalOpen && !isImportingLocalZip && activeUpdate != null,
                        pressedScale = 0.975f,
                    )
                    .alpha(if (modalOpen) 0f else 1f)
                    .clip(anchorShape)
                    .border(1.dp, surfaceBg.edgeOutline(1.25f), anchorShape)
                    .clickable(
                        interactionSource = anchorInteraction,
                        indication = null,
                        enabled = !modalOpen && !isImportingLocalZip && activeUpdate != null,
                    ) {
                        if (onInterceptAnchorTap?.invoke() == true) return@clickable
                        haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                        openModal()
                    },
            ) {
                Box(Modifier.matchParentSize().background(surfaceBg))
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .cardFlightBlurMask(
                                state = blurState,
                                coordsProvider = { anchorCoordsRef.value },
                            ),
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(surfaceBg.copy(alpha = blurConfig.tintAlpha)),
                    )
                    if (morphActive && morphBlurState != null) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .cardFlightBlurMask(
                                    state = morphBlurState,
                                    coordsProvider = { anchorCoordsRef.value },
                                ),
                        )
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(surfaceBg.copy(alpha = blurConfig.tintAlpha)),
                        )
                        if (tintBoost > 0.003f) {
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .background(
                                        MaterialTheme.colorScheme.scrim.copy(
                                            alpha = tintBoost.coerceIn(0f, 1f),
                                        ),
                                    ),
                            )
                        }
                    }
                }

                if (displayImporting) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        CircularProgressIndicator(
                            strokeWidth = 2.5.dp,
                            modifier = Modifier.size(22.dp),
                        )
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = stringResource(R.string.float_importing),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = stringResource(R.string.float_copying),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else if (displayUpdate != null) {
                    FloatBarCompactFace(
                        update = displayUpdate,
                        isAutoInstallQueued = isAutoInstallQueued,
                        determinateProgress = determinateProgress,
                        isRunning = isRunning,
                        wavyProgress = wavyProgress,
                        wavyAmplitude = wavyAmplitude,
                        chevronRotation = 0f,
                        quickActionAlpha = 1f,
                        onPauseDownload = { activeUpdate?.let(onPauseDownload) },
                        onResumeDownload = { activeUpdate?.let(onResumeDownload) },
                        onReboot = onReboot,
                    )
                }
            }
        }

        // ============================================================
        // LAYER 2: 1:1 Centered Flight Modal (single clean spring, zero jumps)
        // ============================================================
        if (modalOpen && activeUpdate != null) {
            val u = activeUpdate
            PredictiveBackHandler(enabled = !closing && !backBlocked) { progress ->
                var startY = Float.NaN
                try {
                    progress.collect { event ->
                        if (startY.isNaN()) startY = event.touchY
                        backEdge = event.swipeEdge
                        backDyNorm = ((event.touchY - startY) / 420f).coerceIn(-1f, 1f)
                        backProgress.snapTo(event.progress.coerceIn(0f, 1f))
                    }
                    closeModal()
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

            val t = sstepE(flightAnim.value.coerceIn(0f, 1f), 0f, 1f)
            val bp = FastOutSlowInEasing.transform(backProgress.value.coerceIn(0f, 1f))
            val backScale = lerpFloat(1f, 0.90f, bp)
            val edgeSign = if (backEdge == BackEventCompat.EDGE_LEFT) 1f else -1f
            val backTx = with(density) { 16.dp.toPx() } * bp * edgeSign
            val backTy = with(density) { 10.dp.toPx() } * bp * backDyNorm
            val scrimAlpha = 0.44f * sstepE(t, 0.08f, 0.90f) * (1f - 0.35f * bp)
            val bodyReveal = sstepE(t, 0.28f, 0.92f)
            val cornerDp = lerp(22.dp, 28.dp, t) + (4.dp * bp)
            val modalShape = RoundedCornerShape(cornerDp)
            val surfaceBg = MaterialTheme.colorScheme.surfaceContainerHigh

            val liveSrc = anchorCoordsRef.value
                ?.takeIf { it.isAttached }
                ?.unclippedBoundsInRoot()
                ?.takeIf { it.width > 1f && it.height > 1f }
                ?: frozenSourceRect

            // Deterministic target height based on whether split parts are present
            val resolvedPartStates = remember(u.partStates, u.parts, u.downloadUrl, u.fileSize) {
                if (u.partStates.isNotEmpty()) {
                    u.partStates
                } else if (u.isSplitDownload) {
                    val urls = if (u.parts.isNotEmpty()) u.parts else {
                        u.downloadUrl.orEmpty().lines().filter { it.isNotBlank() }
                            .map { org.leegar.updater.data.UpdatePart(url = it.trim()) }
                    }
                    val eachSize = if (u.fileSize > 0L && urls.isNotEmpty()) u.fileSize / urls.size else 0L
                    urls.mapIndexed { idx, p ->
                        PartDownloadState(
                            index = idx,
                            name = p.url.substringAfterLast('/').substringBefore('?').ifBlank { context.getString(R.string.part_fallback_name, idx + 1) },
                            totalBytes = p.size ?: eachSize,
                            status = if (u.status == UpdateStatus.PAUSED) PartStatus.PAUSED else PartStatus.PENDING,
                        )
                    }
                } else emptyList()
            }

            val endW = (maxW - 24.dp - horizontalInset * 2)
                .coerceAtLeast(240.dp)
                .coerceAtMost(maxW)
            val startW = if (liveSrc.width > 1f) {
                with(density) { liveSrc.width.roundToInt().toDp() }
            } else endW
            val topLimitDp = maxOf(statusTop, with(density) { topInsetPx.coerceAtLeast(0f).toDp() })
            val topLimitPx = with(density) { topLimitDp.toPx() }
            val maxModalH = (maxH - topLimitDp - navBottom - 14.dp).coerceAtLeast(220.dp)
            val fallbackModalH = if (resolvedPartStates.size > 1) {
                260.dp + (resolvedPartStates.size * 86 + 34).dp
            } else {
                260.dp
            }
            val measuredModalH = if (modalHeaderHeightPx > 0 && modalBodyHeightPx > 0 && modalFooterHeightPx > 0) {
                with(density) {
                    (modalHeaderHeightPx + modalBodyHeightPx + modalFooterHeightPx).toDp()
                } + 14.dp + lerp(8.dp, 0.dp, t)
            } else {
                null
            }
            val targetEndH = (measuredModalH ?: fallbackModalH).coerceIn(180.dp, maxModalH)
            val endH by animateDpAsState(
                targetValue = targetEndH,
                animationSpec = if (flightAnim.value < 0.05f) {
                    snap()
                } else {
                    spring(
                        stiffness = Spring.StiffnessMediumLow,
                        dampingRatio = Spring.DampingRatioNoBouncy,
                    )
                },
                label = "FloatBarEndH",
            )
            val startH = if (liveSrc.height > 1f) {
                with(density) { liveSrc.height.roundToInt().toDp() }
            } else 86.dp

            val curW = lerp(startW.coerceIn(0.dp, maxW), endW, t)
            val curH = lerp(startH.coerceIn(64.dp, maxH), endH, t)

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = scrimAlpha))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { closeModal() },
                    ),
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = topLimitDp, bottom = navBottom)
                    .onPlaced { modalHostCoordsRef.value = it }
                    .onGloballyPositioned { modalHostCoordsRef.value = it },
                contentAlignment = Alignment.Center,
            ) {
                val targetHost = modalHostCoordsRef.value
                    ?.takeIf { it.isAttached }
                    ?.boundsInRoot()
                    ?.takeIf { it.width > 1f && it.height > 1f }
                    ?: Rect(
                        0f,
                        topLimitPx.coerceAtMost(fallbackHpx),
                        fallbackWpx,
                        (fallbackHpx - navBottomPx).coerceAtLeast(topLimitPx + 1f),
                    )

                val dockW = 1f - t
                val cardWpx = with(density) { curW.toPx() }.roundToInt()
                val cardHpx = with(density) { curH.toPx() }.roundToInt().coerceAtLeast(1)
                val hostWpx = targetHost.width.roundToInt()
                val hostHpx = targetHost.height.roundToInt()
                val e0x = (hostWpx - cardWpx) / 2 + cardWpx / 2f - targetHost.width / 2f
                val e0y = (hostHpx - cardHpx) / 2 + cardHpx / 2f - targetHost.height / 2f

                val dx = if (liveSrc.width > 1f) (liveSrc.center.x - targetHost.center.x - e0x) * dockW else 0f
                val dy = if (liveSrc.height > 1f) (liveSrc.center.y - targetHost.center.y - e0y) * dockW else 0f

                Box(
                    modifier = Modifier
                        .width(curW)
                        .height(curH)
                        .graphicsLayer {
                            translationX = dx + backTx
                            translationY = dy + backTy
                            scaleX = backScale
                            scaleY = backScale
                        }
                        .clip(modalShape)
                        .border(1.dp, surfaceBg.edgeOutline(1.3f), modalShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                        ),
                ) {
                    Box(Modifier.matchParentSize().background(surfaceBg))
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .onPlaced { modalCardCoordsRef.value = it }
                                .onGloballyPositioned { modalCardCoordsRef.value = it }
                                .cardFlightBlurMask(
                                    state = blurState,
                                    coordsProvider = { modalCardCoordsRef.value },
                                ),
                        )
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(surfaceBg.copy(alpha = blurConfig.tintAlpha)),
                        )
                    }

                    val mHeaderDp = with(density) { modalHeaderHeightPx.toDp() }
                    val mFooterDp = with(density) { modalFooterHeightPx.toDp() }

                    Box(modifier = Modifier.fillMaxSize()) {
                        // Layer A: Scrollable Telemetry + Per-Part Cards recorded into modalInnerScrollBlur
                        run {
                            val downloadedBytes = u.bytesDownloaded.takeIf { it > 0L }
                                ?: u.file?.takeIf { it.exists() }?.length()
                                ?: if (u.fileSize > 0L) (u.fileSize * u.progress) / 100L else 0L
                            val transferredText = if (u.fileSize > 0L) {
                                "${Formatter.formatShortFileSize(context, downloadedBytes)} / ${Formatter.formatShortFileSize(context, u.fileSize)}"
                            } else if (downloadedBytes > 0L) {
                                Formatter.formatShortFileSize(context, downloadedBytes)
                            } else stringResource(R.string.float_calculating)
                            val speedStr = if (u.speed > 0L) {
                                "${Formatter.formatShortFileSize(context, u.speed)}/s"
                            } else if (u.status == UpdateStatus.PAUSED) context.getString(R.string.action_paused_state) else "—"
                            val etaStr = if (u.eta > 0L) {
                                StringUtil.formatETA(context, u.eta * 1000L).toString()
                            } else "—"

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .then(
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && blurEnabled && t > 0.35f) {
                                            Modifier.recordLayer(
                                                state = modalInnerScrollBlur,
                                                backgroundColor = Color.Transparent,
                                                topEdgeFadeHeight = 0.dp,
                                                bottomEdgeFadeHeight = 0.dp,
                                            )
                                        } else {
                                            Modifier
                                        },
                                    ),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .alpha(bodyReveal)
                                        .verticalScroll(rememberScrollState(), enabled = t > 0.65f),
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(
                                                start = 20.dp,
                                                end = 20.dp,
                                                top = mHeaderDp + 4.dp,
                                                bottom = mFooterDp + 10.dp,
                                            )
                                            .onSizeChanged { if (it.height > 0) modalBodyHeightPx = it.height },
                                        verticalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                // Clean summary strip (no cramped 2x2 grid!)
                                val summaryBg = MaterialTheme.colorScheme.surface
                                val summaryShape = RoundedCornerShape(16.dp)
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(summaryShape)
                                        .background(summaryBg)
                                        .border(1.dp, summaryBg.edgeOutline(), summaryShape)
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(
                                        text = u.name,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                        ),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = transferredText,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                        Text(
                                            text = listOf(speedStr, etaStr)
                                                .filter { it != "—" }
                                                .joinToString(" • ")
                                                .ifEmpty { "—" },
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        if (resolvedPartStates.size > 1) {
                                            UpdaterBadgePill(pluralStringResource(R.plurals.float_parts, resolvedPartStates.size, resolvedPartStates.size))
                                            UpdaterBadgePill(
                                                text = if (isParallelSplitEnabled) stringResource(R.string.float_parallel) else stringResource(R.string.float_sequential),
                                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                            )
                                        } else {
                                            UpdaterBadgePill(stringResource(R.string.float_single))
                                        }
                                        if (!u.sha256.isNullOrBlank()) {
                                            UpdaterBadgePill(stringResource(R.string.badge_sha256))
                                        }
                                        if (isAutoInstallQueued) {
                                            UpdaterBadgePill(
                                                text = stringResource(R.string.float_autoinstall),
                                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                            )
                                        }
                                    }
                                }

                                // Individual Live Part Cards for ALL split parts
                                if (resolvedPartStates.size > 1) {
                                    Text(
                                        text = stringResource(R.string.float_parts_header, resolvedPartStates.count { it.status == PartStatus.DONE }, resolvedPartStates.size),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(top = 2.dp),
                                    )

                                    resolvedPartStates.forEach { partState ->
                                        PartProgressRowCard(
                                            part = partState,
                                            parentRunning = isRunning,
                                            wavyProgress = wavyProgress,
                                            wavyAmplitude = wavyAmplitude,
                                        )
                                    }
                                }

                                var lastFloatErr by remember(u.downloadId) { mutableStateOf(u.errorMessage.orEmpty()) }
                                if (!u.errorMessage.isNullOrBlank()) lastFloatErr = u.errorMessage
                                AnimatedVisibility(
                                    visible = !u.errorMessage.isNullOrBlank(),
                                    enter = expandVertically(tween(240, easing = FastOutSlowInEasing)) + fadeIn(tween(180)),
                                    exit = shrinkVertically(tween(220, easing = FastOutSlowInEasing)) + fadeOut(tween(140)),
                                ) {
                                    val errBg = MaterialTheme.colorScheme.errorContainer
                                    val errShape = RoundedCornerShape(14.dp)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(errShape)
                                            .background(errBg)
                                            .border(1.dp, errBg.edgeOutline(), errShape)
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Warning,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onErrorContainer,
                                            modifier = Modifier.size(18.dp),
                                        )
                                        Text(
                                            text = lastFloatErr,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                        )
                                    }
                                }
                                    }
                                }
                            }
                        }

                        // Layer B: Pinned Header with inner scroll blur frost
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.TopCenter)
                                .onPlaced { modalHeaderCoordsRef.value = it }
                                .onGloballyPositioned { modalHeaderCoordsRef.value = it }
                                .onSizeChanged { modalHeaderHeightPx = it.height }
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    enabled = !closing && t > 0.5f,
                                    onClick = { closeModal() },
                                ),
                        ) {
                            if (bodyReveal > 0.01f) {
                                Box(Modifier.matchParentSize().background(surfaceBg.copy(alpha = bodyReveal)))
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && blurEnabled) {
                                    Box(
                                        modifier = Modifier
                                            .matchParentSize()
                                            .graphicsLayer {
                                                clip = true
                                                alpha = bodyReveal
                                            }
                                            .cardFlightBlurMask(
                                                state = blurState,
                                                coordsProvider = { modalHeaderCoordsRef.value },
                                            ),
                                    )
                                    Box(
                                        modifier = Modifier
                                            .matchParentSize()
                                            .graphicsLayer {
                                                clip = true
                                                alpha = bodyReveal
                                            }
                                            .cardFlightBlurMask(
                                                state = modalInnerScrollBlur,
                                                coordsProvider = { modalHeaderCoordsRef.value },
                                            ),
                                    )
                                    Box(
                                        modifier = Modifier
                                            .matchParentSize()
                                            .background(surfaceBg.copy(alpha = blurConfig.tintAlpha * bodyReveal)),
                                    )
                                }
                            }
                            FloatBarCompactFace(
                                update = u,
                                isAutoInstallQueued = isAutoInstallQueued,
                                determinateProgress = determinateProgress,
                                isRunning = isRunning,
                                wavyProgress = wavyProgress,
                                wavyAmplitude = wavyAmplitude,
                                chevronRotation = 180f * t,
                                quickActionAlpha = 1f - sstepE(t, 0f, 0.45f),
                                titleScale = lerpFloat(1f, 1.08f, t),
                                padH = lerp(16.dp, 20.dp, t),
                                padV = lerp(12.dp, 16.dp, t),
                                onPauseDownload = { onPauseDownload(u) },
                                onResumeDownload = { onResumeDownload(u) },
                                onReboot = onReboot,
                            )
                        }

                        // Layer C: Pinned Footer with inner scroll blur frost + 2-tap Cancel/Abort confirmation
                        run {
                            val footerInteractive = !closing && bodyReveal > 0.5f
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.BottomCenter)
                                    .wrapContentHeight(align = Alignment.Bottom, unbounded = true)
                                    .onPlaced { modalFooterCoordsRef.value = it }
                                    .onGloballyPositioned { modalFooterCoordsRef.value = it }
                                    .onSizeChanged { if (it.height > 0) modalFooterHeightPx = it.height },
                            ) {
                                Box(Modifier.matchParentSize().background(surfaceBg.copy(alpha = bodyReveal)))
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && blurEnabled) {
                                    Box(
                                        modifier = Modifier
                                            .matchParentSize()
                                            .graphicsLayer {
                                                clip = true
                                                alpha = bodyReveal
                                            }
                                            .cardFlightBlurMask(
                                                state = blurState,
                                                coordsProvider = { modalFooterCoordsRef.value },
                                            ),
                                    )
                                    Box(
                                        modifier = Modifier
                                            .matchParentSize()
                                            .graphicsLayer {
                                                clip = true
                                                alpha = bodyReveal
                                            }
                                            .cardFlightBlurMask(
                                                state = modalInnerScrollBlur,
                                                coordsProvider = { modalFooterCoordsRef.value },
                                            ),
                                    )
                                    Box(
                                        modifier = Modifier
                                            .matchParentSize()
                                            .background(surfaceBg.copy(alpha = blurConfig.tintAlpha * bodyReveal)),
                                    )
                                }

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .alpha(bodyReveal)
                                        .padding(horizontal = 20.dp, vertical = 14.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                    )
                                    Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    when (u.status) {
                                        UpdateStatus.DOWNLOADING,
                                        UpdateStatus.STARTING,
                                        UpdateStatus.PAUSED,
                                        UpdateStatus.PAUSED_ERROR,
                                        UpdateStatus.INSTALLING,
                                        UpdateStatus.INSTALLATION_SUSPENDED -> {
                                            val isPausedState = u.status == UpdateStatus.PAUSED ||
                                                u.status == UpdateStatus.PAUSED_ERROR ||
                                                u.status == UpdateStatus.INSTALLATION_SUSPENDED
                                            val isInstallState = u.status == UpdateStatus.INSTALLING ||
                                                u.status == UpdateStatus.INSTALLATION_SUSPENDED
                                            val primaryCorner by animateDpAsState(
                                                targetValue = if (isPausedState) 23.dp else 14.dp,
                                                animationSpec = spring(
                                                    stiffness = Spring.StiffnessMedium,
                                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                                ),
                                                label = "FloatPrimaryCorner",
                                            )
                                            val primaryBg by animateColorAsState(
                                                targetValue = if (isPausedState) {
                                                    MaterialTheme.colorScheme.primary
                                                } else {
                                                    MaterialTheme.colorScheme.primaryContainer
                                                },
                                                animationSpec = spring(stiffness = Spring.StiffnessMedium),
                                                label = "FloatPrimaryBg",
                                            )
                                            val primaryFg by animateColorAsState(
                                                targetValue = if (isPausedState) {
                                                    MaterialTheme.colorScheme.onPrimary
                                                } else {
                                                    MaterialTheme.colorScheme.onPrimaryContainer
                                                },
                                                animationSpec = spring(stiffness = Spring.StiffnessMedium),
                                                label = "FloatPrimaryFg",
                                            )
                                            val primaryShape = RoundedCornerShape(primaryCorner)
                                            val primaryInteraction = remember { MutableInteractionSource() }
                                            val primaryIcon = if (isPausedState) Icons.Rounded.PlayArrow else Icons.Rounded.Pause
                                            val primaryLabel = when {
                                                isPausedState -> stringResource(R.string.action_resume)
                                                isInstallState -> stringResource(R.string.float_suspend)
                                                else -> stringResource(R.string.action_pause)
                                            }

                                            Row(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(46.dp)
                                                    .tactilePressScale(primaryInteraction, pressedScale = 0.96f)
                                                    .clip(primaryShape)
                                                    .background(primaryBg)
                                                    .border(1.dp, primaryBg.edgeOutline(), primaryShape)
                                                    .clickable(
                                                        interactionSource = primaryInteraction,
                                                        indication = androidx.compose.material3.ripple(),
                                                        enabled = footerInteractive,
                                                    ) {
                                                        haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                                                        when {
                                                            isInstallState && isPausedState -> onResumeInstall()
                                                            isInstallState -> onPauseInstall()
                                                            isPausedState -> onResumeDownload(u)
                                                            else -> onPauseDownload(u)
                                                        }
                                                    }
                                                    .padding(horizontal = 14.dp),
                                                horizontalArrangement = Arrangement.Center,
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                AnimatedContent(
                                                    targetState = Pair(primaryIcon, primaryLabel),
                                                    transitionSpec = {
                                                        (fadeIn(tween(180)) + scaleIn(initialScale = 0.88f, animationSpec = tween(180))) togetherWith
                                                            (fadeOut(tween(120)) + scaleOut(targetScale = 0.88f, animationSpec = tween(120)))
                                                    },
                                                    label = "FloatFooterPrimaryMorph",
                                                ) { (ic, lbl) ->
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                                                    ) {
                                                        Icon(
                                                            imageVector = ic,
                                                            contentDescription = null,
                                                            tint = primaryFg,
                                                            modifier = Modifier.size(18.dp),
                                                        )
                                                        Text(
                                                            text = lbl,
                                                            style = MaterialTheme.typography.labelLarge,
                                                            fontWeight = FontWeight.SemiBold,
                                                            color = primaryFg,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis,
                                                        )
                                                    }
                                                }
                                            }

                                            val isCancelArmed = cancelArmedAt > 0L
                                            val cancelCorner by animateDpAsState(
                                                targetValue = if (isCancelArmed) 23.dp else 16.dp,
                                                animationSpec = spring(
                                                    stiffness = Spring.StiffnessMedium,
                                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                                ),
                                                label = "FloatCancelCorner",
                                            )
                                            val cancelBg by animateColorAsState(
                                                targetValue = if (isCancelArmed) {
                                                    MaterialTheme.colorScheme.error
                                                } else {
                                                    MaterialTheme.colorScheme.errorContainer
                                                },
                                                animationSpec = spring(stiffness = Spring.StiffnessMedium),
                                                label = "FloatCancelBg",
                                            )
                                            val cancelFg by animateColorAsState(
                                                targetValue = if (isCancelArmed) {
                                                    MaterialTheme.colorScheme.onError
                                                } else {
                                                    MaterialTheme.colorScheme.onErrorContainer
                                                },
                                                animationSpec = spring(stiffness = Spring.StiffnessMedium),
                                                label = "FloatCancelFg",
                                            )
                                            val cancelShape = RoundedCornerShape(cancelCorner)
                                            val cancelInteraction = remember { MutableInteractionSource() }
                                            val cancelIcon = if (isCancelArmed) Icons.Rounded.Check else Icons.Rounded.Close
                                            val cancelLabel = when {
                                                isCancelArmed -> stringResource(R.string.action_confirm)
                                                isInstallState -> stringResource(R.string.float_abort)
                                                else -> stringResource(R.string.action_cancel)
                                            }

                                            Row(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(46.dp)
                                                    .tactilePressScale(cancelInteraction, pressedScale = 0.96f)
                                                    .clip(cancelShape)
                                                    .background(cancelBg)
                                                    .border(1.dp, cancelBg.edgeOutline(), cancelShape)
                                                    .clickable(
                                                        interactionSource = cancelInteraction,
                                                        indication = androidx.compose.material3.ripple(),
                                                        enabled = footerInteractive,
                                                    ) {
                                                        haptics.performHapticFeedback(
                                                            if (isCancelArmed) HapticFeedbackType.LongPress else HapticFeedbackType.ContextClick,
                                                        )
                                                        if (isCancelArmed) {
                                                            cancelArmedAt = 0L
                                                            if (isInstallState) {
                                                                closeModal { onCancelInstall() }
                                                            } else {
                                                                closeModal { onCancelDownload(u) }
                                                            }
                                                        } else {
                                                            cancelArmedAt = SystemClock.uptimeMillis()
                                                        }
                                                    }
                                                    .padding(horizontal = 14.dp),
                                                horizontalArrangement = Arrangement.Center,
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                AnimatedContent(
                                                    targetState = Pair(cancelIcon, cancelLabel),
                                                    transitionSpec = {
                                                        (fadeIn(tween(180)) + scaleIn(initialScale = 0.88f, animationSpec = tween(180))) togetherWith
                                                            (fadeOut(tween(120)) + scaleOut(targetScale = 0.88f, animationSpec = tween(120))) using
                                                            SizeTransform(clip = false)
                                                    },
                                                    label = "FloatCancelConfirmMorph",
                                                ) { (ic, lbl) ->
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                                                    ) {
                                                        Icon(
                                                            imageVector = ic,
                                                            contentDescription = null,
                                                            tint = cancelFg,
                                                            modifier = Modifier.size(18.dp),
                                                        )
                                                        Text(
                                                            text = lbl,
                                                            style = MaterialTheme.typography.labelLarge,
                                                            fontWeight = FontWeight.SemiBold,
                                                            color = cancelFg,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis,
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        UpdateStatus.UPDATED_NEED_REBOOT -> {
                                            UpdaterPrimaryPillButton(
                                                label = stringResource(R.string.action_reboot_now),
                                                icon = Icons.Rounded.RestartAlt,
                                                enabled = footerInteractive,
                                                height = 46.dp,
                                                onClick = onReboot,
                                                modifier = Modifier.fillMaxWidth(),
                                            )
                                        }

                                        else -> {
                                            SecondaryActionPill(
                                                icon = Icons.Rounded.Close,
                                                label = stringResource(R.string.desc_close),
                                                enabled = footerInteractive,
                                                height = 46.dp,
                                                onClick = { closeModal() },
                                                modifier = Modifier.fillMaxWidth(),
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

@Composable
private fun FloatBarCompactFace(
    update: Update,
    isAutoInstallQueued: Boolean,
    determinateProgress: Float?,
    isRunning: Boolean,
    wavyProgress: Boolean,
    wavyAmplitude: Float,
    chevronRotation: Float,
    quickActionAlpha: Float,
    titleScale: Float = 1f,
    padH: androidx.compose.ui.unit.Dp = 16.dp,
    padV: androidx.compose.ui.unit.Dp = 12.dp,
    onPauseDownload: () -> Unit,
    onResumeDownload: () -> Unit,
    onReboot: () -> Unit,
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val stageTitle = formatStageTitle(update, isAutoInstallQueued)
    val speedStr = if (update.speed > 0L) "${Formatter.formatShortFileSize(context, update.speed)}/s" else null
    val etaStr = if (update.eta > 0L) StringUtil.formatETA(context, update.eta * 1000L).toString() else null
    val compactSub = listOfNotNull(stageTitle.takeIf { it.isNotBlank() }, speedStr, etaStr)
        .joinToString(" • ")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = padH, vertical = padV),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val hasQuickAction = update.status == UpdateStatus.DOWNLOADING ||
            update.status == UpdateStatus.PAUSED ||
            update.status == UpdateStatus.PAUSED_ERROR ||
            update.status == UpdateStatus.UPDATED_NEED_REBOOT
        val quickFraction = quickActionAlpha.coerceIn(0f, 1f)

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val badgeBg = MaterialTheme.colorScheme.primaryContainer
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(badgeBg)
                    .border(1.dp, badgeBg.edgeOutline(), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = when (update.status) {
                        UpdateStatus.VERIFYING -> Icons.Rounded.VerifiedUser
                        UpdateStatus.INSTALLING, UpdateStatus.INSTALLATION_SUSPENDED ->
                            Icons.Rounded.SystemUpdateAlt
                        UpdateStatus.UPDATED_NEED_REBOOT -> Icons.Rounded.CheckCircle
                        else -> Icons.Rounded.Download
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp),
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = update.version.ifBlank { update.name },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.graphicsLayer {
                        scaleX = titleScale
                        scaleY = titleScale
                        transformOrigin = TransformOrigin(0f, 0.5f)
                    },
                )
                Text(
                    text = compactSub,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (hasQuickAction && quickFraction > 0.005f) {
                val isPausedQuick = update.status == UpdateStatus.PAUSED ||
                    update.status == UpdateStatus.PAUSED_ERROR
                val isRebootQuick = update.status == UpdateStatus.UPDATED_NEED_REBOOT
                val quickCorner by animateDpAsState(
                    targetValue = if (isPausedQuick || isRebootQuick) 19.dp else 12.dp,
                    animationSpec = spring(
                        stiffness = Spring.StiffnessMedium,
                        dampingRatio = Spring.DampingRatioNoBouncy,
                    ),
                    label = "FloatQuickCorner",
                )
                val quickBg by animateColorAsState(
                    targetValue = when {
                        isRebootQuick -> MaterialTheme.colorScheme.primary
                        isPausedQuick -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.secondaryContainer
                    },
                    animationSpec = spring(stiffness = Spring.StiffnessMedium),
                    label = "FloatQuickBg",
                )
                val quickFg by animateColorAsState(
                    targetValue = when {
                        isRebootQuick -> MaterialTheme.colorScheme.onPrimary
                        isPausedQuick -> MaterialTheme.colorScheme.onPrimary
                        else -> MaterialTheme.colorScheme.onSecondaryContainer
                    },
                    animationSpec = spring(stiffness = Spring.StiffnessMedium),
                    label = "FloatQuickFg",
                )
                val quickShape = RoundedCornerShape(quickCorner)
                val quickInteraction = remember { MutableInteractionSource() }
                val quickIcon = when {
                    isRebootQuick -> Icons.Rounded.RestartAlt
                    isPausedQuick -> Icons.Rounded.PlayArrow
                    else -> Icons.Rounded.Pause
                }
                val quickScale = lerpFloat(0.65f, 1f, quickFraction)

                Spacer(Modifier.width(lerp(0.dp, 8.dp, quickFraction)))
                Box(
                    modifier = Modifier
                        .width(lerp(0.dp, 38.dp, quickFraction))
                        .height(38.dp)
                        .clipToBounds(),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .graphicsLayer {
                                scaleX = quickScale
                                scaleY = quickScale
                                alpha = quickFraction
                            }
                            .tactilePressScale(
                                quickInteraction,
                                enabled = quickFraction > 0.5f,
                                pressedScale = 0.90f,
                            )
                            .clip(quickShape)
                            .background(quickBg)
                            .border(1.dp, quickBg.edgeOutline(), quickShape)
                            .clickable(
                                interactionSource = quickInteraction,
                                indication = androidx.compose.material3.ripple(),
                                enabled = quickFraction > 0.5f,
                            ) {
                                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                                when {
                                    isRebootQuick -> onReboot()
                                    isPausedQuick -> onResumeDownload()
                                    else -> onPauseDownload()
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        AnimatedContent(
                            targetState = quickIcon,
                            transitionSpec = {
                                (scaleIn(initialScale = 0.6f, animationSpec = spring(stiffness = Spring.StiffnessMedium)) + fadeIn(tween(150))) togetherWith
                                    (scaleOut(targetScale = 0.6f, animationSpec = tween(110)) + fadeOut(tween(110)))
                            },
                            label = "FloatQuickIconMorph",
                        ) { ic ->
                            Icon(
                                imageVector = ic,
                                contentDescription = when {
                                    isRebootQuick -> stringResource(R.string.reboot)
                                    isPausedQuick -> stringResource(R.string.action_resume)
                                    else -> stringResource(R.string.action_pause)
                                },
                                tint = quickFg,
                                modifier = Modifier.size(19.dp),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.width(8.dp))

            val chevronBg = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(chevronBg)
                    .border(1.dp, MaterialTheme.colorScheme.primaryContainer.edgeOutline(), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowUp,
                    contentDescription = stringResource(R.string.desc_toggle_details),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier
                        .size(21.dp)
                        .rotate(chevronRotation),
                )
            }
        }

        UpdaterProgressIndicator(
            progress = determinateProgress,
            wavy = wavyProgress,
            isActive = isRunning,
            wavyAmplitude = wavyAmplitude,
            height = 6.dp,
        )
    }
}

@Composable
private fun PartProgressRowCard(
    part: PartDownloadState,
    parentRunning: Boolean,
    wavyProgress: Boolean,
    wavyAmplitude: Float,
) {
    val context = LocalContext.current
    val bg = MaterialTheme.colorScheme.surface
    val shape = RoundedCornerShape(16.dp)
    val isPartActive = parentRunning &&
        (part.status == PartStatus.DOWNLOADING || part.status == PartStatus.VERIFYING)

    val statusLabel = when (part.status) {
        PartStatus.PENDING -> stringResource(R.string.part_queued)
        PartStatus.DOWNLOADING -> stringResource(R.string.status_progress_percent, part.progressPercent)
        PartStatus.VERIFYING -> stringResource(R.string.part_verifying)
        PartStatus.DONE -> if (part.sha256Verified) stringResource(R.string.part_verified) else stringResource(R.string.part_done)
        PartStatus.PAUSED -> stringResource(R.string.part_paused_format, part.progressPercent)
        PartStatus.ERROR -> stringResource(R.string.part_error)
    }
    val statusBg = when (part.status) {
        PartStatus.DONE -> MaterialTheme.colorScheme.primaryContainer
        PartStatus.DOWNLOADING, PartStatus.VERIFYING -> MaterialTheme.colorScheme.secondaryContainer
        PartStatus.ERROR -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val statusFg = when (part.status) {
        PartStatus.DONE -> MaterialTheme.colorScheme.onPrimaryContainer
        PartStatus.DOWNLOADING, PartStatus.VERIFYING -> MaterialTheme.colorScheme.onSecondaryContainer
        PartStatus.ERROR -> MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val bytesLine = if (part.totalBytes > 0L) {
        "${Formatter.formatShortFileSize(context, part.downloadedBytes)} / ${Formatter.formatShortFileSize(context, part.totalBytes)}"
    } else if (part.downloadedBytes > 0L) {
        Formatter.formatShortFileSize(context, part.downloadedBytes)
    } else stringResource(R.string.part_waiting)

    val speedLine = if (isPartActive && part.speedBytesPerSec > 0L) {
        "${Formatter.formatShortFileSize(context, part.speedBytesPerSec)}/s"
    } else ""

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(bg)
            .border(1.dp, bg.edgeOutline(), shape)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "#${part.index + 1} • ${part.name}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            UpdaterBadgePill(
                text = statusLabel,
                containerColor = statusBg,
                contentColor = statusFg,
            )
        }

        UpdaterProgressIndicator(
            progress = if (part.status == PartStatus.VERIFYING) null else part.progressFraction,
            wavy = wavyProgress,
            isActive = isPartActive,
            wavyAmplitude = wavyAmplitude,
            height = 5.dp,
            color = if (part.status == PartStatus.DONE) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.secondary
            },
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = bytesLine,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (speedLine.isNotEmpty()) {
                Text(
                    text = speedLine,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }
    }
}

private fun isOperationActivelyRunning(u: Update): Boolean = when (u.status) {
    UpdateStatus.STARTING,
    UpdateStatus.DOWNLOADING,
    UpdateStatus.VERIFYING,
    UpdateStatus.INSTALLING -> true
    else -> false
}

@Composable
private fun formatStageTitle(u: Update, autoInstall: Boolean): String =
    when (u.status) {
        UpdateStatus.STARTING -> stringResource(R.string.stage_connecting)
        UpdateStatus.DOWNLOADING -> when (u.downloadStage) {
            Update.STAGE_VERIFYING_PART -> stringResource(R.string.stage_verify_part, u.currentPart, u.totalParts)
            Update.STAGE_MERGING -> stringResource(R.string.stage_assemble, u.currentPart, u.totalParts, u.partProgress)
            Update.STAGE_VERIFYING_FINAL -> stringResource(R.string.stage_verify_final)
            else -> {
                val activeCount = u.partStates.count { it.status == PartStatus.DOWNLOADING }
                val base = when {
                    u.partStates.size > 1 && activeCount > 1 ->
                        stringResource(R.string.stage_parts_active, activeCount, u.progress)
                    u.totalParts > 1 ->
                        stringResource(R.string.stage_part_progress, u.currentPart.coerceAtLeast(1), u.totalParts, u.progress)
                    else ->
                        stringResource(R.string.stage_downloading, u.progress)
                }
                if (autoInstall) "$base " + stringResource(R.string.stage_autoinstall_suffix) else base
            }
        }
        UpdateStatus.PAUSED -> stringResource(R.string.stage_paused, u.progress)
        UpdateStatus.PAUSED_ERROR -> stringResource(R.string.stage_failed)
        UpdateStatus.VERIFYING -> stringResource(R.string.stage_verifying_sig)
        UpdateStatus.INSTALLING -> if (u.isFinalizing) {
            stringResource(R.string.stage_finalizing, u.installProgress)
        } else {
            stringResource(R.string.stage_installing, u.installProgress)
        }
        UpdateStatus.INSTALLATION_SUSPENDED -> stringResource(R.string.stage_suspended, u.installProgress)
        UpdateStatus.UPDATED_NEED_REBOOT -> stringResource(R.string.stage_installed)
        else -> ""
    }