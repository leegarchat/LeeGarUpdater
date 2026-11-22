package org.leegar.updater.ui.markdown

import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.drawable.AnimatedImageDrawable
import android.os.Build
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
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
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.leegar.updater.R
import org.leegar.updater.ui.media.UpdaterMediaCache
import org.leegar.updater.ui.pep.GraphicsLayerRecordingState
import org.leegar.updater.ui.pep.LocalContentHorizontalInset
import org.leegar.updater.ui.pep.StudioCoordsRef
import org.leegar.updater.ui.pep.cardFlightBlurMask
import org.leegar.updater.ui.pep.edgeOutline
import org.leegar.updater.ui.pep.sstepE
import org.leegar.updater.ui.pep.unclippedBoundsInRoot
import kotlin.math.roundToInt

// =====================================================================
// Pure Kotlin Markdown AST & Parser (zero classpath risk in Soong)
// =====================================================================

private enum class TableAlign { START, CENTER, END }

private sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Paragraph(val text: String) : MdBlock
    data class BulletItem(val indent: Int, val text: String) : MdBlock
    data class TaskItem(val indent: Int, val checked: Boolean, val text: String) : MdBlock
    data class OrderedItem(val indent: Int, val number: String, val text: String) : MdBlock
    data class Quote(val text: String) : MdBlock
    data class CodeBlock(val language: String, val code: String) : MdBlock
    data class ImageBlock(val alt: String, val url: String) : MdBlock
    data class Table(
        val headers: List<String>,
        val alignments: List<TableAlign>,
        val rows: List<List<String>>,
    ) : MdBlock
    data object Divider : MdBlock
}

private val IMG_ONLY_REGEX = Regex("""^!\[([^]]*)]\(([^)\s]+)(?:\s+"[^"]*")?\)$""")
private val INLINE_IMG_SPLIT_REGEX = Regex("""!\[([^]]*)]\(([^)\s]+)(?:\s+"[^"]*")?\)""")
private val FENCE_START_REGEX = Regex("""^(`{3,}|~{3,})\s*(.*)$""")
private val HEADING_REGEX = Regex("""^(#{1,6})\s+(.+?)\s*#*$""")
private val TASK_REGEX = Regex("""^(\s*)[-*+]\s+\[([ xX])]\s+(.+)$""")
private val BULLET_REGEX = Regex("""^(\s*)[-*+]\s+(.+)$""")
private val ORDERED_REGEX = Regex("""^(\s*)(\d+)[.)]\s+(.+)$""")
private val DIVIDER_REGEX = Regex("""^\s*([-*_])(?:\s*\1){2,}\s*$""")
private val TABLE_SEP_REGEX = Regex("""^\|?\s*:?-{3,}:?\s*(\|\s*:?-{3,}:?\s*)*\|?\s*$""")
private val HTML_BR_REGEX = Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE)

private fun isTableHeaderStart(lines: List<String>, index: Int): Boolean {
    if (index + 1 >= lines.size) return false
    val header = lines[index].trim()
    val sep = lines[index + 1].trim()
    if (!header.contains('|')) return false
    if (!TABLE_SEP_REGEX.matches(sep)) return false
    return sep.contains('|') || header.startsWith('|')
}

private fun parseTableCells(line: String): List<String> {
    var s = line.trim()
    if (s.startsWith('|')) s = s.substring(1)
    if (s.endsWith('|') && !s.endsWith("\\|")) s = s.dropLast(1)
    val cells = ArrayList<String>()
    val cur = StringBuilder()
    var i = 0
    while (i < s.length) {
        if (s[i] == '\\' && i + 1 < s.length && s[i + 1] == '|') {
            cur.append('|')
            i += 2
        } else if (s[i] == '|') {
            cells.add(cur.toString().trim())
            cur.setLength(0)
            i++
        } else {
            cur.append(s[i])
            i++
        }
    }
    cells.add(cur.toString().trim())
    return cells
}

