package org.leegar.updater.ui.media

import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.BitmapDrawable
import android.media.MediaPlayer
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.PathParser
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.leegar.updater.data.ProjectLogo
import org.leegar.updater.ui.LeeGarLogoGlow
import org.json.JSONObject
import java.io.File

/**
 * Renders the project hero logo from JSON:
 * - Static PNG/JPG/WebP (with or without alpha channel)
 * - Animated GIF / Animated WebP (with alpha support via ImageDecoder + AnimatedImageDrawable)
 * - Looping silent MP4 / WebM video (via MediaPlayer + TextureView)
 * - Graceful fallback to [LeeGarLogoGlow] if no URL is provided or loading fails
 */
@Composable
fun ProjectLogoView(
    logo: ProjectLogo?,
    showDefaultFallback: Boolean = true,
    cornerRadius: Dp = 18.dp,
    modifier: Modifier = Modifier,
) {
    val url = logo?.url?.trim().orEmpty()
    if (url.isEmpty()) {
        if (showDefaultFallback) {
            LeeGarLogoGlow(modifier = modifier.fillMaxWidth())
        }
        return
    }

    val resolvedType = remember(url, logo?.type) {
        resolveLogoKind(url, logo?.type ?: "auto")
    }
    val aspect = (logo?.aspectRatio ?: 3.2f).coerceIn(0.4f, 8.0f)

    when (resolvedType) {
        LogoKind.VIDEO -> {
            VideoLogoPlayer(
                url = url,
                loop = logo?.loop ?: true,
                aspectRatio = aspect,
                cornerRadius = cornerRadius,
                showDefaultFallback = showDefaultFallback,
                modifier = modifier,
            )
        }

        LogoKind.IMAGE_OR_GIF -> {
            ImageLogoRenderer(
                url = url,
                aspectRatio = aspect,
                cornerRadius = cornerRadius,
                showDefaultFallback = showDefaultFallback,
                modifier = modifier,
            )
        }
    }
}

private enum class LogoKind { IMAGE_OR_GIF, VIDEO }

private fun resolveLogoKind(url: String, declaredType: String): LogoKind {
    when (declaredType.lowercase().trim()) {
        "video", "mp4", "webm" -> return LogoKind.VIDEO
        "image", "gif", "png", "webp" -> return LogoKind.IMAGE_OR_GIF
    }
    val path = url.substringBefore('?').substringBefore('#').lowercase()
    return if (path.endsWith(".mp4") || path.endsWith(".webm") || path.endsWith(".mkv")) {
        LogoKind.VIDEO
    } else {
        LogoKind.IMAGE_OR_GIF
    }
}

