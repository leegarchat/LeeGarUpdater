package org.leegar.updater.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.leegar.updater.data.source.local.UpdatesLocalDataSource
import org.leegar.updater.data.source.network.UpdatesNetworkDataSource
import org.leegar.updater.deviceinfo.DeviceInfoUtils
import org.leegar.updater.notifications.NotificationHelper
import org.leegar.updater.ui.media.UpdaterMediaCache
import org.leegar.updater.util.NetworkMonitor
import org.leegar.updater.util.SystemUpdateInfoPublisher

class UpdatesRepository(
    private val appContext: Context,
    private val networkMonitor: NetworkMonitor,
    private val notificationHelper: NotificationHelper,
    private val networkDataSource: UpdatesNetworkDataSource,
    private val localDataSource: UpdatesLocalDataSource,
    private val systemUpdateInfoPublisher: SystemUpdateInfoPublisher,
    private val appStateRepository: AppStateRepository,
    private val changelogRepository: ChangelogRepository,
) {
    fun observeLocalUpdates(): Flow<List<Update>> = localDataSource.observeUpdates()

    /**
     * Fetches ALL builds published in JSON (multi-version, preserving server order),
     * updates ProjectInfo & SupportInfo, and preserves already downloaded/local packages.
     */
    suspend fun fetchUpdates(): Long? {
        if (!networkMonitor.currentNetworkState.isOnline) return null

        val localUpdates = withContext(Dispatchers.IO) {
            localDataSource.getUpdates()
        }.associateBy { it.downloadId }

        val feed = withContext(Dispatchers.IO) {
            networkDataSource.fetchFeed()
        }
        appStateRepository.setSupportInfo(feed.supportInfo)
        appStateRepository.setProjectInfo(feed.projectInfo)

        val networkUpdates = feed.updates
        val networkIds = networkUpdates.map { it.downloadId }.toSet()

        val hasBrandNewNewerBuild = networkUpdates.any {
            it.downloadId !in localUpdates && it.timestamp > DeviceInfoUtils.buildDateTimestamp
        }
        if (localUpdates.isNotEmpty() && hasBrandNewNewerBuild) {
            notificationHelper.showNewUpdatesNotification()
        }

        withContext(Dispatchers.IO) {
            networkUpdates.forEach { networkUpdate ->
                val local = localUpdates[networkUpdate.downloadId]
                val merged = if (local != null && local.status.persistentStatus > 0) {
                    networkUpdate.copy(
                        status = local.status,
                        file = local.file,
                    )
                } else {
                    networkUpdate
                }
                localDataSource.addUpdate(merged)
            }

            // Only prune server entries that were NEVER downloaded (keep local & downloaded files!)
            localUpdates.values.filter { local ->
                local.downloadId !in networkIds &&
                    !local.isLocalUpdate &&
                    local.status.persistentStatus == 0
            }.forEach { stale ->
                localDataSource.removeUpdate(stale.downloadId)
            }
        }

        val refreshedChangelogs = changelogRepository.refreshChangelogs(networkUpdates)
        val mediaUrls = buildList {
            feed.projectInfo.logo?.url?.takeIf { it.isNotBlank() }?.let { add(it) }
            feed.projectInfo.description?.let { desc ->
                MD_IMAGE_URL_REGEX.findAll(desc).forEach { add(it.groupValues[1]) }
            }
            networkUpdates.forEach { u ->
                val md = refreshedChangelogs[u.downloadId] ?: u.changelogInline
                u.resolvedBannerSpec(md)?.let { add(it) }
                u.icoBanner?.let { add(it) }
                md?.let { text ->
                    MD_IMAGE_URL_REGEX.findAll(text).forEach { add(it.groupValues[1]) }
                }
            }
        }
        UpdaterMediaCache.refreshUrls(appContext, mediaUrls)

        systemUpdateInfoPublisher.publish()
        return System.currentTimeMillis()
    }

    private companion object {
        val MD_IMAGE_URL_REGEX = Regex("""!\[[^]]*]\((https?://[^)\s]+)(?:\s+"[^"]*")?\)""")
    }
}