private fun parseTableAlignments(sepLine: String): List<TableAlign> =
    parseTableCells(sepLine).map { cell ->
        val c = cell.trim()
        val left = c.startsWith(':')
        val right = c.endsWith(':')
        when {
            left && right -> TableAlign.CENTER
            right -> TableAlign.END
            else -> TableAlign.START
        }
    }

private fun isListItemStart(line: String): Boolean =
    TASK_REGEX.matches(line) || BULLET_REGEX.matches(line) || ORDERED_REGEX.matches(line)

// Boundary for a list item's continuation lines: blank line or the start of
// any other block (including a nested or sibling list item, which the outer
// loop then handles as its own block).
private fun isListItemBoundary(lines: List<String>, i: Int): Boolean {
    val curTrim = lines[i].trim()
    return curTrim.isEmpty() ||
        FENCE_START_REGEX.matches(curTrim) ||
        curTrim.startsWith(">") ||
        IMG_ONLY_REGEX.matches(curTrim) ||
        HEADING_REGEX.matches(curTrim) ||
        DIVIDER_REGEX.matches(curTrim) ||
        isTableHeaderStart(lines, i) ||
        isListItemStart(lines[i])
}

// Joins directly-adjacent continuation lines (indented or lazy) into a list
// item's text. Returns the joined text and the index of the first
// non-continuation line.
private fun consumeItemContinuation(lines: List<String>, start: Int, first: String): Pair<String, Int> {
    val buf = StringBuilder(first)
    var i = start
    while (i < lines.size && !isListItemBoundary(lines, i)) {
        if (buf.isNotEmpty()) buf.append('\n')
        buf.append(lines[i].trim())
        i++
    }
    return buf.toString() to i
}