@Composable
private fun ImageLogoRenderer(
    url: String,
    aspectRatio: Float,
    cornerRadius: Dp,
    showDefaultFallback: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val mediaRev = UpdaterMediaCache.mediaRevision
    var drawable by remember(url, mediaRev) { mutableStateOf(UpdaterMediaCache.peekDrawable(url, maxDimensionPx = 1440)) }
    var failed by remember(url, mediaRev) { mutableStateOf(false) }

    LaunchedEffect(url, mediaRev) {
        if (drawable != null) return@LaunchedEffect
        failed = false
        val loaded = UpdaterMediaCache.loadDrawable(context, url, maxDimensionPx = 1440)
        if (loaded != null) {
            drawable = loaded
        } else {
            failed = true
        }
    }

    DisposableEffect(drawable) {
        val anim = drawable as? AnimatedImageDrawable
        anim?.start()
        onDispose {
            anim?.stop()
        }
    }

    val shape = RoundedCornerShape(cornerRadius)
    when {
        drawable != null -> {
            val d = drawable!!
            val naturalAspect = remember(d) {
                if (d.intrinsicWidth > 0 && d.intrinsicHeight > 0) {
                    (d.intrinsicWidth.toFloat() / d.intrinsicHeight.toFloat()).coerceIn(0.4f, 8.0f)
                } else {
                    aspectRatio
                }
            }
            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .aspectRatio(naturalAspect)
                    .clip(shape)
                    .clipToBounds(),
                contentAlignment = Alignment.Center,
            ) {
                val bmp = (d as? BitmapDrawable)?.bitmap
                if (bmp != null && !bmp.isRecycled) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            ImageView(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                )
                                scaleType = ImageView.ScaleType.FIT_XY
                                adjustViewBounds = false
                                clipToOutline = true
                                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                                addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                                    override fun onViewAttachedToWindow(v: View) {
                                        ((v as ImageView).drawable as? AnimatedImageDrawable)?.let {
                                            if (!it.isRunning) it.start()
                                        }
                                    }
                                    override fun onViewDetachedFromWindow(v: View) = Unit
                                })
                            }
                        },
                        update = { iv ->
                            if (iv.drawable !== d) {
                                iv.setImageDrawable(d)
                            }
                            (d as? AnimatedImageDrawable)?.let {
                                if (!it.isRunning) it.start()
                            }
                        },
                    )
                }
            }
        }

        failed -> {
            if (showDefaultFallback) {
                LeeGarLogoGlow(modifier = modifier.fillMaxWidth())
            }
        }

        else -> {
            LogoShimmerPlaceholder(
                aspectRatio = aspectRatio,
                cornerRadius = cornerRadius,
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun VideoLogoPlayer(
    url: String,
    loop: Boolean,
    aspectRatio: Float,
    cornerRadius: Dp,
    showDefaultFallback: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mediaRev = UpdaterMediaCache.mediaRevision
    var videoFile by remember(url, mediaRev) { mutableStateOf(UpdaterMediaCache.peekVideoFile(url)) }
    var failed by remember(url, mediaRev) { mutableStateOf(false) }
    var resolvedAspect by remember(url) { mutableFloatStateOf(aspectRatio) }

    LaunchedEffect(url, mediaRev) {
        if (videoFile != null) return@LaunchedEffect
        failed = false
        val cached = UpdaterMediaCache.loadVideoFile(context, url)
        if (cached != null && cached.exists()) {
            videoFile = cached
        } else {
            failed = true
        }
    }

    val shape = RoundedCornerShape(cornerRadius)
    when {
        videoFile != null -> {
            val file = videoFile!!
            val playerHolder = remember(file.absolutePath) { VideoPlayerHolder() }

            DisposableEffect(lifecycleOwner, playerHolder) {
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_RESUME -> playerHolder.resume()
                        Lifecycle.Event.ON_PAUSE -> playerHolder.pause()
                        else -> Unit
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                    playerHolder.release()
                }
            }

            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .aspectRatio(resolvedAspect)
                    .clip(shape)
                    .clipToBounds(),
                contentAlignment = Alignment.Center,
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        TextureView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            )
                            isOpaque = false
                            clipToOutline = true
                            surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                                override fun onSurfaceTextureAvailable(
                                    st: SurfaceTexture,
                                    width: Int,
                                    height: Int,
                                ) {
                                    playerHolder.attachAndStart(
                                        this@apply,
                                        st,
                                        file,
                                        loop,
                                        width,
                                        height,
                                    ) { aspect ->
                                        resolvedAspect = aspect.coerceIn(0.4f, 8.0f)
                                    }
                                }

                                override fun onSurfaceTextureSizeChanged(
                                    st: SurfaceTexture,
                                    width: Int,
                                    height: Int,
                                ) {
                                    playerHolder.updateTransform(this@apply, width, height)
                                }

                                override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                                    playerHolder.release()
                                    return true
                                }

                                override fun onSurfaceTextureUpdated(st: SurfaceTexture) = Unit
                            }
                        }
                    },
                    update = { playerHolder.ensurePlaying() },
                )
            }
        }

        failed -> {
            if (showDefaultFallback) {
                LeeGarLogoGlow(modifier = modifier.fillMaxWidth())
            }
        }

        else -> {
            LogoShimmerPlaceholder(
                aspectRatio = aspectRatio,
                cornerRadius = cornerRadius,
                modifier = modifier,
            )
        }
    }
}

private class VideoPlayerHolder {
    private var mediaPlayer: MediaPlayer? = null
    private var surface: Surface? = null
    private var videoW = 0
    private var videoH = 0
    private var prepared = false
    private var pausedByLifecycle = false

