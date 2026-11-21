package org.leegar.updater.ui.pep

import android.os.Build
import androidx.compose.ui.res.stringResource
import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import org.leegar.updater.R
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private class CoordsRef(var value: LayoutCoordinates? = null)

@Stable
class SliderMorphHostState {
    var request by mutableStateOf<SliderMorphRequest?>(null)
        private set
    var morphProgress by mutableStateOf(0f)
    var closeSignal by mutableStateOf(0)
        private set

    fun open(request: SliderMorphRequest) {
        this.request = request
    }

    fun requestAnimatedClose() {
        if (request != null) closeSignal++
    }

    fun close(target: SliderMorphRequest? = null) {
        if (target == null || request?.sessionId == target.sessionId) {
            request = null
        }
    }

    fun closeById(sessionId: Long) {
        if (request?.sessionId == sessionId) {
            request = null
        }
    }
}

val LocalSliderMorphHost = compositionLocalOf<SliderMorphHostState?> { null }

@Composable
fun rememberSliderMorphHostState(): SliderMorphHostState =
    remember { SliderMorphHostState() }

data class SliderMorphRequest(
    val title: String,
    val initialValue: Float,
    val rangeStart: Float,
    val rangeEnd: Float,
    val unit: String,
    val isInteger: Boolean,
    val stepSize: Float,
    val formattedValue: String,
    val showResetBadge: Boolean,
    val resetText: String,
    val sourceInRoot: Rect,
    val valueFlyDyPx: Float,
    val onConfirm: (Float) -> Unit,
    val onDefault: () -> Unit,
    val onDismiss: () -> Unit,
    val valueFlyDxPx: Float = 0f,
    val visualRangeStart: Float = rangeStart,
    val visualRangeEnd: Float = rangeEnd,
    val sliderFraction: Float = if (rangeEnd > rangeStart) {
        ((initialValue - rangeStart) / (rangeEnd - rangeStart)).coerceIn(0f, 1f)
    } else {
        0f
    },
    val initialTrackHeightPx: Float = 0f,
    val defaultValue: Float? = null,
    val resetThreshold: Float = (stepSize * 0.5f).coerceAtLeast(1e-6f),
    val showDefaultButton: Boolean = true,
    val showInfoIcon: Boolean = false,
    val sourceBoundsProvider: () -> Rect = { sourceInRoot },
    val sessionId: Long = System.nanoTime(),
    val sourceActiveTrackColor: Color? = null,
    val sourceInactiveTrackColor: Color? = null,
    val sourceThumbColor: Color? = null,
)

@Composable
fun SliderMorphRoot(
    blurState: GraphicsLayerRecordingState? = null,
    host: SliderMorphHostState = rememberSliderMorphHostState(),
    morphBlurState: GraphicsLayerRecordingState? = null,
    morphBackgroundColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    content: @Composable BoxScope.() -> Unit,
) {
    var rootRect by remember { mutableStateOf(Rect.Zero) }
    val morphOpen = host.request != null
    CompositionLocalProvider(LocalSliderMorphHost provides host) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (morphOpen && morphBlurState != null) {
                        Modifier.recordLayer(
                            state = morphBlurState,
                            backgroundColor = morphBackgroundColor,
                        )
                    } else {
                        Modifier
                    },
                )
                .onPlaced { rootRect = it.boundsInRoot() }
                .onGloballyPositioned { rootRect = it.boundsInRoot() },
        ) {
            content()
            SliderMorphOverlay(
                host = host,
                blurState = blurState,
                fallbackHostRect = rootRect,
            )
        }
    }
}

