package org.leegar.updater.ui.pep

import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.Shader
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.annotation.RequiresApi
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CanvasHolder
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.abs

/**
 * Global runtime glassmorphism configuration (driven live from Settings sliders).
 * radiusDp: 0f..150f (0 = crystal clear glass without RenderEffect blur)
 * tintAlpha: 0f..1f (opacity of frosted tint over blurred backdrop)
 *
 * The literal defaults below mirror config.xml (def_blur_radius_dp /
 * def_blur_tint_percent) for contexts without Resources access; use
 * [BlurConfigDefaults.fromResources] whenever a Resources instance is available.
 */
@Immutable
data class BlurConfig(
    val radiusDp: Float = 28f,
    val tintAlpha: Float = 0.72f,
)

object BlurConfigDefaults {
    fun fromResources(res: android.content.res.Resources): BlurConfig = BlurConfig(
        radiusDp = res.getInteger(org.leegar.updater.R.integer.def_blur_radius_dp).toFloat()
            .coerceIn(0f, 150f),
        tintAlpha = (res.getInteger(org.leegar.updater.R.integer.def_blur_tint_percent) / 100f)
            .coerceIn(0f, 1f),
    )

    fun wavyAmplitudeDefault(res: android.content.res.Resources): Float =
        (res.getInteger(org.leegar.updater.R.integer.def_wavy_amplitude_percent) / 100f)
            .coerceIn(0f, 1f)
}

val LocalBlurConfig = compositionLocalOf { BlurConfig() }

internal fun sstepE(x: Float, edge0: Float, edge1: Float): Float {
    if (edge1 <= edge0) return if (x >= edge1) 1f else 0f
    val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

@Stable
internal class StudioCoordsRef(var value: LayoutCoordinates? = null)

internal fun LayoutCoordinates.unclippedBoundsInRoot(): Rect {
    if (!isAttached) return Rect.Zero
    val pos = positionInRoot()
    return Rect(
        offset = Offset(pos.x, pos.y),
        size = Size(size.width.toFloat(), size.height.toFloat()),
    )
}

internal fun Color.edgeOutline(alphaBoost: Float = 1f): Color {
    val isLight = luminance() > 0.45f
    return if (isLight) {
        Color.Black.copy(alpha = (0.09f * alphaBoost).coerceIn(0f, 0.35f))
    } else {
        Color.White.copy(alpha = (0.11f * alphaBoost).coerceIn(0f, 0.35f))
    }
}

@Stable
class GraphicsLayerRecordingState(
    val layer: GraphicsLayer,
) {
    val sharedBlurNode: RenderNode? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) RenderNode("SharedFullScreenBlur") else null
    var lastAppliedBlurRadius: Float = -1f

    var contentBounds by mutableStateOf(Rect.Zero)
        internal set

    var hasRecorded by mutableStateOf(false)
        internal set

    var frameVersion by mutableIntStateOf(0)
        internal set
}

@Composable
fun rememberGraphicsLayerRecordingState(): GraphicsLayerRecordingState {
    val layer = rememberGraphicsLayer()
    return remember(layer) { GraphicsLayerRecordingState(layer) }
}

