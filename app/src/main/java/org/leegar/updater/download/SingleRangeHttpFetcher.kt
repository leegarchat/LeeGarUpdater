/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.leegar.updater.download

import android.content.Context
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.HttpURLConnection
import java.util.Locale
import java.util.concurrent.TimeUnit
import org.leegar.updater.R

class SingleRangeHttpFetcher(
    private val url: String,
    private val appContext: Context? = null,
) {

    private fun err(resId: Int, fallback: String, vararg args: Any): String {
        val ctx = appContext
        return if (ctx != null) ctx.getString(resId, *args)
        else String.format(Locale.US, fallback, *args)
    }

    private val client = OkHttpClient.Builder()
        .callTimeout(10, TimeUnit.SECONDS)
        .build()

    @Throws(IOException::class)
    fun download(offset: Long, size: Long): ByteArray = try {
        val endOffset = offset + size - 1
        val request = Request.Builder()
            .url(url)
            .header("Range", "bytes=$offset-$endOffset")
            .header("User-Agent", System.getProperty("http.agent")!!)
            .build()

        client.newCall(request).execute().use { response ->
            if (response.code != HttpURLConnection.HTTP_PARTIAL) {
                throw IOException(
                    err(R.string.err_no_range, "Server does not support range requests: expected HTTP 206, got %1\$d", response.code)
                )
            }

            val contentRange = response.header("Content-Range")
                ?: throw IOException(err(R.string.err_no_content_range, "Missing Content-Range header for %1\$s", url))
            val expectedContentRangePrefix = "bytes $offset-$endOffset/"
            if (!contentRange.startsWith(expectedContentRangePrefix)) {
                throw IOException(
                    err(R.string.err_bad_content_range, "Unexpected Content-Range for %1\$s: expected %2\$s", url, "$expectedContentRangePrefix*, got $contentRange")
                )
            }

            response.body!!.source().readByteArray(size)
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to download range offset=$offset size=$size", e)
        throw e
    }

    companion object {
        private const val TAG = "SingleRangeHttpFetcher"
    }
}
