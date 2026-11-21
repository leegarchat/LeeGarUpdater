package org.leegar.updater.ui.pep

import android.app.Activity
import android.content.ContextWrapper
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import kotlinx.coroutines.launch
import org.leegar.updater.R
import org.leegar.updater.data.ThemeMode
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.math.sin

val LocalContentHorizontalInset = compositionLocalOf { 0.dp }

/**
 * Extra symmetric left/right inset for wide layouts (tablets & landscape).
 * Phones in portrait (<= 500dp) stay untouched at 0.dp.
 */
fun calculateContentHorizontalInset(windowWidth: Dp): Dp {
    val compactLimit = 500.dp
    if (windowWidth <= compactLimit) return 0.dp
    val maxContentWidth = 620.dp
    val targetContentWidth = (compactLimit + (windowWidth - compactLimit) * 0.28f)
        .coerceAtMost(maxContentWidth)
    return ((windowWidth - targetContentWidth) / 2f).coerceAtLeast(0.dp)
}

/**
 * Tactile spring compression on touch for expressive visual feedback.
 */
fun Modifier.tactilePressScale(
    interactionSource: MutableInteractionSource,
    enabled: Boolean = true,
    pressedScale: Float = 0.965f,
): Modifier = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (enabled && pressed) pressedScale else 1f,
        animationSpec = spring(
            stiffness = Spring.StiffnessMedium,
            dampingRatio = Spring.DampingRatioNoBouncy,
        ),
        label = "TactilePressScale",
    )
    this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * Dynamic Material You theme supporting System / Light / Dark override + system bar icon polarity.
 */
@Composable
fun UpdaterTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val colorScheme = if (darkTheme) {
        dynamicDarkColorScheme(context)
    } else {
        dynamicLightColorScheme(context)
    }

    SideEffect {
        val activity = generateSequence(context) { (it as? ContextWrapper)?.baseContext }
            .filterIsInstance<Activity>()
            .firstOrNull()
        activity?.window?.let { window ->
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(colorScheme = colorScheme) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surfaceContainer,
            content = content,
        )
    }
}