fun Modifier.recordLayer(
    state: GraphicsLayerRecordingState,
    backgroundColor: Color = Color.Transparent,
    topEdgeFadeHeight: Dp = 28.dp,
    bottomEdgeFadeHeight: Dp = 20.dp,
): Modifier = composed {
    val canvasHolder = remember { CanvasHolder() }
    val config = LocalBlurConfig.current
    val blurRadiusPx = with(LocalDensity.current) { config.radiusDp.coerceIn(0f, 150f).dp.toPx() }

    this
        .onPlaced { coords ->
            val b = coords.unclippedBoundsInRoot()
            if (b.width > 1f && b.height > 1f) state.contentBounds = b
        }
        .onGloballyPositioned { coords ->
            if (coords.isAttached) {
                val b = coords.unclippedBoundsInRoot()
                if (b.width > 1f && b.height > 1f) state.contentBounds = b
            }
        }
        .drawWithContent {
            val w = size.width.toInt()
            val h = size.height.toInt()
            if (w > 0 && h > 0) {
                val topFadePx = topEdgeFadeHeight.toPx().coerceAtMost(h * 0.25f)
                val bottomFadePx = bottomEdgeFadeHeight.toPx().coerceAtMost(h * 0.25f)

                try {
                    state.layer.record(size = IntSize(w, h)) {
                        this@drawWithContent.drawContent()
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && blurRadiusPx > 0.5f) {
                        val blurNode = state.sharedBlurNode!!
                        blurNode.setPosition(0, 0, w, h)
                        if (abs(state.lastAppliedBlurRadius - blurRadiusPx) > 0.25f) {
                            blurNode.setRenderEffect(
                                RenderEffect.createBlurEffect(
                                    blurRadiusPx,
                                    blurRadiusPx,
                                    Shader.TileMode.CLAMP,
                                ),
                            )
                            state.lastAppliedBlurRadius = blurRadiusPx
                        }
                        val recCanvas = blurNode.beginRecording(w, h)
                        canvasHolder.drawInto(recCanvas) {
                            val orig = drawContext.canvas
                            drawContext.canvas = this@drawInto
                            try {
                                if (backgroundColor.alpha > 0f) {
                                    drawRect(color = backgroundColor)
                                }
                                this@drawWithContent.drawLayer(state.layer)
                                if (backgroundColor.alpha > 0f && topFadePx > 0f) {
                                    drawRect(
                                        brush = Brush.verticalGradient(
                                            colors = listOf(backgroundColor, backgroundColor.copy(alpha = 0f)),
                                            startY = 0f,
                                            endY = topFadePx,
                                        ),
                                        topLeft = Offset.Zero,
                                        size = Size(size.width, topFadePx),
                                    )
                                }
                                if (backgroundColor.alpha > 0f && bottomFadePx > 0f) {
                                    val startY = size.height - bottomFadePx
                                    drawRect(
                                        brush = Brush.verticalGradient(
                                            colors = listOf(backgroundColor.copy(alpha = 0f), backgroundColor),
                                            startY = startY,
                                            endY = size.height,
                                        ),
                                        topLeft = Offset(0f, startY),
                                        size = Size(size.width, bottomFadePx),
                                    )
                                }
                            } finally {
                                drawContext.canvas = orig
                            }
                        }
                        blurNode.endRecording()
                    }
                    state.hasRecorded = true
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                drawLayer(state.layer)
            } else {
                drawContent()
            }
        }
}

@RequiresApi(Build.VERSION_CODES.S)
private fun Modifier.renderLayerAt(
    state: GraphicsLayerRecordingState,
    regionProvider: () -> Rect,
    scaleProvider: () -> Float = { 1f },
): Modifier = this.drawBehind {
    val blurNode = state.sharedBlurNode ?: return@drawBehind
    if (!blurNode.hasDisplayList()) return@drawBehind
    val region = regionProvider()
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

private fun frostedVeilAlpha(blurRadiusPx: Float, minVeilAlpha: Float): Float {
    val t = ((20f - blurRadiusPx) / 20f).coerceIn(0f, 1f)
    return minVeilAlpha + (1f - minVeilAlpha) * t
}

fun Modifier.cardFlightBlurMask(
    state: GraphicsLayerRecordingState,
    coordsProvider: () -> LayoutCoordinates?,
    blurRadius: Dp? = null,
): Modifier = this
    .graphicsLayer { clip = true }
    .drawBehind {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return@drawBehind
        val blurNode = state.sharedBlurNode ?: return@drawBehind
        if (!blurNode.hasDisplayList()) return@drawBehind
        val coords = coordsProvider()?.takeIf { it.isAttached } ?: return@drawBehind
        val pos = coords.positionInRoot()
        val srcOrigin = state.contentBounds.topLeft
        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            val saveCount = native.save()
            native.translate(srcOrigin.x - pos.x, srcOrigin.y - pos.y)
            native.drawRenderNode(blurNode)
            native.restoreToCount(saveCount)
        }
    }

@Composable
fun TopBarBlurOverlay(
    topBarHeight: Dp,
    blurState: GraphicsLayerRecordingState,
    modifier: Modifier = Modifier,
    morphBlurState: GraphicsLayerRecordingState? = null,
    morphActive: Boolean = false,
    tintBoost: Float = 0f,
    blurRadius: Dp? = null,
) {
    val config = LocalBlurConfig.current
    val resolvedRadiusPx = with(LocalDensity.current) {
        (blurRadius ?: config.radiusDp.coerceIn(0f, 150f).dp).toPx()
    }
    val surfaceColor = MaterialTheme.colorScheme.surfaceContainer
    val scrim = MaterialTheme.colorScheme.scrim
    val veilAlpha = frostedVeilAlpha(
        blurRadiusPx = resolvedRadiusPx,
        minVeilAlpha = config.tintAlpha.coerceIn(0f, 1f),
    )
    var region by remember { mutableStateOf(Rect.Zero) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(topBarHeight)
            .zIndex(2f),
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && resolvedRadiusPx > 0.5f) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(surfaceColor),
            )

            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer { clip = true }
                    .onPlaced { coords -> region = coords.boundsInRoot() }
                    .onGloballyPositioned { coords -> region = coords.boundsInRoot() }
                    .renderLayerAt(
                        state = blurState,
                        regionProvider = { region },
                    ),
            )

            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(surfaceColor.copy(alpha = veilAlpha)),
            )

            if (morphActive && morphBlurState != null) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .graphicsLayer { clip = true }
                        .renderLayerAt(
                            state = morphBlurState,
                            regionProvider = { region },
                        ),
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(surfaceColor.copy(alpha = veilAlpha)),
                )
                if (tintBoost > 0.003f) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(scrim.copy(alpha = tintBoost.coerceIn(0f, 1f))),
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(surfaceColor),
            )
        }
    }
}

@Composable
fun TopStatusVeil(
    backgroundColor: Color,
    veilHeight: Dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(veilHeight)
            .zIndex(3f)
            .background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.0f to backgroundColor,
                        0.55f to backgroundColor.copy(alpha = 0.4f),
                        0.8f to backgroundColor.copy(alpha = 0.2f),
                        1.0f to backgroundColor.copy(alpha = 0f),
                    ),
                ),
            ),
    )
}