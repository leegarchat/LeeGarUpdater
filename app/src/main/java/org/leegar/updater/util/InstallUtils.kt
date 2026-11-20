package org.leegar.updater.util

import org.leegar.updater.data.Update
import org.leegar.updater.deviceinfo.DeviceInfoUtils
import java.io.File

object InstallUtils {
    @JvmStatic
    fun isScratchMounted() = runCatching {
        File("/proc/mounts").useLines { lines ->
            lines.any { it.split(" ")[1] == "/mnt/scratch" }
        }
    }.getOrDefault(false)

    enum class BlockedReason {
        NONE, DOWNGRADE, VERSION_UNSUPPORTED
    }

    @JvmStatic
    fun getBlockedReason(update: Update): BlockedReason = getBlockedReason(update, unsafeMode = false)

    @JvmStatic
    fun getBlockedReason(update: Update, unsafeMode: Boolean): BlockedReason {
        if (unsafeMode) return BlockedReason.NONE
        return when {
            !DeviceInfoUtils.isDowngradingAllowed &&
                (update.timestamp < DeviceInfoUtils.buildDateTimestamp ||
                    (update.osSdkLevel > 0 && update.osSdkLevel < DeviceInfoUtils.sdkLevel)) ->
                BlockedReason.DOWNGRADE

            !DeviceInfoUtils.isMajorUpdateAllowed &&
                update.osSdkLevel > DeviceInfoUtils.sdkLevel ->
                BlockedReason.VERSION_UNSUPPORTED

            else -> BlockedReason.NONE
        }
    }

    @JvmStatic
    fun canInstall(update: Update): Boolean = canInstall(update, unsafeMode = false)

    @JvmStatic
    fun canInstall(update: Update, unsafeMode: Boolean): Boolean =
        getBlockedReason(update, unsafeMode) == BlockedReason.NONE

    @JvmStatic
    fun canStreamUpdate(
        update: Update,
        streamUpdatesEnabled: Boolean,
        forceRecovery: Boolean = false,
    ): Boolean =
        DeviceInfoUtils.isABDevice &&
            streamUpdatesEnabled &&
            !forceRecovery &&
            update.isAvailableOnline &&
            !update.isSplitDownload &&
            update.hasPayloadFileRanges() &&
            !update.hasFullyDownloadedPackage() &&
            !update.hasVerifiedPackage()
}