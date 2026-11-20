/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.leegar.updater.controller;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import androidx.preference.PreferenceManager;

import org.leegar.updater.data.Update;
import org.leegar.updater.data.UpdateStatus;
import org.leegar.updater.deviceinfo.DeviceInfoUtils;
import org.leegar.updater.misc.Constants;
import org.leegar.updater.util.FileUtils;
import org.leegar.updater.misc.Utils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;

class UpdateInstaller {

    private static final String TAG = "UpdateInstaller";

    private static UpdateInstaller sInstance = null;
    private static String sInstallingUpdate = null;

    private Thread mPrepareUpdateThread;
    private volatile boolean mCanCancel;

    private final Context mContext;
    private final UpdaterController mUpdaterController;

    private UpdateInstaller(Context context, UpdaterController controller) {
        mContext = context.getApplicationContext();
        mUpdaterController = controller;
    }

    static synchronized UpdateInstaller getInstance(Context context,
            UpdaterController updaterController) {
        if (sInstance == null) {
            sInstance = new UpdateInstaller(context, updaterController);
        }
        return sInstance;
    }

    static synchronized boolean isInstalling() {
        return sInstallingUpdate != null;
    }

    static synchronized boolean isInstalling(String downloadId) {
        return sInstallingUpdate != null && sInstallingUpdate.equals(downloadId);
    }

    void install(String downloadId) {
        install(downloadId, false);
    }

    void install(String downloadId, boolean forceRecovery) {
        if (isInstalling()) {
            Log.e(TAG, "Already installing an update");
            return;
        }

        Update update = mUpdaterController.getUpdate(downloadId);
        if (update == null || update.getFile() == null || !update.getFile().exists()) {
            Log.e(TAG, "Update package missing for recovery install: " + downloadId);
            if (update != null) {
                mUpdaterController.setUpdate(downloadId, update.toBuilder()
                        .setStatus(UpdateStatus.INSTALLATION_FAILED)
                        .setErrorMessage("Update package file not found")
                        .build());
                mUpdaterController.notifyUpdateChange(downloadId);
            }
            return;
        }

        mUpdaterController.setUpdate(downloadId, update.toBuilder()
                .setErrorMessage(null)
                .build());

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(mContext);
        long buildTimestamp = DeviceInfoUtils.getBuildDateTimestamp();
        long lastBuildTimestamp = preferences.getLong(Constants.PREF_INSTALL_OLD_TIMESTAMP,
                buildTimestamp);
        boolean isReinstalling = buildTimestamp == lastBuildTimestamp;
        preferences.edit()
                .putLong(Constants.PREF_INSTALL_OLD_TIMESTAMP, buildTimestamp)
                .putLong(Constants.PREF_INSTALL_NEW_TIMESTAMP, update.getTimestamp())
                .putString(Constants.PREF_INSTALL_PACKAGE_PATH, update.getFile().getAbsolutePath())
                .putBoolean(Constants.PREF_INSTALL_AGAIN, isReinstalling)
                .putBoolean(Constants.PREF_INSTALL_NOTIFIED, false)
                .apply();

        if (forceRecovery) {
            installViaCustomRecoveryAsync(update.getFile(), downloadId);
        } else if (Utils.isEncrypted(mContext, update.getFile())) {
            // uncrypt rewrites the file so that it can be read without mounting
            // the filesystem, so create a copy of it.
            prepareForUncryptAndInstall(update);
        } else {
            new Thread(() -> installPackage(update.getFile(), downloadId), "RecoveryInstall").start();
        }
    }

    @SuppressLint("SetWorldReadable")
    private void installViaCustomRecoveryAsync(File packageFile, String downloadId) {
        new Thread(() -> {
            try {
                //noinspection ResultOfMethodCallIgnored
                packageFile.setReadable(true, false);
                String canonicalPath = packageFile.getCanonicalPath();

                // 1. Write OpenRecoveryScript for TWRP / OrangeFox and reboot to recovery
                if (writeOpenRecoveryScript(canonicalPath)) {
                    PowerManager pm = mContext.getSystemService(PowerManager.class);
                    if (pm != null) {
                        pm.reboot("recovery-update");
                        return;
                    }
                }

                // 2. Fallback: write BCB directly via IRecoverySystem (bypasses uncrypt on A/B + DFE)
                if (rebootRecoveryViaBcb("--update_package=" + canonicalPath + "\n")) {
                    return;
                }

                // 3. Last resort: stock RecoverySystem.installPackage
                android.os.RecoverySystem.installPackage(mContext, packageFile);
            } catch (Throwable e) {
                Log.e(TAG, "Could not reboot into recovery for install", e);
                String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                Update cur = mUpdaterController.getUpdate(downloadId);
                if (cur != null) {
                    mUpdaterController.setUpdate(downloadId, cur.toBuilder()
                            .setStatus(UpdateStatus.INSTALLATION_FAILED)
                            .setErrorMessage(msg)
                            .build());
                }
                mUpdaterController.notifyUpdateChange(downloadId);
                mUpdaterController.notifyDownloadError(downloadId, msg);
            }
        }, "ForceRecoveryInstall").start();
    }

