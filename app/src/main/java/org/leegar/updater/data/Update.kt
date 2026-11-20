package org.leegar.updater.data

import java.io.File

enum class PartStatus {
    PENDING,
    DOWNLOADING,
    VERIFYING,
    DONE,
    PAUSED,
    ERROR,
}

data class PartDownloadState(
    val index: Int,
    val name: String,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val speedBytesPerSec: Long = 0L,
    val status: PartStatus = PartStatus.PENDING,
    val sha256Verified: Boolean = false,
) {
    val progressFraction: Float
        get() = when {
            status == PartStatus.DONE -> 1f
            totalBytes > 0L -> (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
            else -> 0f
        }

    val progressPercent: Int
        get() = (progressFraction * 100f).toInt().coerceIn(0, 100)
}

data class Update(
    val isAvailableOnline: Boolean = false,
    val downloadId: String = LOCAL_ID,
    val downloadUrl: String? = null,
    val eta: Long = 0,
    val file: File? = null,
    val fileSize: Long = 0,
    val isFinalizing: Boolean = false,
    val installProgress: Int = 0,
    val name: String = "",
    val osPatchLevel: String? = null,
    val osSdkLevel: Int = 0,
    val payloadMetadataOffset: Long? = null,
    val payloadMetadataSize: Long? = null,
    val payloadOffset: Long? = null,
    val payloadSize: Long? = null,
    val payloadPropertiesOffset: Long? = null,
    val payloadPropertiesSize: Long? = null,
    val progress: Int = 0,
    val speed: Long = 0,
    val status: UpdateStatus = UpdateStatus.UNKNOWN,
    val timestamp: Long = 0,
    val type: String? = null,
    val version: String = "",
    val sha256: String? = null,
    val parts: List<UpdatePart> = emptyList(),
    val changelogUrl: String? = null,
    val changelogInline: String? = null,
    val tag: String? = null,
    val banner: String? = null,
    val icoBanner: String? = null,
    val links: List<ProjectLink> = emptyList(),
    val orderIndex: Int = 0,
    // Runtime-only live operation metadata (not stored in Room)
    val downloadStage: String? = null,
    val currentPart: Int = 0,
    val totalParts: Int = 0,
    val partProgress: Int = 0,
    val bytesDownloaded: Long = 0L,
    val partStates: List<PartDownloadState> = emptyList(),
    val errorMessage: String? = null,
) {
    val isLocalUpdate: Boolean
        get() = downloadId == LOCAL_ID || downloadId.startsWith(LOCAL_ID_PREFIX)

    val isSplitDownload: Boolean
        get() = parts.size > 1 || downloadUrl?.contains('\n') == true

    fun resolvedBannerSpec(loadedChangelog: String? = null): String? {
        banner?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        val md = loadedChangelog?.takeIf { it.isNotBlank() }
            ?: changelogInline?.takeIf { it.isNotBlank() }
            ?: return null
        return BANNER_MD_REGEX.find(md)?.groupValues?.getOrNull(2)?.trim()?.takeIf { it.isNotEmpty() }
    }

    fun withAvailableOnline(v: Boolean) = copy(isAvailableOnline = v)
    fun withDownloadId(v: String) = copy(downloadId = v)
    fun withDownloadUrl(v: String?) = copy(downloadUrl = v)
    fun withEta(v: Long) = copy(eta = v)
    fun withFile(v: File?) = copy(file = v)
    fun withFileSize(v: Long) = copy(fileSize = v)
    fun withFinalizing(v: Boolean) = copy(isFinalizing = v)
    fun withInstallProgress(v: Int) = copy(installProgress = v)
    fun withName(v: String) = copy(name = v)
    fun withOsPatchLevel(v: String?) = copy(osPatchLevel = v)
    fun withOsSdkLevel(v: Int) = copy(osSdkLevel = v)
    fun withProgress(v: Int) = copy(progress = v)
    fun withSpeed(v: Long) = copy(speed = v)
    fun withStatus(v: UpdateStatus) = copy(status = v)
    fun withTimestamp(v: Long) = copy(timestamp = v)
    fun withType(v: String?) = copy(type = v)
    fun withVersion(v: String) = copy(version = v)
    fun withErrorMessage(v: String?) = copy(errorMessage = v)

    fun toBuilder() = Builder(this)

    class Builder(
        private var isAvailableOnline: Boolean = false,
        private var downloadId: String = LOCAL_ID,
        private var downloadUrl: String? = null,
        private var eta: Long = 0,
        private var file: File? = null,
        private var fileSize: Long = 0,
        private var isFinalizing: Boolean = false,
        private var installProgress: Int = 0,
        private var name: String = "",
        private var osPatchLevel: String? = null,
        private var osSdkLevel: Int = 0,
        private var payloadMetadataOffset: Long? = null,
        private var payloadMetadataSize: Long? = null,
        private var payloadOffset: Long? = null,
        private var payloadSize: Long? = null,
        private var payloadPropertiesOffset: Long? = null,
        private var payloadPropertiesSize: Long? = null,
        private var progress: Int = 0,
        private var speed: Long = 0,
        private var status: UpdateStatus = UpdateStatus.UNKNOWN,
        private var timestamp: Long = 0,
        private var type: String? = null,
        private var version: String = "",
        private var sha256: String? = null,
        private var parts: List<UpdatePart> = emptyList(),
        private var changelogUrl: String? = null,
        private var changelogInline: String? = null,
        private var tag: String? = null,
        private var banner: String? = null,
        private var icoBanner: String? = null,
        private var links: List<ProjectLink> = emptyList(),
        private var orderIndex: Int = 0,
        private var downloadStage: String? = null,
        private var currentPart: Int = 0,
        private var totalParts: Int = 0,
        private var partProgress: Int = 0,
        private var bytesDownloaded: Long = 0L,
        private var partStates: List<PartDownloadState> = emptyList(),
        private var errorMessage: String? = null,
    ) {
        constructor(update: Update) : this(
            isAvailableOnline = update.isAvailableOnline,
            downloadId = update.downloadId,
            downloadUrl = update.downloadUrl,
            eta = update.eta,
            file = update.file,
            fileSize = update.fileSize,
            isFinalizing = update.isFinalizing,
            installProgress = update.installProgress,
            name = update.name,
            osPatchLevel = update.osPatchLevel,
            osSdkLevel = update.osSdkLevel,
            payloadMetadataOffset = update.payloadMetadataOffset,
            payloadMetadataSize = update.payloadMetadataSize,
            payloadOffset = update.payloadOffset,
            payloadSize = update.payloadSize,
            payloadPropertiesOffset = update.payloadPropertiesOffset,
            payloadPropertiesSize = update.payloadPropertiesSize,
            progress = update.progress,
            speed = update.speed,
            status = update.status,
            timestamp = update.timestamp,
            type = update.type,
            version = update.version,
            sha256 = update.sha256,
            parts = update.parts,
            changelogUrl = update.changelogUrl,
            changelogInline = update.changelogInline,
            tag = update.tag,
            banner = update.banner,
            icoBanner = update.icoBanner,
            links = update.links,
            orderIndex = update.orderIndex,
            downloadStage = update.downloadStage,
            currentPart = update.currentPart,
            totalParts = update.totalParts,
            partProgress = update.partProgress,
            bytesDownloaded = update.bytesDownloaded,
            partStates = update.partStates,
            errorMessage = update.errorMessage,
        )

        fun setAvailableOnline(v: Boolean) = apply { isAvailableOnline = v }
        fun setDownloadId(v: String) = apply { downloadId = v }
        fun setDownloadUrl(v: String?) = apply { downloadUrl = v }
        fun setEta(v: Long) = apply { eta = v }
        fun setFile(v: File?) = apply { file = v }
        fun setFileSize(v: Long) = apply { fileSize = v }
        fun setFinalizing(v: Boolean) = apply { isFinalizing = v }
        fun setInstallProgress(v: Int) = apply { installProgress = v }
        fun setName(v: String) = apply { name = v }
        fun setOsPatchLevel(v: String?) = apply { osPatchLevel = v }
        fun setOsSdkLevel(v: Int) = apply { osSdkLevel = v }
        fun setPayloadMetadataOffset(v: Long?) = apply { payloadMetadataOffset = v }
        fun setPayloadMetadataSize(v: Long?) = apply { payloadMetadataSize = v }
        fun setPayloadOffset(v: Long?) = apply { payloadOffset = v }
        fun setPayloadSize(v: Long?) = apply { payloadSize = v }
        fun setPayloadPropertiesOffset(v: Long?) = apply { payloadPropertiesOffset = v }
        fun setPayloadPropertiesSize(v: Long?) = apply { payloadPropertiesSize = v }
        fun setProgress(v: Int) = apply { progress = v }
        fun setSpeed(v: Long) = apply { speed = v }
        fun setStatus(v: UpdateStatus) = apply { status = v }
        fun setTimestamp(v: Long) = apply { timestamp = v }
        fun setType(v: String?) = apply { type = v }
        fun setVersion(v: String) = apply { version = v }
        fun setSha256(v: String?) = apply { sha256 = v }
        fun setParts(v: List<UpdatePart>) = apply { parts = v }
        fun setChangelogUrl(v: String?) = apply { changelogUrl = v }
        fun setChangelogInline(v: String?) = apply { changelogInline = v }
        fun setTag(v: String?) = apply { tag = v }
        fun setBanner(v: String?) = apply { banner = v }
        fun setIcoBanner(v: String?) = apply { icoBanner = v }
        fun setLinks(v: List<ProjectLink>) = apply { links = v }
        fun setOrderIndex(v: Int) = apply { orderIndex = v }
        fun setDownloadStage(v: String?) = apply { downloadStage = v }
        fun setCurrentPart(v: Int) = apply { currentPart = v }
        fun setTotalParts(v: Int) = apply { totalParts = v }
        fun setPartProgress(v: Int) = apply { partProgress = v }
        fun setBytesDownloaded(v: Long) = apply { bytesDownloaded = v }
        fun setPartStates(v: List<PartDownloadState>) = apply { partStates = v }
        fun setErrorMessage(v: String?) = apply { errorMessage = v }

        fun build() = Update(
            isAvailableOnline = isAvailableOnline,
            downloadId = downloadId,
            downloadUrl = downloadUrl,
            eta = eta,
            file = file,
            fileSize = fileSize,
            isFinalizing = isFinalizing,
            installProgress = installProgress,
            name = name,
            osPatchLevel = osPatchLevel,
            osSdkLevel = osSdkLevel,
            payloadMetadataOffset = payloadMetadataOffset,
            payloadMetadataSize = payloadMetadataSize,
            payloadOffset = payloadOffset,
            payloadSize = payloadSize,
            payloadPropertiesOffset = payloadPropertiesOffset,
            payloadPropertiesSize = payloadPropertiesSize,
            progress = progress,
            speed = speed,
            status = status,
            timestamp = timestamp,
            type = type,
            version = version,
            sha256 = sha256,
            parts = parts,
            changelogUrl = changelogUrl,
            changelogInline = changelogInline,
            tag = tag,
            banner = banner,
            icoBanner = icoBanner,
            links = links,
            orderIndex = orderIndex,
            downloadStage = downloadStage,
            currentPart = currentPart,
            totalParts = totalParts,
            partProgress = partProgress,
            bytesDownloaded = bytesDownloaded,
            partStates = partStates,
            errorMessage = errorMessage,
        )
    }

    fun hasPayloadFileRanges(): Boolean =
        payloadOffset != null && payloadSize != null &&
            payloadPropertiesOffset != null && payloadPropertiesSize != null

    fun hasFullyDownloadedPackage(): Boolean =
        file?.let { it.exists() && (fileSize <= 0L || it.length() >= fileSize) && it.length() > 0L } == true

    fun hasVerifiedPackage(): Boolean =
        status.hasVerifiedPackage() && file?.exists() == true

    companion object {
        const val LOCAL_ID = "local"
        const val LOCAL_ID_PREFIX = "local_"

        const val STAGE_DOWNLOADING = "DOWNLOADING"
        const val STAGE_VERIFYING_PART = "VERIFYING_PART"
        const val STAGE_MERGING = "MERGING"
        const val STAGE_VERIFYING_FINAL = "VERIFYING_FINAL"

        private val BANNER_MD_REGEX =
            Regex("""!\[([^]]*banner[^]]*)]\(([^)\s]+)(?:\s+"[^"]*")?\)""", RegexOption.IGNORE_CASE)
    }
}