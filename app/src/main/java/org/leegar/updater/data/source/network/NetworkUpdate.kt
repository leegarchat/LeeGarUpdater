@file:OptIn(ExperimentalSerializationApi::class)

package org.leegar.updater.data.source.network

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonIgnoreUnknownKeys
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.leegar.updater.data.ProjectInfo
import org.leegar.updater.data.ProjectLink
import org.leegar.updater.data.ProjectLogo
import org.leegar.updater.data.SupportInfo
import org.leegar.updater.data.Update
import org.leegar.updater.data.UpdatePart
import org.leegar.updater.deviceinfo.DeviceInfoUtils
import org.leegar.updater.misc.Constants

@Suppress("PROVIDED_RUNTIME_TOO_LOW")
@Serializable
@JsonIgnoreUnknownKeys
data class NetworkUpdates(
    @SerialName("project") val project: NetworkProject? = null,
    @SerialName("response") val response: List<NetworkUpdate>? = null,
    @SerialName("items") val items: List<NetworkUpdate>? = null,
) {
    val resolvedItems: List<NetworkUpdate>
        get() = response ?: items ?: emptyList()
}

@Suppress("PROVIDED_RUNTIME_TOO_LOW")
@Serializable
@JsonIgnoreUnknownKeys
data class NetworkProject(
    @SerialName("name") val name: String? = null,
    @SerialName("tagline") val tagline: String? = null,
    @SerialName("description") val description: String? = null,
    @SerialName("logo") val logo: ProjectLogo? = null,
    @SerialName("logo_url") val logoUrl: String? = null,
    @SerialName("logo_type") val logoType: String? = null,
    @SerialName("maintainer") val maintainer: String? = null,
    @SerialName("links") val links: List<ProjectLink> = emptyList(),
)

@Suppress("PROVIDED_RUNTIME_TOO_LOW")
@Serializable
@JsonIgnoreUnknownKeys
data class NetworkUpdate(
    @SerialName("id") val id: String? = null,
    @SerialName("buildtype") val buildType: String? = null,
    @SerialName("tag") val tag: String? = null,
    @SerialName("download") val download: String = "",
    @SerialName("parts") val parts: List<UpdatePart>? = null,
    @SerialName("filename") val filename: String,
    @SerialName("firmware") val firmware: String? = null,
    @SerialName("forum") val forum: String? = null,
    @SerialName("github") val github: String? = null,
    @SerialName("maintainer") val maintainer: String? = null,
    @SerialName("os_patch_level") val osPatchLevel: String? = null,
    @SerialName("os_sdk_level") val osSdkLevel: Int? = null,
    @SerialName("ota_property_files") val otaPropertyFiles: String? = null,
    @SerialName("paypal") val paypal: String? = null,
    @SerialName("sha256") val sha256: String? = null,
    @SerialName("size") val size: Long = 0L,
    @SerialName("timestamp") val timestamp: Long = 0L,
    @SerialName("version") val version: String = "",
    @SerialName("changelog_url") val changelogUrl: String? = null,
    @SerialName("changelog") val changelog: String? = null,
    @Serializable(with = FlexibleMediaStringSerializer::class)
    @SerialName("banner") val banner: String? = null,
    @Serializable(with = FlexibleMediaStringSerializer::class)
    @SerialName("ico_banner") val icoBanner: String? = null,
    @SerialName("links") val links: List<ProjectLink> = emptyList(),
)

private object FlexibleMediaStringSerializer : KSerializer<String> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("FlexibleMediaString", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): String {
        val jsonDecoder = decoder as? JsonDecoder ?: return decoder.decodeString().trim()
        return when (val element = jsonDecoder.decodeJsonElement()) {
            is JsonNull -> ""
            is JsonPrimitive -> element.content.trim()
            is JsonObject -> element.toString()
            else -> ""
        }
    }

    override fun serialize(encoder: Encoder, value: String) {
        encoder.encodeString(value)
    }
}

data class NetworkFeed(
    val projectInfo: ProjectInfo,
    val supportInfo: SupportInfo,
    val updates: List<Update>,
)

private data class PackageFileRange(
    val offset: Long,
    val size: Long,
)

private fun String.parsePackageFileRanges() =
    split(",").mapNotNull { token ->
        val segs = token.trim().split(":")
        if (segs.size < 3) return@mapNotNull null
        val off = segs[1].trim().toLongOrNull() ?: return@mapNotNull null
        val sz = segs[2].trim().toLongOrNull() ?: return@mapNotNull null
        segs[0].trim() to PackageFileRange(off, sz)
    }.toMap()