@Composable
fun UpdaterProgressIndicator(
    progress: Float?, // null => indeterminate
    wavy: Boolean,
    isActive: Boolean = true,
    wavyAmplitude: Float = 0.85f,
    modifier: Modifier = Modifier,
    height: Dp = 6.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
) {
    val isIndeterminate = progress == null
    val animatedProgress by animateFloatAsState(
        targetValue = (progress ?: 0f).coerceIn(0f, 1f),
        animationSpec = spring(
            stiffness = Spring.StiffnessLow,
            dampingRatio = Spring.DampingRatioNoBouncy,
            visibilityThreshold = 0.0005f,
        ),
        label = "ProgressGlide",
    )

    val targetAmplitude = if (wavy && isActive) wavyAmplitude.coerceIn(0f, 1f) else 0f
    val animatedAmplitude by animateFloatAsState(
        targetValue = targetAmplitude,
        animationSpec = spring(
            stiffness = Spring.StiffnessMediumLow,
            dampingRatio = Spring.DampingRatioNoBouncy,
            visibilityThreshold = 0.001f,
        ),
        label = "WavyAmplitudeMorph",
    )

    val currentIsIndeterminate by rememberUpdatedState(isIndeterminate)
    val currentIsActive by rememberUpdatedState(isActive)
    val shouldTick = (isIndeterminate && isActive) || animatedAmplitude > 0.001f
    val twoPi = (2.0 * PI).toFloat()
    var wavePhaseRad by remember {
        mutableFloatStateOf(
            (Math.floorMod(System.nanoTime(), 1_000_000_000L).toFloat() / 1_000_000_000f) * twoPi,
        )
    }
    var indeterminateCycle by remember {
        mutableFloatStateOf(
            Math.floorMod(System.nanoTime(), 1_800_000_000L).toFloat() / 1_800_000_000f,
        )
    }

    LaunchedEffect(shouldTick) {
        if (!shouldTick) return@LaunchedEffect
        while (true) {
            withFrameNanos { now ->
                wavePhaseRad = (Math.floorMod(now, 1_000_000_000L).toFloat() / 1_000_000_000f) * twoPi
                if (currentIsIndeterminate && currentIsActive) {
                    indeterminateCycle = Math.floorMod(now, 1_800_000_000L).toFloat() / 1_800_000_000f
                }
            }
        }
    }

    val strokeDp = (height * 0.72f).coerceIn(3.dp, 6.dp)
    val maxWaveHalfAmpDp = (height * 0.65f).coerceIn(3.dp, 5.dp)
    val totalHeightDp = strokeDp + maxWaveHalfAmpDp * 2f
    val wavePath = remember { Path() }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(totalHeightDp),
    ) {
        val strokePx = strokeDp.toPx()
        val halfStroke = strokePx / 2f
        val startX = halfStroke
        val endX = (size.width - halfStroke).coerceAtLeast(startX)
        val usableW = (endX - startX).coerceAtLeast(0f)
        if (usableW <= 0f) return@Canvas

        val centerY = size.height / 2f
        val gapPx = 4.dp.toPx() + strokePx
        val wavelengthPx = 40.dp.toPx()
        val ampPx = maxWaveHalfAmpDp.toPx() * animatedAmplitude.coerceIn(0f, 1f)
        val phase = wavePhaseRad
        val twoPi = (2.0 * PI).toFloat()

        fun drawWavySegment(segStart: Float, segEnd: Float) {
            val segLen = segEnd - segStart
            if (segLen <= 0.5f) return
            if (ampPx <= 0.15f) {
                drawLine(
                    color = color,
                    start = Offset(segStart, centerY),
                    end = Offset(segEnd, centerY),
                    strokeWidth = strokePx,
                    cap = StrokeCap.Round,
                )
                return
            }
            wavePath.reset()
            val stepPx = 2.5f
            val steps = (segLen / stepPx).roundToInt().coerceAtLeast(1)
            val k = twoPi / wavelengthPx
            val lenEnvelope = (segLen / (wavelengthPx * 0.4f)).coerceIn(0f, 1f)
            val effAmp = ampPx * lenEnvelope
            for (i in 0..steps) {
                val x = segStart + (segLen * i) / steps
                val y = centerY + sin(x * k - phase) * effAmp
                if (i == 0) wavePath.moveTo(x, y) else wavePath.lineTo(x, y)
            }
            drawPath(
                path = wavePath,
                color = color,
                style = Stroke(
                    width = strokePx,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            )
        }

        if (isIndeterminate) {
            val cycle = indeterminateCycle
            val head = FastOutSlowInEasing.transform((cycle / 0.72f).coerceIn(0f, 1f))
            val tail = FastOutSlowInEasing.transform(((cycle - 0.28f) / 0.72f).coerceIn(0f, 1f))
            val segStart = startX + usableW * tail
            val segEnd = startX + usableW * head

            val leftTrackEnd = segStart - gapPx
            if (leftTrackEnd > startX) {
                drawLine(
                    color = trackColor,
                    start = Offset(startX, centerY),
                    end = Offset(leftTrackEnd, centerY),
                    strokeWidth = strokePx,
                    cap = StrokeCap.Round,
                )
            }
            val rightTrackStart = segEnd + gapPx
            if (rightTrackStart < endX) {
                drawLine(
                    color = trackColor,
                    start = Offset(rightTrackStart, centerY),
                    end = Offset(endX, centerY),
                    strokeWidth = strokePx,
                    cap = StrokeCap.Round,
                )
            }
            drawWavySegment(segStart, segEnd)
        } else {
            val p = animatedProgress.coerceIn(0f, 1f)
            val activeEnd = startX + usableW * p

            if (p <= 0.001f) {
                drawLine(
                    color = trackColor,
                    start = Offset(startX, centerY),
                    end = Offset(endX, centerY),
                    strokeWidth = strokePx,
                    cap = StrokeCap.Round,
                )
            } else {
                val trackStart = activeEnd + gapPx
                if (trackStart < endX) {
                    drawLine(
                        color = trackColor,
                        start = Offset(trackStart, centerY),
                        end = Offset(endX, centerY),
                        strokeWidth = strokePx,
                        cap = StrokeCap.Round,
                    )
                }
                drawWavySegment(startX, activeEnd)
            }

            if (p < 0.985f) {
                drawCircle(
                    color = color,
                    radius = strokePx * 0.42f,
                    center = Offset(endX, centerY),
                )
            }
        }
    }
}

