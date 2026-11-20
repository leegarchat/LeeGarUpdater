package org.leegar.updater.ui.markdown

import android.graphics.drawable.AnimatedImageDrawable
import android.os.Build
import android.view.View
import android.widget.ImageView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrokenImage
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import org.leegar.updater.R
import org.leegar.updater.ui.media.UpdaterMediaCache
import org.leegar.updater.ui.pep.GraphicsLayerRecordingState
import org.leegar.updater.ui.pep.StudioCoordsRef
import org.leegar.updater.ui.pep.cardFlightBlurMask
import org.leegar.updater.ui.pep.edgeOutline
import org.leegar.updater.ui.pep.sstepE
import org.leegar.updater.ui.pep.unclippedBoundsInRoot
import kotlin.math.roundToInt

// =====================================================================
// Pure Kotlin Markdown AST & Parser (zero classpath risk in Soong)
// =====================================================================

private sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Paragraph(val text: String) : MdBlock
    data class BulletItem(val indent: Int, val text: String) : MdBlock
    data class OrderedItem(val indent: Int, val number: String, val text: String) : MdBlock
    data class Quote(val text: String) : MdBlock
    data class CodeBlock(val language: String, val code: String) : MdBlock
    data class ImageBlock(val alt: String, val url: String) : MdBlock
    data object Divider : MdBlock
}

private val IMG_ONLY_REGEX = Regex("""^!\[([^]]*)]\(([^)\s]+)(?:\s+"[^"]*")?\)$""")
private val HEADING_REGEX = Regex("""^(#{1,6})\s+(.+?)\s*#*$""")
private val BULLET_REGEX = Regex("""^(\s*)[-*+]\s+(.+)$""")
private val ORDERED_REGEX = Regex("""^(\s*)(\d+)[.)]\s+(.+)$""")
private val DIVIDER_REGEX = Regex("""^\s*([-*_])(?:\s*\1){2,}\s*$""")
private val INLINE_TOKEN_REGEX = Regex(
    """!\[([^]]*)]\(([^)\s]+)(?:\s+"[^"]*")?\)|""" + // 1,2: inline image
        """\[([^]]+)]\(([^)\s]+)\)|""" +              // 3,4: markdown link
        """`([^`]+)`|""" +                            // 5: inline code
        """\*\*(.+?)\*\*|__(.+?)__|""" +              // 6,7: bold
        """~~(.+?)~~|""" +                            // 8: strikethrough
        """\*(?!\s)(.+?)(?<!\s)\*|_(?!\s)(.+?)(?<!\s)_|""" + // 9,10: italic
        """(https?://[^\s<>)\]]+)""",                 // 11: autolink
)

