/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mozillaonline.providers.downloads;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.CharArrayBuffer;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.util.Log;
import android.util.Pair;

import com.mozillaonline.providers.DownloadManager;

/**
 * Stores information about an individual download.
 */
public class DownloadInfo {
    public static class Reader {
        private ContentResolver mResolver;
        private Cursor mCursor;
        private CharArrayBuffer mOldChars;
        private CharArrayBuffer mNewChars;

        public Reader(ContentResolver resolver, Cursor cursor) {
            mResolver = resolver;
            mCursor = cursor;
        }

        public DownloadInfo newDownloadInfo(Context context, SystemFacade systemFacade) {
            DownloadInfo info = new DownloadInfo(context, systemFacade);
            updateFromDatabase(info);
            readRequestHeaders(info);
            return info;
        }

        public void updateFromDatabase(DownloadInfo info) {
            info.mId = getLong(Downloads._ID);
            info.mUri = getString(info.mUri, Downloads.COLUMN_URI);
            info.mNoIntegrity = getInt(Downloads.COLUMN_NO_INTEGRITY) == 1;
            info.mHint = getString(info.mHint, Downloads.COLUMN_FILE_NAME_HINT);
            info.mFileName = getString(info.mFileName, Downloads._DATA);
            info.mMimeType = getString(info.mMimeType, Downloads.COLUMN_MIME_TYPE);
            info.mDestination = getInt(Downloads.COLUMN_DESTINATION);
            info.mVisibility = getInt(Downloads.COLUMN_VISIBILITY);
            info.mStatus = getInt(Downloads.COLUMN_STATUS);
            info.mNumFailed = getInt(Constants.FAILED_CONNECTIONS);
            int retryRedirect = getInt(Constants.RETRY_AFTER_X_REDIRECT_COUNT);
            info.mRetryAfter = retryRedirect & 0xfffffff;
            info.mLastMod = getLong(Downloads.COLUMN_LAST_MODIFICATION);
            info.mPackage = getString(info.mPackage, Downloads.COLUMN_NOTIFICATION_PACKAGE);
            info.mClass = getString(info.mClass, Downloads.COLUMN_NOTIFICATION_CLASS);
            info.mExtras = getString(info.mExtras, Downloads.COLUMN_NOTIFICATION_EXTRAS);
            info.mCookies = getString(info.mCookies, Downloads.COLUMN_COOKIE_DATA);
            info.mUserAgent = getString(info.mUserAgent, Downloads.COLUMN_USER_AGENT);
            info.mReferer = getString(info.mReferer, Downloads.COLUMN_REFERER);
            info.mTotalBytes = getLong(Downloads.COLUMN_TOTAL_BYTES);
            info.mCurrentBytes = getLong(Downloads.COLUMN_CURRENT_BYTES);
            info.mETag = getString(info.mETag, Constants.ETAG);
            info.mDeleted = getInt(Downloads.COLUMN_DELETED) == 1;
            info.mIsPublicApi = getInt(Downloads.COLUMN_IS_PUBLIC_API) != 0;
            info.mAllowedNetworkTypes = getInt(Downloads.COLUMN_ALLOWED_NETWORK_TYPES);
            info.mAllowRoaming = getInt(Downloads.COLUMN_ALLOW_ROAMING) != 0;
            info.mTitle = getString(info.mTitle, Downloads.COLUMN_TITLE);
            info.mDescription = getString(info.mDescription, Downloads.COLUMN_DESCRIPTION);
            info.mBypassRecommendedSizeLimit =
                    getInt(Downloads.COLUMN_BYPASS_RECOMMENDED_SIZE_LIMIT);

            synchronized (this) {
                info.mControl = getInt(Downloads.COLUMN_CONTROL);
            }
        }

        private void readRequestHeaders(DownloadInfo info) {
            info.mRequestHeaders.clear();
            Uri headerUri = Uri.withAppendedPath(
                    info.getAllDownloadsUri(), Downloads.RequestHeaders.URI_SEGMENT);
            Cursor cursor = mResolver.query(headerUri, null, null, null, null);
            try {
                int headerIndex =
                        cursor.getColumnIndexOrThrow(Downloads.RequestHeaders.COLUMN_HEADER);
                int valueIndex =
                        cursor.getColumnIndexOrThrow(Downloads.RequestHeaders.COLUMN_VALUE);
                for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
                    addHeader(info, cursor.getString(headerIndex), cursor.getString(valueIndex));
                }
            } finally {
                cursor.close();
            }

            if (info.mCookies != null) {
                addHeader(info, "Cookie", info.mCookies);
            }
            if (info.mReferer != null) {
                addHeader(info, "Referer", info.mReferer);
            }
        }

        private void addHeader(DownloadInfo info, String header, String value) {
            info.mRequestHeaders.add(Pair.create(header, value));
        }

