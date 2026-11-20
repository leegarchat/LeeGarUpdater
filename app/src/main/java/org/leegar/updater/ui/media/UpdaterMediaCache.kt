package org.leegar.updater.ui.media

import android.content.Context
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Disk-backed media loader for project logos (PNG/WebP/GIF with alpha, MP4/WebM videos)
 * and changelog Markdown images. Zero external image-loader dependencies.
 */
object UpdaterMediaCache {

    private const val TAG = "UpdaterMediaCache"
    private const val MAX_IMAGE_BYTES = 20L * 1024L * 1024L // 20 MB
    private const val MAX_VIDEO_BYTES = 40L * 1024L * 1024L // 40 MB
    private const val MAX_CACHE_FILES = 64

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    // In-memory cache for static drawables only (AnimatedImageDrawable is never shared across views)
    private val drawableCache = ConcurrentHashMap<String, Drawable>()
    private val animatedFileCache = ConcurrentHashMap<String, File>()
    private val videoFileCache = ConcurrentHashMap<String, File>()

    var mediaRevision by mutableIntStateOf(0)
        private set

    fun peekDrawable(url: String, maxDimensionPx: Int = 1600): Drawable? {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return null
        drawableCache[trimmed]?.let { return it }
        val animFile = animatedFileCache[trimmed]
        if (animFile != null && animFile.exists() && animFile.length() > 0L) {
            return decodeFromFile(animFile, maxDimensionPx)
        }
        return null
    }

    fun peekVideoFile(url: String): File? {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return null
        return videoFileCache[trimmed]?.takeIf { it.exists() && it.length() > 0L }
    }

    private fun decodeFromFile(file: File, maxDimensionPx: Int): Drawable? =
        runCatching {
            val source = ImageDecoder.createSource(file)
            ImageDecoder.decodeDrawable(source) { decoder, info, _ ->
                val w = info.size.width
                val h = info.size.height
                if (maxDimensionPx > 0 && (w > maxDimensionPx || h > maxDimensionPx)) {
                    val scale = maxDimensionPx.toFloat() / maxOf(w, h).toFloat()
                    decoder.setTargetSize(
                        (w * scale).toInt().coerceAtLeast(1),
                        (h * scale).toInt().coerceAtLeast(1),
                    )
                }
                // Preserve full alpha channel on PNG/WebP/GIF
                decoder.isMutableRequired = false
            }.also { decoded ->
                if (decoded is AnimatedImageDrawable) {
                    decoded.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
                }
            }
        }.onFailure {
            Log.w(TAG, "Failed to decode image from ${file.name}", it)
        }.getOrNull()

    suspend fun loadDrawable(
        context: Context,
        url: String,
        maxDimensionPx: Int = 1600,
    ): Drawable? = withContext(Dispatchers.IO) {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return@withContext null

        drawableCache[trimmed]?.let { return@withContext it }

        val cachedAnimFile = animatedFileCache[trimmed]?.takeIf { it.exists() && it.length() > 0L }
        if (cachedAnimFile != null) {
            decodeFromFile(cachedAnimFile, maxDimensionPx)?.let { return@withContext it }
            animatedFileCache.remove(trimmed)
        }

        val file = fetchToDisk(context, trimmed, subDir = "images", maxBytes = MAX_IMAGE_BYTES)
            ?: return@withContext null

        val decoded = decodeFromFile(file, maxDimensionPx)
        if (decoded == null) {
            file.delete()
            return@withContext null
        }

        if (decoded is AnimatedImageDrawable) {
            animatedFileCache[trimmed] = file
        } else {
            if (drawableCache.size > 48) drawableCache.clear()
            drawableCache[trimmed] = decoded
        }
        decoded
    }

    suspend fun loadVideoFile(context: Context, url: String): File? = withContext(Dispatchers.IO) {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return@withContext null
        peekVideoFile(trimmed)?.let { return@withContext it }
        val file = fetchToDisk(context, trimmed, subDir = "videos", maxBytes = MAX_VIDEO_BYTES)
        if (file != null && file.exists()) {
            videoFileCache[trimmed] = file
        }
        file
    }