private fun parseMarkdownBlocks(raw: String): List<MdBlock> {
    val lines = raw.replace("\r\n", "\n").lines()
    val out = ArrayList<MdBlock>()
    var i = 0

    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trim()

        if (trimmed.isEmpty()) {
            i++
            continue
        }

        // Fenced code block
        if (trimmed.startsWith("```")) {
            val lang = trimmed.removePrefix("```").trim()
            val codeBuf = StringBuilder()
            i++
            while (i < lines.size && !lines[i].trim().startsWith("```")) {
                if (codeBuf.isNotEmpty()) codeBuf.append('\n')
                codeBuf.append(lines[i])
                i++
            }
            if (i < lines.size) i++ // skip closing fence
            out.add(MdBlock.CodeBlock(lang, codeBuf.toString()))
            continue
        }

        // Standalone image ![alt](url)
        IMG_ONLY_REGEX.matchEntire(trimmed)?.let { m ->
            out.add(MdBlock.ImageBlock(alt = m.groupValues[1], url = m.groupValues[2]))
            i++
            continue
        }

        // Divider --- / *** / ___
        if (DIVIDER_REGEX.matches(trimmed)) {
            out.add(MdBlock.Divider)
            i++
            continue
        }

        // Heading # .. ######
        HEADING_REGEX.matchEntire(trimmed)?.let { m ->
            out.add(MdBlock.Heading(level = m.groupValues[1].length, text = m.groupValues[2]))
            i++
            continue
        }

        // Blockquote >
        if (trimmed.startsWith(">")) {
            val quoteLines = ArrayList<String>()
            while (i < lines.size && lines[i].trim().startsWith(">")) {
                quoteLines.add(lines[i].trim().removePrefix(">").trimStart())
                i++
            }
            out.add(MdBlock.Quote(quoteLines.joinToString("\n")))
            continue
        }

        // Bullet list item
        BULLET_REGEX.matchEntire(line)?.let { m ->
            val indent = (m.groupValues[1].length / 2).coerceIn(0, 3)
            out.add(MdBlock.BulletItem(indent, m.groupValues[2].trim()))
            i++
            continue
        }

        // Ordered list item
        ORDERED_REGEX.matchEntire(line)?.let { m ->
            val indent = (m.groupValues[1].length / 2).coerceIn(0, 3)
            out.add(MdBlock.OrderedItem(indent, m.groupValues[2], m.groupValues[3].trim()))
            i++
            continue
        }

        // Paragraph (merges consecutive plain lines, splits out embedded images)
        val paraBuf = StringBuilder()
        while (i < lines.size) {
            val cur = lines[i]
            val curTrim = cur.trim()
            if (curTrim.isEmpty() ||
                curTrim.startsWith("```") ||
                curTrim.startsWith(">") ||
                IMG_ONLY_REGEX.matches(curTrim) ||
                HEADING_REGEX.matches(curTrim) ||
                DIVIDER_REGEX.matches(curTrim) ||
                BULLET_REGEX.matches(cur) ||
                ORDERED_REGEX.matches(cur)
            ) {
                break
            }
            if (paraBuf.isNotEmpty()) paraBuf.append('\n')
            paraBuf.append(curTrim)
            i++
        }
        val paraText = paraBuf.toString()
        // Extract inline images cleanly as image blocks if present inside a paragraph
        val imgMatches = Regex("""!\[([^]]*)]\(([^)\s]+)(?:\s+"[^"]*")?\)""").findAll(paraText).toList()
        if (imgMatches.isEmpty()) {
            out.add(MdBlock.Paragraph(paraText))
        } else {
            var cursor = 0
            for (match in imgMatches) {
                val before = paraText.substring(cursor, match.range.first).trim()
                if (before.isNotEmpty()) out.add(MdBlock.Paragraph(before))
                out.add(MdBlock.ImageBlock(match.groupValues[1], match.groupValues[2]))
                cursor = match.range.last + 1
            }
            val after = paraText.substring(cursor).trim()
            if (after.isNotEmpty()) out.add(MdBlock.Paragraph(after))
        }
    }

    return out
}

@Composable
private fun rememberInlineAnnotatedString(
    text: String,
    linkColor: Color = MaterialTheme.colorScheme.primary,
    codeBg: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    codeFg: Color = MaterialTheme.colorScheme.onSurface,
): AnnotatedString {
    val altDefault = stringResource(R.string.md_image_alt)
    return remember(text, linkColor, codeBg, codeFg, altDefault) {
    buildAnnotatedString {
        var cursor = 0
        for (match in INLINE_TOKEN_REGEX.findAll(text)) {
            if (match.range.first > cursor) {
                append(text.substring(cursor, match.range.first))
            }
            when {
                // Inline image fallback label (standalone images are already split into ImageBlock)
                match.groups[2] != null -> {
                    val alt = match.groupValues[1].ifBlank { altDefault }
                    val url = match.groupValues[2]
                    val start = length
                    append(alt)
                    addLink(
                        LinkAnnotation.Url(
                            url = url,
                            styles = TextLinkStyles(
                                style = SpanStyle(
                                    color = linkColor,
                                    textDecoration = TextDecoration.Underline,
                                ),
                            ),
                        ),
                        start,
                        length,
                    )
                }

                // Markdown link [label](url)
                match.groups[3] != null && match.groups[4] != null -> {
                    val label = match.groupValues[3]
                    val url = match.groupValues[4]
                    val start = length
                    append(label)
                    addLink(
                        LinkAnnotation.Url(
                            url = url,
                            styles = TextLinkStyles(
                                style = SpanStyle(
                                    color = linkColor,
                                    fontWeight = FontWeight.Medium,
                                    textDecoration = TextDecoration.Underline,
                                ),
                            ),
                        ),
                        start,
                        length,
                    )
                }

                // Inline `code`
                match.groups[5] != null -> {
                    val start = length
                    append(" ${match.groupValues[5]} ")
                    addStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = codeBg,
                            color = codeFg,
                        ),
                        start,
                        length,
                    )
                }

                // Bold **text** or __text__
                match.groups[6] != null || match.groups[7] != null -> {
                    val content = match.groups[6]?.value ?: match.groups[7]!!.value
                    val start = length
                    append(content)
                    addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, length)
                }

                // Strikethrough ~~text~~
                match.groups[8] != null -> {
                    val start = length
                    append(match.groupValues[8])
                    addStyle(SpanStyle(textDecoration = TextDecoration.LineThrough), start, length)
                }

                // Italic *text* or _text_
                match.groups[9] != null || match.groups[10] != null -> {
                    val content = match.groups[9]?.value ?: match.groups[10]!!.value
                    val start = length
                    append(content)
                    addStyle(SpanStyle(fontStyle = FontStyle.Italic), start, length)
                }

                // Autolink https://...
                match.groups[11] != null -> {
                    val url = match.groupValues[11]
                    val start = length
                    append(url)
                    addLink(
                        LinkAnnotation.Url(
                            url = url,
                            styles = TextLinkStyles(
                                style = SpanStyle(
                                    color = linkColor,
                                    textDecoration = TextDecoration.Underline,
                                ),
                            ),
                        ),
                        start,
                        length,
                    )
                }
            }
            cursor = match.range.last + 1
        }
        if (cursor < text.length) {
            append(text.substring(cursor))
        }
    }
}
}

