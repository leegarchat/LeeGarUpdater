package org.leegar.updater.download;

import android.content.Context;

import org.leegar.updater.R;
import org.leegar.updater.data.PartDownloadState;
import org.leegar.updater.data.UpdatePart;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

public interface DownloadClient {

    interface DownloadCallback {
        void onResponse(Headers headers);

        void onSuccess();

        void onFailure(boolean cancelled);

        default void onFailure(boolean cancelled, String errorMessage) {
            onFailure(cancelled);
        }
    }

    interface ProgressListener {
        void update(long bytesRead, long contentLength, long speed, long eta);

        default void updateStage(String stage, int partIndex, int partCount, int partPercent) {
        }

        default void updateParts(List<PartDownloadState> states) {
        }
    }

    interface Headers {
        String get(String name);
    }

    void start();

    void resume();

    void cancel();

    final class Builder {
        private String mDownloadId = "";
        private String mUrl;
        private List<UpdatePart> mParts = Collections.emptyList();
        private String mExpectedSha256;
        private boolean mVerifyHashes = true;
        private boolean mParallelSplit = false;
        private File mDestination;
        private DownloadClient.DownloadCallback mCallback;
        private DownloadClient.ProgressListener mProgressListener;
        private boolean mUseDuplicateLinks;
        private Context mContext;

        public DownloadClient build() throws IOException {
            if ((mUrl == null || mUrl.isEmpty()) && (mParts == null || mParts.isEmpty())) {
                throw new IllegalStateException(err(R.string.err_no_url, "No download URL or parts defined"));
            } else if (mDestination == null) {
                throw new IllegalStateException(err(R.string.err_no_dest, "No download destination defined"));
            } else if (mCallback == null) {
                throw new IllegalStateException(err(R.string.err_no_callback, "No download callback defined"));
            }
            boolean isSplit = (mParts != null && !mParts.isEmpty())
                    || (mUrl != null && mUrl.contains("\n"));
            if (isSplit) {
                return new SplitHttpDownloadClient(
                        mDownloadId,
                        mParts,
                        mUrl != null ? mUrl : "",
                        mDestination,
                        mExpectedSha256,
                        mVerifyHashes,
                        mParallelSplit,
                        mProgressListener,
                        mCallback,
                        mContext != null ? mContext.getApplicationContext() : null
                );
            }
            return new HttpURLConnectionClient(mContext != null ? mContext.getApplicationContext() : null, mUrl, mDestination, mProgressListener, mCallback,
                    mUseDuplicateLinks);
        }

        public Builder setDownloadId(String downloadId) {
            mDownloadId = downloadId != null ? downloadId : "";
            return this;
        }

        public Builder setUrl(String url) {
            mUrl = url;
            return this;
        }

        public Builder setParts(List<UpdatePart> parts) {
            mParts = parts != null ? parts : Collections.emptyList();
            return this;
        }

        public Builder setExpectedSha256(String sha256) {
            mExpectedSha256 = sha256;
            return this;
        }

        public Builder setVerifyHashes(boolean verifyHashes) {
            mVerifyHashes = verifyHashes;
            return this;
        }

        public Builder setParallelSplit(boolean parallelSplit) {
            mParallelSplit = parallelSplit;
            return this;
        }

        public Builder setDestination(File destination) {
            mDestination = destination;
            return this;
        }

        public Builder setDownloadCallback(DownloadClient.DownloadCallback downloadCallback) {
            mCallback = downloadCallback;
            return this;
        }

        public Builder setProgressListener(DownloadClient.ProgressListener progressListener) {
            mProgressListener = progressListener;
            return this;
        }

        public Builder setUseDuplicateLinks(boolean useDuplicateLinks) {
            mUseDuplicateLinks = useDuplicateLinks;
            return this;
        }

        public Builder setContext(Context context) {
            mContext = context;
            return this;
        }

        private String err(int resId, String fallback) {
            return mContext != null ? mContext.getString(resId) : fallback;
        }
    }
}