    suspend fun refreshUrls(context: Context, urls: Collection<String>) = withContext(Dispatchers.IO) {
        val unique = urls.map { it.trim() }
            .filter { it.startsWith("http://", true) || it.startsWith("https://", true) }
            .distinct()
        if (unique.isEmpty()) return@withContext
        var changed = false
        coroutineScope {
            unique.map { url ->
                async {
                    val clean = url.substringBefore('?').substringBefore('#').lowercase()
                    val isVideo = clean.endsWith(".mp4") || clean.endsWith(".webm") || clean.endsWith(".mkv")
                    val subDir = if (isVideo) "videos" else "images"
                    val maxBytes = if (isVideo) MAX_VIDEO_BYTES else MAX_IMAGE_BYTES
                    val refreshed = fetchToDisk(context, url, subDir, maxBytes, forceRefresh = true)
                    if (refreshed != null) {
                        drawableCache.remove(url)
                        animatedFileCache.remove(url)
                        if (isVideo) {
                            videoFileCache[url] = refreshed
                        } else {
                            videoFileCache.remove(url)
                        }
                        changed = true
                    }
                }
            }.awaitAll()
        }
        if (changed) {
            withContext(Dispatchers.Main) {
                mediaRevision++
            }
        }
    }

    fun clearAll(context: Context) {
        drawableCache.clear()
        animatedFileCache.clear()
        videoFileCache.clear()
        runCatching {
            File(context.cacheDir, "updater_media").deleteRecursively()
        }
        mediaRevision++
    }

    private fun fetchToDisk(
        context: Context,
        url: String,
        subDir: String,
        maxBytes: Long,
        forceRefresh: Boolean = false,
    ): File? {
        val root = File(File(context.cacheDir, "updater_media"), subDir)
        if (!root.exists()) root.mkdirs()

        val ext = inferExtension(url)
        val target = File(root, "${sha256Short(url)}$ext")
        if (!forceRefresh && target.exists() && target.length() > 0L) {
            target.setLastModified(System.currentTimeMillis())
            return target
        }

        val tmp = File(root, "${target.name}.tmp")
        return try {
            val req = Request.Builder()
                .url(url)
                .apply { if (forceRefresh) header("Cache-Control", "no-cache") }
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    throw IOException("HTTP ${resp.code} for $url")
                }
                val body = resp.body ?: throw IOException("Empty body for $url")
                val contentLength = body.contentLength()
                if (contentLength > maxBytes) {
                    throw IOException("Media too large: $contentLength bytes")
                }
                var written = 0L
                val buf = ByteArray(32 * 1024)
                body.byteStream().use { input ->
                    FileOutputStream(tmp).use { out ->
                        while (true) {
                            val read = input.read(buf)
                            if (read <= 0) break
                            written += read
                            if (written > maxBytes) {
                                throw IOException("Media exceeded $maxBytes bytes")
                            }
                            out.write(buf, 0, read)
                        }
                        out.flush()
                    }
                }
            }
            if (target.exists()) target.delete()
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }
            trimCacheDir(root)
            target
        } catch (e: Exception) {
            Log.w(TAG, "Failed to download media: $url", e)
            tmp.delete()
            if (target.exists() && target.length() > 0L) target else null
        }
    }

    private fun trimCacheDir(dir: File) {
        val files = dir.listFiles()?.filter { it.isFile && !it.name.endsWith(".tmp") } ?: return
        if (files.size <= MAX_CACHE_FILES) return
        files.sortedBy { it.lastModified() }
            .take(files.size - MAX_CACHE_FILES)
            .forEach { it.delete() }
    }

    private fun inferExtension(url: String): String {
        val clean = url.substringBefore('?').substringBefore('#').lowercase()
        return when {
            clean.endsWith(".gif") -> ".gif"
            clean.endsWith(".webp") -> ".webp"
            clean.endsWith(".png") -> ".png"
            clean.endsWith(".jpg") || clean.endsWith(".jpeg") -> ".jpg"
            clean.endsWith(".mp4") -> ".mp4"
            clean.endsWith(".webm") -> ".webm"
            clean.endsWith(".mkv") -> ".mkv"
            else -> ".bin"
        }
    }

    private fun sha256Short(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.take(16).joinToString("") { "%02x".format(it) }
    }
}