        /**
         * Returns a String that holds the current value of the column, optimizing for the case
         * where the value hasn't changed.
         */
        private String getString(String old, String column) {
            int index = mCursor.getColumnIndexOrThrow(column);
            if (old == null) {
                return mCursor.getString(index);
            }
            if (mNewChars == null) {
                mNewChars = new CharArrayBuffer(128);
            }
            mCursor.copyStringToBuffer(index, mNewChars);
            int length = mNewChars.sizeCopied;
            if (length != old.length()) {
                return new String(mNewChars.data, 0, length);
            }
            if (mOldChars == null || mOldChars.sizeCopied < length) {
                mOldChars = new CharArrayBuffer(length);
            }
            char[] oldArray = mOldChars.data;
            char[] newArray = mNewChars.data;
            old.getChars(0, length, oldArray, 0);
            for (int i = length - 1; i >= 0; --i) {
                if (oldArray[i] != newArray[i]) {
                    return new String(newArray, 0, length);
                }
            }
            return old;
        }

        private Integer getInt(String column) {
            return mCursor.getInt(mCursor.getColumnIndexOrThrow(column));
        }

        private Long getLong(String column) {
            return mCursor.getLong(mCursor.getColumnIndexOrThrow(column));
        }
    }

    // the following NETWORK_* constants are used to indicates specfic reasons for disallowing a
    // download from using a network, since specific causes can require special handling

    /**
     * The network is usable for the given download.
     */
    public static final int NETWORK_OK = 1;

    /**
     * There is no network connectivity.
     */
    public static final int NETWORK_NO_CONNECTION = 2;

    /**
     * The download exceeds the maximum size for this network.
     */
    public static final int NETWORK_UNUSABLE_DUE_TO_SIZE = 3;

    /**
     * The download exceeds the recommended maximum size for this network, the user must confirm for
     * this download to proceed without WiFi.
     */
    public static final int NETWORK_RECOMMENDED_UNUSABLE_DUE_TO_SIZE = 4;

    /**
     * The current connection is roaming, and the download can't proceed over a roaming connection.
     */
    public static final int NETWORK_CANNOT_USE_ROAMING = 5;

    /**
     * The app requesting the download specific that it can't use the current network connection.
     */
    public static final int NETWORK_TYPE_DISALLOWED_BY_REQUESTOR = 6;

    /**
     * For intents used to notify the user that a download exceeds a size threshold, if this extra
     * is true, WiFi is required for this download size; otherwise, it is only recommended.
     */
    public static final String EXTRA_IS_WIFI_REQUIRED = "isWifiRequired";


    public long mId;
    public String mUri;
    public boolean mNoIntegrity;
    public String mHint;
    public String mFileName;
    public String mMimeType;
    public int mDestination;
    public int mVisibility;
    public int mControl;
    public int mStatus;
    public int mNumFailed;
    public int mRetryAfter;
    public long mLastMod;
    public String mPackage;
    public String mClass;
    public String mExtras;
    public String mCookies;
    public String mUserAgent;
    public String mReferer;
    public long mTotalBytes;
    public long mCurrentBytes;
    public String mETag;
    public boolean mDeleted;
    public boolean mIsPublicApi;
    public int mAllowedNetworkTypes;
    public boolean mAllowRoaming;
    public String mTitle;
    public String mDescription;
    public int mBypassRecommendedSizeLimit;

    public int mFuzz;

    public volatile boolean mHasActiveThread;

    private List<Pair<String, String>> mRequestHeaders = new ArrayList<Pair<String, String>>();
    private SystemFacade mSystemFacade;
    private Context mContext;

    private DownloadInfo(Context context, SystemFacade systemFacade) {
        mContext = context;
        mSystemFacade = systemFacade;
        mFuzz = Helpers.sRandom.nextInt(1001);
    }

    public Collection<Pair<String, String>> getHeaders() {
        return Collections.unmodifiableList(mRequestHeaders);
    }

    public void sendIntentIfRequested() {
        if (mPackage == null) {
            return;
        }

        Intent intent;
        if (mIsPublicApi) {
            intent = new Intent(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
            intent.setPackage(mPackage);
            intent.putExtra(DownloadManager.EXTRA_DOWNLOAD_ID, mId);
        } else { // legacy behavior
            if (mClass == null) {
                return;
            }
            intent = new Intent(Downloads.ACTION_DOWNLOAD_COMPLETED);
            intent.setClassName(mPackage, mClass);
            if (mExtras != null) {
                intent.putExtra(Downloads.COLUMN_NOTIFICATION_EXTRAS, mExtras);
            }
            // We only send the content: URI, for security reasons. Otherwise, malicious
            //     applications would have an easier time spoofing download results by
            //     sending spoofed intents.
            intent.setData(getMyDownloadsUri());
        }
        mSystemFacade.sendBroadcast(intent);
    }

    /**
     * Returns the time when a download should be restarted.
     */
    public long restartTime(long now) {
        if (mNumFailed == 0) {
            return now;
        }
        if (mRetryAfter > 0) {
            return mLastMod + mRetryAfter;
        }
        return mLastMod +
                Constants.RETRY_FIRST_DELAY *
                    (1000 + mFuzz) * (1 << (mNumFailed - 1));
    }

    /**
     * Returns whether this download (which the download manager hasn't seen yet)
     * should be started.
     */
    private boolean isReadyToStart(long now) {
        if (mHasActiveThread) {
            // already running
            return false;
        }
        if (mControl == Downloads.CONTROL_PAUSED) {
            // the download is paused, so it's not going to start
            return false;
        }
        switch (mStatus) {
            case 0: // status hasn't been initialized yet, this is a new download
            case Downloads.STATUS_PENDING: // download is explicit marked as ready to start
            case Downloads.STATUS_RUNNING: // download interrupted (process killed etc) while
                                                // running, without a chance to update the database
                return true;

            case Downloads.STATUS_WAITING_FOR_NETWORK:
            case Downloads.STATUS_QUEUED_FOR_WIFI:
                return checkCanUseNetwork() == NETWORK_OK;

            case Downloads.STATUS_WAITING_TO_RETRY:
                // download was waiting for a delayed restart
                return restartTime(now) <= now;
        }
        return false;
    }

    /**
     * Returns whether this download has a visible notification after
     * completion.
     */
    public boolean hasCompletionNotification() {
        if (!Downloads.isStatusCompleted(mStatus)) {
            return false;
        }
        if (mVisibility == Downloads.VISIBILITY_VISIBLE_NOTIFY_COMPLETED) {
            return true;
        }
        return false;
    }

    /**
     * Returns whether this download is allowed to use the network.
     * @return one of the NETWORK_* constants
     */
    public int checkCanUseNetwork() {
        Integer networkType = mSystemFacade.getActiveNetworkType();
        if (networkType == null) {
            return NETWORK_NO_CONNECTION;
        }
        if (!isRoamingAllowed() && mSystemFacade.isNetworkRoaming()) {
            return NETWORK_CANNOT_USE_ROAMING;
        }
        return checkIsNetworkTypeAllowed(networkType);
    }

    private boolean isRoamingAllowed() {
        if (mIsPublicApi) {
            return mAllowRoaming;
        } else { // legacy behavior
            return true;
        }
    }

    /**
     * @return a non-localized string appropriate for logging corresponding to one of the
     * NETWORK_* constants.
     */
    public String getLogMessageForNetworkError(int networkError) {
        switch (networkError) {
            case NETWORK_RECOMMENDED_UNUSABLE_DUE_TO_SIZE:
                return "download size exceeds recommended limit for mobile network";

            case NETWORK_UNUSABLE_DUE_TO_SIZE:
                return "download size exceeds limit for mobile network";

            case NETWORK_NO_CONNECTION:
                return "no network connection available";

            case NETWORK_CANNOT_USE_ROAMING:
                return "download cannot use the current network connection because it is roaming";

            case NETWORK_TYPE_DISALLOWED_BY_REQUESTOR:
                return "download was requested to not use the current network type";

            default:
                return "unknown error with network connectivity";
        }
    }

    /**
     * Check if this download can proceed over the given network type.
     * @param networkType a constant from ConnectivityManager.TYPE_*.
     * @return one of the NETWORK_* constants
     */
    private int checkIsNetworkTypeAllowed(int networkType) {
        if (mIsPublicApi) {
            int flag = translateNetworkTypeToApiFlag(networkType);
            if ((flag & mAllowedNetworkTypes) == 0) {
                return NETWORK_TYPE_DISALLOWED_BY_REQUESTOR;
            }
        }
        return checkSizeAllowedForNetwork(networkType);
    }

    /**
     * Translate a ConnectivityManager.TYPE_* constant to the corresponding
     * DownloadManager.Request.NETWORK_* bit flag.
     */
    private int translateNetworkTypeToApiFlag(int networkType) {
        switch (networkType) {
            case ConnectivityManager.TYPE_MOBILE:
                return DownloadManager.Request.NETWORK_MOBILE;

            case ConnectivityManager.TYPE_WIFI:
                return DownloadManager.Request.NETWORK_WIFI;

            default:
                return 0;
        }
    }

    /**
     * Check if the download's size prohibits it from running over the current network.
     * @return one of the NETWORK_* constants
     */
    private int checkSizeAllowedForNetwork(int networkType) {
        if (mTotalBytes <= 0) {
            return NETWORK_OK; // we don't know the size yet
        }
        if (networkType == ConnectivityManager.TYPE_WIFI) {
            return NETWORK_OK; // anything goes over wifi
        }
        Long maxBytesOverMobile = mSystemFacade.getMaxBytesOverMobile();
        if (maxBytesOverMobile != null && mTotalBytes > maxBytesOverMobile) {
            return NETWORK_UNUSABLE_DUE_TO_SIZE;
        }
        if (mBypassRecommendedSizeLimit == 0) {
            Long recommendedMaxBytesOverMobile = mSystemFacade.getRecommendedMaxBytesOverMobile();
            if (recommendedMaxBytesOverMobile != null
                    && mTotalBytes > recommendedMaxBytesOverMobile) {
                return NETWORK_RECOMMENDED_UNUSABLE_DUE_TO_SIZE;
            }
        }
        return NETWORK_OK;
    }

    void startIfReady(long now) {
        if (!isReadyToStart(now)) {
            return;
        }

        if (Constants.LOGV) {
            Log.v(Constants.TAG, "Service spawning thread to handle download " + mId);
        }
        if (mHasActiveThread) {
            throw new IllegalStateException("Multiple threads on same download");
        }
        if (mStatus != Downloads.STATUS_RUNNING) {
            mStatus = Downloads.STATUS_RUNNING;
            ContentValues values = new ContentValues();
            values.put(Downloads.COLUMN_STATUS, mStatus);
            mContext.getContentResolver().update(getAllDownloadsUri(), values, null, null);
            return;
        }
        DownloadThread downloader = new DownloadThread(mContext, mSystemFacade, this);
        mHasActiveThread = true;
        mSystemFacade.startThread(downloader);
    }

    public Uri getMyDownloadsUri() {
        return ContentUris.withAppendedId(Downloads.CONTENT_URI, mId);
    }

    public Uri getAllDownloadsUri() {
        return ContentUris.withAppendedId(Downloads.ALL_DOWNLOADS_CONTENT_URI, mId);
    }


    public void logVerboseInfo() {
        Log.v(Constants.TAG, "Service adding new entry");
        Log.v(Constants.TAG, "ID      : " + mId);
        Log.v(Constants.TAG, "URI     : " + ((mUri != null) ? "yes" : "no"));
        Log.v(Constants.TAG, "NO_INTEG: " + mNoIntegrity);
        Log.v(Constants.TAG, "HINT    : " + mHint);
        Log.v(Constants.TAG, "FILENAME: " + mFileName);
        Log.v(Constants.TAG, "MIMETYPE: " + mMimeType);
        Log.v(Constants.TAG, "DESTINAT: " + mDestination);
        Log.v(Constants.TAG, "VISIBILI: " + mVisibility);
        Log.v(Constants.TAG, "CONTROL : " + mControl);
        Log.v(Constants.TAG, "STATUS  : " + mStatus);
        Log.v(Constants.TAG, "FAILED_C: " + mNumFailed);
        Log.v(Constants.TAG, "RETRY_AF: " + mRetryAfter);
        Log.v(Constants.TAG, "LAST_MOD: " + mLastMod);
        Log.v(Constants.TAG, "PACKAGE : " + mPackage);
        Log.v(Constants.TAG, "CLASS   : " + mClass);
        Log.v(Constants.TAG, "COOKIES : " + ((mCookies != null) ? "yes" : "no"));
        Log.v(Constants.TAG, "AGENT   : " + mUserAgent);
        Log.v(Constants.TAG, "REFERER : " + ((mReferer != null) ? "yes" : "no"));
        Log.v(Constants.TAG, "TOTAL   : " + mTotalBytes);
        Log.v(Constants.TAG, "CURRENT : " + mCurrentBytes);
        Log.v(Constants.TAG, "ETAG    : " + mETag);
        Log.v(Constants.TAG, "DELETED : " + mDeleted);
    }

    /**
     * Returns the amount of time (as measured from the "now" parameter)
     * at which a download will be active.
     * 0 = immediately - service should stick around to handle this download.
     * -1 = never - service can go away without ever waking up.
     * positive value - service must wake up in the future, as specified in ms from "now"
     */
    long nextAction(long now) {
        if (Downloads.isStatusCompleted(mStatus)) {
            return -1;
        }
        if (mStatus != Downloads.STATUS_WAITING_TO_RETRY) {
            return 0;
        }
        long when = restartTime(now);
        if (when <= now) {
            return 0;
        }
        return when - now;
    }

    void notifyPauseDueToSize(boolean isWifiRequired) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(getAllDownloadsUri());
        intent.setClassName(SizeLimitActivity.class.getPackage().getName(),
                SizeLimitActivity.class.getName());
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(EXTRA_IS_WIFI_REQUIRED, isWifiRequired);
        mContext.startActivity(intent);
    }
}
