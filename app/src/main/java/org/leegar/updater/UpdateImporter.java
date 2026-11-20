package org.leegar.updater;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import org.leegar.updater.controller.UpdaterController;
import org.leegar.updater.data.Update;
import org.leegar.updater.data.UpdateStatus;
import org.leegar.updater.data.UserPreferencesRepository;
import org.leegar.updater.deviceinfo.DeviceInfoUtils;
import org.leegar.updater.misc.Utils;
import org.leegar.updater.util.FileUtils;
import org.leegar.updater.util.OtaMetadataParser;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public class UpdateImporter {
    private static final int REQUEST_PICK = 9061;
    private static final String TAG = "UpdateImporter";
    private static final String MIME_ZIP = "application/zip";

    private final Activity activity;
    private final Callbacks callbacks;

    private Thread workingThread;

    public UpdateImporter(Activity activity, Callbacks callbacks) {
        this.activity = activity;
        this.callbacks = callbacks;
    }

    public void stopImport() {
        if (workingThread != null && workingThread.isAlive()) {
            workingThread.interrupt();
            workingThread = null;
        }
    }

    public void openImportPicker() {
        final Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType(MIME_ZIP);
        activity.startActivityForResult(intent, REQUEST_PICK);
    }

    public boolean onResult(int requestCode, int resultCode, Intent data) {
        if (resultCode != Activity.RESULT_OK || requestCode != REQUEST_PICK || data == null) {
            return false;
        }
        return onPicked(data.getData());
    }

    public boolean importFromUri(Uri uri) {
        if (uri == null) return false;
        return onPicked(uri);
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private boolean onPicked(Uri uri) {
        if (uri == null) return false;
        callbacks.onImportStarted();

        workingThread = new Thread(() -> {
            File importedFile = null;
            try {
                UserPreferencesRepository prefs =
                        ((UpdaterApplication) activity.getApplication()).getUserPreferencesRepository();
                boolean unsafeMode = prefs.getUnsafeInstallBlocking();

                String displayName = FileUtils.queryName(activity.getContentResolver(), uri);
                if (displayName == null || displayName.trim().isEmpty()) {
                    displayName = "localUpdate_" + System.currentTimeMillis() + ".zip";
                }
                importedFile = importFile(uri, displayName);

                if (!unsafeMode) {
                    verifyPackage(importedFile);
                }

                final Update update = buildLocalUpdate(importedFile, displayName, unsafeMode);
                addUpdate(update);
                activity.runOnUiThread(() -> callbacks.onImportCompleted(update));
            } catch (Exception e) {
                Log.e(TAG, "Failed to import update package", e);
                if (importedFile != null && importedFile.exists()) {
                    importedFile.delete();
                }
                activity.runOnUiThread(() -> callbacks.onImportCompleted(null));
            }
        }, "LocalUpdateImport");
        workingThread.start();
        return true;
    }

    @SuppressLint("SetWorldReadable")
    @SuppressWarnings("ResultOfMethodCallIgnored")
    private File importFile(Uri uri, String displayName) throws IOException {
        final ParcelFileDescriptor parcelDescriptor = activity.getContentResolver()
                .openFileDescriptor(uri, "r");
        if (parcelDescriptor == null) {
            throw new IOException(activity.getString(R.string.err_no_fd));
        }

        final File downloadDir = Utils.getDownloadPath(activity);
        if (!downloadDir.exists()) {
            downloadDir.mkdirs();
        }
        String safeName = displayName.replaceAll("[^a-zA-Z0-9._-]", "_");
        if (!safeName.endsWith(".zip")) {
            safeName = safeName + ".zip";
        }
        File outFile = new File(downloadDir, safeName);
        if (outFile.exists()) {
            outFile = Utils.appendSequentialNumber(outFile);
        }

        try (FileInputStream iStream = new FileInputStream(parcelDescriptor.getFileDescriptor());
             FileOutputStream oStream = new FileOutputStream(outFile)) {
            int read;
            final byte[] buffer = new byte[64 * 1024];
            while ((read = iStream.read(buffer)) > 0) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new IOException(activity.getString(R.string.err_import_cancelled));
                }
                oStream.write(buffer, 0, read);
            }
            oStream.flush();
        } finally {
            parcelDescriptor.close();
        }

        outFile.setReadable(true, false);
        return outFile;
    }

    private Update buildLocalUpdate(File file, String displayName, boolean unsafeMode) throws IOException {
        long timestamp;
        String patchLevel = null;
        int sdkLevel;
        try {
            final OtaMetadataParser metadata = new OtaMetadataParser(file, activity);
            timestamp = metadata.getTimestamp();
            patchLevel = metadata.getSecurityPatchLevel();
            sdkLevel = metadata.getSdkLevel();
        } catch (IOException e) {
            if (!unsafeMode) throw e;
            timestamp = System.currentTimeMillis() / 1000L;
            sdkLevel = DeviceInfoUtils.INSTANCE.getSdkLevel();
        }

        String localId = Update.LOCAL_ID_PREFIX + System.currentTimeMillis();
        return new Update.Builder()
                .setDownloadId(localId)
                .setName(displayName)
                .setFile(file)
                .setFileSize(file.length())
                .setTimestamp(timestamp)
                .setOsPatchLevel(patchLevel)
                .setOsSdkLevel(sdkLevel)
                .setStatus(UpdateStatus.VERIFIED)
                .setVersion(displayName.replace(".zip", ""))
                .setTag(activity.getString(R.string.importer_tag_local))
                .build();
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private void verifyPackage(File file) throws Exception {
        try {
            android.os.RecoverySystem.verifyPackage(file, null, null);
        } catch (Exception e) {
            if (file.exists()) {
                file.delete();
                throw new Exception(activity.getString(R.string.err_verify_deleted), e);
            }
            throw e;
        }
    }

    private void addUpdate(Update update) {
        UpdaterController controller = UpdaterController.getInstance(activity);
        controller.addLocalUpdate(update);
    }

    public interface Callbacks {
        void onImportStarted();
        void onImportCompleted(Update update);
    }
}