    fun attachAndStart(
        view: TextureView,
        surfaceTexture: SurfaceTexture,
        file: File,
        loop: Boolean,
        viewW: Int,
        viewH: Int,
        onVideoAspect: ((Float) -> Unit)? = null,
    ) {
        release()
        val s = Surface(surfaceTexture)
        surface = s
        val mp = MediaPlayer()
        mediaPlayer = mp
        try {
            mp.setSurface(s)
            mp.setDataSource(file.absolutePath)
            mp.isLooping = loop
            mp.setVolume(0f, 0f)
            mp.setOnVideoSizeChangedListener { _, w, h ->
                videoW = w
                videoH = h
                if (w > 0 && h > 0) onVideoAspect?.invoke(w.toFloat() / h.toFloat())
                updateTransform(view, view.width.takeIf { it > 0 } ?: viewW, view.height.takeIf { it > 0 } ?: viewH)
            }
            mp.setOnCompletionListener { player ->
                if (loop && prepared && !pausedByLifecycle) {
                    runCatching {
                        player.seekTo(0)
                        player.start()
                    }
                }
            }
            mp.setOnErrorListener { _, _, _ -> true }
            mp.setOnPreparedListener {
                prepared = true
                it.isLooping = loop
                videoW = it.videoWidth
                videoH = it.videoHeight
                if (videoW > 0 && videoH > 0) onVideoAspect?.invoke(videoW.toFloat() / videoH.toFloat())
                updateTransform(view, view.width.takeIf { it > 0 } ?: viewW, view.height.takeIf { it > 0 } ?: viewH)
                if (!pausedByLifecycle) it.start()
            }
            mp.prepareAsync()
        } catch (_: Throwable) {
            release()
        }
    }

    fun updateTransform(view: TextureView, viewW: Int, viewH: Int) {
        if (videoW <= 0 || videoH <= 0 || viewW <= 0 || viewH <= 0) return
        val videoAspect = videoW.toFloat() / videoH.toFloat()
        val viewAspect = viewW.toFloat() / viewH.toFloat()
        val scaleX: Float
        val scaleY: Float
        if (videoAspect > viewAspect) {
            scaleX = videoAspect / viewAspect
            scaleY = 1f
        } else {
            scaleX = 1f
            scaleY = viewAspect / videoAspect
        }
        val matrix = Matrix().apply {
            setScale(scaleX, scaleY, viewW / 2f, viewH / 2f)
        }
        view.setTransform(matrix)
    }

    fun pause() {
        pausedByLifecycle = true
        runCatching {
            if (prepared && mediaPlayer?.isPlaying == true) mediaPlayer?.pause()
        }
    }

    fun resume() {
        pausedByLifecycle = false
        ensurePlaying()
    }

    fun ensurePlaying() {
        if (pausedByLifecycle) return
        runCatching {
            if (prepared && mediaPlayer?.isPlaying == false) mediaPlayer?.start()
        }
    }

    fun release() {
        prepared = false
        runCatching { mediaPlayer?.stop() }
        runCatching { mediaPlayer?.release() }
        mediaPlayer = null
        runCatching { surface?.release() }
        surface = null
    }
}

@Composable
private fun LogoShimmerPlaceholder(
    aspectRatio: Float,
    cornerRadius: Dp = 18.dp,
    modifier: Modifier = Modifier,
) {
    val pulse = rememberInfiniteTransition(label = "LogoPulse")
    val alpha by pulse.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(750),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "LogoPulseAlpha",
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio)
            .clip(RoundedCornerShape(cornerRadius))
            .alpha(alpha)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    )
}

private sealed interface ParsedMediaSpec {
    data class Remote(
        val url: String,
        val kind: LogoKind,
        val loop: Boolean = true,
    ) : ParsedMediaSpec

    data class Vector(
        val paths: List<Path>,
        val viewportW: Float?,
        val viewportH: Float?,
        val offsetX: Float = 0f,
        val offsetY: Float = 0f,
    ) : ParsedMediaSpec
}

