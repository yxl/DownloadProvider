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


package com.mozillaonline.providers.downloads.ui;

import com.mozillaonline.providers.DownloadManager;
import com.mozillaonline.providers.downloads.ui.DownloadItem.DownloadSelectListener;

import android.content.Context;
import android.database.Cursor;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;

/**
 * Adapter for a date-sorted list of downloads.  Delegates all the real work to
 * {@link DownloadAdapter}.
 */
public class DateSortedDownloadAdapter extends DateSortedExpandableListAdapter {
    private DownloadAdapter mDelegate;

    public DateSortedDownloadAdapter(Context context, Cursor cursor,
            DownloadSelectListener selectionListener) {
        super(context, cursor,
                cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LAST_MODIFIED_TIMESTAMP));
        mDelegate = new DownloadAdapter(context, cursor, selectionListener);
    }

    @Override
    public View getChildView(int groupPosition, int childPosition,
                boolean isLastChild, View convertView, ViewGroup parent) {
        // The layout file uses a RelativeLayout, whereas the GroupViews use TextView.
        if (null == convertView || !(convertView instanceof RelativeLayout)) {
            convertView = mDelegate.newView();
        }

        // Bail early if the Cursor is closed.
        if (!moveCursorToChildPosition(groupPosition, childPosition)) {
            return convertView;
        }

        mDelegate.bindView(convertView);
        return convertView;
    }
}
