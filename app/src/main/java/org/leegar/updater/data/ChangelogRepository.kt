package org.leegar.updater.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.leegar.updater.R
import org.leegar.updater.data.source.network.ServerErrorFormatter
import org.leegar.updater.deviceinfo.DeviceInfoUtils
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

sealed interface ChangelogState {
    data object Idle : ChangelogState
    data object Loading : ChangelogState
    data class Loaded(
        val markdown: String,
        val isRefreshing: Boolean = false,
    ) : ChangelogState
    data class Error(val message: String? = null) : ChangelogState
}

/**
 * Fetches and disk-caches raw Markdown changelog per update:
 * 1) inline `changelog` field in JSON
 * 2) disk/memory cache (unless forceRefresh = true)
 * 3) per-update `changelog_url` in JSON or fallback device changelog URL
 */
class ChangelogRepository(private val context: Context) {
    private val client = OkHttpClient.Builder()
        .callTimeout(15, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val cache = ConcurrentHashMap<String, String>()
    private val diskDir: File
        get() = File(context.cacheDir, "updater_changelogs").apply { if (!exists()) mkdirs() }

    private fun cacheKey(update: Update): String =
        "${update.downloadId}|${update.changelogUrl.orEmpty()}"

    private fun cacheFile(key: String): File {
        val digest = MessageDigest.getInstance("SHA-256").digest(key.toByteArray())
        val name = digest.take(16).joinToString("") { "%02x".format(it) }
        return File(diskDir, "$name.md")
    }

    fun peekCached(update: Update): String? {
        update.changelogInline?.takeIf { it.isNotBlank() }?.let { return it.trim() }
        val key = cacheKey(update)
        cache[key]?.let { return it }
        val file = cacheFile(key)
        if (file.isFile) {
            val text = runCatching { file.readText() }.getOrNull()
            if (text != null) {
                cache[key] = text
                return text
            }
        }
        return null
    }

    suspend fun loadCachedMap(updates: List<Update>): Map<String, ChangelogState> =
        withContext(Dispatchers.IO) {
            val out = LinkedHashMap<String, ChangelogState>()
            updates.forEach { u ->
                peekCached(u)?.let { md ->
                    out[u.downloadId] = ChangelogState.Loaded(markdown = md, isRefreshing = false)
                }
            }
            out
        }

    suspend fun refreshChangelogs(updates: List<Update>): Map<String, String> =
        withContext(Dispatchers.IO) {
            val refreshed = LinkedHashMap<String, String>()
            updates.forEach { update ->
                runCatching {
                    val md = fetchChangelog(update, forceRefresh = true)
                    refreshed[update.downloadId] = md
                }
            }
            refreshed
        }

    suspend fun fetchChangelog(update: Update, forceRefresh: Boolean = false): String =
        withContext(Dispatchers.IO) {
            update.changelogInline?.takeIf { it.isNotBlank() }?.let {
                return@withContext it.trim()
            }

            val key = cacheKey(update)
            if (!forceRefresh) {
                peekCached(update)?.let { return@withContext it }
            }

            val url = update.changelogUrl?.takeIf { it.isNotBlank() }
                ?: run {
                    val device = DeviceInfoUtils.device
                    if (device.isBlank()) return@withContext ""
                    context.getString(R.string.menu_changelog_url, device)
                }

            val raw = fetch(url).trim()
            cache[key] = raw
            runCatching { cacheFile(key).writeText(raw) }
            raw
        }

    fun clearCache() {
        cache.clear()
        runCatching { File(context.cacheDir, "updater_changelogs").deleteRecursively() }
    }

    private fun fetch(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("Cache-Control", "no-cache")
            .build()

        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException(ServerErrorFormatter.formatOkHttpResponse(context, url, response))
            }
            val body = response.body ?: throw IOException(context.getString(R.string.err_changelog_body_empty))
            val contentLength = body.contentLength()
            if (contentLength > MAX_RESPONSE_BYTES) {
                throw IOException(context.getString(R.string.err_changelog_too_large, MAX_RESPONSE_BYTES))
            }
            val bytes = body.source().readByteArray()
            if (bytes.size > MAX_RESPONSE_BYTES) {
                throw IOException(context.getString(R.string.err_changelog_too_large, MAX_RESPONSE_BYTES))
            }
            bytes.decodeToString()
        }
    }

    private companion object {
        const val MAX_RESPONSE_BYTES = 512L * 1024L
    }
}