private val SVG_PATH_D_REGEX = Regex("""\bd\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
private val ANDROID_PATHDATA_REGEX = Regex("""pathData\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
private val SVG_VIEWBOX_REGEX = Regex("""viewBox\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
private val ANDROID_VW_REGEX = Regex("""viewportWidth\s*=\s*["']([0-9.]+)["']""", RegexOption.IGNORE_CASE)
private val ANDROID_VH_REGEX = Regex("""viewportHeight\s*=\s*["']([0-9.]+)["']""", RegexOption.IGNORE_CASE)
private val RAW_PATHDATA_REGEX = Regex("""^[Mm][0-9.,\s+\-eELlHhVvCcSsQqTtAaZz]+$""")

private fun parseMediaSpec(rawSpec: String): ParsedMediaSpec? {
    val trimmed = rawSpec.trim()
    if (trimmed.isEmpty()) return null

    if (trimmed.startsWith("{")) {
        val obj = runCatching { JSONObject(trimmed) }.getOrNull()
        if (obj != null) {
            val pathStr = obj.optString("path").ifBlank {
                obj.optString("pathData").ifBlank { obj.optString("d") }
            }
            val svgStr = obj.optString("svg").ifBlank { obj.optString("vector") }
            if (svgStr.isNotBlank()) {
                parseXmlVector(svgStr)?.let { return it }
            }
            if (pathStr.isNotBlank()) {
                val p = runCatching { PathParser.createPathFromPathData(pathStr.trim()) }.getOrNull()
                if (p != null) {
                    val vw = obj.optDouble("viewportWidth", Double.NaN).takeIf { !it.isNaN() && it > 0 }?.toFloat()
                    val vh = obj.optDouble("viewportHeight", Double.NaN).takeIf { !it.isNaN() && it > 0 }?.toFloat()
                    return ParsedMediaSpec.Vector(listOf(p), vw, vh)
                }
            }
            val url = obj.optString("url").trim()
            if (url.isNotEmpty()) {
                val type = obj.optString("type", "auto")
                val loop = obj.optBoolean("loop", true)
                return ParsedMediaSpec.Remote(url, resolveLogoKind(url, type), loop)
            }
        }
    }

    if (trimmed.startsWith("vector:", ignoreCase = true) || trimmed.startsWith("path:", ignoreCase = true)) {
        val data = trimmed.substringAfter(':').trim()
        if (data.contains('<')) {
            parseXmlVector(data)?.let { return it }
        }
        val p = runCatching { PathParser.createPathFromPathData(data) }.getOrNull()
        if (p != null) return ParsedMediaSpec.Vector(listOf(p), null, null)
    }

    if (trimmed.contains("<svg", ignoreCase = true) ||
        trimmed.contains("<vector", ignoreCase = true) ||
        trimmed.contains("<path", ignoreCase = true)
    ) {
        parseXmlVector(trimmed)?.let { return it }
    }

    if (!trimmed.startsWith("http://", ignoreCase = true) &&
        !trimmed.startsWith("https://", ignoreCase = true) &&
        RAW_PATHDATA_REGEX.matches(trimmed)
    ) {
        val p = runCatching { PathParser.createPathFromPathData(trimmed) }.getOrNull()
        if (p != null) return ParsedMediaSpec.Vector(listOf(p), null, null)
    }

    return ParsedMediaSpec.Remote(
        url = trimmed,
        kind = resolveLogoKind(trimmed, "auto"),
        loop = true,
    )
}

private fun parseXmlVector(xml: String): ParsedMediaSpec.Vector? {
    val pathStrings = buildList {
        ANDROID_PATHDATA_REGEX.findAll(xml).forEach { add(it.groupValues[1].trim()) }
        if (isEmpty()) {
            SVG_PATH_D_REGEX.findAll(xml).forEach { add(it.groupValues[1].trim()) }
        }
    }.filter { it.isNotEmpty() }
    if (pathStrings.isEmpty()) return null

    val paths = pathStrings.mapNotNull { runCatching { PathParser.createPathFromPathData(it) }.getOrNull() }
    if (paths.isEmpty()) return null

    var ox = 0f
    var oy = 0f
    var vw = ANDROID_VW_REGEX.find(xml)?.groupValues?.getOrNull(1)?.toFloatOrNull()
    var vh = ANDROID_VH_REGEX.find(xml)?.groupValues?.getOrNull(1)?.toFloatOrNull()
    if (vw == null || vh == null) {
        SVG_VIEWBOX_REGEX.find(xml)?.groupValues?.getOrNull(1)?.trim()?.split(Regex("""[\s,]+"""))?.let { parts ->
            if (parts.size == 4) {
                ox = parts[0].toFloatOrNull() ?: 0f
                oy = parts[1].toFloatOrNull() ?: 0f
                vw = parts[2].toFloatOrNull()
                vh = parts[3].toFloatOrNull()
            }
        }
    }
    return ParsedMediaSpec.Vector(paths, vw, vh, ox, oy)
}

@Composable
fun ReleaseBannerView(
    bannerSpec: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val parsed = remember(bannerSpec) { parseMediaSpec(bannerSpec) } ?: return

    when (parsed) {
        is ParsedMediaSpec.Remote -> {
            if (parsed.kind == LogoKind.VIDEO) {
                val lifecycleOwner = LocalLifecycleOwner.current
                val mediaRev = UpdaterMediaCache.mediaRevision
                var videoFile by remember(parsed.url, mediaRev) { mutableStateOf(UpdaterMediaCache.peekVideoFile(parsed.url)) }
                var failed by remember(parsed.url, mediaRev) { mutableStateOf(false) }
                var videoAspect by remember(parsed.url) { mutableFloatStateOf(16f / 9f) }
                LaunchedEffect(parsed.url, mediaRev) {
                    if (videoFile != null) return@LaunchedEffect
                    failed = false
                    val f = UpdaterMediaCache.loadVideoFile(context, parsed.url)
                    if (f != null && f.exists()) videoFile = f else failed = true
                }
                val file = videoFile
                if (file != null && file.exists()) {
                    val holder = remember(file.absolutePath) { VideoPlayerHolder() }
                    DisposableEffect(lifecycleOwner, holder) {
                        val observer = LifecycleEventObserver { _, event ->
                            when (event) {
                                Lifecycle.Event.ON_RESUME -> holder.resume()
                                Lifecycle.Event.ON_PAUSE -> holder.pause()
                                else -> Unit
                            }
                        }
                        lifecycleOwner.lifecycle.addObserver(observer)
                        onDispose {
                            lifecycleOwner.lifecycle.removeObserver(observer)
                            holder.release()
                        }
                    }
                    Box(
                        modifier = modifier
                            .fillMaxWidth()
                            .aspectRatio(videoAspect)
                            .clipToBounds()
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                        contentAlignment = Alignment.Center,
                    ) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { ctx ->
                                TextureView(ctx).apply {
                                    layoutParams = ViewGroup.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                    )
                                    isOpaque = false
                                    clipToOutline = true
                                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                                        override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                                            holder.attachAndStart(this@apply, st, file, parsed.loop, w, h) { aspect ->
                                                videoAspect = aspect.coerceIn(0.4f, 8.0f)
                                            }
                                        }
                                        override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {
                                            holder.updateTransform(this@apply, w, h)
                                        }
                                        override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                                            holder.release()
                                            return true
                                        }
                                        override fun onSurfaceTextureUpdated(st: SurfaceTexture) = Unit
                                    }
                                }
                            },
                            update = { holder.ensurePlaying() },
                        )
                    }
                } else if (!failed) {
                    LogoShimmerPlaceholder(
                        aspectRatio = 16f / 9f,
                        cornerRadius = 0.dp,
                        modifier = modifier,
                    )
                }
            } else {
                val mediaRev = UpdaterMediaCache.mediaRevision
                var drawable by remember(parsed.url, mediaRev) { mutableStateOf(UpdaterMediaCache.peekDrawable(parsed.url, maxDimensionPx = 1440)) }
                var failed by remember(parsed.url, mediaRev) { mutableStateOf(false) }
                LaunchedEffect(parsed.url, mediaRev) {
                    if (drawable == null) {
                        failed = false
                        val loaded = UpdaterMediaCache.loadDrawable(context, parsed.url, maxDimensionPx = 1440)
                        if (loaded != null) drawable = loaded else failed = true
                    }
                }
                DisposableEffect(drawable) {
                    val anim = drawable as? AnimatedImageDrawable
                    anim?.start()
                    onDispose { anim?.stop() }
                }
                val d = drawable
                if (d != null) {
                    val naturalAspect = remember(d) {
                        if (d.intrinsicWidth > 0 && d.intrinsicHeight > 0) {
                            (d.intrinsicWidth.toFloat() / d.intrinsicHeight.toFloat()).coerceIn(0.4f, 8.0f)
                        } else {
                            16f / 9f
                        }
                    }
                    Box(
                        modifier = modifier
                            .fillMaxWidth()
                            .aspectRatio(naturalAspect)
                            .clipToBounds()
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                        contentAlignment = Alignment.Center,
                    ) {
                        val bmp = (d as? BitmapDrawable)?.bitmap
                        if (bmp != null && !bmp.isRecycled) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = null,
                                contentScale = ContentScale.FillWidth,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            AndroidView(
                                modifier = Modifier.fillMaxSize(),
                                factory = { ctx ->
                                    ImageView(ctx).apply {
                                        layoutParams = ViewGroup.LayoutParams(
                                            ViewGroup.LayoutParams.MATCH_PARENT,
                                            ViewGroup.LayoutParams.MATCH_PARENT,
                                        )
                                        scaleType = ImageView.ScaleType.FIT_XY
                                        adjustViewBounds = false
                                        clipToOutline = true
                                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                                        addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                                            override fun onViewAttachedToWindow(v: View) {
                                                ((v as ImageView).drawable as? AnimatedImageDrawable)?.let {
                                                    if (!it.isRunning) it.start()
                                                }
                                            }
                                            override fun onViewDetachedFromWindow(v: View) = Unit
                                        })
                                    }
                                },
                                update = { iv ->
                                    if (iv.drawable !== d) iv.setImageDrawable(d)
                                    (d as? AnimatedImageDrawable)?.let { if (!it.isRunning) it.start() }
                                },
                            )
                        }
                    }
                } else if (!failed) {
                    LogoShimmerPlaceholder(
                        aspectRatio = 16f / 9f,
                        cornerRadius = 0.dp,
                        modifier = modifier,
                    )
                }
            }
        }

        is ParsedMediaSpec.Vector -> {
            val tint = MaterialTheme.colorScheme.primary
            val vecAspect = remember(parsed) {
                val w = parsed.viewportW
                val h = parsed.viewportH
                if (w != null && h != null && w > 0f && h > 0f) {
                    (w / h).coerceIn(1.0f, 8.0f)
                } else {
                    16f / 9f
                }
            }
            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .aspectRatio(vecAspect)
                    .clipToBounds()
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                VectorSpecCanvas(
                    spec = parsed,
                    tint = tint,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                )
            }
        }
    }
}