// =====================================================================
// Pure Compose Markdown Renderer
// =====================================================================

@Composable
fun ComposeMarkdown(
    markdown: String,
    modifier: Modifier = Modifier,
    activeFlightImageUrl: String? = null,
    onImageClick: ((url: String, alt: String, bounds: Rect, provider: () -> Rect) -> Unit)? = null,
) {
    val blocks = remember(markdown) { parseMarkdownBlocks(markdown) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Heading -> {
                    val style: TextStyle = when (block.level) {
                        1 -> MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        2 -> MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        3 -> MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        else -> MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    }
                    Text(
                        text = rememberInlineAnnotatedString(block.text),
                        style = style,
                        color = if (block.level <= 2) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        modifier = Modifier.padding(top = if (block.level <= 2) 6.dp else 2.dp),
                    )
                }

                is MdBlock.Paragraph -> {
                    Text(
                        text = rememberInlineAnnotatedString(block.text),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                is MdBlock.BulletItem -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = (block.indent * 14).dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(top = 8.dp, end = 10.dp)
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                        )
                        Text(
                            text = rememberInlineAnnotatedString(block.text),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                is MdBlock.OrderedItem -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = (block.indent * 14).dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Text(
                            text = "${block.number}.",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                        Text(
                            text = rememberInlineAnnotatedString(block.text),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                is MdBlock.Quote -> {
                    val quoteBg = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f)
                    val shape = RoundedCornerShape(14.dp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(shape)
                            .background(quoteBg)
                            .border(1.dp, quoteBg.edgeOutline(), shape)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .heightIn(min = 20.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = rememberInlineAnnotatedString(block.text),
                            style = MaterialTheme.typography.bodySmall,
                            fontStyle = FontStyle.Italic,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                is MdBlock.CodeBlock -> {
                    val codeBg = MaterialTheme.colorScheme.surfaceContainerLowest
                    val shape = RoundedCornerShape(16.dp)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(shape)
                            .background(codeBg)
                            .border(1.dp, codeBg.edgeOutline(), shape)
                            .padding(12.dp),
                    ) {
                        if (block.language.isNotBlank()) {
                            Text(
                                text = block.language.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(bottom = 4.dp),
                            )
                        }
                        Text(
                            text = block.code,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                        )
                    }
                }

                is MdBlock.ImageBlock -> {
                    MarkdownRemoteImage(
                        url = block.url,
                        alt = block.alt,
                        hiddenForFlight = activeFlightImageUrl == block.url,
                        onImageClick = onImageClick,
                    )
                }

                MdBlock.Divider -> {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun MarkdownRemoteImage(
    url: String,
    alt: String,
    hiddenForFlight: Boolean,
    onImageClick: ((String, String, Rect, () -> Rect) -> Unit)?,
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val coordsRef = remember(url) { StudioCoordsRef() }
    val mediaRev = UpdaterMediaCache.mediaRevision
    var retryNonce by remember(url, mediaRev) { mutableIntStateOf(0) }
    var drawable by remember(url, mediaRev) { mutableStateOf(UpdaterMediaCache.peekDrawable(url, maxDimensionPx = 1600)) }
    var failed by remember(url, mediaRev) { mutableStateOf(false) }
    var isRetrying by remember(url, mediaRev) { mutableStateOf(false) }

    LaunchedEffect(url, mediaRev, retryNonce) {
        if (drawable != null && retryNonce == 0) return@LaunchedEffect
        if (failed) {
            isRetrying = true
        }
        val loaded = UpdaterMediaCache.loadDrawable(context, url, maxDimensionPx = 1600)
        isRetrying = false
        if (loaded != null) {
            drawable = loaded
            failed = false
        } else {
            failed = true
        }
    }

    DisposableEffect(drawable) {
        val anim = drawable as? AnimatedImageDrawable
        anim?.start()
        onDispose { anim?.stop() }
    }

    val cardBg = MaterialTheme.colorScheme.surfaceContainerLowest
    val shape = RoundedCornerShape(18.dp)

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Card(
            shape = shape,
            colors = CardDefaults.cardColors(containerColor = cardBg),
            border = BorderStroke(1.dp, cardBg.edgeOutline()),
            modifier = Modifier
                .fillMaxWidth()
                .onPlaced { coordsRef.value = it }
                .onGloballyPositioned { coordsRef.value = it }
                .alpha(if (hiddenForFlight) 0f else 1f),
        ) {
            when {
                drawable != null -> {
                    val d = drawable!!
                    val aspect = remember(d) {
                        if (d.intrinsicWidth > 0 && d.intrinsicHeight > 0) {
                            (d.intrinsicWidth.toFloat() / d.intrinsicHeight.toFloat()).coerceIn(0.6f, 3.2f)
                        } else {
                            16f / 9f
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 260.dp)
                            .aspectRatio(aspect)
                            .clickable(enabled = onImageClick != null && !hiddenForFlight) {
                                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                                val rect = coordsRef.value
                                    ?.takeIf { it.isAttached }
                                    ?.unclippedBoundsInRoot()
                                    ?: Rect.Zero
                                onImageClick?.invoke(url, alt, rect) {
                                    coordsRef.value
                                        ?.takeIf { it.isAttached }
                                        ?.unclippedBoundsInRoot()
                                        ?: rect
                                }
                            },
                    ) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { ctx ->
                                ImageView(ctx).apply {
                                    scaleType = ImageView.ScaleType.FIT_CENTER
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
                                (d as? AnimatedImageDrawable)?.let {
                                    if (!it.isRunning) it.start()
                                }
                            },
                        )
                    }
                }

                failed -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !isRetrying) { retryNonce++ }
                            .padding(horizontal = 16.dp, vertical = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.BrokenImage,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            text = alt.ifBlank { stringResource(R.string.md_image_failed) },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (isRetrying) {
                            androidx.compose.material3.CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp),
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = stringResource(R.string.desc_retry),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }

                else -> {
                    val pulse = rememberInfiniteTransition(label = "MdImgPulse")
                    val alpha by pulse.animateFloat(
                        initialValue = 0.25f,
                        targetValue = 0.6f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(700),
                            repeatMode = RepeatMode.Reverse,
                        ),
                        label = "MdImgPulseAlpha",
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                            .alpha(alpha)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    )
                }
            }
        }

        if (alt.isNotBlank() && drawable != null) {
            Text(
                text = alt,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 6.dp),
            )
        }
    }
}

// =====================================================================
// Level-2 1:1 Flight Image Viewer Pseudo-Modal (for Changelog Images)
// =====================================================================

@Stable
data class ImageFlightTarget(
    val url: String,
    val alt: String,
    val source: Rect = Rect.Zero,
    val sourceBoundsProvider: () -> Rect = { source },
    val sessionId: Long = System.nanoTime(),
)

@Composable
fun MarkdownImageFlightOverlay(
    target: ImageFlightTarget?,
    backdropBlur: GraphicsLayerRecordingState,
    onProgress: (Float) -> Unit = {},
    onClose: () -> Unit,
) {
    val flight = target ?: return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    val anim = remember(flight.sessionId) { Animatable(0f) }
    var closing by remember(flight.sessionId) { mutableStateOf(false) }
    var drawable by remember(flight.sessionId, flight.url) {
        mutableStateOf(UpdaterMediaCache.peekDrawable(flight.url, maxDimensionPx = 2048))
    }

    var zoomScale by remember(flight.sessionId) { mutableFloatStateOf(1f) }
    var panOffset by remember(flight.sessionId) { mutableStateOf(Offset.Zero) }
    val overlayCoordsRef = remember(flight.sessionId) { StudioCoordsRef() }

    LaunchedEffect(flight.sessionId, flight.url) {
        if (drawable == null) {
            drawable = UpdaterMediaCache.loadDrawable(context, flight.url, maxDimensionPx = 2048)
        }
    }

    DisposableEffect(drawable) {
        val animDrawable = drawable as? AnimatedImageDrawable
        animDrawable?.start()
        onDispose { animDrawable?.stop() }
    }

    LaunchedEffect(flight.sessionId) {
        withFrameNanos { }
        if (!closing) {
            anim.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    stiffness = 120f,
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    visibilityThreshold = 0.002f,
                ),
            )
        }
    }

    fun closeAnimated() {
        if (closing) return
        closing = true
        zoomScale = 1f
        panOffset = Offset.Zero
        scope.launch {
            anim.stop()
            try {
                anim.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(
                        stiffness = 165f,
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        visibilityThreshold = 0.005f,
                    ),
                )
            } finally {
                onProgress(0f)
                onClose()
            }
        }
    }

    BackHandler(enabled = !closing) { closeAnimated() }

    val t = sstepE(anim.value.coerceIn(0f, 1f), 0f, 1f)
    DisposableEffect(t) {
        onProgress(t)
        onDispose {}
    }

    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(10f)
            .onPlaced { overlayCoordsRef.value = it }
            .onGloballyPositioned { overlayCoordsRef.value = it },
    ) {
        val rootWpx = with(density) { maxWidth.toPx() }
        val rootHpx = with(density) { maxHeight.toPx() }

        // Blurred backdrop
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = sstepE(t, 0.1f, 0.85f) }
                    .cardFlightBlurMask(
                        state = backdropBlur,
                        coordsProvider = { overlayCoordsRef.value },
                        blurRadius = 32.dp,
                    ),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.72f * sstepE(t, 0.05f, 0.85f)))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { closeAnimated() },
                ),
        )

        val liveSrc = flight.sourceBoundsProvider().let {
            if (it.width > 1f && it.height > 1f) it else flight.source
        }
        val endW = (maxWidth - 24.dp).coerceAtLeast(120.dp)
        val endH = (maxHeight - statusTop - navBottom - 120.dp).coerceAtLeast(180.dp)
        val startW = if (liveSrc.width > 1f) with(density) { liveSrc.width.roundToInt().toDp() } else endW
        val startH = if (liveSrc.height > 1f) with(density) { liveSrc.height.roundToInt().toDp() } else endH

        val curW = lerp(startW, endW, t)
        val curH = lerp(startH, endH, t)
        val dx = if (liveSrc.width > 1f) (liveSrc.center.x - rootWpx / 2f) * (1f - t) else 0f
        val dy = if (liveSrc.height > 1f) (liveSrc.center.y - rootHpx / 2f) * (1f - t) else 0f
        val shape = RoundedCornerShape(lerp(18.dp, 22.dp, t))

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .width(curW)
                .height(curH)
                .graphicsLayer {
                    translationX = dx + panOffset.x
                    translationY = dy + panOffset.y
                    scaleX = zoomScale
                    scaleY = zoomScale
                }
                .clip(shape)
                .pointerInput(flight.sessionId) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val nextScale = (zoomScale * zoom).coerceIn(1f, 4f)
                        zoomScale = nextScale
                        panOffset = if (nextScale > 1.02f) {
                            Offset(panOffset.x + pan.x, panOffset.y + pan.y)
                        } else {
                            Offset.Zero
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            drawable?.let { d ->
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        ImageView(ctx).apply {
                            scaleType = ImageView.ScaleType.FIT_CENTER
                            setBackgroundColor(android.graphics.Color.TRANSPARENT)
                        }
                    },
                    update = { iv ->
                        if (iv.drawable !== d) iv.setImageDrawable(d)
                        (d as? AnimatedImageDrawable)?.let { if (!it.isRunning) it.start() }
                    },
                )
            }
        }

        // Top close pill + bottom caption
        if (t > 0.35f) {
            val chromeAlpha = sstepE(t, 0.35f, 0.9f)
            val btnBg = Color.Black.copy(alpha = 0.5f)
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = statusTop + 14.dp, end = 16.dp)
                    .alpha(chromeAlpha)
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(btnBg)
                    .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape)
                    .clickable { closeAnimated() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.desc_close),
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }

            if (flight.alt.isNotBlank()) {
                Text(
                    text = flight.alt,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.9f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(
                            start = 24.dp,
                            end = 24.dp,
                            bottom = navBottom + 20.dp,
                        )
                        .alpha(chromeAlpha)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
    }
}