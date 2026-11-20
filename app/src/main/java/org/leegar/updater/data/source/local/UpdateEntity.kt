package org.leegar.updater.data.source.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.leegar.updater.data.ProjectLink
import org.leegar.updater.data.Update
import org.leegar.updater.data.UpdatePart
import org.leegar.updater.data.UpdateStatus
import java.io.File

private val EntityJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

@Entity(tableName = "updates")
data class UpdateEntity(
    @PrimaryKey
    @ColumnInfo(name = "download_id")
    val downloadId: String,

    @ColumnInfo(name = "status")
    val status: Int,

    @ColumnInfo(name = "path")
    val path: String?,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long,

    @ColumnInfo(name = "type")
    val type: String?,

    @ColumnInfo(name = "version")
    val version: String,

    @ColumnInfo(name = "size")
    val size: Long,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "os_patch_level")
    val osPatchLevel: String?,

    @ColumnInfo(name = "os_sdk_level")
    val osSdkLevel: Int?,

    @ColumnInfo(name = "payload_metadata_offset")
    val payloadMetadataOffset: Long?,

    @ColumnInfo(name = "payload_metadata_size")
    val payloadMetadataSize: Long?,

    @ColumnInfo(name = "payload_offset")
    val payloadOffset: Long?,

    @ColumnInfo(name = "payload_size")
    val payloadSize: Long?,

    @ColumnInfo(name = "payload_properties_offset")
    val payloadPropertiesOffset: Long?,

    @ColumnInfo(name = "payload_properties_size")
    val payloadPropertiesSize: Long?,

    @ColumnInfo(name = "download_url")
    val downloadUrl: String?,

    @ColumnInfo(name = "sha256")
    val sha256: String? = null,

    @ColumnInfo(name = "parts_json")
    val partsJson: String? = null,

    @ColumnInfo(name = "changelog_url")
    val changelogUrl: String? = null,

    @ColumnInfo(name = "changelog_inline")
    val changelogInline: String? = null,

    @ColumnInfo(name = "tag")
    val tag: String? = null,

    @ColumnInfo(name = "banner")
    val banner: String? = null,

    @ColumnInfo(name = "ico_banner")
    val icoBanner: String? = null,

    @ColumnInfo(name = "links_json")
    val linksJson: String? = null,

    @ColumnInfo(name = "order_index")
    val orderIndex: Int = 0,
)

fun Update.toEntity() = UpdateEntity(
    downloadId = downloadId,
    status = status.persistentStatus,
    path = file?.absolutePath,
    timestamp = timestamp,
    type = type,
    version = version,
    size = fileSize,
    name = name,
    osPatchLevel = osPatchLevel,
    osSdkLevel = osSdkLevel,
    payloadMetadataOffset = payloadMetadataOffset,
    payloadMetadataSize = payloadMetadataSize,
    payloadOffset = payloadOffset,
    payloadSize = payloadSize,
    payloadPropertiesOffset = payloadPropertiesOffset,
    payloadPropertiesSize = payloadPropertiesSize,
    downloadUrl = downloadUrl,
    sha256 = sha256,
    partsJson = parts.takeIf { it.isNotEmpty() }?.let { EntityJson.encodeToString(it) },
    changelogUrl = changelogUrl,
    changelogInline = changelogInline,
    tag = tag,
    banner = banner,
    icoBanner = icoBanner,
    linksJson = links.takeIf { it.isNotEmpty() }?.let { EntityJson.encodeToString(it) },
    orderIndex = orderIndex,
)

fun UpdateEntity.toUpdate(): Update {
    val decodedParts = partsJson?.takeIf { it.isNotBlank() }?.let {
        runCatching { EntityJson.decodeFromString<List<UpdatePart>>(it) }.getOrNull()
    }.orEmpty()
    val decodedLinks = linksJson?.takeIf { it.isNotBlank() }?.let {
        runCatching { EntityJson.decodeFromString<List<ProjectLink>>(it) }.getOrNull()
    }.orEmpty()

    return Update(
        downloadId = downloadId,
        status = UpdateStatus.fromPersistentStatus(status),
        file = path?.let { File(it) },
        timestamp = timestamp,
        type = type,
        version = version,
        fileSize = size,
        name = name,
        osPatchLevel = osPatchLevel,
        osSdkLevel = osSdkLevel ?: 0,
        payloadMetadataOffset = payloadMetadataOffset,
        payloadMetadataSize = payloadMetadataSize,
        payloadOffset = payloadOffset,
        payloadSize = payloadSize,
        payloadPropertiesOffset = payloadPropertiesOffset,
        payloadPropertiesSize = payloadPropertiesSize,
        downloadUrl = downloadUrl,
        sha256 = sha256,
        parts = decodedParts,
        changelogUrl = changelogUrl,
        changelogInline = changelogInline,
        tag = tag,
        banner = banner,
        icoBanner = icoBanner,
        links = decodedLinks,
        orderIndex = orderIndex,
    )
}