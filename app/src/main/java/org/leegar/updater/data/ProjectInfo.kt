package org.leegar.updater.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

@Serializable
data class ProjectInfo(
    val name: String? = null,
    val tagline: String? = null,
    val description: String? = null,
    val logo: ProjectLogo? = null,
    val maintainer: String? = null,
    val links: List<ProjectLink> = emptyList(),
)

@Serializable
data class ProjectLogo(
    val url: String,
    /** "auto", "image", "gif", "video" */
    val type: String = "auto",
    @SerialName("aspect_ratio") val aspectRatio: Float? = null,
    val loop: Boolean = true,
)

@Serializable
data class ProjectLink(
    val title: String,
    val url: String,
    /** "forum", "firmware", "donate", "telegram", "github", "web", "mirror" */
    val icon: String? = null,
)

@Serializable(with = UpdatePartSerializer::class)
data class UpdatePart(
    val url: String,
    val sha256: String? = null,
    val size: Long? = null,
)

/**
 * Accepts both legacy string entries ("https://.../rom.zip.001")
 * and extended objects ({"url": "...", "sha256": "...", "size": 123456}).
 */
object UpdatePartSerializer : KSerializer<UpdatePart> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("UpdatePart", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): UpdatePart {
        val jsonDecoder = decoder as? JsonDecoder
            ?: return UpdatePart(url = decoder.decodeString())
        return when (val element = jsonDecoder.decodeJsonElement()) {
            is JsonPrimitive -> UpdatePart(url = element.content.trim())
            is JsonObject -> UpdatePart(
                url = element["url"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty(),
                sha256 = element["sha256"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() },
                size = element["size"]?.jsonPrimitive?.longOrNull,
            )
            else -> UpdatePart(url = "")
        }
    }

    override fun serialize(encoder: Encoder, value: UpdatePart) {
        val jsonEncoder = encoder as? JsonEncoder
        if (jsonEncoder == null) {
            encoder.encodeString(value.url)
            return
        }
        val obj = buildJsonObject {
            put("url", value.url)
            value.sha256?.let { put("sha256", it) }
            value.size?.let { put("size", it) }
        }
        jsonEncoder.encodeJsonElement(obj)
    }
}