internal fun sstep(x: Float, a: Float, b: Float): Float {
    if (b <= a) return if (x >= b) 1f else 0f
    val t = ((x - a) / (b - a)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

private val ReadoutLeadingNum = Regex("""-?\d+(?:[.,]\d+)?""")
private val LeadingFloatRegex = Regex("""-?\d+(?:[.,]\d+)?""")

private fun formatExitReadout(old: String, confirmed: Float, isInteger: Boolean, unit: String): String {
    if (isInteger) {
        val num = confirmed.toInt().toString()
        val m = ReadoutLeadingNum.find(old)
        return if (m != null) {
            old.replaceRange(m.range, num)
        } else {
            if (unit.isNotEmpty()) "$num $unit" else num
        }
    }
    val m = ReadoutLeadingNum.find(old)
    val newNum = formatFloatLike(oldToken = m?.value, value = confirmed)
    return if (m != null) {
        old.replaceRange(m.range, newNum)
    } else {
        if (unit.isNotEmpty()) "$newNum $unit" else newNum
    }
}

private fun formatFloatLike(oldToken: String?, value: Float): String {
    val oldFrac = oldToken?.let {
        val i = it.indexOfFirst { ch -> ch == '.' || ch == ',' }
        if (i >= 0) (it.length - i - 1).coerceIn(0, 6) else 0
    } ?: 2
    val plain = value.toString()
    val naturalFrac = plain.indexOf('.').let { i ->
        if (i < 0) {
            0
        } else {
            var e = plain.length - 1
            while (e > i && plain[e] == '0') e--
            (e - i).coerceIn(0, 6)
        }
    }
    val digits = maxOf(oldFrac, naturalFrac).coerceIn(0, 6)
    var out = String.format(Locale.US, "%.${digits}f", value)
    if (oldToken?.contains(',') == true) out = out.replace('.', ',')
    return out
}

@Composable
fun SliderMorphOverlay(
    host: SliderMorphHostState,
    blurState: GraphicsLayerRecordingState?,
    modifier: Modifier = Modifier,
    fallbackHostRect: Rect = Rect.Zero,
) {
    val req = host.request ?: return
    val haptics = LocalHapticFeedback.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val blurConfig = LocalBlurConfig.current
    val horizontalInset = LocalContentHorizontalInset.current
    val blurEnabled = with(density) { blurConfig.radiusDp.coerceIn(0f, 150f).dp.toPx() } > 0.5f

    val containerColor = MaterialTheme.colorScheme.surface

    val morphAnim = remember(req.sessionId) { Animatable(0f) }
    val backProgress = remember(req.sessionId) { Animatable(0f) }
    var backEdge by remember(req.sessionId) { mutableIntStateOf(BackEventCompat.EDGE_LEFT) }
    var backDyNorm by remember(req.sessionId) { mutableFloatStateOf(0f) }
    var closing by remember(req.sessionId) { mutableStateOf(false) }
    var pendingCommit by remember(req.sessionId) { mutableStateOf<(() -> Unit)?>(null) }
    val hostCoordsRef = remember(req.sessionId) { CoordsRef() }
    val cardCoordsRef = remember(req.sessionId) { CoordsRef() }
    val flightCoordsRef = remember(req.sessionId) { CoordsRef() }

    LaunchedEffect(req.sessionId) {
        withFrameNanos { }
        if (!closing) {
            haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
            morphAnim.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    stiffness = 100f,
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    visibilityThreshold = 0.002f,
                ),
            )
        }
    }

    fun closeAnimated(commit: () -> Unit) {
        if (closing) return
        closing = true
        pendingCommit = commit
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
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
            morphAnim.stop()
            try {
                morphAnim.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(
                        stiffness = 150f,
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        visibilityThreshold = 0.05f,
                    ),
                )
            } finally {
                commit()
                host.close(req)
            }
        }
    }

    PredictiveBackHandler(enabled = !closing) { progress ->
        var startY = Float.NaN
        try {
            progress.collect { event ->
                if (startY.isNaN()) startY = event.touchY
                backEdge = event.swipeEdge
                backDyNorm = ((event.touchY - startY) / 420f).coerceIn(-1f, 1f)
                backProgress.snapTo(event.progress.coerceIn(0f, 1f))
            }
            closeAnimated(req.onDismiss)
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

    val initialCloseSignal = remember(req.sessionId) { host.closeSignal }
    LaunchedEffect(host.closeSignal) {
        if (host.closeSignal > initialCloseSignal && !closing) {
            closeAnimated(req.onDismiss)
        }
    }

    val rawT = morphAnim.value.coerceIn(0f, 1f)
    val morphT = sstep(rawT, 0f, 1f)
    val bp = FastOutSlowInEasing.transform(backProgress.value.coerceIn(0f, 1f))
    val edgeSign = if (backEdge == BackEventCompat.EDGE_LEFT) 1f else -1f
    val backTx = with(density) { 14.dp.toPx() } * bp * edgeSign
    val backTy = with(density) { 8.dp.toPx() } * bp * backDyNorm
    host.morphProgress = morphT * (1f - 0.35f * bp)

    val scrimAlpha = 0.42f * sstep(morphT, 0.45f, 0.92f) * (1f - 0.35f * bp)
    val cardBgAlpha = sstep(morphT, 0.0f, 0.35f)
    val blurReveal = sstep(morphT, 0.55f, 0.97f)
    val cornerProgress = sstep(morphT, 0.40f, 0.88f)
    val modalCorner = lerp(24.dp, 22.dp, cornerProgress) + (4.dp * bp)
    val modalShape = RoundedCornerShape(modalCorner)
    val outlineAlpha = sstep(morphT, 0.65f, 0.96f)
    val cardBorderColor = containerColor.edgeOutline().let {
        it.copy(alpha = it.alpha * outlineAlpha)
    }

    val cardScale = lerp(1f, 0.90f, bp)
    val outerInsetH = lerp(1.dp, 0.dp, cornerProgress)
    val innerPadH = with(density) {
        val totalPx = 20.dp.toPx()
        val outerPxInt = outerInsetH.toPx().roundToInt()
        (totalPx - outerPxInt).toDp()
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize().zIndex(9f)) {
        val maxW = maxWidth
        val fallbackWpx = with(density) { maxWidth.toPx() }
        val fallbackHpx = with(density) { maxHeight.toPx() }
        val liveSource = req.sourceBoundsProvider().let {
            if (it.width > 1f && it.height > 1f) it else req.sourceInRoot
        }
        val endW = (maxW - 32.dp - horizontalInset * 2)
            .coerceAtLeast(240.dp)
            .coerceAtMost(maxW)
        val startW = if (liveSource.width > 1f) {
            with(density) { liveSource.width.roundToInt().toDp() }
        } else {
            endW
        }.coerceIn(0.dp, maxW)

        val cardWidth: Dp = lerp(startW, endW, sstep(morphT, 0f, 0.90f))

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = scrimAlpha))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = !closing,
                    onClick = { closeAnimated(req.onDismiss) },
                ),
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .onPlaced { hostCoordsRef.value = it }
                .onGloballyPositioned { hostCoordsRef.value = it },
            contentAlignment = Alignment.Center,
        ) {
            val flightProgress = sstep(morphT, 0.0f, 0.96f)
            val hostBounds = hostCoordsRef.value?.takeIf { it.isAttached }?.boundsInRoot()
            val targetHost = when {
                hostBounds != null && hostBounds.width > 1f && hostBounds.height > 1f -> hostBounds
                fallbackHostRect.width > 1f && fallbackHostRect.height > 1f -> fallbackHostRect
                else -> Rect(0f, 0f, fallbackWpx, fallbackHpx)
            }
            val src = liveSource
            val dockW = 1f - flightProgress
            val cardWpx = with(density) { cardWidth.toPx() }.roundToInt()
            val cardHpx = flightCoordsRef.value?.takeIf { it.isAttached }?.size?.height
                ?.takeIf { it > 0 }
                ?: src.height.roundToInt().coerceAtLeast(1)
            val hostWpx = targetHost.width.roundToInt()
            val hostHpx = targetHost.height.roundToInt()
            val e0x = (hostWpx - cardWpx) / 2 + cardWpx / 2f - targetHost.width / 2f
            val e0y = (hostHpx - cardHpx) / 2 + cardHpx / 2f - targetHost.height / 2f
            val dx = if (targetHost.width > 1f && src.width > 1f) {
                (src.center.x - targetHost.center.x - e0x) * dockW
            } else {
                0f
            }
            val dy = if (targetHost.height > 1f && src.height > 1f) {
                (src.center.y - targetHost.center.y - e0y) * dockW
            } else {
                0f
            }

            LaunchedEffect(closing) {
                if (closing) {
                    val distPx = (targetHost.center - src.center).getDistance()
                    snapshotFlow { morphAnim.value }.first { raw ->
                        if (raw >= 0.7f) return@first false
                        val mt = sstep(raw, 0f, 1f)
                        sstep(mt, 0f, 0.96f) * distPx < 1.5f
                    }
                    pendingCommit?.invoke()
                    host.close(req)
                }
            }

            val clipViewport = blurState?.contentBounds
                ?.takeIf { it.width > 1f && it.height > 1f }
                ?: targetHost

            Box(
                modifier = Modifier
                    .width(cardWidth)
                    .onPlaced { flightCoordsRef.value = it }
                    .graphicsLayer {
                        translationX = dx + backTx
                        translationY = dy + backTy
                        scaleX = cardScale
                        scaleY = cardScale
                    }
                    .drawWithContent {
                        if (clipViewport.width > 1f && flightProgress < 0.999f) {
                            val w = size.width
                            val h = size.height
                            val rL = src.center.x - w / 2f
                            val rT = src.center.y - h / 2f
                            val keep = 1f - flightProgress
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
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = outerInsetH)
                        .clip(modalShape)
                        .border(1.dp, cardBorderColor, modalShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            enabled = !closing,
                            onClick = {},
                        ),
                ) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(containerColor.copy(alpha = cardBgAlpha)),
                    )
                    if (blurState != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && blurEnabled) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .graphicsLayer {
                                    clip = true
                                    alpha = blurReveal
                                }
                                .onPlaced { cardCoordsRef.value = it }
                                .onGloballyPositioned { cardCoordsRef.value = it }
                                .sliderMorphBlurMask(
                                    state = blurState,
                                    coordsProvider = { cardCoordsRef.value },
                                    scaleProvider = { cardScale },
                                ),
                        )
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(
                                    containerColor.copy(
                                        alpha = lerp(
                                            1f,
                                            blurConfig.tintAlpha.coerceIn(0f, 1f),
                                            sstep(morphT, 0.45f, 0.92f),
                                        ) * cardBgAlpha,
                                    ),
                                ),
                        )
                    }

                    val padVert = lerp(12.dp, 18.dp, sstep(morphT, 0.18f, 0.88f))
                    val visibleMaxH = (this@BoxWithConstraints.maxHeight - 24.dp).coerceAtLeast(160.dp)
                    val maxCardH = lerp(this@BoxWithConstraints.maxHeight, visibleMaxH, sstep(morphT, 0.18f, 0.88f))
                    val editorScrollState = rememberScrollState()
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = maxCardH)
                            .verticalScroll(editorScrollState, enabled = morphT > 0.7f)
                            .padding(horizontal = innerPadH, vertical = padVert),
                    ) {
                        SliderMorphEditorContent(
                            request = req,
                            morphT = morphT,
                            outlineAlpha = outlineAlpha,
                            closing = closing,
                            closeAnimated = ::closeAnimated,
                        )
                    }
                }
            }
        }
    }
}