@Stable
class UpdaterPagerState(
    internal val pagerState: PagerState,
) {
    val anchor: Int
        get() = pagerState.currentPage

    val position: Float
        get() = (pagerState.currentPage + pagerState.currentPageOffsetFraction)
            .coerceIn(0f, (pagerState.pageCount - 1).coerceAtLeast(0).toFloat())

    val isScrollInProgress: Boolean
        get() = pagerState.isScrollInProgress

    suspend fun animateScrollToPage(page: Int) {
        pagerState.animateScrollToPage(
            page = page,
            animationSpec = spring(
                stiffness = Spring.StiffnessMediumLow,
                dampingRatio = Spring.DampingRatioNoBouncy,
            ),
        )
    }
}

@Composable
fun rememberUpdaterPagerState(
    pageCount: Int,
    initialPage: Int = 0,
): UpdaterPagerState {
    val internal = rememberPagerState(initialPage = initialPage) { pageCount }
    return remember(internal) { UpdaterPagerState(internal) }
}

@Composable
fun UpdaterPager(
    state: UpdaterPagerState,
    userScrollEnabled: Boolean = true,
    onHorizontalDragDetected: () -> Unit = {},
    modifier: Modifier = Modifier,
    pageContent: @Composable (page: Int) -> Unit,
) {
    LaunchedEffect(state.isScrollInProgress) {
        if (state.isScrollInProgress) {
            onHorizontalDragDetected()
        }
    }
    HorizontalPager(
        state = state.pagerState,
        userScrollEnabled = userScrollEnabled,
        beyondViewportPageCount = 1,
        modifier = modifier,
    ) { page ->
        pageContent(page)
    }
}

/**
 * PEP segmented pill tab bar with smooth continuous indicator tracking and tactile press feedback.
 */