    @SuppressLint("SetWorldReadable")
    @SuppressWarnings("ResultOfMethodCallIgnored")
    private boolean writeOpenRecoveryScript(String canonicalPath) {
        String script = "install " + canonicalPath + "\nreboot\n";
        File[] dirs = new File[] {
                new File("/cache/recovery"),
                new File("/data/cache/recovery"),
        };
        for (File dir : dirs) {
            try {
                if (!dir.exists()) {
                    dir.mkdirs();
                }
                if (!dir.isDirectory()) {
                    continue;
                }
                File ors = new File(dir, "openrecoveryscript");
                try (FileOutputStream fos = new FileOutputStream(ors, false)) {
                    fos.write(script.getBytes(StandardCharsets.UTF_8));
                    fos.getFD().sync();
                }
                ors.setReadable(true, false);
                Log.i(TAG, "Wrote OpenRecoveryScript to " + ors.getAbsolutePath());
                return true;
            } catch (Throwable e) {
                Log.w(TAG, "Cannot write OpenRecoveryScript in " + dir.getAbsolutePath() + ": " + e.getMessage());
            }
        }
        return false;
    }

    private boolean rebootRecoveryViaBcb(String command) {
        try {
            Class<?> smClass = Class.forName("android.os.ServiceManager");
            Method getService = smClass.getMethod("getService", String.class);
            IBinder binder = (IBinder) getService.invoke(null, Context.RECOVERY_SERVICE);
            if (binder == null) {
                return false;
            }
            Class<?> stubClass = Class.forName("android.os.IRecoverySystem$Stub");
            Method asInterface = stubClass.getMethod("asInterface", IBinder.class);
            Object recoveryService = asInterface.invoke(null, binder);
            if (recoveryService == null) {
                return false;
            }
            Method setupBcb = recoveryService.getClass()
                    .getMethod("setupBcb", String.class);
            if (Boolean.TRUE.equals(setupBcb.invoke(recoveryService, command))) {
                PowerManager pm = mContext.getSystemService(PowerManager.class);
                if (pm != null) {
                    pm.reboot("recovery-update");
                    return true;
                }
            }
            Method rebootCmd = recoveryService.getClass()
                    .getMethod("rebootRecoveryWithCommand", String.class);
            rebootCmd.invoke(recoveryService, command);
            return true;
        } catch (Throwable e) {
            Log.w(TAG, "IRecoverySystem BCB reboot failed: " + e.getMessage());
            return false;
        }
    }

    private void installPackage(File update, String downloadId) {
        try {
            android.os.RecoverySystem.installPackage(mContext, update);
        } catch (Throwable e) {
            Log.e(TAG, "Could not install update", e);
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            Update cur = mUpdaterController.getUpdate(downloadId);
            if (cur != null) {
                mUpdaterController.setUpdate(downloadId, cur.toBuilder()
                        .setStatus(UpdateStatus.INSTALLATION_FAILED)
                        .setErrorMessage(msg)
                        .build());
            }
            mUpdaterController.notifyUpdateChange(downloadId);
            mUpdaterController.notifyDownloadError(downloadId, msg);
        }
    }

    private synchronized void prepareForUncryptAndInstall(Update update) {
        String uncryptFilePath = update.getFile().getAbsolutePath() + Constants.UNCRYPT_FILE_EXT;
        File uncryptFile = new File(uncryptFilePath);

        Runnable copyUpdateRunnable = new Runnable() {
            @Override
            public void run() {
                try {
                    mCanCancel = true;
                    FileUtils.copyFile(update.getFile(), uncryptFile);
                    try {
                        Set<PosixFilePermission> perms = new HashSet<>();
                        perms.add(PosixFilePermission.OWNER_READ);
                        perms.add(PosixFilePermission.OWNER_WRITE);
                        perms.add(PosixFilePermission.OTHERS_READ);
                        perms.add(PosixFilePermission.GROUP_READ);
                        Files.setPosixFilePermissions(uncryptFile.toPath(), perms);
                    } catch (IOException exception) {}

                    mCanCancel = false;
                    if (mPrepareUpdateThread.isInterrupted()) {
                        mUpdaterController.setUpdate(update.getDownloadId(), mUpdaterController.getUpdate(update.getDownloadId()).toBuilder()
                                .setStatus(UpdateStatus.INSTALLATION_CANCELLED)
                                .setInstallProgress(0).build());
                        //noinspection ResultOfMethodCallIgnored
                        uncryptFile.delete();
                    } else {
                        installPackage(uncryptFile, update.getDownloadId());
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Could not copy update", e);
                    //noinspection ResultOfMethodCallIgnored
                    uncryptFile.delete();
                    mUpdaterController.setUpdate(update.getDownloadId(), mUpdaterController.getUpdate(update.getDownloadId())
                            .withStatus(UpdateStatus.INSTALLATION_FAILED));
                } finally {
                    synchronized (UpdateInstaller.this) {
                        mCanCancel = false;
                        mPrepareUpdateThread = null;
                        sInstallingUpdate = null;
                    }
                    mUpdaterController.notifyUpdateChange(update.getDownloadId());
                }
            }
        };

        mPrepareUpdateThread = new Thread(copyUpdateRunnable);
        mPrepareUpdateThread.start();
        sInstallingUpdate = update.getDownloadId();
        mCanCancel = false;

        mUpdaterController.setUpdate(update.getDownloadId(), mUpdaterController.getUpdate(update.getDownloadId())
                .withStatus(UpdateStatus.INSTALLING));
        mUpdaterController.notifyUpdateChange(update.getDownloadId());
    }

    public synchronized void cancel() {
        if (!mCanCancel) {
            Log.d(TAG, "Nothing to cancel");
            return;
        }
        mPrepareUpdateThread.interrupt();
    }
}