internal fun Modifier.unfoldPill(progress: Float): Modifier = this.layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
    val p = progress.coerceIn(0f, 1f)
    if (p <= 0.001f) {
        layout(0, 0) {}
    } else {
        val hFactor = sstep(p, 0f, 0.45f)
        val wFactor = sstep(p, 0.05f, 1f)
        val targetH = (placeable.height * hFactor).roundToInt().coerceAtLeast(1)
        val minPillW = minOf(placeable.width, targetH)
        val targetW = lerp(minPillW.toFloat(), placeable.width.toFloat(), wFactor)
            .roundToInt()
            .coerceIn(1, placeable.width)
        layout(targetW, targetH) {
            placeable.place(0, (targetH - placeable.height) / 2)
        }
    }
}

private fun Modifier.sliderMorphBlurMask(
    state: GraphicsLayerRecordingState,
    coordsProvider: () -> LayoutCoordinates?,
    scaleProvider: () -> Float = { 1f },
): Modifier = this.drawBehind {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return@drawBehind
    val blurNode = state.sharedBlurNode ?: return@drawBehind
    if (!blurNode.hasDisplayList()) return@drawBehind
    val coords = coordsProvider() ?: return@drawBehind
    if (!coords.isAttached) return@drawBehind
    val region = coords.boundsInRoot()
    if (region.isEmpty) return@drawBehind
    val s = scaleProvider().coerceAtLeast(0.01f)
    val srcOrigin = state.contentBounds.topLeft
    drawIntoCanvas { canvas ->
        val native = canvas.nativeCanvas
        val saveCount = native.save()
        if (abs(s - 1f) > 0.001f) {
            val centerInRoot = region.center
            native.translate(size.width * 0.5f, size.height * 0.5f)
            native.scale(1f / s, 1f / s)
            native.translate(srcOrigin.x - centerInRoot.x, srcOrigin.y - centerInRoot.y)
        } else {
            native.translate(srcOrigin.x - region.left, srcOrigin.y - region.top)
        }
        native.drawRenderNode(blurNode)
        native.restoreToCount(saveCount)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SliderMorphEditorContent(
    request: SliderMorphRequest,
    morphT: Float,
    outlineAlpha: Float = sstep(morphT, 0.72f, 0.98f),
    closing: Boolean,
    closeAnimated: (() -> Unit) -> Unit,
) {
    val req = request
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val dpPx = with(density) { 1.dp.toPx() }

    val initText = if (req.isInteger) req.initialValue.toInt().toString() else req.initialValue.toString()
    var text by remember(req.sessionId) { mutableStateOf(initText) }
    var isError by remember(req.sessionId) { mutableStateOf(false) }
    var isFieldFocused by remember(req.sessionId) { mutableStateOf(false) }

    fun parseCandidate(candidate: String): Float? =
        candidate.trim().replace(',', '.').toFloatOrNull()

    fun validate(candidate: String): Boolean {
        val num = parseCandidate(candidate)
        return num != null && num >= req.rangeStart && num <= req.rangeEnd
    }

    var confirmedNum by remember(req.sessionId) { mutableStateOf<Float?>(null) }
    var exitSliderValue by remember(req.sessionId) { mutableStateOf<Float?>(null) }
    var unitBounds by remember(req.sessionId) { mutableStateOf<Rect?>(null) }
    var flyOriginBounds by remember(req.sessionId) { mutableStateOf<Rect?>(null) }
    LaunchedEffect(closing) {
        if (closing && exitSliderValue == null) {
            exitSliderValue = confirmedNum ?: req.initialValue
        }
    }

    fun tryApply() {
        if (closing) return
        val num = parseCandidate(text)
        if (num != null && num >= req.rangeStart && num <= req.rangeEnd) {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            confirmedNum = num
            exitSliderValue = num
            closeAnimated { req.onConfirm(num) }
        } else {
            isError = true
        }
    }

    val expandW = sstep(morphT, 0.18f, 0.88f)
    val trackW = sstep(morphT, 0.08f, 0.54f)
    val fieldW = sstep(morphT, 0.22f, 0.86f)
    val flyW = sstep(morphT, 0.08f, 0.60f)
    val fieldTextW = sstep(morphT, 0.44f, 0.84f)
    val rangeW = sstep(morphT, 0.22f, 0.86f)
    val cancelW = sstep(morphT, 0.34f, 0.88f)
    val applyW = sstep(morphT, 0.34f, 0.92f)

    val initialRowHDp = remember(req.initialTrackHeightPx, density) {
        if (req.initialTrackHeightPx > 1f) {
            with(density) { req.initialTrackHeightPx.toDp() }
        } else {
            48.dp
        }
    }
    val slotHDp = lerp(initialRowHDp, 56.dp, expandW)
    val topGapSlotHDp = lerp(10.dp, 42.dp, expandW)
    val bottomRowHDp = lerp(32.dp, 38.dp, expandW)
    val applySlotHDp = lerp(0.dp, 62.dp, expandW)
    val stepBtnSize = lerp(38.dp, 56.dp, fieldW)
    val stepBtnCorner = lerp(12.dp, 16.dp, sstep(fieldW, 0.15f, 0.85f))

    val rangeBg = MaterialTheme.colorScheme.secondaryContainer
    val resetBg = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
    val fieldBg = MaterialTheme.colorScheme.surfaceContainerHighest
    val cancelBg = MaterialTheme.colorScheme.surfaceContainerHigh

    val draftNum = parseCandidate(text) ?: req.initialValue
    fun stepDraft(dir: Int) {
        if (closing) return
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        val stepped = (draftNum + dir * req.stepSize).coerceIn(req.rangeStart, req.rangeEnd)
        text = if (req.isInteger) stepped.toInt().toString() else stepped.toString()
        isError = !validate(text)
    }

    val isDraftModified = if (req.defaultValue != null) {
        draftNum != req.defaultValue
    } else {
        draftNum != req.initialValue
    }
    val shouldShowReset = req.showDefaultButton && isDraftModified
    val resetReveal by animateFloatAsState(
        targetValue = if (shouldShowReset) 1f else 0f,
        animationSpec = spring(
            stiffness = 300f,
            dampingRatio = Spring.DampingRatioNoBouncy,
        ),
        label = "ResetBadgeReveal",
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        SliderTitleText(
            title = req.title,
            growProgress = sstep(morphT, 0.05f, 0.75f),
            maxGrow = 2f,
            modifier = Modifier.fillMaxWidth(),
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(topGapSlotHDp)
                .clipToBounds(),
            contentAlignment = Alignment.CenterStart,
        ) {
            val rangeBorderColor = rangeBg.edgeOutline().let {
                it.copy(alpha = it.alpha * outlineAlpha)
            }
            Box(
                modifier = Modifier
                    .graphicsLayer {
                        alpha = sstep(rangeW, 0.02f, 0.32f)
                    }
                    .clip(CircleShape)
                    .background(rangeBg)
                    .border(1.dp, rangeBorderColor, CircleShape)
                    .unfoldPill(rangeW),
            ) {
                Text(
                    text = "${req.rangeStart.toInt()} – ${req.rangeEnd.toInt()}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            ExpressiveStepButton(
                symbol = "−",
                enabled = !closing && draftNum > req.rangeStart,
                size = stepBtnSize,
                corner = stepBtnCorner,
                onClick = { stepDraft(-1) },
            )

            Spacer(Modifier.width(10.dp))

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(slotHDp),
                contentAlignment = Alignment.Center,
            ) {
                val fieldBoxH = lerp(14.dp, 56.dp, fieldW)
                val fieldInsetH = lerp(6.dp, 0.dp, fieldW)
                val collapseY = trackW *
                    (with(density) { slotHDp.toPx() - fieldBoxH.toPx() } / 2f).coerceAtLeast(0f)
                val collapseScaleY = (1f - 0.82f * trackW).coerceAtLeast(0.01f)
                val sliderColors = SliderDefaults.colors(
                    activeTrackColor = req.sourceActiveTrackColor ?: MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = req.sourceInactiveTrackColor ?: MaterialTheme.colorScheme.surfaceContainerHighest,
                    thumbColor = req.sourceThumbColor ?: MaterialTheme.colorScheme.primary,
                    activeTickColor = Color.Transparent,
                    inactiveTickColor = Color.Transparent,
                    disabledThumbColor = req.sourceThumbColor ?: MaterialTheme.colorScheme.primary,
                    disabledActiveTrackColor = req.sourceActiveTrackColor ?: MaterialTheme.colorScheme.primary,
                    disabledInactiveTrackColor = req.sourceInactiveTrackColor ?: MaterialTheme.colorScheme.surfaceContainerHighest,
                    disabledActiveTickColor = Color.Transparent,
                    disabledInactiveTickColor = Color.Transparent,
                )

                Slider(
                    value = (exitSliderValue ?: draftNum).coerceIn(req.visualRangeStart, req.visualRangeEnd),
                    onValueChange = {},
                    valueRange = req.visualRangeStart..req.visualRangeEnd,
                    enabled = false,
                    colors = sliderColors,
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            translationY = collapseY
                            scaleY = collapseScaleY
                            alpha = 1f - trackW
                        },
                    thumb = {
                        Box(
                            modifier = Modifier
                                .width(8.dp)
                                .height(28.dp)
                                .clip(CircleShape)
                                .background(req.sourceThumbColor ?: MaterialTheme.colorScheme.primary),
                        )
                    },
                )

                val fieldCorner = lerp(8.dp, 16.dp, sstep(fieldW, 0.15f, 0.85f))
                val fieldShape = RoundedCornerShape(fieldCorner)
                val baseFieldBorder = when {
                    isError -> MaterialTheme.colorScheme.error
                    isFieldFocused -> MaterialTheme.colorScheme.primary
                    else -> fieldBg.edgeOutline()
                }
                val fieldBorderAlpha = if (isError || isFieldFocused) 1f else outlineAlpha
                val fieldBorderColor = baseFieldBorder.copy(
                    alpha = baseFieldBorder.alpha * fieldBorderAlpha,
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = fieldInsetH)
                        .height(fieldBoxH)
                        .graphicsLayer {
                            alpha = sstep(fieldW, 0.02f, 0.28f)
                        }
                        .clip(fieldShape)
                        .background(fieldBg)
                        .border(1.dp, fieldBorderColor, fieldShape)
                        .clipToBounds(),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .graphicsLayer {
                                alpha = fieldTextW
                            },
                        verticalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = stringResource(R.string.dialog_value_title),
                            style = MaterialTheme.typography.labelSmall,
                            color = when {
                                isError -> MaterialTheme.colorScheme.error
                                isFieldFocused -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            maxLines = 1,
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            BasicTextField(
                                value = text,
                                onValueChange = {
                                    text = it
                                    isError = !validate(it)
                                },
                                readOnly = closing || morphT < 0.8f,
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = if (req.isInteger) {
                                        KeyboardType.Number
                                    } else {
                                        KeyboardType.Decimal
                                    },
                                    imeAction = ImeAction.Done,
                                ),
                                keyboardActions = KeyboardActions(onDone = { tryApply() }),
                                textStyle = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                modifier = Modifier
                                    .weight(1f)
                                    .onFocusChanged { isFieldFocused = it.isFocused },
                            )

                            if (req.unit.isNotEmpty()) {
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = req.unit,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    modifier = Modifier.onPlaced { coords ->
                                        val b = coords.boundsInRoot()
                                        if (b.width > 1f && b.height > 1f) unitBounds = b
                                    },
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.width(10.dp))

            ExpressiveStepButton(
                symbol = "+",
                enabled = !closing && draftNum < req.rangeEnd,
                size = stepBtnSize,
                corner = stepBtnCorner,
                onClick = { stepDraft(1) },
            )
        }

        Spacer(Modifier.height(lerp(10.dp, 12.dp, expandW)))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(bottomRowHDp),
        ) {
            if (req.showInfoIcon) {
                val infoOut = sstep(morphT, 0.05f, 0.50f)
                if (infoOut < 1f) {
                    val infoShiftPx = with(density) { 32.dp.toPx() }
                    Box(
                        modifier = Modifier
                            .width(lerp(32.dp, 0.dp, infoOut))
                            .height(32.dp)
                            .clipToBounds(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier
                                .size(20.dp)
                                .graphicsLayer {
                                    translationX = -infoShiftPx * infoOut
                                    alpha = 1f - sstep(infoOut, 0f, 0.7f)
                                },
                        )
                    }
                }
            }
            if (resetReveal > 0.001f) {
                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            alpha = sstep(resetReveal, 0.02f, 0.35f)
                        }
                        .clip(CircleShape)
                        .background(resetBg)
                        .border(1.dp, resetBg.edgeOutline(), CircleShape)
                        .unfoldPill(resetReveal)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            enabled = !closing && shouldShowReset,
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                val base = req.defaultValue ?: req.initialValue
                                text = if (req.isInteger) {
                                    base.toInt().toString()
                                } else {
                                    base.toString()
                                }
                                isError = !validate(text)
                            },
                        ),
                ) {
                    val resetGrow = sstep(morphT, 0.30f, 0.90f)
                    Text(
                        text = req.resetText,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontSize = lerp(
                                MaterialTheme.typography.labelMedium.fontSize,
                                MaterialTheme.typography.labelLarge.fontSize,
                                resetGrow,
                            ),
                            fontWeight = if (resetGrow > 0.5f) FontWeight.SemiBold else FontWeight.Medium,
                        ),
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(
                            horizontal = lerp(12.dp, 16.dp, resetGrow),
                            vertical = lerp(5.dp, 7.dp, resetGrow),
                        ),
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            Box(contentAlignment = Alignment.CenterEnd) {
                val exitReadoutText = remember(req.formattedValue, confirmedNum) {
                    val c = confirmedNum ?: return@remember req.formattedValue
                    formatExitReadout(req.formattedValue, c, req.isInteger, req.unit)
                }
                if (req.formattedValue.isNotEmpty()) {
                    val splitMatch = remember(exitReadoutText) {
                        ReadoutLeadingNum.find(exitReadoutText)
                    }
                    val splitAfter = splitMatch
                        ?.let { exitReadoutText.substring(it.range.last + 1) }
                        .orEmpty()
                    val splitGap = splitAfter.takeWhile { it.isWhitespace() }
                    val splitSuffix = splitAfter.drop(splitGap.length)
                    val flightStyle = MaterialTheme.typography.labelLarge
                    val flightColor = MaterialTheme.colorScheme.primary
                    val flightFade = 1f - sstep(morphT, 0.30f, 0.56f)
                    if (splitMatch != null && splitSuffix.isNotEmpty()) {
                        val numberPart = splitMatch.value
                        val numTx = req.valueFlyDxPx * flyW
                        val numTy = -req.valueFlyDyPx * flyW
                        val unitAbsTx = if (unitBounds != null && flyOriginBounds != null) {
                            (unitBounds!!.left - flyOriginBounds!!.left) * flyW
                        } else {
                            numTx
                        }
                        val unitAbsTy = if (unitBounds != null && flyOriginBounds != null) {
                            (unitBounds!!.center.y - flyOriginBounds!!.center.y) * flyW
                        } else {
                            numTy
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .onPlaced { coords ->
                                    if (flyW < 0.01f) {
                                        val b = coords.boundsInRoot()
                                        if (b.width > 1f && b.height > 1f) flyOriginBounds = b
                                    }
                                }
                                .graphicsLayer {
                                    translationX = numTx
                                    translationY = numTy
                                    alpha = flightFade
                                },
                        ) {
                            Text(
                                text = numberPart,
                                style = flightStyle,
                                fontWeight = FontWeight.Bold,
                                color = flightColor,
                                maxLines = 1,
                                softWrap = false,
                            )
                            Text(
                                text = splitGap + splitSuffix,
                                style = flightStyle,
                                fontWeight = FontWeight.Bold,
                                color = flightColor,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.graphicsLayer {
                                    translationX = unitAbsTx - numTx
                                    translationY = unitAbsTy - numTy
                                },
                            )
                        }
                    } else {
                        Text(
                            text = exitReadoutText,
                            style = flightStyle,
                            fontWeight = FontWeight.Bold,
                            color = flightColor,
                            maxLines = 1,
                            textAlign = TextAlign.End,
                            modifier = Modifier.graphicsLayer {
                                translationX = req.valueFlyDxPx * flyW
                                translationY = -req.valueFlyDyPx * flyW
                                alpha = flightFade
                            },
                        )
                    }
                }

                val cancelShape = RoundedCornerShape(14.dp)
                val cancelBorderColor = cancelBg.edgeOutline().let {
                    it.copy(alpha = it.alpha * outlineAlpha)
                }
                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            alpha = sstep(cancelW, 0.05f, 0.45f)
                        }
                        .clip(cancelShape)
                        .background(cancelBg)
                        .border(1.dp, cancelBorderColor, cancelShape)
                        .unfoldPill(cancelW)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            enabled = !closing && morphT > 0.5f,
                            onClick = { closeAnimated(req.onDismiss) },
                        )
                        .padding(horizontal = 16.dp, vertical = 7.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.action_cancel),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }

        val applyBorderColor = MaterialTheme.colorScheme.primary.edgeOutline().let {
            it.copy(alpha = it.alpha * outlineAlpha)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(applySlotHDp)
                .clipToBounds(),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Button(
                onClick = { if (!closing && morphT > 0.6f) tryApply() },
                enabled = true,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, applyBorderColor),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .graphicsLayer {
                        scaleY = 0.72f + 0.28f * applyW
                        alpha = sstep(applyW, 0.02f, 0.55f)
                        translationY = (1f - applyW) * 28f * dpPx
                    },
            ) {
                Text(
                    text = stringResource(R.string.action_apply),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
fun SliderSetting(
    title: String,
    value: Int,
    range: IntRange,
    unit: String,
    enabled: Boolean,
    onValueChange: (Int) -> Unit,
    onDefault: () -> Unit,
    valueText: String? = null,
    infoText: String? = null,
    videoResName: String? = null,
    onInfoClick: ((String, String, String?) -> Unit)? = null,
    onValueChangeFinished: (() -> Unit)? = null,
    showDefaultButton: Boolean = true,
    inputRange: IntRange? = null,
    defaultValue: Int? = null,
    showValueBelow: Boolean = false,
    defaultValueText: String? = null,
) {
    SliderSettingFloat(
        title = title,
        value = value.toFloat(),
        range = range.first.toFloat()..range.last.toFloat(),
        unit = unit,
        enabled = enabled,
        onValueChange = { onValueChange(it.toInt()) },
        onDefault = onDefault,
        valueText = valueText,
        isInteger = true,
        infoText = infoText,
        videoResName = videoResName,
        onInfoClick = onInfoClick,
        onValueChangeFinished = onValueChangeFinished,
        showDefaultButton = showDefaultButton,
        inputRange = inputRange?.let { it.first.toFloat()..it.last.toFloat() },
        stepSize = 1f,
        defaultValue = defaultValue?.toFloat(),
        showValueBelow = showValueBelow,
        defaultValueText = defaultValueText,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SliderSettingFloat(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    unit: String,
    enabled: Boolean,
    onValueChange: (Float) -> Unit,
    onDefault: () -> Unit,
    valueText: String? = null,
    isInteger: Boolean = false,
    infoText: String? = null,
    videoResName: String? = null,
    onInfoClick: ((String, String, String?) -> Unit)? = null,
    onValueChangeFinished: (() -> Unit)? = null,
    showDefaultButton: Boolean = true,
    inputRange: ClosedFloatingPointRange<Float>? = null,
    stepSize: Float = if (isInteger) 1f else 0.01f,
    defaultValue: Float? = null,
    showValueBelow: Boolean = false,
    defaultValueText: String? = null,
) {
    var showManualInput by remember { mutableStateOf(false) }
    var snapResetNext by remember { mutableStateOf(false) }
    var activeSessionId by remember { mutableLongStateOf(0L) }
    val haptics = LocalHapticFeedback.current
    val morphHost = LocalSliderMorphHost.current
    val density = LocalDensity.current

    var containerRect by remember { mutableStateOf(Rect.Zero) }
    var trackRect by remember { mutableStateOf(Rect.Zero) }
    var readoutRect by remember { mutableStateOf(Rect.Zero) }

    val latestSessionId by rememberUpdatedState(activeSessionId)
    LaunchedEffect(morphHost?.request?.sessionId, activeSessionId) {
        val currentReqId = morphHost?.request?.sessionId
        if (showManualInput && morphHost != null && activeSessionId != 0L && currentReqId != activeSessionId) {
            showManualInput = false
        }
    }
    DisposableEffect(morphHost) {
        onDispose {
            if (latestSessionId != 0L) {
                morphHost?.closeById(latestSessionId)
            }
        }
    }

    val contentAlpha = if (enabled) 1f else 0.42f
    val rowHidden = showManualInput && morphHost != null
    val rowEnabled = enabled && !rowHidden

    val formattedValue = valueText ?: if (isInteger) {
        if (unit.isNotEmpty()) "${value.toInt()} $unit" else "${value.toInt()}"
    } else {
        String.format(Locale.US, "%.2f %s", value, unit).trim()
    }

    val resetThreshold = minOf(
        (range.endInclusive - range.start) * 0.005f,
        stepSize * 0.5f,
    ).coerceAtLeast(1e-6f)

    val parsedTextDefault = remember(defaultValueText) {
        defaultValueText?.let {
            LeadingFloatRegex.find(it)?.value?.replace(',', '.')?.toFloatOrNull()
        }
    }
    var capturedDefault by remember { mutableStateOf<Float?>(null) }
    var awaitingResetCapture by remember { mutableStateOf(false) }
    LaunchedEffect(value, awaitingResetCapture) {
        if (awaitingResetCapture) {
            capturedDefault = value
            awaitingResetCapture = false
        }
    }
    val effectiveDefaultValue = defaultValue ?: parsedTextDefault ?: capturedDefault

    val showResetBadge = showDefaultButton &&
        (effectiveDefaultValue == null || value != effectiveDefaultValue)

    val dialogRange = inputRange ?: range
    val morphResetText = if (defaultValueText.isNullOrEmpty()) {
        stringResource(R.string.slider_default)
    } else {
        stringResource(R.string.slider_default_value, defaultValueText)
    }

    val sliderFraction = if (range.endInclusive > range.start) {
        ((value.coerceIn(range.start, range.endInclusive) - range.start) /
            (range.endInclusive - range.start)).coerceIn(0f, 1f)
    } else {
        0f
    }

    fun triggerDefaultReset() {
        awaitingResetCapture = true
        onDefault()
        capturedDefault = value
    }

    val morphSliderActive = MaterialTheme.colorScheme.primary
    val morphSliderInactive = MaterialTheme.colorScheme.surfaceContainerHighest

    fun openManualInput() {
        val textPadStartPx = with(density) { 16.dp.toPx() }
        val textOffsetYFromCenterPx = with(density) { 8.dp.toPx() }
        val flyDyPx = if (trackRect.height > 1f && readoutRect.height > 1f) {
            (readoutRect.center.y - (trackRect.center.y + textOffsetYFromCenterPx))
                .coerceAtLeast(0f)
        } else {
            0f
        }
        val flyDxPx = if (trackRect.width > 1f && readoutRect.width > 1f) {
            (trackRect.left + textPadStartPx) - readoutRect.left
        } else {
            0f
        }
        val sessionId = System.nanoTime()
        activeSessionId = sessionId
        showManualInput = true
        morphHost?.open(
            SliderMorphRequest(
                title = title,
                initialValue = value,
                rangeStart = dialogRange.start,
                rangeEnd = dialogRange.endInclusive,
                visualRangeStart = range.start,
                visualRangeEnd = range.endInclusive,
                unit = unit,
                isInteger = isInteger,
                stepSize = stepSize,
                formattedValue = formattedValue,
                showResetBadge = showResetBadge,
                resetText = morphResetText,
                sourceInRoot = containerRect,
                valueFlyDyPx = flyDyPx,
                valueFlyDxPx = flyDxPx,
                sliderFraction = sliderFraction,
                initialTrackHeightPx = trackRect.height,
                defaultValue = effectiveDefaultValue,
                resetThreshold = resetThreshold,
                showDefaultButton = showDefaultButton,
                showInfoIcon = onInfoClick != null && infoText != null,
                sourceBoundsProvider = { containerRect },
                sessionId = sessionId,
                sourceActiveTrackColor = morphSliderActive,
                sourceInactiveTrackColor = morphSliderInactive,
                sourceThumbColor = morphSliderActive,
                onConfirm = {
                    snapResetNext = true
                    onValueChange(it)
                    onValueChangeFinished?.invoke()
                    showManualInput = false
                },
                onDefault = {
                    snapResetNext = true
                    triggerDefaultReset()
                    onValueChangeFinished?.invoke()
                    showManualInput = false
                },
                onDismiss = {
                    snapResetNext = true
                    showManualInput = false
                },
            ),
        )
    }

    if (showManualInput && morphHost == null) {
        SliderInlineManualInput(
            title = title,
            value = value,
            range = inputRange ?: range,
            unit = unit,
            isInteger = isInteger,
            stepSize = stepSize,
            formattedValue = formattedValue,
            showResetBadge = showResetBadge,
            defaultValueText = defaultValueText,
            sliderFraction = sliderFraction,
            defaultValue = effectiveDefaultValue,
            resetThreshold = resetThreshold,
            showDefaultButton = showDefaultButton,
            onConfirm = {
                onValueChange(it)
                onValueChangeFinished?.invoke()
                showManualInput = false
            },
            onDefault = {
                triggerDefaultReset()
                onValueChangeFinished?.invoke()
                showManualInput = false
            },
            onDismiss = { showManualInput = false },
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .onPlaced {
                val r = it.unclippedBoundsInRoot()
                if (r.width > 1f && r.height > 1f) containerRect = r
            }
            .onGloballyPositioned {
                val r = it.unclippedBoundsInRoot()
                if (r.width > 1f && r.height > 1f) containerRect = r
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = rowEnabled,
            ) { openManualInput() }
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .alpha(if (rowHidden) 0f else 1f),
    ) {
        SliderTitleText(
            title = title,
            modifier = Modifier
                .fillMaxWidth()
                .alpha(contentAlpha),
        )

        Spacer(Modifier.height(10.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            ExpressiveStepButton(
                symbol = "−",
                enabled = rowEnabled && value > range.start,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    val newValue = (value - stepSize).coerceIn(range.start, range.endInclusive)
                    onValueChange(newValue)
                    onValueChangeFinished?.invoke()
                },
                modifier = Modifier.alpha(contentAlpha),
            )

            Spacer(Modifier.width(10.dp))

            val sliderColors = SliderDefaults.colors(
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
                disabledThumbColor = MaterialTheme.colorScheme.primary,
                disabledActiveTrackColor = MaterialTheme.colorScheme.primary,
                disabledInactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                disabledActiveTickColor = Color.Transparent,
                disabledInactiveTickColor = Color.Transparent,
            )
            Slider(
                value = value.coerceIn(range.start, range.endInclusive),
                onValueChange = onValueChange,
                valueRange = range,
                enabled = rowEnabled,
                colors = sliderColors,
                modifier = Modifier
                    .weight(1f)
                    .alpha(contentAlpha)
                    .onPlaced {
                        val r = it.unclippedBoundsInRoot()
                        if (r.width > 1f && r.height > 1f) trackRect = r
                    }
                    .onGloballyPositioned {
                        val r = it.unclippedBoundsInRoot()
                        if (r.width > 1f && r.height > 1f) trackRect = r
                    },
                onValueChangeFinished = onValueChangeFinished,
                thumb = {
                    Box(
                        modifier = Modifier
                            .width(8.dp)
                            .height(28.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                },
            )

            Spacer(Modifier.width(10.dp))

            ExpressiveStepButton(
                symbol = "+",
                enabled = rowEnabled && value < range.endInclusive,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    val newValue = (value + stepSize).coerceIn(range.start, range.endInclusive)
                    onValueChange(newValue)
                    onValueChangeFinished?.invoke()
                },
                modifier = Modifier.alpha(contentAlpha),
            )
        }

        Spacer(Modifier.height(10.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
                .alpha(contentAlpha),
        ) {
            if (onInfoClick != null && infoText != null) {
                IconButton(
                    onClick = { onInfoClick(title, infoText, videoResName) },
                    enabled = rowEnabled,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = stringResource(R.string.action_info),
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            val rowResetAnim = remember { Animatable(if (showResetBadge) 1f else 0f) }
            val rowResetTarget = if (showResetBadge) 1f else 0f
            LaunchedEffect(showResetBadge, rowHidden) {
                if (rowHidden || snapResetNext) {
                    snapResetNext = false
                    rowResetAnim.snapTo(rowResetTarget)
                } else if (rowResetAnim.targetValue != rowResetTarget) {
                    rowResetAnim.animateTo(
                        targetValue = rowResetTarget,
                        animationSpec = spring(
                            stiffness = 300f,
                            dampingRatio = Spring.DampingRatioNoBouncy,
                        ),
                    )
                }
            }
            val rowResetReveal = if (rowHidden || snapResetNext) rowResetTarget else rowResetAnim.value
            if (rowResetReveal > 0.001f) {
                val resetText = if (defaultValueText.isNullOrEmpty()) {
                    stringResource(R.string.slider_default)
                } else {
                    stringResource(R.string.slider_default_value, defaultValueText)
                }
                val resetBg = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            alpha = sstep(rowResetReveal, 0.02f, 0.35f)
                        }
                        .clip(CircleShape)
                        .background(resetBg)
                        .border(1.dp, resetBg.edgeOutline(), CircleShape)
                        .unfoldPill(rowResetReveal)
                        .clickable(enabled = rowEnabled && showResetBadge) {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            triggerDefaultReset()
                            onValueChangeFinished?.invoke()
                        },
                ) {
                    Text(
                        text = resetText,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            Text(
                text = formattedValue,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                textAlign = TextAlign.End,
                modifier = Modifier
                    .onPlaced {
                        val r = it.unclippedBoundsInRoot()
                        if (r.width > 1f && r.height > 1f) readoutRect = r
                    }
                    .onGloballyPositioned {
                        val r = it.unclippedBoundsInRoot()
                        if (r.width > 1f && r.height > 1f) readoutRect = r
                    },
            )
        }
    }
}

@Composable
fun SliderTitleText(
    title: String,
    modifier: Modifier = Modifier,
    growProgress: Float = 0f,
    maxGrow: Float = 2f,
    baseStyle: TextStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
) {
    val baseSizeSp = baseStyle.fontSize.value
    val minSizeSp = (baseSizeSp * 0.7f).coerceAtLeast(10f)
    val startSizeSp = remember(title) { baseSizeSp }
    var shrinkSp by remember(title) { mutableStateOf(0f) }
    val fittedOpenSp = (baseSizeSp * maxGrow - shrinkSp).coerceAtLeast(minSizeSp)
    val sizeSp = (startSizeSp + (fittedOpenSp - startSizeSp) * growProgress.coerceIn(0f, 1f))
        .coerceAtLeast(minSizeSp)
    Text(
        text = title,
        style = baseStyle.copy(fontSize = sizeSp.sp),
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip,
        onTextLayout = { result ->
            if (result.hasVisualOverflow && sizeSp > minSizeSp) {
                shrinkSp += 0.5f
            }
        },
        modifier = modifier,
    )
}

@Composable
internal fun ExpressiveStepButton(
    symbol: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 38.dp,
    corner: Dp = 12.dp,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else 1f,
        animationSpec = spring(
            stiffness = Spring.StiffnessMedium,
            dampingRatio = Spring.DampingRatioMediumBouncy,
        ),
        label = "StepBtnScale",
    )
    val bgColor by animateColorAsState(
        targetValue = if (isPressed) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        label = "StepBtnBg",
    )

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(corner))
            .background(bgColor)
            .border(1.dp, bgColor.edgeOutline(), RoundedCornerShape(corner))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = symbol,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun SliderInlineManualInput(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    unit: String,
    isInteger: Boolean,
    stepSize: Float,
    formattedValue: String,
    showResetBadge: Boolean,
    defaultValueText: String?,
    sliderFraction: Float = 0f,
    defaultValue: Float? = null,
    resetThreshold: Float = (stepSize * 0.5f).coerceAtLeast(1e-6f),
    showDefaultButton: Boolean = true,
    onConfirm: (Float) -> Unit,
    onDefault: () -> Unit,
    onDismiss: () -> Unit,
) {
    val morphAnim = remember { Animatable(0f) }
    var closing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        morphAnim.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                stiffness = 250f,
                dampingRatio = Spring.DampingRatioNoBouncy,
                visibilityThreshold = 0.002f,
            ),
        )
    }

    fun closeAnimated(commit: () -> Unit) {
        if (closing) return
        closing = true
        scope.launch {
            morphAnim.stop()
            try {
                morphAnim.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(
                        stiffness = 500f,
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        visibilityThreshold = 0.01f,
                    ),
                )
            } finally {
                commit()
            }
        }
    }

    PredictiveBackHandler(enabled = !closing) { progress ->
        try {
            progress.collect { }
            closeAnimated(onDismiss)
        } catch (_: CancellationException) {
        }
    }

    val resetText = if (defaultValueText.isNullOrEmpty()) {
        stringResource(R.string.slider_default)
    } else {
        stringResource(R.string.slider_default_value, defaultValueText)
    }

    Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
        SliderMorphEditorContent(
            request = SliderMorphRequest(
                title = title,
                initialValue = value,
                rangeStart = range.start,
                rangeEnd = range.endInclusive,
                unit = unit,
                isInteger = isInteger,
                stepSize = stepSize,
                formattedValue = formattedValue,
                showResetBadge = showResetBadge,
                resetText = resetText,
                sourceInRoot = Rect.Zero,
                valueFlyDyPx = 0f,
                sliderFraction = sliderFraction,
                defaultValue = defaultValue,
                resetThreshold = resetThreshold,
                showDefaultButton = showDefaultButton,
                sourceActiveTrackColor = MaterialTheme.colorScheme.primary,
                sourceInactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                sourceThumbColor = MaterialTheme.colorScheme.primary,
                onConfirm = onConfirm,
                onDefault = onDefault,
                onDismiss = onDismiss,
            ),
            morphT = morphAnim.value.coerceIn(0f, 1f),
            closing = closing,
            closeAnimated = ::closeAnimated,
        )
    }
}