private fun parseMarkdownBlocks(raw: String): List<MdBlock> {
    val lines = raw.replace("\r\n", "\n").replace('\r', '\n').lines()
    val out = ArrayList<MdBlock>()
    var i = 0

    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trim()

        if (trimmed.isEmpty()) {
            i++
            continue
        }

        // Fenced code block (``` or ~~~)
        FENCE_START_REGEX.matchEntire(trimmed)?.let { m ->
            val fence = m.groupValues[1]
            val fenceChar = fence[0]
            val fenceLen = fence.length
            val lang = m.groupValues[2].trim()
            val codeBuf = StringBuilder()
            i++
            while (i < lines.size) {
                val closeTrim = lines[i].trim()
                if (closeTrim.length >= fenceLen && closeTrim.all { it == fenceChar }) {
                    i++
                    break
                }
                if (codeBuf.isNotEmpty()) codeBuf.append('\n')
                codeBuf.append(lines[i])
                i++
            }
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

        // GFM Table
        if (isTableHeaderStart(lines, i)) {
            val headers = parseTableCells(trimmed)
            val alignments = parseTableAlignments(lines[i + 1].trim())
            i += 2
            val rows = ArrayList<List<String>>()
            while (i < lines.size) {
                val rowTrim = lines[i].trim()
                if (rowTrim.isEmpty() || !rowTrim.contains('|') ||
                    FENCE_START_REGEX.matches(rowTrim) ||
                    HEADING_REGEX.matches(rowTrim) ||
                    DIVIDER_REGEX.matches(rowTrim)
                ) {
                    break
                }
                rows.add(parseTableCells(rowTrim))
                i++
            }
            out.add(MdBlock.Table(headers, alignments, rows))
            continue
        }

        // Blockquote >
        if (trimmed.startsWith(">")) {
            val quoteLines = ArrayList<String>()
            while (i < lines.size && lines[i].trim().startsWith(">")) {
                var q = lines[i].trim().removePrefix(">")
                if (q.startsWith(" ")) q = q.substring(1)
                quoteLines.add(q)
                i++
            }
            out.add(MdBlock.Quote(quoteLines.joinToString("\n")))
            continue
        }

        // Task list item - [x] / - [ ]
        TASK_REGEX.matchEntire(line)?.let { m ->
            val indent = (m.groupValues[1].length / 2).coerceIn(0, 3)
            val checked = m.groupValues[2].equals("x", ignoreCase = true)
            i++
            val (text, next) = consumeItemContinuation(lines, i, m.groupValues[3].trim())
            i = next
            out.add(MdBlock.TaskItem(indent, checked, text))
            continue
        }

        // Bullet list item
        BULLET_REGEX.matchEntire(line)?.let { m ->
            val indent = (m.groupValues[1].length / 2).coerceIn(0, 3)
            i++
            val (text, next) = consumeItemContinuation(lines, i, m.groupValues[2].trim())
            i = next
            out.add(MdBlock.BulletItem(indent, text))
            continue
        }

        // Ordered list item
        ORDERED_REGEX.matchEntire(line)?.let { m ->
            val indent = (m.groupValues[1].length / 2).coerceIn(0, 3)
            i++
            val (text, next) = consumeItemContinuation(lines, i, m.groupValues[3].trim())
            i = next
            out.add(MdBlock.OrderedItem(indent, m.groupValues[2], text))
            continue
        }

        // Paragraph (merges consecutive plain lines, splits out embedded images)
        val paraBuf = StringBuilder()
        while (i < lines.size) {
            val cur = lines[i]
            val curTrim = cur.trim()
            if (curTrim.isEmpty() ||
                FENCE_START_REGEX.matches(curTrim) ||
                curTrim.startsWith(">") ||
                IMG_ONLY_REGEX.matches(curTrim) ||
                HEADING_REGEX.matches(curTrim) ||
                DIVIDER_REGEX.matches(curTrim) ||
                isTableHeaderStart(lines, i) ||
                TASK_REGEX.matches(cur) ||
                BULLET_REGEX.matches(cur) ||
                ORDERED_REGEX.matches(cur)
            ) {
                break
            }
            if (paraBuf.isNotEmpty()) paraBuf.append('\n')
            paraBuf.append(curTrim)
            i++
        }
        val paraText = HTML_BR_REGEX.replace(paraBuf.toString(), "\n")
        val imgMatches = INLINE_IMG_SPLIT_REGEX.findAll(paraText).toList()
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

private const val ESCAPABLE_MD_CHARS = "\\`*_{}[]()#+-.!~|<>"

private fun findClosingBackticks(text: String, start: Int, count: Int): Int {
    var i = start
    while (i < text.length) {
        if (text[i] == '`') {
            var run = 0
            while (i + run < text.length && text[i + run] == '`') run++
            if (run == count) return i
            i += run
        } else {
            i++
        }
    }
    return -1
}

private fun findMatchingBracket(text: String, openIdx: Int): Int {
    var depth = 0
    var i = openIdx
    while (i < text.length) {
        when (text[i]) {
            '\\' -> i += 2
            '`' -> {
                var run = 0
                while (i + run < text.length && text[i + run] == '`') run++
                val close = findClosingBackticks(text, i + run, run)
                i = if (close >= 0) close + run else i + run
            }
            '[' -> {
                depth++
                i++
            }
            ']' -> {
                depth--
                if (depth == 0) return i
                i++
            }
            else -> i++
        }
    }
    return -1
}

private fun findMatchingParen(text: String, openIdx: Int): Int {
    var depth = 0
    var i = openIdx
    while (i < text.length) {
        when (text[i]) {
            '\\' -> i += 2
            '(' -> {
                depth++
                i++
            }
            ')' -> {
                depth--
                if (depth == 0) return i
                i++
            }
            else -> {
                if (text[i].isWhitespace() && depth == 1) return -1
                i++
            }
        }
    }
    return -1
}

private fun findClosingDelimiter(
    text: String,
    start: Int,
    delim: String,
    requireWordBoundary: Boolean,
): Int {
    val len = delim.length
    val ch = delim[0]
    var i = start
    while (i <= text.length - len) {
        when {
            text[i] == '\\' && i + 1 < text.length -> i += 2
            text[i] == '`' -> {
                var run = 0
                while (i + run < text.length && text[i + run] == '`') run++
                val close = findClosingBackticks(text, i + run, run)
                i = if (close >= 0) close + run else i + run
            }
            text[i] == ch -> {
                var run = 0
                while (i + run < text.length && text[i + run] == ch) run++
                val prevOk = i > start && !text[i - 1].isWhitespace()
                val afterIdx = i + run
                val boundaryOk = !requireWordBoundary ||
                    afterIdx >= text.length ||
                    !text[afterIdx].isLetterOrDigit()

                if (prevOk && boundaryOk && run >= len) {
                    if (len == 1 && run == 2) {
                        i += 2
                        continue
                    }
                    // For ** closing inside *** or * closing inside ***, take the trailing `len` chars
                    return afterIdx - len
                }
                i += run
            }
            else -> i++
        }
    }
    return -1
}

private fun AnnotatedString.Builder.appendMarkdownInline(
    text: String,
    linkColor: Color,
    codeBg: Color,
    codeFg: Color,
    altDefault: String,
    depth: Int = 0,
) {
    if (depth > 8) {
        append(text)
        return
    }
    var i = 0
    val n = text.length
    while (i < n) {
        val c = text[i]

        // 1. Backslash escape
        if (c == '\\' && i + 1 < n && text[i + 1] in ESCAPABLE_MD_CHARS) {
            append(text[i + 1])
            i += 2
            continue
        }

        // 2. HTML <br> / <br/>
        if (c == '<') {
            val brMatch = HTML_BR_REGEX.matchAt(text, i)
            if (brMatch != null) {
                append('\n')
                i = brMatch.range.last + 1
                continue
            }
        }

        // 3. Inline code `...` / ``...``
        if (c == '`') {
            var run = 0
            while (i + run < n && text[i + run] == '`') run++
            val close = findClosingBackticks(text, i + run, run)
            if (close >= 0) {
                var code = text.substring(i + run, close)
                if (code.length >= 2 && code.startsWith(' ') && code.endsWith(' ') && code.any { it != ' ' }) {
                    code = code.substring(1, code.length - 1)
                }
                pushStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        background = codeBg,
                        color = codeFg,
                    ),
                )
                append(" $code ")
                pop()
                i = close + run
                continue
            }
        }

        // 4. Inline image fallback ![alt](url)
        if (c == '!' && i + 1 < n && text[i + 1] == '[') {
            val closeBracket = findMatchingBracket(text, i + 1)
            if (closeBracket > 0 && closeBracket + 1 < n && text[closeBracket + 1] == '(') {
                val closeParen = findMatchingParen(text, closeBracket + 1)
                if (closeParen > 0) {
                    val alt = text.substring(i + 2, closeBracket).ifBlank { altDefault }
                    val url = text.substring(closeBracket + 2, closeParen).trim()
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
                    i = closeParen + 1
                    continue
                }
            }
        }

        // 5. Markdown link [label](url) with recursive formatting inside label
        if (c == '[') {
            val closeBracket = findMatchingBracket(text, i)
            if (closeBracket > i + 1 && closeBracket + 1 < n && text[closeBracket + 1] == '(') {
                val closeParen = findMatchingParen(text, closeBracket + 1)
                if (closeParen > closeBracket + 2) {
                    val label = text.substring(i + 1, closeBracket)
                    val url = text.substring(closeBracket + 2, closeParen).trim()
                    val start = length
                    appendMarkdownInline(label, linkColor, codeBg, codeFg, altDefault, depth + 1)
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
                    i = closeParen + 1
                    continue
                }
            }
        }

        // 6. Bold + Italic (*** or ___)
        if ((c == '*' || c == '_') && i + 2 < n && text[i + 1] == c && text[i + 2] == c) {
            val wordBoundary = c == '_'
            val startOk = (!wordBoundary || i == 0 || !text[i - 1].isLetterOrDigit()) &&
                i + 3 < n && !text[i + 3].isWhitespace()
            if (startOk) {
                val delim = if (c == '*') "***" else "___"
                val close = findClosingDelimiter(text, i + 3, delim, wordBoundary)
                if (close > i + 3) {
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic))
                    appendMarkdownInline(text.substring(i + 3, close), linkColor, codeBg, codeFg, altDefault, depth + 1)
                    pop()
                    i = close + 3
                    continue
                }
            }
        }

        // 7. Bold (** or __)
        if ((c == '*' || c == '_') && i + 1 < n && text[i + 1] == c) {
            val wordBoundary = c == '_'
            val startOk = (!wordBoundary || i == 0 || !text[i - 1].isLetterOrDigit()) &&
                i + 2 < n && !text[i + 2].isWhitespace()
            if (startOk) {
                val delim = if (c == '*') "**" else "__"
                val close = findClosingDelimiter(text, i + 2, delim, wordBoundary)
                if (close > i + 2) {
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                    appendMarkdownInline(text.substring(i + 2, close), linkColor, codeBg, codeFg, altDefault, depth + 1)
                    pop()
                    i = close + 2
                    continue
                }
            }
        }

        // 8. Strikethrough (~~)
        if (c == '~' && i + 1 < n && text[i + 1] == '~') {
            if (i + 2 < n && !text[i + 2].isWhitespace()) {
                val close = findClosingDelimiter(text, i + 2, "~~", requireWordBoundary = false)
                if (close > i + 2) {
                    pushStyle(SpanStyle(textDecoration = TextDecoration.LineThrough))
                    appendMarkdownInline(text.substring(i + 2, close), linkColor, codeBg, codeFg, altDefault, depth + 1)
                    pop()
                    i = close + 2
                    continue
                }
            }
        }

        // 9. Italic (* or _)
        if (c == '*' || c == '_') {
            val wordBoundary = c == '_'
            val startOk = (!wordBoundary || i == 0 || !text[i - 1].isLetterOrDigit()) &&
                i + 1 < n && !text[i + 1].isWhitespace() && text[i + 1] != c
            if (startOk) {
                val close = findClosingDelimiter(text, i + 1, c.toString(), wordBoundary)
                if (close > i + 1) {
                    pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                    appendMarkdownInline(text.substring(i + 1, close), linkColor, codeBg, codeFg, altDefault, depth + 1)
                    pop()
                    i = close + 1
                    continue
                }
            }
        }

        // 10. Autolink https://... or http://...
        if (c == 'h' && (text.startsWith("https://", i) || text.startsWith("http://", i))) {
            var end = i
            while (end < n && !text[end].isWhitespace() && text[end] !in "<>\"[]") {
                end++
            }
            var url = text.substring(i, end)
            while (url.isNotEmpty() && url.last() in ".,;:!?") {
                url = url.dropLast(1)
            }
            while (url.endsWith(')') && url.count { it == ')' } > url.count { it == '(' }) {
                url = url.dropLast(1)
            }
            if (url.length > 8) {
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
                i += url.length
                continue
            }
        }

        append(c)
        i++
    }
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
            appendMarkdownInline(
                text = text,
                linkColor = linkColor,
                codeBg = codeBg,
                codeFg = codeFg,
                altDefault = altDefault,
            )
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

                is MdBlock.TaskItem -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = (block.indent * 14).dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Icon(
                            imageVector = if (block.checked) {
                                Icons.Rounded.CheckBox
                            } else {
                                Icons.Rounded.CheckBoxOutlineBlank
                            },
                            contentDescription = null,
                            tint = if (block.checked) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier
                                .padding(top = 2.dp, end = 8.dp)
                                .size(18.dp),
                        )
                        Text(
                            text = rememberInlineAnnotatedString(block.text),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (block.checked) {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            textDecoration = if (block.checked) TextDecoration.LineThrough else null,
                            modifier = Modifier.weight(1f),
                        )
                    }
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
                            .height(IntrinsicSize.Min)
                            .clip(shape)
                            .background(quoteBg)
                            .border(1.dp, quoteBg.edgeOutline(), shape)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .fillMaxHeight()
                                .padding(vertical = 2.dp)
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
                    val context = LocalContext.current
                    val haptics = LocalHapticFeedback.current
                    val codeBg = MaterialTheme.colorScheme.surfaceContainerLowest
                    val thumbColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                    val shape = RoundedCornerShape(16.dp)
                    val codeScroll = rememberScrollState()
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(shape)
                            .background(codeBg)
                            .border(1.dp, codeBg.edgeOutline(), shape),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = block.language.ifBlank { "CODE" }.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                                        val cm = context.getSystemService(ClipboardManager::class.java)
                                        cm?.setPrimaryClip(ClipData.newPlainText("Code", block.code))
                                        Toast.makeText(
                                            context,
                                            context.getString(android.R.string.copy),
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ContentCopy,
                                    contentDescription = stringResource(android.R.string.copy),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScrollFadeAndBar(
                                    scrollState = codeScroll,
                                    fadeColor = codeBg,
                                    thumbColor = thumbColor,
                                ),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(codeScroll)
                                    .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 14.dp),
                            ) {
                                Text(
                                    text = block.code,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    softWrap = false,
                                )
                            }
                        }
                    }
                }

                is MdBlock.Table -> {
                    MarkdownTable(
                        headers = block.headers,
                        alignments = block.alignments,
                        rows = block.rows,
                    )
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

private fun Modifier.horizontalScrollFadeAndBar(
    scrollState: ScrollState,
    fadeColor: Color,
    thumbColor: Color,
): Modifier = this.drawWithContent {
    drawContent()
    val fadeW = 20.dp.toPx()
    if (scrollState.canScrollBackward) {
        drawRect(
            brush = Brush.horizontalGradient(
                colors = listOf(fadeColor, Color.Transparent),
                startX = 0f,
                endX = fadeW,
            ),
            topLeft = Offset.Zero,
            size = Size(fadeW, size.height),
        )
    }
    if (scrollState.canScrollForward) {
        drawRect(
            brush = Brush.horizontalGradient(
                colors = listOf(Color.Transparent, fadeColor),
                startX = size.width - fadeW,
                endX = size.width,
            ),
            topLeft = Offset(size.width - fadeW, 0f),
            size = Size(fadeW, size.height),
        )
    }
    if (scrollState.maxValue > 0) {
        val trackPad = 12.dp.toPx()
        val trackW = (size.width - trackPad * 2f).coerceAtLeast(1f)
        val totalContentW = size.width + scrollState.maxValue.toFloat()
        val thumbW = ((size.width / totalContentW) * trackW).coerceIn(24.dp.toPx(), trackW)
        val frac = (scrollState.value.toFloat() / scrollState.maxValue.toFloat()).coerceIn(0f, 1f)
        val thumbX = trackPad + (trackW - thumbW) * frac
        val barH = 2.5.dp.toPx()
        drawRoundRect(
            color = thumbColor,
            topLeft = Offset(thumbX, size.height - barH - 4.dp.toPx()),
            size = Size(thumbW, barH),
            cornerRadius = CornerRadius(barH / 2f, barH / 2f),
        )
    }
}

@Composable
private fun MarkdownTable(
    headers: List<String>,
    alignments: List<TableAlign>,
    rows: List<List<String>>,
) {
    val tableBg = MaterialTheme.colorScheme.surfaceContainerLowest
    val headerBg = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f)
    val dividerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
    val thumbColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
    val shape = RoundedCornerShape(16.dp)
    val colCount = maxOf(headers.size, rows.maxOfOrNull { it.size } ?: 0).coerceAtLeast(1)
    val allRows = remember(headers, rows, colCount) {
        buildList {
            add(List(colCount) { headers.getOrElse(it) { "" } })
            rows.forEach { r -> add(List(colCount) { r.getOrElse(it) { "" } }) }
        }
    }
    val scrollState = rememberScrollState()

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(tableBg)
            .border(1.dp, tableBg.edgeOutline(), shape),
    ) {
        val density = LocalDensity.current
        val minTableWpx = with(density) { maxWidth.roundToPx() }
        val minColWpx = with(density) { 64.dp.roundToPx() }
        val maxColWpx = with(density) { 240.dp.roundToPx() }
        val dividerPx = with(density) { 1.dp.roundToPx() }.coerceAtLeast(1)
        val barBottomPadPx = with(density) { 8.dp.roundToPx() }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScrollFadeAndBar(
                    scrollState = scrollState,
                    fadeColor = tableBg,
                    thumbColor = thumbColor,
                ),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scrollState),
            ) {
                Layout(
                    content = {
                        // 0: Header row background
                        Box(modifier = Modifier.background(headerBg))
                        // 1..(rowCount-1): Row dividers
                        for (rIdx in 0 until allRows.size - 1) {
                            Box(modifier = Modifier.background(dividerColor))
                        }
                        // Cells
                        allRows.forEachIndexed { rIdx, rowCells ->
                            val isHeader = rIdx == 0
                            rowCells.forEachIndexed { cIdx, cellText ->
                                val align = alignments.getOrElse(cIdx) { TableAlign.START }
                                val textAlign = when (align) {
                                    TableAlign.START -> TextAlign.Start
                                    TableAlign.CENTER -> TextAlign.Center
                                    TableAlign.END -> TextAlign.End
                                }
                                Box(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                    contentAlignment = when (align) {
                                        TableAlign.START -> Alignment.CenterStart
                                        TableAlign.CENTER -> Alignment.Center
                                        TableAlign.END -> Alignment.CenterEnd
                                    },
                                ) {
                                    Text(
                                        text = rememberInlineAnnotatedString(cellText),
                                        style = if (isHeader) {
                                            MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                        } else {
                                            MaterialTheme.typography.bodySmall
                                        },
                                        color = if (isHeader) {
                                            MaterialTheme.colorScheme.onSurface
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        textAlign = textAlign,
                                    )
                                }
                            }
                        }
                    },
                ) { measurables, _ ->
                    val rowCount = allRows.size
                    val dividerCount = (rowCount - 1).coerceAtLeast(0)
                    val headerBgMeasurable = measurables[0]
                    val dividerMeasurables = measurables.subList(1, 1 + dividerCount)
                    val cellMeasurables = measurables.subList(1 + dividerCount, measurables.size)

                    val colWidths = IntArray(colCount) { minColWpx }
                    for (r in 0 until rowCount) {
                        for (c in 0 until colCount) {
                            val intrinsicW = cellMeasurables[r * colCount + c].maxIntrinsicWidth(Int.MAX_VALUE)
                            if (intrinsicW > colWidths[c]) {
                                colWidths[c] = intrinsicW.coerceAtMost(maxColWpx)
                            }
                        }
                    }

                    val sumW = colWidths.sum()
                    val totalW = maxOf(sumW, minTableWpx)
                    if (totalW > sumW) {
                        val extra = totalW - sumW
                        val perCol = extra / colCount
                        var rem = extra % colCount
                        for (c in 0 until colCount) {
                            colWidths[c] += perCol + if (rem-- > 0) 1 else 0
                        }
                    }

                    val cellPlaceables = Array(rowCount) { r ->
                        Array(colCount) { c ->
                            cellMeasurables[r * colCount + c].measure(
                                Constraints.fixedWidth(colWidths[c]),
                            )
                        }
                    }
                    val rowHeights = IntArray(rowCount) { r ->
                        cellPlaceables[r].maxOfOrNull { it.height } ?: 0
                    }

                    val headerBgPlaceable = headerBgMeasurable.measure(
                        Constraints.fixed(totalW, rowHeights[0]),
                    )
                    val dividerPlaceables = dividerMeasurables.map {
                        it.measure(Constraints.fixed(totalW, dividerPx))
                    }
                    val extraBottom = if (totalW > minTableWpx) barBottomPadPx else 0
                    val totalH = rowHeights.sum() + dividerCount * dividerPx + extraBottom

                    layout(totalW, totalH) {
                        headerBgPlaceable.placeRelative(0, 0)
                        var y = 0
                        for (r in 0 until rowCount) {
                            var x = 0
                            val rh = rowHeights[r]
                            for (c in 0 until colCount) {
                                val p = cellPlaceables[r][c]
                                p.placeRelative(x, y + (rh - p.height) / 2)
                                x += colWidths[c]
                            }
                            y += rh
                            if (r < dividerCount) {
                                dividerPlaceables[r].placeRelative(0, y)
                                y += dividerPx
                            }
                        }
                    }
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
    val horizontalInset = LocalContentHorizontalInset.current

    val anim = remember(flight.sessionId) { Animatable(0f) }
    val backProgress = remember(flight.sessionId) { Animatable(0f) }
    var backEdge by remember(flight.sessionId) { mutableIntStateOf(BackEventCompat.EDGE_LEFT) }
    var backDyNorm by remember(flight.sessionId) { mutableFloatStateOf(0f) }
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

    PredictiveBackHandler(enabled = !closing) { progress ->
        var startY = Float.NaN
        try {
            progress.collect { event ->
                if (startY.isNaN()) startY = event.touchY
                backEdge = event.swipeEdge
                backDyNorm = ((event.touchY - startY) / 420f).coerceIn(-1f, 1f)
                backProgress.snapTo(event.progress.coerceIn(0f, 1f))
            }
            closeAnimated()
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

    val t = sstepE(anim.value.coerceIn(0f, 1f), 0f, 1f)
    val bp = FastOutSlowInEasing.transform(backProgress.value.coerceIn(0f, 1f))
    val backScale = 1f - 0.15f * bp
    val edgeSign = if (backEdge == BackEventCompat.EDGE_LEFT) 1f else -1f
    val backTx = with(density) { 20.dp.toPx() } * bp * edgeSign
    val backTy = with(density) { 14.dp.toPx() } * bp * backDyNorm
    DisposableEffect(t, bp) {
        onProgress(t * (1f - 0.45f * bp))
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
                    .graphicsLayer { alpha = sstepE(t, 0.1f, 0.85f) * (1f - 0.45f * bp) }
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
                .background(Color.Black.copy(alpha = 0.72f * sstepE(t, 0.05f, 0.85f) * (1f - 0.45f * bp)))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { closeAnimated() },
                ),
        )

        val liveSrc = flight.sourceBoundsProvider().let {
            if (it.width > 1f && it.height > 1f) it else flight.source
        }
        val endW = (maxWidth - 24.dp - horizontalInset * 2)
            .coerceAtLeast(120.dp)
            .coerceAtMost(maxWidth)
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
                    translationX = dx + panOffset.x + backTx
                    translationY = dy + panOffset.y + backTy
                    scaleX = zoomScale * backScale
                    scaleY = zoomScale * backScale
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
            val chromeAlpha = sstepE(t, 0.35f, 0.9f) * (1f - bp)
            val btnBg = Color.Black.copy(alpha = 0.5f)
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = statusTop + 14.dp, end = 16.dp + horizontalInset)
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
                            start = 24.dp + horizontalInset,
                            end = 24.dp + horizontalInset,
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