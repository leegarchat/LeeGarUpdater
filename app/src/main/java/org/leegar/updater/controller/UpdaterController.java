package org.leegar.updater.controller;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;

import org.leegar.updater.UpdaterApplication;
import org.leegar.updater.R;
import org.leegar.updater.data.PartDownloadState;
import org.leegar.updater.data.PartStatus;
import org.leegar.updater.data.Update;
import java.util.Collections;
import org.leegar.updater.data.UpdateStatus;
import org.leegar.updater.data.UserPreferencesRepository;
import org.leegar.updater.data.source.local.UpdatesDatabase;
import org.leegar.updater.data.source.local.UpdatesLocalDataSource;
import org.leegar.updater.download.DownloadClient;
import org.leegar.updater.download.SplitHttpDownloadClient;
import org.leegar.updater.misc.Utils;
import org.leegar.updater.util.SystemUpdateInfoPublisher;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class UpdaterController {

    public static final String ACTION_DOWNLOAD_PROGRESS = "action_download_progress";
    public static final String ACTION_INSTALL_PROGRESS = "action_install_progress";
    public static final String ACTION_UPDATE_REMOVED = "action_update_removed";
    public static final String ACTION_UPDATE_STATUS = "action_update_status_change";
    public static final String ACTION_DOWNLOAD_ERROR = "action_download_error";
    public static final String EXTRA_DOWNLOAD_ID = "extra_download_id";
    public static final String EXTRA_ERROR_MESSAGE = "extra_error_message";

    private final String TAG = "UpdaterController";

    private static UpdaterController sUpdaterController;

    private static final int MAX_REPORT_INTERVAL_MS = 350;

    private final Context mContext;
    private final UpdatesLocalDataSource mUpdatesLocalDataSource;
    private final UserPreferencesRepository mUserPreferencesRepository;
    private final SystemUpdateInfoPublisher mSystemUpdateInfoPublisher;

    private final PowerManager.WakeLock mWakeLock;
    private final File mDownloadRoot;

    private int mActiveDownloads = 0;
    private final Set<String> mVerifyingUpdates = ConcurrentHashMap.newKeySet();
    private final Set<String> mAutoInstallIds = ConcurrentHashMap.newKeySet();

    public static synchronized UpdaterController getInstance(Context context) {
        if (sUpdaterController == null) {
            UpdaterApplication application = (UpdaterApplication) context.getApplicationContext();
            sUpdaterController = new UpdaterController(context,
                    application.getUserPreferencesRepository(),
                    application.getSystemUpdateInfoPublisher());
        }
        return sUpdaterController;
    }

    public static synchronized UpdaterController peekInstance() {
        return sUpdaterController;
    }

    private UpdaterController(Context context, UserPreferencesRepository userPreferencesRepository,
            SystemUpdateInfoPublisher systemUpdateInfoPublisher) {
        mUpdatesLocalDataSource =
                new UpdatesLocalDataSource(UpdatesDatabase.getInstance(context).updateDao());
        mUserPreferencesRepository = userPreferencesRepository;
        mSystemUpdateInfoPublisher = systemUpdateInfoPublisher;
        mDownloadRoot = Utils.getDownloadPath(context);
        PowerManager powerManager = context.getSystemService(PowerManager.class);
        mWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "LGU:wakelock");
        mWakeLock.setReferenceCounted(false);
        mContext = context.getApplicationContext();

        new Thread(() -> {
            Utils.cleanupDownloadsDir(context, userPreferencesRepository);
            for (Update update : mUpdatesLocalDataSource.getUpdates()) {
                addUpdate(update, false);
            }
            mSystemUpdateInfoPublisher.publish();
        }).start();
    }

    private static class DownloadEntry {
        Update mUpdate;
        DownloadClient mDownloadClient;

        private DownloadEntry(Update update) {
            mUpdate = update;
        }
    }

    private final Map<String, DownloadEntry> mDownloads = new ConcurrentHashMap<>();

    void notifyUpdateChange(String downloadId) {
        Intent intent = new Intent();
        intent.setAction(ACTION_UPDATE_STATUS);
        intent.setPackage(mContext.getPackageName());
        intent.putExtra(EXTRA_DOWNLOAD_ID, downloadId);
        mContext.sendBroadcast(intent);
        mSystemUpdateInfoPublisher.publish();
    }

    void notifyUpdateDelete(String downloadId) {
        Intent intent = new Intent();
        intent.setAction(ACTION_UPDATE_REMOVED);
        intent.setPackage(mContext.getPackageName());
        intent.putExtra(EXTRA_DOWNLOAD_ID, downloadId);
        mContext.sendBroadcast(intent);
        mSystemUpdateInfoPublisher.publish();
    }

    void notifyDownloadProgress(String downloadId) {
        Intent intent = new Intent();
        intent.setAction(ACTION_DOWNLOAD_PROGRESS);
        intent.setPackage(mContext.getPackageName());
        intent.putExtra(EXTRA_DOWNLOAD_ID, downloadId);
        mContext.sendBroadcast(intent);
    }

    void notifyInstallProgress(String downloadId) {
        Intent intent = new Intent();
        intent.setAction(ACTION_INSTALL_PROGRESS);
        intent.setPackage(mContext.getPackageName());
        intent.putExtra(EXTRA_DOWNLOAD_ID, downloadId);
        mContext.sendBroadcast(intent);
    }

    void notifyDownloadError(String downloadId, String errorMessage) {
        Intent intent = new Intent();
        intent.setAction(ACTION_DOWNLOAD_ERROR);
        intent.setPackage(mContext.getPackageName());
        intent.putExtra(EXTRA_DOWNLOAD_ID, downloadId);
        intent.putExtra(EXTRA_ERROR_MESSAGE, errorMessage);
        mContext.sendBroadcast(intent);
    }

    private void tryReleaseWakelock() {
        if (!hasActiveDownloads() && mWakeLock.isHeld()) {
            mWakeLock.release();
        }
    }

    private void addDownloadClient(DownloadEntry entry, DownloadClient downloadClient) {
        if (entry.mDownloadClient != null) {
            return;
        }
        entry.mDownloadClient = downloadClient;
        mActiveDownloads++;
    }

    private void removeDownloadClient(DownloadEntry entry) {
        if (entry.mDownloadClient == null) {
            return;
        }
        entry.mDownloadClient = null;
        mActiveDownloads = Math.max(0, mActiveDownloads - 1);
    }

    private DownloadClient.DownloadCallback getDownloadCallback(final String downloadId) {
        return new DownloadClient.DownloadCallback() {

            @Override
            public void onResponse(DownloadClient.Headers headers) {
                final DownloadEntry entry = mDownloads.get(downloadId);
                if (entry == null) {
                    return;
                }
                final Update newUpdate;
                synchronized (entry) {
                    if (entry.mDownloadClient == null) {
                        return;
                    }
                    final Update update = entry.mUpdate;
                    Update.Builder builder = update.toBuilder();
                    String contentLength = headers.get("Content-Length");
                    if (contentLength != null) {
                        try {
                            long size = Long.parseLong(contentLength);
                            if (update.getFileSize() < size) {
                                builder.setFileSize(size);
                            }
                        } catch (NumberFormatException e) {
                            Log.e(TAG, "Could not parse content-length");
                        }
                    }
                    builder.setStatus(UpdateStatus.DOWNLOADING);
                    builder.setErrorMessage(null);
                    newUpdate = builder.build();
                    entry.mUpdate = newUpdate;
                }
                new Thread(() -> mUpdatesLocalDataSource.addUpdate(newUpdate)).start();
                notifyUpdateChange(downloadId);
            }

            @Override
            public void onSuccess() {
                Log.d(TAG, "Download complete: " + downloadId);
                DownloadEntry entry = mDownloads.get(downloadId);
                if (entry != null) {
                    synchronized (entry) {
                        if (entry.mDownloadClient == null) {
                            tryReleaseWakelock();
                            return;
                        }
                        entry.mUpdate = entry.mUpdate.toBuilder()
                                .setStatus(UpdateStatus.VERIFYING)
                                .setDownloadStage(Update.STAGE_VERIFYING_FINAL)
                                .setProgress(100)
                                .setErrorMessage(null)
                                .build();
                    }
                    removeDownloadClient(entry);
                    verifyUpdateAsync(downloadId);
                    notifyUpdateChange(downloadId);
                    tryReleaseWakelock();
                }
            }

            @Override
            public void onFailure(boolean cancelled) {
                onFailure(cancelled, null);
            }

            @Override
            public void onFailure(boolean cancelled, String errorMessage) {
                if (cancelled) {
                    Log.d(TAG, "Download cancelled/paused: " + downloadId);
                } else {
                    DownloadEntry entry = mDownloads.get(downloadId);
                    if (entry != null) {
                        boolean hasDownloadedData;
                        Update oldUpdate;
                        synchronized (entry) {
                            if (entry.mDownloadClient == null) {
                                tryReleaseWakelock();
                                return;
                            }
                            Log.e(TAG, "Download failed: " + errorMessage);
                            removeDownloadClient(entry);
                            oldUpdate = entry.mUpdate;
                            File f = oldUpdate.getFile();
                            hasDownloadedData = oldUpdate.getBytesDownloaded() > 0L
                                    || (f != null && f.exists() && f.length() > 0L)
                                    || SplitHttpDownloadClient.hasDownloadedPartBytes(mDownloadRoot, downloadId);

                            if (!hasDownloadedData) {
                                mAutoInstallIds.remove(downloadId);
                                entry.mUpdate = oldUpdate.toBuilder()
                                        .setStatus(UpdateStatus.DELETED)
                                        .setFile(null)
                                        .setProgress(0)
                                        .setEta(0)
                                        .setSpeed(0)
                                        .setDownloadStage(null)
                                        .setCurrentPart(0)
                                        .setTotalParts(0)
                                        .setPartProgress(0)
                                        .setBytesDownloaded(0L)
                                        .setPartStates(Collections.emptyList())
                                        .setErrorMessage(null)
                                        .build();
                            } else {
                                entry.mUpdate = oldUpdate.toBuilder()
                                        .setStatus(UpdateStatus.PAUSED_ERROR)
                                        .setErrorMessage(errorMessage)
                                        .setSpeed(0)
                                        .setEta(0)
                                        .build();
                            }
                        }

                        if (!hasDownloadedData) {
                            deleteUpdateAsync(oldUpdate);
                            if (!oldUpdate.isAvailableOnline() || oldUpdate.isLocalUpdate()) {
                                mDownloads.remove(downloadId);
                                notifyUpdateDelete(downloadId);
                            } else {
                                notifyUpdateChange(downloadId);
                            }
                        } else {
                            notifyUpdateChange(downloadId);
                        }
                        if (errorMessage != null && !errorMessage.isEmpty()) {
                            notifyDownloadError(downloadId, errorMessage);
                        }
                    }
                }
                tryReleaseWakelock();
            }
        };
    }

    private DownloadClient.ProgressListener getProgressListener(final String downloadId) {
        return new DownloadClient.ProgressListener() {
            private long mLastUpdate = 0;
            private int mProgress = -1;

            @Override
            public void update(long bytesRead, long contentLength, long speed, long eta) {
                DownloadEntry entry = mDownloads.get(downloadId);
                if (entry == null || entry.mDownloadClient == null) {
                    return;
                }
                Update update = entry.mUpdate;
                if (contentLength <= 0) {
                    contentLength = update.getFileSize();
                }
                if (contentLength <= 0) {
                    return;
                }
                final long now = SystemClock.elapsedRealtime();
                int progress = Math.min(100, Math.max(0, Math.round(bytesRead * 100f / contentLength)));
                if (progress != mProgress || (now - mLastUpdate) > MAX_REPORT_INTERVAL_MS) {
                    mProgress = progress;
                    mLastUpdate = now;
                    synchronized (entry) {
                        if (entry.mDownloadClient == null) {
                            return;
                        }
                        entry.mUpdate = entry.mUpdate.toBuilder()
                                .setProgress(progress)
                                .setBytesDownloaded(bytesRead)
                                .setEta(eta)
                                .setSpeed(speed)
                                .build();
                    }
                    notifyDownloadProgress(downloadId);
                }
            }

            @Override
            public void updateStage(String stage, int partIndex, int partCount, int partPercent) {
                DownloadEntry entry = mDownloads.get(downloadId);
                if (entry == null || entry.mDownloadClient == null) {
                    return;
                }
                synchronized (entry) {
                    if (entry.mDownloadClient == null) {
                        return;
                    }
                    entry.mUpdate = entry.mUpdate.toBuilder()
                            .setDownloadStage(stage)
                            .setCurrentPart(partIndex)
                            .setTotalParts(partCount)
                            .setPartProgress(partPercent)
                            .build();
                }
                notifyDownloadProgress(downloadId);
            }

            @Override
            public void updateParts(List<PartDownloadState> states) {
                DownloadEntry entry = mDownloads.get(downloadId);
                if (entry == null || entry.mDownloadClient == null) {
                    return;
                }
                synchronized (entry) {
                    if (entry.mDownloadClient == null) {
                        return;
                    }
                    entry.mUpdate = entry.mUpdate.toBuilder()
                            .setPartStates(states != null ? states : Collections.emptyList())
                            .build();
                }
                notifyDownloadProgress(downloadId);
            }
        };
    }

    @SuppressLint("SetWorldReadable")
    private void verifyUpdateAsync(final String downloadId) {
        mVerifyingUpdates.add(downloadId);
        new Thread(() -> {
            DownloadEntry entry = mDownloads.get(downloadId);
            if (entry != null) {
                Update update = entry.mUpdate;
                File file = update.getFile();
                boolean unsafeMode = mUserPreferencesRepository.getUnsafeInstallBlocking();
                boolean verifyHashes = mUserPreferencesRepository.getVerifyHashesBlocking();

                String failReason = null;
                boolean ok = file != null && file.exists() && file.length() > 0;
                if (!ok) {
                    failReason = mContext.getString(R.string.err_download_file_missing);
                }

                // 1. Check SHA-256 for single-file downloads (split client already verifies on merge)
                if (ok && verifyHashes && !update.isSplitDownload()
                        && update.getSha256() != null && !update.getSha256().isEmpty()) {
                    try {
                        String actual = SplitHttpDownloadClient.computeFileSha256(file);
                        if (!actual.equalsIgnoreCase(update.getSha256().trim())) {
                            ok = false;
                            failReason = mContext.getString(R.string.err_sha_mismatch);
                            Log.e(TAG, "SHA-256 mismatch: expected=" + update.getSha256()
                                    + " actual=" + actual);
                        }
                    } catch (Exception e) {
                        ok = false;
                        failReason = mContext.getString(R.string.err_sha_calc_failed);
                        Log.e(TAG, failReason, e);
                    }
                }

                // 2. Platform OTA signature verification (skipped in Unsafe / Force mode)
                if (ok && !unsafeMode) {
                    if (!verifyPackage(file)) {
                        ok = false;
                        failReason = mContext.getString(R.string.err_ota_signature_failed);
                    }
                }

                if (ok) {
                    //noinspection ResultOfMethodCallIgnored
                    file.setReadable(true, false);
                    final Update verifiedUpdate;
                    synchronized (entry) {
                        verifiedUpdate = entry.mUpdate.toBuilder()
                                .setStatus(UpdateStatus.VERIFIED)
                                .setDownloadStage(null)
                                .setErrorMessage(null)
                                .setProgress(100)
                                .build();
                        entry.mUpdate = verifiedUpdate;
                    }
                    mUpdatesLocalDataSource.addUpdate(verifiedUpdate);
                    mVerifyingUpdates.remove(downloadId);
                    notifyUpdateChange(downloadId);

                    if (mAutoInstallIds.remove(downloadId)) {
                        Utils.triggerUpdate(mContext, downloadId);
                    }
                } else {
                    mAutoInstallIds.remove(downloadId);
                    if (file != null && file.exists()) {
                        //noinspection ResultOfMethodCallIgnored
                        file.delete();
                    }
                    SplitHttpDownloadClient.cleanupParts(mDownloadRoot, downloadId);
                    if (!update.isAvailableOnline()) {
                        mUpdatesLocalDataSource.removeUpdate(downloadId);
                    } else {
                        mUpdatesLocalDataSource.changeStatus(downloadId, UpdateStatus.UNKNOWN);
                    }
                    synchronized (entry) {
                        entry.mUpdate = entry.mUpdate.toBuilder()
                                .setProgress(0)
                                .setDownloadStage(null)
                                .setCurrentPart(0)
                                .setTotalParts(0)
                                .setPartProgress(0)
                                .setErrorMessage(failReason)
                                .setStatus(UpdateStatus.VERIFICATION_FAILED)
                                .build();
                    }
                    mVerifyingUpdates.remove(downloadId);
                    notifyUpdateChange(downloadId);
                }
            } else {
                mVerifyingUpdates.remove(downloadId);
            }
        }).start();
    }

    private boolean verifyPackage(File file) {
        try {
            android.os.RecoverySystem.verifyPackage(file, null, null);
            Log.d(TAG, "OTA signature verification successful");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "OTA signature verification failed", e);
            return false;
        }
    }

    public void setUpdatesAvailableOnline(List<String> downloadIds, boolean purgeList) {
        List<String> toRemove = new ArrayList<>();
        for (DownloadEntry entry : mDownloads.values()) {
            boolean online = downloadIds.contains(entry.mUpdate.getDownloadId());
            entry.mUpdate = entry.mUpdate.withAvailableOnline(online);
            if (!online && purgeList &&
                    !entry.mUpdate.isLocalUpdate() &&
                    entry.mUpdate.getStatus().getPersistentStatus() == 0) {
                toRemove.add(entry.mUpdate.getDownloadId());
            }
        }
        for (String downloadId : toRemove) {
            Log.d(TAG, downloadId + " no longer available online, removing");
            mDownloads.remove(downloadId);
            notifyUpdateDelete(downloadId);
        }
    }

    public boolean addUpdate(Update update) {
        return addUpdate(update, true);
    }

    public void addLocalUpdate(Update update) {
        Update localUpdate = update.withAvailableOnline(false);
        mDownloads.put(localUpdate.getDownloadId(), new DownloadEntry(localUpdate));
        mUpdatesLocalDataSource.addUpdate(localUpdate);
        notifyUpdateChange(localUpdate.getDownloadId());
    }

    public boolean addUpdate(final Update updateInfo, boolean availableOnline) {
        if (mDownloads.containsKey(updateInfo.getDownloadId())) {
            DownloadEntry entry = mDownloads.get(updateInfo.getDownloadId());
            if (entry != null) {
                synchronized (entry) {
                    entry.mUpdate = entry.mUpdate.toBuilder()
                            .setAvailableOnline(availableOnline)
                            .setDownloadUrl(updateInfo.getDownloadUrl())
                            .setOsPatchLevel(updateInfo.getOsPatchLevel())
                            .setOsSdkLevel(updateInfo.getOsSdkLevel())
                            .setPayloadMetadataOffset(updateInfo.getPayloadMetadataOffset())
                            .setPayloadMetadataSize(updateInfo.getPayloadMetadataSize())
                            .setPayloadOffset(updateInfo.getPayloadOffset())
                            .setPayloadSize(updateInfo.getPayloadSize())
                            .setPayloadPropertiesOffset(updateInfo.getPayloadPropertiesOffset())
                            .setPayloadPropertiesSize(updateInfo.getPayloadPropertiesSize())
                            .setSha256(updateInfo.getSha256())
                            .setParts(updateInfo.getParts())
                            .setChangelogUrl(updateInfo.getChangelogUrl())
                            .setChangelogInline(updateInfo.getChangelogInline())
                            .setTag(updateInfo.getTag())
                            .setBanner(updateInfo.getBanner())
                            .setIcoBanner(updateInfo.getIcoBanner())
                            .setLinks(updateInfo.getLinks())
                            .setOrderIndex(updateInfo.getOrderIndex())
                            .build();
                }
            }
            return false;
        }
        Update.Builder builder = updateInfo.toBuilder();
        if (updateInfo.getStatus().hasVerifiedPackage()) {
            boolean isLocallyValid = updateInfo.getFile() != null && updateInfo.getFile().exists();
            if (!isLocallyValid) {
                builder.setStatus(UpdateStatus.UPDATE_AVAILABLE);
                builder.setProgress(0);
                if (!availableOnline) {
                    deleteUpdateAsync(updateInfo);
                    return false;
                }
            }
        } else if (updateInfo.getStatus() == UpdateStatus.PAUSED ||
                updateInfo.getStatus() == UpdateStatus.PAUSED_ERROR) {
            boolean hasSingleFile = updateInfo.getFile() != null && updateInfo.getFile().exists();
            boolean hasSplitParts = SplitHttpDownloadClient.hasPartialParts(
                    mDownloadRoot, updateInfo.getDownloadId());
            if (!hasSingleFile && !hasSplitParts) {
                builder.setStatus(UpdateStatus.UPDATE_AVAILABLE);
                builder.setProgress(0);
                if (!availableOnline) {
                    deleteUpdateAsync(updateInfo);
                    return false;
                }
            } else {
                builder.setStatus(UpdateStatus.PAUSED);
                if (hasSplitParts) {
                    List<PartDownloadState> inspected =
                            SplitHttpDownloadClient.inspectPartStates(mDownloadRoot, updateInfo);
                    builder.setPartStates(inspected);
                    builder.setTotalParts(inspected.size());
                    long sumDl = 0;
                    for (PartDownloadState ps : inspected) sumDl += ps.getDownloadedBytes();
                    builder.setBytesDownloaded(sumDl);
                    if (updateInfo.getFileSize() > 0) {
                        builder.setProgress(Math.min(100, Math.round(sumDl * 100f / updateInfo.getFileSize())));
                    }
                } else if (hasSingleFile && updateInfo.getFileSize() > 0) {
                    long len = updateInfo.getFile().length();
                    builder.setBytesDownloaded(len);
                    int progress = Math.round(len * 100f / updateInfo.getFileSize());
                    builder.setProgress(Math.min(100, progress));
                }
            }
        }
        builder.setAvailableOnline(availableOnline);
        mDownloads.put(updateInfo.getDownloadId(), new DownloadEntry(builder.build()));
        return true;
    }

    public void startDownload(String downloadId) {
        startDownload(downloadId, false);
    }

    @SuppressLint("WakelockTimeout")
    public void startDownload(String downloadId, boolean autoInstall) {
        Log.d(TAG, "Starting " + downloadId + " (autoInstall=" + autoInstall + ")");
        if (!mDownloads.containsKey(downloadId) || isDownloading(downloadId)) {
            return;
        }
        pauseActiveDownloads();
        DownloadEntry entry = mDownloads.get(downloadId);
        if (entry == null) {
            return;
        }
        if (autoInstall) {
            mAutoInstallIds.add(downloadId);
        } else {
            mAutoInstallIds.remove(downloadId);
        }
        Update update = entry.mUpdate;
        if (!mDownloadRoot.exists()) {
            //noinspection ResultOfMethodCallIgnored
            mDownloadRoot.mkdirs();
        }
        File destination = new File(mDownloadRoot, update.getName());
        if (destination.exists()) {
            destination = Utils.appendSequentialNumber(destination);
        }
        entry.mUpdate = update.toBuilder()
                .setFile(destination)
                .setErrorMessage(null)
                .setProgress(0)
                .setCurrentPart(0)
                .setTotalParts(update.getParts().size())
                .setPartProgress(0)
                .build();

        DownloadClient downloadClient;
        try {
            downloadClient = new DownloadClient.Builder()
                    .setDownloadId(downloadId)
                    .setUrl(entry.mUpdate.getDownloadUrl())
                    .setParts(entry.mUpdate.getParts())
                    .setExpectedSha256(entry.mUpdate.getSha256())
                    .setVerifyHashes(mUserPreferencesRepository.getVerifyHashesBlocking())
                    .setParallelSplit(mUserPreferencesRepository.getParallelSplitBlocking())
                    .setDestination(entry.mUpdate.getFile())
                    .setDownloadCallback(getDownloadCallback(downloadId))
                    .setProgressListener(getProgressListener(downloadId))
                    .setUseDuplicateLinks(true)
                    .setContext(mContext)
                    .build();
        } catch (IOException exception) {
            Log.e(TAG, "Could not build download client", exception);
            entry.mUpdate = entry.mUpdate.toBuilder()
                    .setStatus(UpdateStatus.PAUSED_ERROR)
                    .setErrorMessage(exception.getMessage())
                    .build();
            notifyUpdateChange(downloadId);
            return;
        }
        addDownloadClient(entry, downloadClient);
        entry.mUpdate = entry.mUpdate.withStatus(UpdateStatus.STARTING);
        notifyUpdateChange(downloadId);
        downloadClient.start();
        mWakeLock.acquire();
    }

    @SuppressLint("WakelockTimeout")
    public void resumeDownload(String downloadId) {
        Log.d(TAG, "Resuming " + downloadId);
        if (!mDownloads.containsKey(downloadId) || isDownloading(downloadId)) {
            return;
        }
        pauseActiveDownloads();
        DownloadEntry entry = mDownloads.get(downloadId);
        if (entry == null) {
            return;
        }
        Update update = entry.mUpdate;
        File file = update.getFile();
        if (file == null) {
            file = new File(mDownloadRoot, update.getName());
            update = update.withFile(file);
            entry.mUpdate = update;
        }

        boolean isSplit = update.isSplitDownload();
        boolean hasPartialData = file.exists()
                || (isSplit && SplitHttpDownloadClient.hasPartialParts(mDownloadRoot, downloadId));
        if (!hasPartialData) {
            startDownload(downloadId, mAutoInstallIds.contains(downloadId));
            return;
        }

        if (!isSplit && isFullyDownloaded(update)) {
            entry.mUpdate = entry.mUpdate.withStatus(UpdateStatus.VERIFYING);
            verifyUpdateAsync(downloadId);
            notifyUpdateChange(downloadId);
            return;
        }

        DownloadClient downloadClient;
        try {
            downloadClient = new DownloadClient.Builder()
                    .setDownloadId(downloadId)
                    .setUrl(update.getDownloadUrl())
                    .setParts(update.getParts())
                    .setExpectedSha256(update.getSha256())
                    .setVerifyHashes(mUserPreferencesRepository.getVerifyHashesBlocking())
                    .setParallelSplit(mUserPreferencesRepository.getParallelSplitBlocking())
                    .setDestination(file)
                    .setDownloadCallback(getDownloadCallback(downloadId))
                    .setProgressListener(getProgressListener(downloadId))
                    .setUseDuplicateLinks(true)
                    .setContext(mContext)
                    .build();
        } catch (IOException exception) {
            Log.e(TAG, "Could not build download client", exception);
            entry.mUpdate = entry.mUpdate.toBuilder()
                    .setStatus(UpdateStatus.PAUSED_ERROR)
                    .setErrorMessage(exception.getMessage())
                    .build();
            notifyUpdateChange(downloadId);
            return;
        }
        addDownloadClient(entry, downloadClient);
        entry.mUpdate = entry.mUpdate.toBuilder()
                .setStatus(UpdateStatus.STARTING)
                .setErrorMessage(null)
                .build();
        notifyUpdateChange(downloadId);
        downloadClient.resume();
        mWakeLock.acquire();
    }

    public void pauseDownload(String downloadId) {
        if (!isDownloading(downloadId)) {
            return;
        }
        DownloadEntry entry = mDownloads.get(downloadId);
        if (entry != null) {
            entry.mDownloadClient.cancel();
            removeDownloadClient(entry);
            synchronized (entry) {
                List<PartDownloadState> pausedParts = new ArrayList<>();
                for (PartDownloadState ps : entry.mUpdate.getPartStates()) {
                    PartStatus nextStatus = (ps.getStatus() == PartStatus.DOWNLOADING
                            || ps.getStatus() == PartStatus.VERIFYING)
                            ? PartStatus.PAUSED : ps.getStatus();
                    pausedParts.add(new PartDownloadState(
                            ps.getIndex(),
                            ps.getName(),
                            ps.getDownloadedBytes(),
                            ps.getTotalBytes(),
                            0L,
                            nextStatus,
                            ps.getSha256Verified()
                    ));
                }
                entry.mUpdate = entry.mUpdate.toBuilder()
                        .setStatus(UpdateStatus.PAUSED)
                        .setPartStates(pausedParts)
                        .setEta(0)
                        .setSpeed(0)
                        .build();
            }
            notifyUpdateChange(downloadId);
        }
        tryReleaseWakelock();
    }

    private void deleteUpdateAsync(final Update update) {
        new Thread(() -> {
            File file = update.getFile();
            if (file != null && file.exists() && !file.delete()) {
                Log.e(TAG, "Could not delete " + file.getAbsolutePath());
            }
            SplitHttpDownloadClient.cleanupParts(mDownloadRoot, update.getDownloadId());
            if (!update.isAvailableOnline() || update.isLocalUpdate()) {
                mUpdatesLocalDataSource.removeUpdate(update.getDownloadId());
            } else {
                mUpdatesLocalDataSource.addUpdate(update.toBuilder()
                        .setStatus(UpdateStatus.UPDATE_AVAILABLE)
                        .setFile(null)
                        .setProgress(0)
                        .build());
            }
        }).start();
    }

    public void deleteUpdate(String downloadId) {
        if (!mDownloads.containsKey(downloadId) || isDownloading(downloadId)) {
            return;
        }
        mAutoInstallIds.remove(downloadId);
        DownloadEntry entry = mDownloads.get(downloadId);
        if (entry != null) {
            Update oldUpdate;
            synchronized (entry) {
                oldUpdate = entry.mUpdate;
                entry.mUpdate = oldUpdate.toBuilder()
                        .setStatus(UpdateStatus.DELETED)
                        .setFile(null)
                        .setProgress(0)
                        .setEta(0)
                        .setSpeed(0)
                        .setDownloadStage(null)
                        .setCurrentPart(0)
                        .setTotalParts(0)
                        .setPartProgress(0)
                        .setBytesDownloaded(0L)
                        .setPartStates(Collections.emptyList())
                        .setErrorMessage(null)
                        .build();
            }
            deleteUpdateAsync(oldUpdate);

            if (!oldUpdate.isAvailableOnline() || oldUpdate.isLocalUpdate()) {
                mDownloads.remove(downloadId);
                notifyUpdateDelete(downloadId);
            } else {
                notifyUpdateChange(downloadId);
            }
        }
    }

    public void cancelDownload(String downloadId) {
        DownloadEntry entry = mDownloads.get(downloadId);
        if (entry == null) {
            return;
        }
        mAutoInstallIds.remove(downloadId);
        if (isDownloading(downloadId)) {
            entry.mDownloadClient.cancel();
            removeDownloadClient(entry);
        }
        Update oldUpdate;
        synchronized (entry) {
            oldUpdate = entry.mUpdate;
            entry.mUpdate = oldUpdate.toBuilder()
                    .setStatus(UpdateStatus.DELETED)
                    .setFile(null)
                    .setProgress(0)
                    .setEta(0)
                    .setSpeed(0)
                    .setDownloadStage(null)
                    .setCurrentPart(0)
                    .setTotalParts(0)
                    .setPartProgress(0)
                    .setBytesDownloaded(0L)
                    .setPartStates(Collections.emptyList())
                    .setErrorMessage(null)
                    .build();
        }
        deleteUpdateAsync(oldUpdate);

        if (!oldUpdate.isAvailableOnline() || oldUpdate.isLocalUpdate()) {
            mDownloads.remove(downloadId);
            notifyUpdateDelete(downloadId);
        } else {
            notifyUpdateChange(downloadId);
        }
        tryReleaseWakelock();
    }

    public List<Update> getUpdates() {
        List<Update> updates = new ArrayList<>();
        for (DownloadEntry entry : mDownloads.values()) {
            updates.add(entry.mUpdate);
        }
        return updates;
    }

    public Update getUpdate(String downloadId) {
        DownloadEntry entry = mDownloads.get(downloadId);
        return entry != null ? entry.mUpdate : null;
    }

    public void setUpdate(String downloadId, Update update) {
        DownloadEntry entry = mDownloads.get(downloadId);
        if (entry != null) {
            synchronized (entry) {
                entry.mUpdate = update;
            }
        }
    }

    public boolean isDownloading(String downloadId) {
        DownloadEntry entry = mDownloads.get(downloadId);
        return entry != null && entry.mDownloadClient != null;
    }

    public boolean hasActiveDownloads() {
        return mActiveDownloads > 0;
    }

    public boolean isVerifyingUpdate() {
        return !mVerifyingUpdates.isEmpty();
    }

    public boolean isVerifyingUpdate(String downloadId) {
        return mVerifyingUpdates.contains(downloadId);
    }

    public boolean isInstallingUpdate() {
        return UpdateInstaller.isInstalling() ||
                ABUpdateInstaller.isInstallingUpdate(mContext);
    }

    public boolean isInstallingUpdate(String downloadId) {
        return UpdateInstaller.isInstalling(downloadId) ||
                ABUpdateInstaller.isInstallingUpdate(mContext, downloadId);
    }

    public boolean isBusy() {
        return hasActiveDownloads() || isVerifyingUpdate() || isInstallingUpdate();
    }

    public boolean isInstallingABUpdate() {
        return ABUpdateInstaller.isInstallingUpdate(mContext);
    }

    public boolean isWaitingForReboot(String downloadId) {
        return ABUpdateInstaller.isWaitingForReboot(mContext, downloadId);
    }

    public boolean isAutoInstallQueued(String downloadId) {
        return mAutoInstallIds.contains(downloadId);
    }

    private void pauseActiveDownloads() {
        for (DownloadEntry entry : mDownloads.values()) {
            if (isDownloading(entry.mUpdate.getDownloadId())) {
                pauseDownload(entry.mUpdate.getDownloadId());
            }
        }
    }

    public boolean isFullyDownloaded(Update update) {
        return update.hasFullyDownloadedPackage();
    }
}