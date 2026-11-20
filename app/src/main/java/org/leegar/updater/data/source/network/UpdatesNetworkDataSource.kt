package org.leegar.updater.data.source.network

import android.content.Context
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.leegar.updater.R
import org.leegar.updater.data.UserPreferencesRepository
import org.leegar.updater.deviceinfo.DeviceInfoUtils
import java.io.IOException
import java.net.URI
import java.util.concurrent.TimeUnit
import kotlin.math.ceil

class UpdatesNetworkDataSource(
    private val context: Context,
    private val userPreferencesRepository: UserPreferencesRepository,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val client = OkHttpClient.Builder()
        .callTimeout(15, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    fun resolveServerUrl(): String {
        val custom = userPreferencesRepository.getCustomOtaUrlBlocking().trim()
        if (custom.isNotEmpty()) return custom

        val propUri = DeviceInfoUtils.updaterUri.trim()
        if (propUri.isNotEmpty()) return propUri

        // Overlayable default: R.string.updater_server_url with {device} placeholder,
        // replaceable per-device via DeviceTree runtime resource overlay.
        val template = context.getString(R.string.updater_server_url)
        val device = DeviceInfoUtils.device.trim()
        return if (device.isNotEmpty()) template.replace("{device}", device) else template
    }

    fun fetchFeed(): NetworkFeed {
        val url = resolveServerUrl()
        val request = Request.Builder()
            .url(url)
            .header("Cache-Control", "no-cache")
            .build()

        val responseBody = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException(ServerErrorFormatter.formatOkHttpResponse(context, url, response))
            }
            response.body?.string() ?: throw IOException(context.getString(R.string.err_response_empty))
        }

        val parsed = json.decodeFromString<NetworkUpdates>(responseBody)
        return parsed.toNetworkFeed(
            githubPrefix = context.getString(R.string.link_github_prefix),
            forumTitle = context.getString(R.string.support_forum),
            githubTitle = context.getString(R.string.label_github),
            firmwareTitle = context.getString(R.string.support_firmware),
            donateTitle = context.getString(R.string.support_donate),
        )
    }
}

object ServerErrorFormatter {
    fun formatOkHttpResponse(context: Context, url: String?, response: Response): String {
        val bodySnippet = runCatching { response.peekBody(512L).string() }.getOrNull()
        val remaining = response.header("X-RateLimit-Remaining")
            ?: response.header("RateLimit-Remaining")
        val reset = response.header("X-RateLimit-Reset")
            ?: response.header("RateLimit-Reset")
        val retryAfter = response.header("Retry-After")
        return formatHttpError(context, url, response.code, remaining, reset, retryAfter, bodySnippet)
    }

    /**
     * Null-context fallback for background threads without access to Resources.
     * Prefer [formatHttpError] with a Context so user-visible text comes from resources.
     */
    @JvmStatic
    fun formatOkHttpResponse(url: String?, response: Response): String {
        val bodySnippet = runCatching { response.peekBody(512L).string() }.getOrNull()
        val remaining = response.header("X-RateLimit-Remaining")
            ?: response.header("RateLimit-Remaining")
        val reset = response.header("X-RateLimit-Reset")
            ?: response.header("RateLimit-Reset")
        val retryAfter = response.header("Retry-After")
        return formatHttpError(url, response.code, remaining, reset, retryAfter, bodySnippet)
    }

    @JvmStatic
    fun formatHttpError(
        context: Context,
        url: String?,
        code: Int,
        rateLimitRemaining: String?,
        rateLimitReset: String?,
        retryAfter: String?,
        bodySnippet: String?,
    ): String {
        val host = resolveHostLabel(context, url)
        val bodyLower = bodySnippet.orEmpty().lowercase()
        val isRateLimit = code == 429 ||
            (code == 403 && (
                rateLimitRemaining?.trim() == "0" ||
                    bodyLower.contains("rate limit") ||
                    bodyLower.contains("too many requests") ||
                    bodyLower.contains("abuse detection") ||
                    bodyLower.contains("secondary rate")
                ))

        if (isRateLimit) {
            val waitText = formatWaitHint(context, retryAfter, rateLimitReset)
            return if (waitText != null) {
                context.getString(R.string.err_rate_limit_retry, host, code, waitText)
            } else {
                context.getString(R.string.err_rate_limit_later, host, code)
            }
        }

        return when (code) {
            404 -> context.getString(R.string.err_feed_not_found, host)
            401, 403 -> context.getString(R.string.err_access_denied, host, code)
            410 -> context.getString(R.string.err_resource_removed, host)
            in 500..599 -> context.getString(R.string.err_server_unavailable, host, code)
            else -> context.getString(R.string.err_http_status, host, code)
        }
    }

    @JvmStatic
    fun formatHttpError(
        url: String?,
        code: Int,
        rateLimitRemaining: String?,
        rateLimitReset: String?,
        retryAfter: String?,
        bodySnippet: String?,
    ): String {
        val host = resolveHostLabel(null, url)
        val bodyLower = bodySnippet.orEmpty().lowercase()
        val isRateLimit = code == 429 ||
            (code == 403 && (
                rateLimitRemaining?.trim() == "0" ||
                    bodyLower.contains("rate limit") ||
                    bodyLower.contains("too many requests") ||
                    bodyLower.contains("abuse detection") ||
                    bodyLower.contains("secondary rate")
                ))

        if (isRateLimit) {
            val waitText = formatWaitHint(null, retryAfter, rateLimitReset)
            return if (waitText != null) {
                "$host: rate limit exceeded (HTTP $code). Retry in $waitText."
            } else {
                "$host: rate limit exceeded (HTTP $code). Try again later."
            }
        }

        return when (code) {
            404 -> "$host: file or feed not found (HTTP 404)"
            401, 403 -> "$host: access denied (HTTP $code)"
            410 -> "$host: resource removed (HTTP 410)"
            in 500..599 -> "$host: server unavailable (HTTP $code)"
            else -> "$host: HTTP $code"
        }
    }

    private fun resolveHostLabel(context: Context?, url: String?): String {
        val rawHost = runCatching { url?.let { URI(it).host } }.getOrNull()?.lowercase().orEmpty()
        return when {
            rawHost.contains("github") -> "GitHub"
            rawHost.contains("gitlab") -> "GitLab"
            rawHost.contains("sourceforge") -> "SourceForge"
            rawHost.contains("codeberg") -> "Codeberg"
            rawHost.isNotBlank() -> rawHost
            else -> context?.getString(R.string.err_host_server) ?: "Server"
        }
    }

    private fun formatWaitHint(context: Context?, retryAfter: String?, rateLimitReset: String?): String? {
        val retrySec = retryAfter?.trim()?.toLongOrNull()
        val resetSec = rateLimitReset?.trim()?.toLongOrNull()?.let { resetEpoch ->
            val nowSec = System.currentTimeMillis() / 1000L
            (resetEpoch - nowSec).takeIf { it > 0L }
        }
        val seconds = retrySec?.takeIf { it > 0L } ?: resetSec ?: return null
        return if (seconds >= 60L) {
            val mins = ceil(seconds / 60.0).toLong()
            context?.getString(R.string.err_wait_minutes_short, mins) ?: "~$mins min"
        } else {
            context?.getString(R.string.err_wait_seconds_short, seconds) ?: "${seconds}s"
        }
    }
}