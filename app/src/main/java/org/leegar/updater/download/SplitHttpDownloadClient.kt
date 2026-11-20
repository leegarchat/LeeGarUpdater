package org.leegar.updater.download

import android.content.Context
import android.os.SystemClock
import android.util.Log
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import org.leegar.updater.R
import org.leegar.updater.data.PartDownloadState
import org.leegar.updater.data.PartStatus
import org.leegar.updater.data.Update
import org.leegar.updater.data.UpdatePart
import org.leegar.updater.data.source.network.ServerErrorFormatter
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLongArray

class SplitHttpDownloadClient(
    private val downloadId: String,
    rawParts: List<UpdatePart>,
    rawUrls: String,
    private val destination: File,
    private val expectedFinalSha256: String?,
    private val verifyHashes: Boolean,
    private val parallelDownload: Boolean = false,
    private val progressListener: DownloadClient.ProgressListener?,
    private val callback: DownloadClient.DownloadCallback,
    private val appContext: Context? = null,
) : DownloadClient {

    private fun err(resId: Int, fallback: String, vararg args: Any): String {
        val ctx = appContext
        return if (ctx != null) ctx.getString(resId, *args)
        else String.format(Locale.US, fallback, *args)
    }

    private fun serverHttpError(url: String, response: okhttp3.Response): String {
        val ctx = appContext
        return if (ctx != null) ServerErrorFormatter.formatOkHttpResponse(ctx, url, response)
        else ServerErrorFormatter.formatOkHttpResponse(url, response)
    }

    private val parts: List<UpdatePart> = if (rawParts.isNotEmpty()) {
        rawParts.filter { it.url.isNotBlank() }
    } else {
        rawUrls.split("\n").map { it.trim() }.filter { it.isNotEmpty() }.map { UpdatePart(url = it) }
    }

    private val cancelled = AtomicBoolean(false)
    private val activeCalls = CopyOnWriteArrayList<Call>()

    @Volatile
    private var workerThread: Thread? = null

    @Volatile
    private var parallelPool: ExecutorService? = null

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    override fun start() {
        if (workerThread != null) return
        startWorker(resume = false)
    }

    override fun resume() {
        if (workerThread != null) return
        startWorker(resume = true)
    }

    override fun cancel() {
        cancelled.set(true)
        activeCalls.forEach { runCatching { it.cancel() } }
        activeCalls.clear()
        parallelPool?.shutdownNow()
        parallelPool = null
        workerThread?.interrupt()
        workerThread = null
    }

    private fun startWorker(resume: Boolean) {
        cancelled.set(false)
        workerThread = Thread({
            try {
                runDownload(resume)
            } catch (e: Exception) {
                Log.e(TAG, "Split download failed", e)
                if (cancelled.get()) {
                    callback.onFailure(true, null)
                } else {
                    callback.onFailure(false, e.message ?: err(R.string.toast_download_failed, "Download failed"))
                }
            } finally {
                activeCalls.clear()
                parallelPool?.shutdownNow()
                parallelPool = null
                workerThread = null
            }
        }, "SplitDownloadThread").apply { start() }
    }

    private fun runDownload(resume: Boolean) {
        if (parts.isEmpty()) {
            callback.onFailure(false, err(R.string.err_no_parts, "No download parts specified"))
            return
        }

        val partsDir = getPartsDir(destination.parentFile!!, downloadId.ifBlank { destination.name })
        if (!resume && partsDir.exists()) {
            partsDir.deleteRecursively()
        }
        if (!partsDir.exists() && !partsDir.mkdirs()) {
            throw IOException(err(R.string.err_no_tempdir, "Cannot create temp parts directory: %1\$s", partsDir.absolutePath))
        }

        val partSizes = LongArray(parts.size) { i ->
            parts[i].size?.takeIf { it > 0L } ?: fetchPartSize(parts[i].url)
        }
        val totalBytes = partSizes.sum()

        callback.onResponse(DownloadClient.Headers { name ->
            if (name.equals("Content-Length", ignoreCase = true) && totalBytes > 0L) {
                totalBytes.toString()
            } else null
        })

        val partBytes = AtomicLongArray(parts.size)
        val partSpeeds = AtomicLongArray(parts.size)
        val partStatuses = Array(parts.size) { PartStatus.PENDING }
        val partVerified = BooleanArray(parts.size)
        val partNames = Array(parts.size) { i -> extractPartName(parts[i].url, i) }
        val completedParts = AtomicInteger(0)

        for (i in parts.indices) {
            val pFile = partFile(partsDir, i)
            val marker = doneMarker(partsDir, i)
            if (isPartDoneValid(pFile, marker, partSizes[i], parts[i].sha256)) {
                partBytes.set(i, pFile.length())
                partStatuses[i] = PartStatus.DONE
                partVerified[i] = !parts[i].sha256.isNullOrBlank()
                completedParts.incrementAndGet()
            } else if (pFile.exists()) {
                partBytes.set(i, pFile.length())
                partStatuses[i] = PartStatus.PAUSED
            }
        }

        val statsLock = Any()
        var lastGlobalMillis = SystemClock.elapsedRealtime()
        var lastEmitMillis = 0L
        var curSampleBytes = sumBytes(partBytes)
        var globalSpeed = -1L

        fun buildSnapshot(): List<PartDownloadState> = List(parts.size) { idx ->
            PartDownloadState(
                index = idx,
                name = partNames[idx],
                downloadedBytes = partBytes.get(idx),
                totalBytes = partSizes[idx],
                speedBytesPerSec = if (partStatuses[idx] == PartStatus.DOWNLOADING) {
                    partSpeeds.get(idx).coerceAtLeast(0L)
                } else 0L,
                status = partStatuses[idx],
                sha256Verified = partVerified[idx],
            )
        }

        fun reportProgress(
            stage: String,
            displayPartIndex: Int,
            displayPartPct: Int,
            forceEmit: Boolean = false,
        ) {
            synchronized(statsLock) {
                val totalRead = sumBytes(partBytes)
                val now = SystemClock.elapsedRealtime()
                val delta = now - lastGlobalMillis
                if (delta >= 400L) {
                    val curSpeed = ((totalRead - curSampleBytes).coerceAtLeast(0L) * 1000L) / delta
                    globalSpeed = if (globalSpeed == -1L) curSpeed else ((globalSpeed * 3L) + curSpeed) / 4L
                    lastGlobalMillis = now
                    curSampleBytes = totalRead
                }
                if (!forceEmit && (now - lastEmitMillis) < 220L) return
                lastEmitMillis = now
                val eta = if (globalSpeed > 0L) {
                    ((totalBytes - totalRead).coerceAtLeast(0L) / globalSpeed).coerceAtLeast(1L)
                } else -1L
                progressListener?.updateParts(buildSnapshot())
                progressListener?.updateStage(stage, displayPartIndex, parts.size, displayPartPct)
                progressListener?.update(totalRead, totalBytes, globalSpeed.coerceAtLeast(0L), eta)
            }
        }

        // Emit initial part list immediately
        reportProgress(Update.STAGE_DOWNLOADING, 1, 0, forceEmit = true)

        fun executePart(i: Int) {
            if (isCancelled()) return
            val part = parts[i]
            val expectedSize = partSizes[i]
            val pFile = partFile(partsDir, i)
            val marker = doneMarker(partsDir, i)

            if (isPartDoneValid(pFile, marker, expectedSize, part.sha256)) {
                synchronized(statsLock) {
                    partBytes.set(i, pFile.length())
                    partSpeeds.set(i, 0L)
                    partStatuses[i] = PartStatus.DONE
                    partVerified[i] = !part.sha256.isNullOrBlank()
                }
                reportProgress(
                    Update.STAGE_DOWNLOADING,
                    if (parallelDownload) completedParts.get().coerceAtLeast(1) else i + 1,
                    100,
                    forceEmit = true,
                )
                return
            }

            var partSucceeded = false
            var lastError: Exception? = null

            for (attempt in 1..MAX_PART_RETRIES) {
                if (isCancelled()) return
                try {
                    synchronized(statsLock) {
                        partStatuses[i] = PartStatus.DOWNLOADING
                        partSpeeds.set(i, 0L)
                    }
                    reportProgress(Update.STAGE_DOWNLOADING, i + 1, 0, forceEmit = true)

                    var partSampleMillis = SystemClock.elapsedRealtime()
                    var partSampleBytes = if (pFile.exists()) pFile.length() else 0L
                    var localSpeed = -1L

                    downloadSinglePart(
                        partIndex = i,
                        partCount = parts.size,
                        part = part,
                        expectedSize = expectedSize,
                        partFile = pFile,
                        onPartBytes = { bytesInThisPart ->
                            partBytes.set(i, bytesInThisPart)
                            val now = SystemClock.elapsedRealtime()
                            val dt = now - partSampleMillis
                            if (dt >= 350L) {
                                val inst = ((bytesInThisPart - partSampleBytes).coerceAtLeast(0L) * 1000L) / dt
                                localSpeed = if (localSpeed == -1L) inst else ((localSpeed * 3L) + inst) / 4L
                                partSpeeds.set(i, localSpeed)
                                partSampleMillis = now
                                partSampleBytes = bytesInThisPart
                            }
                            val partPct = if (expectedSize > 0L) {
                                ((bytesInThisPart * 100L) / expectedSize).toInt().coerceIn(0, 100)
                            } else 0
                            val shownPart = if (parallelDownload) {
                                (completedParts.get() + 1).coerceAtMost(parts.size)
                            } else {
                                i + 1
                            }
                            reportProgress(Update.STAGE_DOWNLOADING, shownPart, partPct)
                        },
                    )

                    if (isCancelled()) return

                    partSpeeds.set(i, 0L)
                    var verifiedHash = ""
                    if (verifyHashes && !part.sha256.isNullOrBlank()) {
                        synchronized(statsLock) {
                            partStatuses[i] = PartStatus.VERIFYING
                        }
                        reportProgress(Update.STAGE_VERIFYING_PART, i + 1, 100, forceEmit = true)
                        verifiedHash = computeSha256(pFile)
                        if (!verifiedHash.equals(part.sha256.trim(), ignoreCase = true)) {
                            Log.w(TAG, "Part ${i + 1}/${parts.size} SHA-256 mismatch (attempt $attempt)")
                            pFile.delete()
                            marker.delete()
                            synchronized(statsLock) {
                                partBytes.set(i, 0L)
                                partStatuses[i] = PartStatus.ERROR
                            }
                            reportProgress(Update.STAGE_DOWNLOADING, i + 1, 0, forceEmit = true)
                            throw IOException(err(R.string.err_part_mismatch, "Part %1\$d/%2\$d checksum mismatch", i + 1, parts.size))
                        }
                    }

                    marker.writeText("${pFile.length()}:$verifiedHash")
                    synchronized(statsLock) {
                        partBytes.set(i, pFile.length())
                        partSpeeds.set(i, 0L)
                        partStatuses[i] = PartStatus.DONE
                        partVerified[i] = verifyHashes && !part.sha256.isNullOrBlank()
                    }
                    val doneCount = completedParts.incrementAndGet()
                    reportProgress(
                        Update.STAGE_DOWNLOADING,
                        if (parallelDownload) doneCount.coerceAtMost(parts.size) else i + 1,
                        100,
                        forceEmit = true,
                    )
                    partSucceeded = true
                    break
                } catch (e: Exception) {
                    if (isCancelled()) return
                    lastError = e
                    synchronized(statsLock) {
                        partSpeeds.set(i, 0L)
                        partStatuses[i] = PartStatus.ERROR
                    }
                    reportProgress(Update.STAGE_DOWNLOADING, i + 1, 0, forceEmit = true)
                    Log.w(TAG, "Part ${i + 1} attempt $attempt failed: ${e.message}")
                    if (e is PermanentHttpException) break
                    if (attempt < MAX_PART_RETRIES) {
                        try {
                            Thread.sleep(1000L * attempt)
                        } catch (_: InterruptedException) {
                            return
                        }
                    }
                }
            }

            if (!partSucceeded) {
                throw lastError ?: IOException(err(R.string.err_part_failed, "Failed to download part %1\$d/%2\$d", i + 1, parts.size))
            }
        }

        if (parallelDownload && parts.size > 1) {
            val workerCount = minOf(MAX_PARALLEL_WORKERS, parts.size)
            val pool = Executors.newFixedThreadPool(workerCount)
            parallelPool = pool
            val futures = ArrayList<Future<*>>(parts.size)
            for (i in parts.indices) {
                futures.add(pool.submit { executePart(i) })
            }
            try {
                for (f in futures) {
                    f.get()
                }
            } catch (e: ExecutionException) {
                cancelled.set(true)
                activeCalls.forEach { runCatching { it.cancel() } }
                pool.shutdownNow()
                throw (e.cause as? Exception) ?: IOException(e.message)
            } catch (_: InterruptedException) {
                cancelled.set(true)
                pool.shutdownNow()
                callback.onFailure(true, null)
                return
            } finally {
                pool.shutdown()
                parallelPool = null
            }
        } else {
            for (i in parts.indices) {
                if (isCancelled()) {
                    callback.onFailure(true, null)
                    return
                }
                executePart(i)
            }
        }

        if (isCancelled()) {
            callback.onFailure(true, null)
            return
        }

        val finalDigest = mergeAndHashParts(partsDir, totalBytes)

        if (isCancelled()) {
            destination.delete()
            callback.onFailure(true, null)
            return
        }

        if (verifyHashes && !expectedFinalSha256.isNullOrBlank()) {
            progressListener?.updateStage(Update.STAGE_VERIFYING_FINAL, parts.size, parts.size, 100)
            if (!finalDigest.equals(expectedFinalSha256.trim(), ignoreCase = true)) {
                Log.e(TAG, "Final SHA-256 mismatch: expected=$expectedFinalSha256, actual=$finalDigest")
                destination.delete()
                if (parts.none { !it.sha256.isNullOrBlank() }) {
                    partsDir.deleteRecursively()
                }
                throw IOException(err(R.string.err_final_sha, "Final archive SHA-256 verification failed"))
            }
        }

        partsDir.deleteRecursively()
        progressListener?.updateParts(buildSnapshot())
        progressListener?.update(totalBytes, totalBytes, globalSpeed.coerceAtLeast(0L), 0L)
        callback.onSuccess()
    }

    private fun downloadSinglePart(
        partIndex: Int,
        partCount: Int,
        part: UpdatePart,
        expectedSize: Long,
        partFile: File,
        onPartBytes: (Long) -> Unit,
    ) {
        var existingLen = if (partFile.exists()) partFile.length() else 0L
        if (expectedSize > 0L && existingLen > expectedSize) {
            partFile.delete()
            existingLen = 0L
        }
        if (expectedSize > 0L && existingLen == expectedSize) {
            onPartBytes(existingLen)
            return
        }

        val reqBuilder = Request.Builder().url(part.url)
        if (existingLen > 0L) {
            reqBuilder.header("Range", "bytes=$existingLen-")
        }

        val call = client.newCall(reqBuilder.build())
        activeCalls.add(call)
        try {
            call.execute().use { response ->
                if (response.code == 416) {
                    partFile.delete()
                    throw IOException(err(R.string.err_server_reply, "Server replied with %1\$s", "HTTP 416 for part ${partIndex + 1}/$partCount, retrying"))
                }
                val isPartial = response.code == 206
                if ((existingLen > 0L && !isPartial && response.code != 200) ||
                    (existingLen == 0L && !response.isSuccessful)
                ) {
                    val formatted = serverHttpError(part.url, response)
                    if (response.code in PERMANENT_HTTP_CODES) {
                        throw PermanentHttpException(formatted)
                    }
                    throw IOException(formatted)
                }

                val startPos = if (existingLen > 0L && isPartial) existingLen else 0L
                RandomAccessFile(partFile, "rw").use { raf ->
                    if (startPos == 0L) raf.setLength(0L)
                    raf.seek(startPos)
                    var partBytesRead = startPos
                    val body = response.body
                        ?: throw IOException(err(R.string.err_empty_body, "Empty response body for part %1\$d", partIndex + 1))
                    val buf = ByteArray(IO_BUFFER_SIZE)
                    body.byteStream().use { input ->
                        while (!isCancelled()) {
                            val read = input.read(buf)
                            if (read <= 0) break
                            raf.write(buf, 0, read)
                            partBytesRead += read
                            onPartBytes(partBytesRead)
                        }
                    }
                }
            }
        } finally {
            activeCalls.remove(call)
        }
    }

    private fun mergeAndHashParts(partsDir: File, totalBytes: Long): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val parent = destination.parentFile
        if (parent != null && !parent.exists()) parent.mkdirs()
        if (destination.exists()) destination.delete()

        var writtenTotal = 0L
        var lastReportMs = 0L
        val buf = ByteArray(MERGE_BUFFER_SIZE)

        FileOutputStream(destination).use { out ->
            for (i in parts.indices) {
                if (isCancelled()) return ""
                val pFile = partFile(partsDir, i)
                FileInputStream(pFile).use { input ->
                    while (!isCancelled()) {
                        val read = input.read(buf)
                        if (read <= 0) break
                        out.write(buf, 0, read)
                        digest.update(buf, 0, read)
                        writtenTotal += read

                        val now = SystemClock.elapsedRealtime()
                        if (now - lastReportMs >= 200L) {
                            lastReportMs = now
                            val pct = if (totalBytes > 0L) {
                                ((writtenTotal * 100L) / totalBytes).toInt().coerceIn(0, 100)
                            } else 0
                            progressListener?.updateStage(Update.STAGE_MERGING, i + 1, parts.size, pct)
                            progressListener?.update(writtenTotal, totalBytes, 0L, 0L)
                        }
                    }
                }
            }
            out.flush()
        }

        progressListener?.updateStage(Update.STAGE_MERGING, parts.size, parts.size, 100)
        return digest.digest().toHexString()
    }

    private fun computeSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buf = ByteArray(MERGE_BUFFER_SIZE)
        FileInputStream(file).use { input ->
            while (!isCancelled()) {
                val read = input.read(buf)
                if (read <= 0) break
                digest.update(buf, 0, read)
            }
        }
        return digest.digest().toHexString()
    }

    private fun sumBytes(arr: AtomicLongArray): Long {
        var s = 0L
        for (i in 0 until arr.length()) s += arr.get(i)
        return s
    }

    private fun isPartDoneValid(
        partFile: File,
        marker: File,
        expectedSize: Long,
        expectedSha256: String?,
    ): Boolean {
        if (!partFile.exists() || !marker.exists()) return false
        if (expectedSize > 0L && partFile.length() != expectedSize) return false
        val saved = runCatching { marker.readText().trim() }.getOrNull() ?: return false
        val colonIdx = saved.indexOf(':')
        if (colonIdx <= 0) return false
        val savedLen = saved.substring(0, colonIdx).toLongOrNull() ?: return false
        val savedHash = saved.substring(colonIdx + 1)
        if (savedLen != partFile.length()) return false
        if (verifyHashes && !expectedSha256.isNullOrBlank()) {
            if (!savedHash.equals(expectedSha256.trim(), ignoreCase = true)) return false
        }
        return true
    }

    private class PermanentHttpException(message: String) : IOException(message)

    private fun fetchPartSize(url: String): Long {
        val headReq = Request.Builder().url(url).head().build()
        client.newCall(headReq).execute().use { resp ->
            val len = resp.header("Content-Length")?.toLongOrNull()
            if (resp.isSuccessful && len != null && len > 0L) return len
            if (resp.code in PERMANENT_HTTP_CODES) {
                throw PermanentHttpException(serverHttpError(url, resp))
            }
        }
        val rangeReq = Request.Builder().url(url).header("Range", "bytes=0-0").build()
        client.newCall(rangeReq).execute().use { resp ->
            if (!resp.isSuccessful && resp.code != 206) {
                val formatted = serverHttpError(url, resp)
                if (resp.code in PERMANENT_HTTP_CODES) throw PermanentHttpException(formatted)
                throw IOException(formatted)
            }
            val total = resp.header("Content-Range")?.substringAfterLast('/')?.toLongOrNull()
            if (total != null && total > 0L) return total
            val len = resp.header("Content-Length")?.toLongOrNull()
            if (len != null && len > 0L) return len
        }
        throw IOException(err(R.string.err_unknown_size, "Cannot determine size for %1\$s", url))
    }

    private fun isCancelled(): Boolean = cancelled.get() || Thread.currentThread().isInterrupted

    private fun ByteArray.toHexString(): String = buildString(size * 2) {
        for (b in this@toHexString) {
            val v = b.toInt() and 0xFF
            append(HEX_CHARS[v ushr 4])
            append(HEX_CHARS[v and 0x0F])
        }
    }

    companion object {
        private const val TAG = "SplitHttpDownloadClient"
        private const val IO_BUFFER_SIZE = 64 * 1024
        private const val MERGE_BUFFER_SIZE = 256 * 1024
        private const val MAX_PART_RETRIES = 3
        private const val MAX_PARALLEL_WORKERS = 3
        private const val HEX_CHARS = "0123456789abcdef"
        private val PERMANENT_HTTP_CODES = setOf(401, 403, 404, 410, 429)

        private fun partFile(dir: File, index: Int): File =
            File(dir, String.format(Locale.US, "part_%03d.bin", index + 1))

        private fun doneMarker(dir: File, index: Int): File =
            File(dir, String.format(Locale.US, "part_%03d.done", index + 1))

        private fun extractPartName(url: String, index: Int): String =
            url.substringAfterLast('/').substringBefore('?').ifBlank { "part_${index + 1}" }

        @JvmStatic
        fun getPartsDir(downloadRoot: File, downloadId: String): File {
            val safeId = downloadId.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(80)
            return File(File(downloadRoot, ".parts"), safeId)
        }

        @JvmStatic
        fun cleanupParts(downloadRoot: File, downloadId: String) {
            runCatching {
                val dir = getPartsDir(downloadRoot, downloadId)
                if (dir.exists()) dir.deleteRecursively()
            }
        }

        @JvmStatic
        fun hasPartialParts(downloadRoot: File, downloadId: String): Boolean {
            val dir = getPartsDir(downloadRoot, downloadId)
            return dir.isDirectory && (dir.listFiles()?.isNotEmpty() == true)
        }

        @JvmStatic
        fun hasDownloadedPartBytes(downloadRoot: File, downloadId: String): Boolean {
            val dir = getPartsDir(downloadRoot, downloadId)
            if (!dir.isDirectory) return false
            val files = dir.listFiles() ?: return false
            return files.any { it.isFile && it.name.endsWith(".bin") && it.length() > 0L }
        }

        /**
         * Inspects existing files in `.parts/<downloadId>/` to populate per-part UI on resume/pause/restart.
         */
        @JvmStatic
        fun inspectPartStates(downloadRoot: File, update: Update): List<PartDownloadState> {
            val resolvedParts = if (update.parts.isNotEmpty()) {
                update.parts.filter { it.url.isNotBlank() }
            } else {
                update.downloadUrl.orEmpty()
                    .split("\n")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .map { UpdatePart(url = it) }
            }
            if (resolvedParts.size <= 1) return emptyList()
            val dir = getPartsDir(downloadRoot, update.downloadId)
            val fallbackEach = if (update.fileSize > 0L) update.fileSize / resolvedParts.size else 0L
            return resolvedParts.mapIndexed { idx, part ->
                val pFile = partFile(dir, idx)
                val marker = doneMarker(dir, idx)
                val len = if (pFile.exists()) pFile.length() else 0L
                val expected = part.size?.takeIf { it > 0L } ?: fallbackEach
                val isDone = marker.exists() && pFile.exists()
                PartDownloadState(
                    index = idx,
                    name = extractPartName(part.url, idx),
                    downloadedBytes = len,
                    totalBytes = if (isDone && len > 0L) len else expected,
                    speedBytesPerSec = 0L,
                    status = when {
                        isDone -> PartStatus.DONE
                        len > 0L -> PartStatus.PAUSED
                        else -> PartStatus.PENDING
                    },
                    sha256Verified = isDone && !part.sha256.isNullOrBlank(),
                )
            }
        }

        @JvmStatic
        fun computeFileSha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            val buf = ByteArray(MERGE_BUFFER_SIZE)
            FileInputStream(file).use { input ->
                while (true) {
                    val read = input.read(buf)
                    if (read <= 0) break
                    digest.update(buf, 0, read)
                }
            }
            return buildString(64) {
                for (b in digest.digest()) {
                    val v = b.toInt() and 0xFF
                    append(HEX_CHARS[v ushr 4])
                    append(HEX_CHARS[v and 0x0F])
                }
            }
        }
    }
}