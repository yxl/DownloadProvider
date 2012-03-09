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

import java.io.File;

import com.mozillaonline.providers.DownloadManager;

import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.util.Log;

/**
 * Receives system broadcasts (boot, network connectivity)
 */
public class DownloadReceiver extends BroadcastReceiver {
    SystemFacade mSystemFacade = null;

    public void onReceive(Context context, Intent intent) {
        if (mSystemFacade == null) {
            mSystemFacade = new RealSystemFacade(context);
        }

        String action = intent.getAction();
        if (action.equals(Intent.ACTION_BOOT_COMPLETED)) {
            startService(context);
        } else if (action.equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
            NetworkInfo info = (NetworkInfo)
                    intent.getParcelableExtra(ConnectivityManager.EXTRA_NETWORK_INFO);
            if (info != null && info.isConnected()) {
                startService(context);
            }
        } else if (action.equals(Constants.ACTION_RETRY)) {
            startService(context);
        } else if (action.equals(Constants.ACTION_OPEN)
                || action.equals(Constants.ACTION_LIST)
                || action.equals(Constants.ACTION_HIDE)) {
            handleNotificationBroadcast(context, intent);
        }
    }

    /**
     * Handle any broadcast related to a system notification.
     */
    private void handleNotificationBroadcast(Context context, Intent intent) {
        Uri uri = intent.getData();
        String action = intent.getAction();
        if (Constants.LOGVV) {
            if (action.equals(Constants.ACTION_OPEN)) {
                Log.v(Constants.TAG, "Receiver open for " + uri);
            } else if (action.equals(Constants.ACTION_LIST)) {
                Log.v(Constants.TAG, "Receiver list for " + uri);
            } else { // ACTION_HIDE
                Log.v(Constants.TAG, "Receiver hide for " + uri);
            }
        }

        Cursor cursor = context.getContentResolver().query(uri, null, null, null, null);
        if (cursor == null) {
            return;
        }
        try {
            if (!cursor.moveToFirst()) {
                return;
            }

            if (action.equals(Constants.ACTION_OPEN)) {
                openDownload(context, cursor);
                hideNotification(context, uri, cursor);
            } else if (action.equals(Constants.ACTION_LIST)) {
                sendNotificationClickedIntent(intent, cursor);
            } else { // ACTION_HIDE
                hideNotification(context, uri, cursor);
            }
        } finally {
            cursor.close();
        }
    }

    /**
     * Hide a system notification for a download.
     * @param uri URI to update the download
     * @param cursor Cursor for reading the download's fields
     */
    private void hideNotification(Context context, Uri uri, Cursor cursor) {
        mSystemFacade.cancelNotification(ContentUris.parseId(uri));

        int statusColumn = cursor.getColumnIndexOrThrow(Downloads.COLUMN_STATUS);
        int status = cursor.getInt(statusColumn);
        int visibilityColumn =
                cursor.getColumnIndexOrThrow(Downloads.COLUMN_VISIBILITY);
        int visibility = cursor.getInt(visibilityColumn);
        if (Downloads.isStatusCompleted(status)
                && visibility == Downloads.VISIBILITY_VISIBLE_NOTIFY_COMPLETED) {
            ContentValues values = new ContentValues();
            values.put(Downloads.COLUMN_VISIBILITY,
                    Downloads.VISIBILITY_VISIBLE);
            context.getContentResolver().update(uri, values, null, null);
        }
    }

    /**
     * Open the download that cursor is currently pointing to, since it's completed notification
     * has been clicked.
     */
    private void openDownload(Context context, Cursor cursor) {
        String filename = cursor.getString(cursor.getColumnIndexOrThrow(Downloads._DATA));
        String mimetype =
            cursor.getString(cursor.getColumnIndexOrThrow(Downloads.COLUMN_MIME_TYPE));
        Uri path = Uri.parse(filename);
        // If there is no scheme, then it must be a file
        if (path.getScheme() == null) {
            path = Uri.fromFile(new File(filename));
        }

        Intent activityIntent = new Intent(Intent.ACTION_VIEW);
        activityIntent.setDataAndType(path, mimetype);
        activityIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(activityIntent);
        } catch (ActivityNotFoundException ex) {
            Log.d(Constants.TAG, "no activity for " + mimetype, ex);
        }
    }

    /**
     * Notify the owner of a running download that its notification was clicked.
     * @param intent the broadcast intent sent by the notification manager
     * @param cursor Cursor for reading the download's fields
     */
    private void sendNotificationClickedIntent(Intent intent, Cursor cursor) {
        String pckg = cursor.getString(
                cursor.getColumnIndexOrThrow(Downloads.COLUMN_NOTIFICATION_PACKAGE));
        if (pckg == null) {
            return;
        }

        String clazz = cursor.getString(
                cursor.getColumnIndexOrThrow(Downloads.COLUMN_NOTIFICATION_CLASS));
        boolean isPublicApi =
                cursor.getInt(cursor.getColumnIndex(Downloads.COLUMN_IS_PUBLIC_API)) != 0;

        Intent appIntent = null;
        if (isPublicApi) {
            appIntent = new Intent(DownloadManager.ACTION_NOTIFICATION_CLICKED);
            appIntent.setPackage(pckg);
        } else { // legacy behavior
            if (clazz == null) {
                return;
            }
            appIntent = new Intent(Downloads.ACTION_NOTIFICATION_CLICKED);
            appIntent.setClassName(pckg, clazz);
            if (intent.getBooleanExtra("multiple", true)) {
                appIntent.setData(Downloads.CONTENT_URI);
            } else {
                long downloadId = cursor.getLong(cursor.getColumnIndexOrThrow(Downloads._ID));
                appIntent.setData(
                        ContentUris.withAppendedId(Downloads.CONTENT_URI, downloadId));
            }
        }

        mSystemFacade.sendBroadcast(appIntent);
    }

    private void startService(Context context) {
        context.startService(new Intent(context, DownloadService.class));
    }
}