fun NetworkUpdate.toUpdate(orderIndex: Int = 0, disambiguationSuffix: String = ""): Update {
    val packageFileRanges = otaPropertyFiles?.parsePackageFileRanges().orEmpty()
    val payloadMetadataRange = packageFileRanges[Constants.AB_PAYLOAD_METADATA_PATH]
    val payloadRange = packageFileRanges[Constants.AB_PAYLOAD_BIN_PATH]
    val payloadPropertiesRange = packageFileRanges[Constants.AB_PAYLOAD_PROPERTIES_PATH]

    val validParts = parts.orEmpty().filter { it.url.isNotBlank() }
    val resolvedUrl = when {
        validParts.isNotEmpty() -> validParts.joinToString("\n") { it.url }
        download.isNotBlank() -> download.trim()
        else -> null
    }
    val computedSize = if (size > 0L) {
        size
    } else {
        validParts.mapNotNull { it.size }.takeIf { it.size == validParts.size }?.sum() ?: 0L
    }

    val baseId = id?.trim()?.takeIf { it.isNotEmpty() }
        ?: sha256?.trim()?.takeIf { it.isNotEmpty() }
        ?: "${filename}_${timestamp}"
    val finalId = if (disambiguationSuffix.isEmpty()) baseId else "${baseId}_$disambiguationSuffix"

    return Update(
        downloadId = finalId,
        name = filename,
        timestamp = timestamp,
        type = buildType?.takeIf { it.isNotBlank() },
        fileSize = computedSize,
        downloadUrl = resolvedUrl,
        version = version,
        osPatchLevel = osPatchLevel?.takeIf { it.isNotBlank() },
        osSdkLevel = osSdkLevel ?: DeviceInfoUtils.sdkLevel,
        payloadMetadataOffset = payloadMetadataRange?.offset,
        payloadMetadataSize = payloadMetadataRange?.size,
        payloadOffset = payloadRange?.offset,
        payloadSize = payloadRange?.size,
        payloadPropertiesOffset = payloadPropertiesRange?.offset,
        payloadPropertiesSize = payloadPropertiesRange?.size,
        isAvailableOnline = true,
        sha256 = sha256?.trim()?.takeIf { it.isNotEmpty() },
        parts = validParts,
        changelogUrl = changelogUrl?.trim()?.takeIf { it.isNotEmpty() },
        changelogInline = changelog?.takeIf { it.isNotBlank() },
        tag = tag?.trim()?.takeIf { it.isNotEmpty() },
        banner = banner?.trim()?.takeIf { it.isNotEmpty() },
        icoBanner = icoBanner?.trim()?.takeIf { it.isNotEmpty() },
        links = links.filter { it.url.isNotBlank() && it.title.isNotBlank() },
        orderIndex = orderIndex,
    )
}

fun NetworkUpdate.toSupportInfo() = SupportInfo(
    maintainer = maintainer?.takeIf { it.isNotBlank() },
    forum = forum?.takeIf { it.isNotBlank() },
    firmware = firmware?.takeIf { it.isNotBlank() },
    paypal = paypal?.takeIf { it.isNotBlank() },
)

fun NetworkUpdates.toNetworkFeed(
    githubPrefix: String,
    forumTitle: String,
    githubTitle: String,
    firmwareTitle: String,
    donateTitle: String,
): NetworkFeed {
    val rawItems = resolvedItems
    val primaryItem = rawItems.firstOrNull()
    val supportInfo = rawItems.maxByOrNull { it.timestamp }?.toSupportInfo() ?: SupportInfo()

    val proj = project
    val resolvedLogo = proj?.logo ?: proj?.logoUrl?.takeIf { it.isNotBlank() }?.let {
        ProjectLogo(url = it, type = proj.logoType ?: "auto")
    }
    val legacyGithubUrl = primaryItem?.github?.trim()?.takeIf { it.isNotEmpty() }?.let { raw ->
        if (raw.startsWith("http://") || raw.startsWith("https://")) raw else "$githubPrefix$raw"
    }
    val legacyLinks = buildList {
        supportInfo.forum?.let { add(ProjectLink(title = forumTitle, url = it, icon = "forum")) }
        legacyGithubUrl?.let { add(ProjectLink(title = githubTitle, url = it, icon = "github")) }
        supportInfo.firmware?.let { add(ProjectLink(title = firmwareTitle, url = it, icon = "firmware")) }
        supportInfo.paypal?.let { add(ProjectLink(title = donateTitle, url = it, icon = "donate")) }
    }
    val mergedLinks = if (!proj?.links.isNullOrEmpty()) {
        proj!!.links.filter { it.url.isNotBlank() && it.title.isNotBlank() }
    } else {
        legacyLinks
    }

    val projectInfo = ProjectInfo(
        name = proj?.name?.takeIf { it.isNotBlank() },
        tagline = proj?.tagline?.takeIf { it.isNotBlank() },
        description = proj?.description?.takeIf { it.isNotBlank() },
        logo = resolvedLogo,
        maintainer = proj?.maintainer?.takeIf { it.isNotBlank() }
            ?: supportInfo.maintainer
            ?: primaryItem?.maintainer?.takeIf { it.isNotBlank() },
        links = mergedLinks,
    )

    // Ensure unique downloadIds even if JSON lists multiple entries without distinct sha256/id
    val usedIds = HashSet<String>()
    val updates = rawItems.mapIndexed { idx, net ->
        val candidate = net.toUpdate(orderIndex = idx)
        if (usedIds.add(candidate.downloadId)) {
            candidate
        } else {
            val disambiguated = net.toUpdate(orderIndex = idx, disambiguationSuffix = idx.toString())
            usedIds.add(disambiguated.downloadId)
            disambiguated
        }
    }

    return NetworkFeed(
        projectInfo = projectInfo,
        supportInfo = supportInfo,
        updates = updates,
    )
}