@Composable
fun UpdaterTabs(
    titles: List<String>,
    position: Float,
    onTabClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (titles.isEmpty()) return
    val haptics = LocalHapticFeedback.current
    val trackBg = MaterialTheme.colorScheme.surface
    val indicatorBg = MaterialTheme.colorScheme.primaryContainer
    val shape = RoundedCornerShape(22.dp)

    val smoothPos by animateFloatAsState(
        targetValue = position.coerceIn(0f, (titles.size - 1).toFloat()),
        animationSpec = spring(
            stiffness = Spring.StiffnessMedium,
            dampingRatio = Spring.DampingRatioNoBouncy,
        ),
        label = "TabsIndicatorPos",
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(46.dp)
            .clip(shape)
            .background(trackBg)
            .border(1.dp, trackBg.edgeOutline(), shape)
            .padding(4.dp),
    ) {
        val count = titles.size
        val tabWidth = maxWidth / count
        val clampedPos = smoothPos.coerceIn(0f, (count - 1).toFloat())
        val indicatorShape = RoundedCornerShape(18.dp)

        Box(
            modifier = Modifier
                .width(tabWidth)
                .fillMaxHeight()
                .offset { IntOffset((tabWidth.toPx() * clampedPos).roundToInt(), 0) }
                .clip(indicatorShape)
                .background(indicatorBg)
                .border(1.dp, indicatorBg.edgeOutline(), indicatorShape),
        )

        Row(modifier = Modifier.fillMaxSize()) {
            titles.forEachIndexed { index, title ->
                val proximity = (1f - abs(clampedPos - index)).coerceIn(0f, 1f)
                val textColor = androidx.compose.ui.graphics.lerp(
                    MaterialTheme.colorScheme.onSurfaceVariant,
                    MaterialTheme.colorScheme.onPrimaryContainer,
                    proximity,
                )
                val interaction = remember { MutableInteractionSource() }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .tactilePressScale(interaction, pressedScale = 0.94f)
                        .clip(indicatorShape)
                        .clickable(
                            interactionSource = interaction,
                            indication = null,
                        ) {
                            haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                            onTabClick(index)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (proximity > 0.5f) FontWeight.Bold else FontWeight.Medium,
                        color = textColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun SecondaryActionPill(
    icon: ImageVector?,
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 44.dp,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val animBg by animateColorAsState(
        targetValue = containerColor,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "PillBg",
    )
    val animFg by animateColorAsState(
        targetValue = contentColor,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "PillFg",
    )
    val animAlpha by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.45f,
        animationSpec = tween(180),
        label = "PillAlpha",
    )
    val shape = RoundedCornerShape(16.dp)

    Row(
        modifier = modifier
            .height(height)
            .tactilePressScale(interaction, enabled = enabled, pressedScale = 0.96f)
            .alpha(animAlpha)
            .clip(shape)
            .background(animBg)
            .border(1.dp, animBg.edgeOutline(), shape)
            .clickable(
                interactionSource = interaction,
                indication = androidx.compose.material3.ripple(),
                enabled = enabled,
            ) {
                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                onClick()
            }
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedContent(
            targetState = Pair(icon, label),
            transitionSpec = {
                (fadeIn(tween(180)) + scaleIn(initialScale = 0.94f, animationSpec = tween(180))) togetherWith
                    (fadeOut(tween(120)) + scaleOut(targetScale = 0.94f, animationSpec = tween(120)))
            },
            label = "PillContentAnim",
        ) { (ic, text) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                if (ic != null) {
                    Icon(
                        imageVector = ic,
                        contentDescription = null,
                        tint = animFg,
                        modifier = Modifier.size(17.dp),
                    )
                    Spacer(Modifier.width(7.dp))
                }
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = animFg,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun UpdaterPrimaryPillButton(
    label: String,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    isDanger: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 46.dp,
) {
    val bg by animateColorAsState(
        targetValue = if (isDanger) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.primary
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "PrimaryPillBg",
    )
    val fg = if (isDanger) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary
    SecondaryActionPill(
        icon = icon,
        label = label,
        enabled = enabled,
        onClick = onClick,
        modifier = modifier,
        height = height,
        containerColor = bg,
        contentColor = fg,
    )
}

@Composable
fun UpdaterTwoTapDeleteButton(
    armed: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 46.dp,
) {
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val corner by animateFloatAsState(
        targetValue = if (armed) 23f else 16f,
        animationSpec = spring(
            stiffness = Spring.StiffnessMedium,
            dampingRatio = Spring.DampingRatioNoBouncy,
        ),
        label = "TwoTapDeleteCorner",
    )
    val bg by animateColorAsState(
        targetValue = if (armed) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.errorContainer
        },
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "TwoTapDeleteBg",
    )
    val fg by animateColorAsState(
        targetValue = if (armed) {
            MaterialTheme.colorScheme.onError
        } else {
            MaterialTheme.colorScheme.onErrorContainer
        },
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "TwoTapDeleteFg",
    )
    val shape = RoundedCornerShape(corner.dp)

    Box(
        modifier = modifier
            .size(size)
            .tactilePressScale(interaction, enabled = enabled, pressedScale = 0.92f)
            .alpha(if (enabled) 1f else 0.45f)
            .clip(shape)
            .background(bg)
            .border(1.dp, bg.edgeOutline(), shape)
            .clickable(
                interactionSource = interaction,
                indication = androidx.compose.material3.ripple(),
                enabled = enabled,
            ) {
                haptics.performHapticFeedback(
                    if (armed) HapticFeedbackType.LongPress else HapticFeedbackType.ContextClick,
                )
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = armed,
            transitionSpec = {
                (scaleIn(initialScale = 0.6f, animationSpec = spring(stiffness = Spring.StiffnessMedium)) + fadeIn(tween(150))) togetherWith
                    (scaleOut(targetScale = 0.6f, animationSpec = tween(120)) + fadeOut(tween(120)))
            },
            label = "DeleteIconMorph",
        ) { isArmed ->
            Icon(
                imageVector = if (isArmed) Icons.Rounded.Check else Icons.Rounded.Delete,
                contentDescription = if (isArmed) stringResource(R.string.overlay_confirm_delete) else stringResource(R.string.action_delete),
                tint = fg,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
fun UpdaterBadgePill(
    text: String,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier: Modifier = Modifier,
) {
    val animBg by animateColorAsState(
        targetValue = containerColor,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "BadgeBg",
    )
    val animFg by animateColorAsState(
        targetValue = contentColor,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "BadgeFg",
    )
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(animBg)
            .border(1.dp, animBg.edgeOutline(), CircleShape)
            .padding(horizontal = 9.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = animFg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun UpdaterSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(horizontal = 6.dp, vertical = 4.dp),
    )
}

@Composable
fun UpdaterSettingsGroupCard(
    title: String = "",
    containerColor: Color = MaterialTheme.colorScheme.surface,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (title.isNotBlank()) {
            UpdaterSectionHeader(title)
            Spacer(Modifier.height(4.dp))
        }
        Card(
            colors = CardDefaults.cardColors(containerColor = containerColor),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, containerColor.edgeOutline()),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                content = content,
            )
        }
    }
}

@Composable
fun UpdaterSwitchRow(
    title: String,
    summary: String? = null,
    checked: Boolean,
    enabled: Boolean = true,
    accentDanger: Boolean = false,
    onCheckedChange: (Boolean) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val animAlpha by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.5f,
        animationSpec = tween(180),
        label = "SwitchRowAlpha",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .tactilePressScale(interaction, enabled = enabled, pressedScale = 0.985f)
            .alpha(animAlpha)
            .clickable(
                interactionSource = interaction,
                indication = androidx.compose.material3.ripple(),
                enabled = enabled,
            ) {
                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                onCheckedChange(!checked)
            }
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (accentDanger) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            if (!summary.isNullOrBlank()) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = {
                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                onCheckedChange(it)
            },
        )
    }
}

@Composable
fun UpdaterEmptyCard(
    icon: ImageVector,
    title: String,
    description: String = "",
    modifier: Modifier = Modifier,
) {
    val bg = MaterialTheme.colorScheme.surface
    Card(
        colors = CardDefaults.cardColors(containerColor = bg),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, bg.edgeOutline()),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(36.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            if (description.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

private class InertialFlowState {
    val offsets = HashMap<Int, Animatable<Offset, AnimationVector2D>>()
    val targets = HashMap<Int, Offset>()
    val arcs = HashMap<Int, Animatable<Float, AnimationVector1D>>()
    val arcDirs = HashMap<Int, Float>()
    var heightAnim: Animatable<Float, AnimationVector1D>? = null
    var targetHeight: Float = -1f
}

@Composable
fun AnimatedInertialFlowRow(
    modifier: Modifier = Modifier,
    horizontalSpacing: Dp = 6.dp,
    verticalSpacing: Dp = 6.dp,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val state = remember { InertialFlowState() }

    Layout(
        content = content,
        modifier = modifier.fillMaxWidth(),
    ) { measurables, constraints ->
        val hGap = horizontalSpacing.roundToPx()
        val vGap = verticalSpacing.roundToPx()
        val maxW = constraints.maxWidth.coerceAtLeast(1)
        val childConstraints = constraints.copy(minWidth = 0, minHeight = 0)
        val placeables = measurables.map { it.measure(childConstraints) }

        val computed = ArrayList<Offset>(placeables.size)
        var x = 0
        var y = 0
        var rowH = 0
        for (p in placeables) {
            if (x > 0 && x + p.width > maxW) {
                x = 0
                y += rowH + vGap
                rowH = 0
            }
            computed.add(Offset(x.toFloat(), y.toFloat()))
            x += p.width + hGap
            if (p.height > rowH) rowH = p.height
        }
        val targetH = (y + rowH).toFloat()

        val arcAmpPx = 10.dp.toPx()
        for (i in computed.indices) {
            val target = computed[i]
            val anim = state.offsets[i]
            if (anim == null) {
                state.offsets[i] = Animatable(target, Offset.VectorConverter)
                state.targets[i] = target
                state.arcs[i] = Animatable(0f)
                state.arcDirs[i] = 0f
            } else if (state.targets[i] != target) {
                val prev = state.targets[i] ?: target
                state.targets[i] = target
                val lineWrapped = abs(target.y - prev.y) > 2f
                scope.launch {
                    anim.animateTo(
                        targetValue = target,
                        animationSpec = spring(
                            stiffness = Spring.StiffnessMediumLow,
                            dampingRatio = 0.78f,
                            visibilityThreshold = Offset(0.5f, 0.5f),
                        ),
                    )
                }
                if (lineWrapped) {
                    val arc = state.arcs.getOrPut(i) { Animatable(0f) }
                    state.arcDirs[i] = if (target.y < prev.y) -1f else 1f
                    scope.launch {
                        arc.snapTo(0f)
                        arc.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(360, easing = FastOutSlowInEasing),
                        )
                    }
                }
            }
        }

        val hAnim = state.heightAnim
        val resolvedHeight = if (hAnim == null) {
            state.heightAnim = Animatable(targetH)
            state.targetHeight = targetH
            targetH.roundToInt()
        } else {
            if (abs(state.targetHeight - targetH) > 0.5f) {
                state.targetHeight = targetH
                scope.launch {
                    hAnim.animateTo(
                        targetValue = targetH,
                        animationSpec = spring(
                            stiffness = Spring.StiffnessMediumLow,
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            visibilityThreshold = 0.5f,
                        ),
                    )
                }
            }
            hAnim.value.roundToInt().coerceAtLeast(0)
        }

        layout(maxW, resolvedHeight) {
            placeables.forEachIndexed { idx, p ->
                val cur = state.offsets[idx]?.value ?: computed[idx]
                val arcProgress = state.arcs[idx]?.value ?: 0f
                val arcDir = state.arcDirs[idx] ?: 0f
                val bowY = if (arcDir != 0f && arcProgress in 0.001f..0.999f) {
                    sin(arcProgress * PI).toFloat() * arcAmpPx * arcDir
                } else {
                    0f
                }
                p.placeRelative(cur.x.roundToInt(), (cur.y + bowY).roundToInt())
            }
        }
    }
}

@Composable
fun AutoSizeSingleLineText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    maxFontSize: TextUnit = style.fontSize,
    minFontSize: TextUnit = 6.sp,
) {
    val textMeasurer = rememberTextMeasurer()
    val maxSp = maxFontSize.value.coerceAtLeast(minFontSize.value)
    val minScale = (minFontSize.value / maxSp).coerceIn(0.25f, 1f)
    val resolvedStyle = remember(style, maxSp) { style.copy(fontSize = maxSp.sp) }
    val layoutResult = remember(text, resolvedStyle, textMeasurer) {
        textMeasurer.measure(
            text = text,
            style = resolvedStyle,
            maxLines = 1,
            softWrap = false,
        )
    }

    Layout(
        modifier = modifier
            .fillMaxWidth()
            .clipToBounds()
            .drawBehind {
                val availW = size.width
                val natW = layoutResult.size.width.toFloat()
                val natH = layoutResult.size.height.toFloat()
                if (availW <= 1f || natW <= 0f || natH <= 0f) return@drawBehind
                val scale = (availW / natW).coerceIn(minScale, 1f)
                val topOffset = (size.height - natH * scale) * 0.5f
                withTransform({
                    translate(left = 0f, top = topOffset)
                    scale(scaleX = scale, scaleY = scale, pivot = Offset.Zero)
                }) {
                    drawText(
                        textLayoutResult = layoutResult,
                        color = color,
                    )
                }
            },
    ) { _, constraints ->
        val w = constraints.maxWidth.coerceAtLeast(1)
        val natW = layoutResult.size.width.toFloat().coerceAtLeast(1f)
        val natH = layoutResult.size.height.toFloat().coerceAtLeast(1f)
        val scale = (w / natW).coerceIn(minScale, 1f)
        val h = ceil(natH * scale).toInt().coerceAtLeast(1)
        layout(w, h) {}
    }
}

@Composable
fun MorphingDownloadControlRow(
    isPaused: Boolean,
    pauseLabel: String,
    resumeLabel: String,
    cancelLabel: String,
    deleteLabel: String,
    confirmDeleteLabel: String,
    resumeEnabled: Boolean,
    morphToExpanded: Float,
    deleteArmed: Boolean,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val btnH = 46.dp
    val morph = morphToExpanded.coerceIn(0f, 1f)

    val pausedFrac by animateFloatAsState(
        targetValue = if (isPaused) 1f else 0f,
        animationSpec = spring(
            stiffness = Spring.StiffnessMediumLow,
            dampingRatio = Spring.DampingRatioNoBouncy,
            visibilityThreshold = 0.002f,
        ),
        label = "DownloadCtrlPausedFrac",
    )
    val compactTrash = (pausedFrac * (1f - morph)).coerceIn(0f, 1f)

    val primaryCorner by animateDpAsState(
        targetValue = if (isPaused) 23.dp else 14.dp,
        animationSpec = spring(
            stiffness = Spring.StiffnessMedium,
            dampingRatio = Spring.DampingRatioNoBouncy,
        ),
        label = "DownloadCtrlPrimaryCorner",
    )
    val primaryBg by animateColorAsState(
        targetValue = if (isPaused) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.primaryContainer
        },
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "DownloadCtrlPrimaryBg",
    )
    val primaryFg by animateColorAsState(
        targetValue = if (isPaused) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onPrimaryContainer
        },
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "DownloadCtrlPrimaryFg",
    )
    val primaryEnabled = !isPaused || resumeEnabled
    val primaryAlpha by animateFloatAsState(
        targetValue = if (primaryEnabled) 1f else 0.45f,
        animationSpec = tween(180),
        label = "DownloadCtrlPrimaryAlpha",
    )

    val rightArmed = deleteArmed
    val rightCorner by animateDpAsState(
        targetValue = if (rightArmed) 23.dp else 16.dp,
        animationSpec = spring(
            stiffness = Spring.StiffnessMedium,
            dampingRatio = Spring.DampingRatioNoBouncy,
        ),
        label = "DownloadCtrlRightCorner",
    )
    val rightBg by animateColorAsState(
        targetValue = if (rightArmed) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.errorContainer
        },
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "DownloadCtrlRightBg",
    )
    val rightFg by animateColorAsState(
        targetValue = if (rightArmed) {
            MaterialTheme.colorScheme.onError
        } else {
            MaterialTheme.colorScheme.onErrorContainer
        },
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "DownloadCtrlRightFg",
    )

    val rightState = when {
        deleteArmed -> 2
        !isPaused -> 0
        else -> 1
    }
    val primaryInteraction = remember { MutableInteractionSource() }
    val rightInteraction = remember { MutableInteractionSource() }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(btnH),
    ) {
        val totalW = maxWidth
        val gap = lerp(8.dp, 10.dp, morph)
        val halfW = ((totalW - gap) / 2f).coerceAtLeast(btnH)
        val compactPrimaryW = (totalW - btnH - gap).coerceAtLeast(btnH)

        val primaryW = lerp(halfW, compactPrimaryW, compactTrash)
        val rightW = lerp(halfW, btnH, compactTrash)
        val rightX = (totalW - rightW).coerceAtLeast(0.dp)
        val rightTextVis = 1f - sstepE(compactTrash, 0.05f, 0.70f)

        val primaryShape = RoundedCornerShape(primaryCorner)
        Row(
            modifier = Modifier
                .width(primaryW)
                .height(btnH)
                .align(Alignment.CenterStart)
                .tactilePressScale(primaryInteraction, enabled = primaryEnabled, pressedScale = 0.96f)
                .alpha(primaryAlpha)
                .clip(primaryShape)
                .background(primaryBg)
                .border(1.dp, primaryBg.edgeOutline(), primaryShape)
                .clickable(
                    interactionSource = primaryInteraction,
                    indication = androidx.compose.material3.ripple(),
                    enabled = primaryEnabled,
                ) {
                    haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                    if (isPaused) onResume() else onPause()
                }
                .padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AnimatedContent(
                targetState = isPaused,
                transitionSpec = {
                    (fadeIn(tween(180)) + scaleIn(initialScale = 0.88f, animationSpec = tween(180))) togetherWith
                        (fadeOut(tween(120)) + scaleOut(targetScale = 0.88f, animationSpec = tween(120))) using
                        SizeTransform(clip = false)
                },
                label = "DownloadCtrlPrimaryContent",
            ) { paused ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Icon(
                        imageVector = if (paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                        contentDescription = null,
                        tint = primaryFg,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = if (paused) resumeLabel else pauseLabel,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = primaryFg,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        val rightShape = RoundedCornerShape(rightCorner)
        Row(
            modifier = Modifier
                .offset { IntOffset(with(density) { rightX.roundToPx() }, 0) }
                .width(rightW)
                .height(btnH)
                .tactilePressScale(rightInteraction, pressedScale = 0.94f)
                .clip(rightShape)
                .background(rightBg)
                .border(1.dp, rightBg.edgeOutline(), rightShape)
                .clickable(
                    interactionSource = rightInteraction,
                    indication = androidx.compose.material3.ripple(),
                ) {
                    haptics.performHapticFeedback(
                        if (rightArmed) HapticFeedbackType.LongPress else HapticFeedbackType.ContextClick,
                    )
                    if (isPaused) onDelete() else onCancel()
                }
                .padding(horizontal = lerp(12.dp, 0.dp, compactTrash)),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AnimatedContent(
                targetState = rightState,
                transitionSpec = {
                    (scaleIn(initialScale = 0.65f, animationSpec = spring(stiffness = Spring.StiffnessMedium)) + fadeIn(tween(150))) togetherWith
                        (scaleOut(targetScale = 0.65f, animationSpec = tween(110)) + fadeOut(tween(110)))
                },
                label = "DownloadCtrlRightIcon",
            ) { state ->
                Icon(
                    imageVector = when (state) {
                        0 -> Icons.Rounded.Close
                        2 -> Icons.Rounded.Check
                        else -> Icons.Rounded.Delete
                    },
                    contentDescription = when (state) {
                        0 -> cancelLabel
                        2 -> confirmDeleteLabel
                        else -> deleteLabel
                    },
                    tint = rightFg,
                    modifier = Modifier.size(18.dp),
                )
            }

            if (rightTextVis > 0.01f) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clipToBounds()
                        .layout { measurable, constraints ->
                            val p = measurable.measure(constraints.copy(minWidth = 0))
                            val w = (p.width * rightTextVis).roundToInt().coerceAtLeast(0)
                            layout(w, p.height) {
                                p.placeRelative(0, 0)
                            }
                        }
                        .alpha(rightTextVis),
                ) {
                    Spacer(Modifier.width(7.dp))
                    AnimatedContent(
                        targetState = rightState,
                        transitionSpec = {
                            (fadeIn(tween(160)) + scaleIn(initialScale = 0.90f, animationSpec = tween(160))) togetherWith
                                (fadeOut(tween(110)) + scaleOut(targetScale = 0.90f, animationSpec = tween(110))) using
                                SizeTransform(clip = false)
                        },
                        label = "DownloadCtrlRightText",
                    ) { state ->
                        Text(
                            text = when (state) {
                                0 -> cancelLabel
                                2 -> confirmDeleteLabel
                                else -> deleteLabel
                            },
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = rightFg,
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