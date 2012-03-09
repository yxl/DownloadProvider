/*
 * Copyright (C) 2010 The Android Open Source Project
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

import java.util.LinkedList;
import java.util.Queue;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.text.format.Formatter;
import android.util.Log;

import com.mozillaonline.downloadprovider.R;

/**
 * Activity to show dialogs to the user when a download exceeds a limit on download sizes for
 * mobile networks.  This activity gets started by the background download service when a download's
 * size is discovered to be exceeded one of these thresholds.
 */
public class SizeLimitActivity extends Activity
        implements DialogInterface.OnCancelListener, DialogInterface.OnClickListener {
    private Dialog mDialog;
    private Queue<Intent> mDownloadsToShow = new LinkedList<Intent>();
    private Uri mCurrentUri;
    private Intent mCurrentIntent;

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Intent intent = getIntent();
        if (intent != null) {
            mDownloadsToShow.add(intent);
            setIntent(null);
            showNextDialog();
        }
        if (mDialog != null && !mDialog.isShowing()) {
            mDialog.show();
        }
    }

    private void showNextDialog() {
        if (mDialog != null) {
            return;
        }

        if (mDownloadsToShow.isEmpty()) {
            finish();
            return;
        }

        mCurrentIntent = mDownloadsToShow.poll();
        mCurrentUri = mCurrentIntent.getData();
        Cursor cursor = getContentResolver().query(mCurrentUri, null, null, null, null);
        try {
            if (!cursor.moveToFirst()) {
                Log.e(Constants.TAG, "Empty cursor for URI " + mCurrentUri);
                dialogClosed();
                return;
            }
            showDialog(cursor);
        } finally {
            cursor.close();
        }
    }

    private void showDialog(Cursor cursor) {
        int size = cursor.getInt(cursor.getColumnIndexOrThrow(Downloads.COLUMN_TOTAL_BYTES));
        String sizeString = Formatter.formatFileSize(this, size);
        String queueText = getString(R.string.button_queue_for_wifi);
        boolean isWifiRequired =
            mCurrentIntent.getExtras().getBoolean(DownloadInfo.EXTRA_IS_WIFI_REQUIRED);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        if (isWifiRequired) {
            builder.setTitle(R.string.wifi_required_title)
                    .setMessage(getString(R.string.wifi_required_body, sizeString, queueText))
                    .setPositiveButton(R.string.button_queue_for_wifi, this)
                    .setNegativeButton(R.string.button_cancel_download, this);
        } else {
            builder.setTitle(R.string.wifi_recommended_title)
                    .setMessage(getString(R.string.wifi_recommended_body, sizeString, queueText))
                    .setPositiveButton(R.string.button_start_now, this)
                    .setNegativeButton(R.string.button_queue_for_wifi, this);
        }
        mDialog = builder.setOnCancelListener(this).show();
    }

    @Override
    public void onCancel(DialogInterface dialog) {
        dialogClosed();
    }

    private void dialogClosed() {
        mDialog = null;
        mCurrentUri = null;
        showNextDialog();
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        boolean isRequired =
                mCurrentIntent.getExtras().getBoolean(DownloadInfo.EXTRA_IS_WIFI_REQUIRED);
        if (isRequired && which == AlertDialog.BUTTON_NEGATIVE) {
            getContentResolver().delete(mCurrentUri, null, null);
        } else if (!isRequired && which == AlertDialog.BUTTON_POSITIVE) {
            ContentValues values = new ContentValues();
            values.put(Downloads.COLUMN_BYPASS_RECOMMENDED_SIZE_LIMIT, true);
            getContentResolver().update(mCurrentUri, values , null, null);
        }
        dialogClosed();
    }
}