@Composable
fun ReleaseIconBannerView(
    icoSpec: String?,
    tint: Color,
    iconSize: Dp,
    modifier: Modifier = Modifier,
    fallback: @Composable () -> Unit,
) {
    val specStr = icoSpec?.trim().orEmpty()
    val parsed = remember(specStr) { if (specStr.isNotEmpty()) parseMediaSpec(specStr) else null }
    if (parsed == null) {
        fallback()
        return
    }

    val context = LocalContext.current
    when (parsed) {
        is ParsedMediaSpec.Vector -> {
            VectorSpecCanvas(
                spec = parsed,
                tint = tint,
                modifier = modifier.size(iconSize),
            )
        }

        is ParsedMediaSpec.Remote -> {
            if (parsed.kind == LogoKind.VIDEO) {
                val lifecycleOwner = LocalLifecycleOwner.current
                val mediaRev = UpdaterMediaCache.mediaRevision
                var videoFile by remember(parsed.url, mediaRev) { mutableStateOf(UpdaterMediaCache.peekVideoFile(parsed.url)) }
                var failed by remember(parsed.url, mediaRev) { mutableStateOf(false) }
                LaunchedEffect(parsed.url, mediaRev) {
                    if (videoFile != null) return@LaunchedEffect
                    failed = false
                    val f = UpdaterMediaCache.loadVideoFile(context, parsed.url)
                    if (f != null && f.exists()) videoFile = f else failed = true
                }
                val file = videoFile
                if (file != null && file.exists()) {
                    val holder = remember(file.absolutePath) { VideoPlayerHolder() }
                    DisposableEffect(lifecycleOwner, holder) {
                        val observer = LifecycleEventObserver { _, event ->
                            when (event) {
                                Lifecycle.Event.ON_RESUME -> holder.resume()
                                Lifecycle.Event.ON_PAUSE -> holder.pause()
                                else -> Unit
                            }
                        }
                        lifecycleOwner.lifecycle.addObserver(observer)
                        onDispose {
                            lifecycleOwner.lifecycle.removeObserver(observer)
                            holder.release()
                        }
                    }
                    AndroidView(
                        modifier = modifier
                            .fillMaxSize()
                            .clip(CircleShape),
                        factory = { ctx ->
                            TextureView(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                )
                                isOpaque = false
                                clipToOutline = true
                                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                                    override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                                        holder.attachAndStart(this@apply, st, file, parsed.loop, w, h)
                                    }
                                    override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {
                                        holder.updateTransform(this@apply, w, h)
                                    }
                                    override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                                        holder.release()
                                        return true
                                    }
                                    override fun onSurfaceTextureUpdated(st: SurfaceTexture) = Unit
                                }
                            }
                        },
                        update = { holder.ensurePlaying() },
                    )
                } else {
                    fallback()
                }
            } else {
                val mediaRev = UpdaterMediaCache.mediaRevision
                var drawable by remember(parsed.url, mediaRev) { mutableStateOf(UpdaterMediaCache.peekDrawable(parsed.url, maxDimensionPx = 256)) }
                LaunchedEffect(parsed.url, mediaRev) {
                    if (drawable == null) {
                        drawable = UpdaterMediaCache.loadDrawable(context, parsed.url, maxDimensionPx = 256)
                    }
                }
                val d = drawable
                if (d != null) {
                    val bmp = (d as? BitmapDrawable)?.bitmap
                    if (bmp != null && !bmp.isRecycled) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = modifier
                                .fillMaxSize()
                                .clip(CircleShape),
                        )
                    } else {
                        DisposableEffect(d) {
                            val anim = d as? AnimatedImageDrawable
                            anim?.start()
                            onDispose { anim?.stop() }
                        }
                        AndroidView(
                            modifier = modifier
                                .fillMaxSize()
                                .clip(CircleShape),
                            factory = { ctx ->
                                ImageView(ctx).apply {
                                    layoutParams = ViewGroup.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                    )
                                    scaleType = ImageView.ScaleType.CENTER_CROP
                                    adjustViewBounds = false
                                    clipToOutline = true
                                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                                    addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                                        override fun onViewAttachedToWindow(v: View) {
                                            ((v as ImageView).drawable as? AnimatedImageDrawable)?.let {
                                                if (!it.isRunning) it.start()
                                            }
                                        }
                                        override fun onViewDetachedFromWindow(v: View) = Unit
                                    })
                                }
                            },
                            update = { iv ->
                                if (iv.drawable !== d) iv.setImageDrawable(d)
                                (d as? AnimatedImageDrawable)?.let { if (!it.isRunning) it.start() }
                            },
                        )
                    }
                } else {
                    fallback()
                }
            }
        }
    }
}

@Composable
private fun VectorSpecCanvas(
    spec: ParsedMediaSpec.Vector,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    val paint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
    }
    val combinedPath = remember(spec) {
        Path().apply {
            spec.paths.forEach { addPath(it) }
        }
    }
    val pathBounds = remember(combinedPath) {
        RectF().also { combinedPath.computeBounds(it, true) }
    }
    val drawPath = remember { Path() }
    val matrix = remember { Matrix() }

    Canvas(modifier = modifier) {
        val srcLeft = if (spec.viewportW != null) spec.offsetX else pathBounds.left
        val srcTop = if (spec.viewportH != null) spec.offsetY else pathBounds.top
        val srcW = (spec.viewportW ?: pathBounds.width()).coerceAtLeast(1f)
        val srcH = (spec.viewportH ?: pathBounds.height()).coerceAtLeast(1f)
        val scale = minOf(size.width / srcW, size.height / srcH)
        val dx = (size.width - srcW * scale) / 2f - srcLeft * scale
        val dy = (size.height - srcH * scale) / 2f - srcTop * scale

        matrix.reset()
        matrix.postScale(scale, scale)
        matrix.postTranslate(dx, dy)
        combinedPath.transform(matrix, drawPath)
        paint.color = tint.toArgb()

        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawPath(drawPath, paint)
        }
    }
}