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

import android.net.Uri;
import android.provider.BaseColumns;

/**
 * The Download Manager
 * 
 * @pending
 */
public final class Downloads implements BaseColumns {

    /**
     * DownloadProvider authority
     */
    public static final String AUTHORITY = "com.mozillaonline.downloads";

    /**
     * @hide
     */
    private Downloads() {
    }

    /**
     * The permission to access the download manager
     * 
     * @hide
     */
    public static final String PERMISSION_ACCESS = "com.mozillaonline.permission.ACCESS_DOWNLOAD_MANAGER";

    /**
     * The permission to access the download manager's advanced functions
     * 
     * @hide
     */
    public static final String PERMISSION_ACCESS_ADVANCED = "com.mozillaonline.permission.ACCESS_DOWNLOAD_MANAGER_ADVANCED";

    /**
     * The permission to access the all the downloads in the manager.
     */
    public static final String PERMISSION_ACCESS_ALL = "com.mozillaonline.permission.ACCESS_ALL_DOWNLOADS";

    /**
     * The permission to send broadcasts on download completion
     * 
     * @hide
     */
    public static final String PERMISSION_SEND_INTENTS = "com.mozillaonline.permission.SEND_DOWNLOAD_COMPLETED_INTENTS";

    /**
     * The permission to download files without any system notification being
     * shown.
     */
    public static final String PERMISSION_NO_NOTIFICATION = "com.mozillaonline.permission.DOWNLOAD_WITHOUT_NOTIFICATION";

    /**
     * The content:// URI for the data table in the provider
     * 
     * @hide
     */
    public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY
	    + "/my_downloads");

    /**
     * The content URI for accessing all downloads across all UIDs (requires the
     * ACCESS_ALL_DOWNLOADS permission).
     */
    public static final Uri ALL_DOWNLOADS_CONTENT_URI = Uri.parse("content://"
	    + AUTHORITY + "/all_downloads");

    /**
     * Broadcast Action: this is sent by the download manager to the app that
     * had initiated a download when that download completes. The download's
     * content: uri is specified in the intent's data.
     * 
     * @hide
     */
    public static final String ACTION_DOWNLOAD_COMPLETED = "android.intent.action.DOWNLOAD_COMPLETED";

    /**
     * Broadcast Action: this is sent by the download manager to the app that
     * had initiated a download when the user selects the notification
     * associated with that download. The download's content: uri is specified
     * in the intent's data if the click is associated with a single download,
     * or Downloads.CONTENT_URI if the notification is associated with multiple
     * downloads. Note: this is not currently sent for downloads that have
     * completed successfully.
     * 
     * @hide
     */
    public static final String ACTION_NOTIFICATION_CLICKED = "android.intent.action.DOWNLOAD_NOTIFICATION_CLICKED";

    /**
     * The name of the column containing the URI of the data being downloaded.
     * <P>
     * Type: TEXT
     * </P>
     * <P>
     * Owner can Init/Read
     * </P>
     * 
     * @hide
     */
    public static final String COLUMN_URI = "uri";

    /**
     * The name of the column containing application-specific data.
     * <P>
     * Type: TEXT
     * </P>
     * <P>
     * Owner can Init/Read/Write
     * </P>
     * 
     * @hide
     */
    public static final String COLUMN_APP_DATA = "entity";

    /**
     * The name of the column containing the flags that indicates whether the
     * initiating application is capable of verifying the integrity of the
     * downloaded file. When this flag is set, the download manager performs
     * downloads and reports success even in some situations where it can't
     * guarantee that the download has completed (e.g. when doing a byte-range
     * request without an ETag, or when it can't determine whether a download
     * fully completed).
     * <P>
     * Type: BOOLEAN
     * </P>
     * <P>
     * Owner can Init
     * </P>
     * 
     * @hide
     */
    public static final String COLUMN_NO_INTEGRITY = "no_integrity";

    /**
     * The name of the column containing the filename that the initiating
     * application recommends. When possible, the download manager will attempt
     * to use this filename, or a variation, as the actual name for the file.
     * <P>
     * Type: TEXT
     * </P>
     * <P>
     * Owner can Init
     * </P>
     * 
     * @hide
     */
    public static final String COLUMN_FILE_NAME_HINT = "hint";

    /**
     * The name of the column containing the filename where the downloaded data
     * was actually stored.
     * <P>
     * Type: TEXT
     * </P>
     * <P>
     * Owner can Read
     * </P>
     * 
     * @hide
     */
    public static final String _DATA = "_data";

    /**
     * The name of the column containing the MIME type of the downloaded data.
     * <P>
     * Type: TEXT
     * </P>
     * <P>
     * Owner can Init/Read
     * </P>
     * 
     * @hide
     */
    public static final String COLUMN_MIME_TYPE = "mimetype";

    /**
     * The name of the column containing the flag that controls the destination
     * of the download. See the DESTINATION_* constants for a list of legal
     * values.
     * <P>
     * Type: INTEGER
     * </P>
     * <P>
     * Owner can Init
     * </P>
     * 
     * @hide
     */
    public static final String COLUMN_DESTINATION = "destination";

    /**
     * The name of the column containing the flags that controls whether the
     * download is displayed by the UI. See the VISIBILITY_* constants for a
     * list of legal values.
     * <P>
     * Type: INTEGER
     * </P>
     * <P>
     * Owner can Init/Read/Write
     * </P>
     * 
     * @hide
     */
    public static final String COLUMN_VISIBILITY = "visibility";

    /**
     * The name of the column containing the current control state of the
     * download. Applications can write to this to control (pause/resume) the
     * download. the CONTROL_* constants for a list of legal values.
     * <P>
     * Type: INTEGER
     * </P>
     * <P>
     * Owner can Read
     * </P>
     * 
     * @hide
     */
    public static final String COLUMN_CONTROL = "control";

    /**
     * The name of the column containing the current status of the download.
     * Applications can read this to follow the progress of each download. See
     * the STATUS_* constants for a list of legal values.
     * <P>
     * Type: INTEGER
     * </P>
     * <P>
     * Owner can Read
     * </P>
     * 
     * @hide
     */
    public static final String COLUMN_STATUS = "status";

    /**
     * The name of the column containing the date at which some interesting
     * status changed in the download. Stored as a System.currentTimeMillis()
     * value.
     * <P>
     * Type: BIGINT
     * </P>
     * <P>
     * Owner can Read
     * </P>
     * 
     * @hide
     */
    public static final String COLUMN_LAST_MODIFICATION = "lastmod";

    /**
     * The name of the column containing the package name of the application
     * that initiating the download. The download manager will send
     * notifications to a component in this package when the download completes.
     * <P>
     * Type: TEXT
     * </P>
     * <P>
     * Owner can Init/Read
     * </P>
     * 
     * @hide
     */
    public static final String COLUMN_NOTIFICATION_PACKAGE = "notificationpackage";

    /**
     * The name of the column containing the component name of the class that
     * will receive notifications associated with the download. The
     * package/class combination is passed to
     * Intent.setClassName(String,String).
     * <P>
     * Type: TEXT
     * </P>
     * <P>
     * Owner can Init/Read
     * </P>
     * 
     * @hide
     */
    public static final String COLUMN_NOTIFICATION_CLASS = "notificationclass";

    /**
     * If extras are specified when requesting a download they will be provided
     * in the intent that is sent to the specified class and package when a
     * download has finished.
     * <P>
     * Type: TEXT
     * </P>
     * <P>
     * Owner can Init
     * </P>
     * 
     * @hide
     */
    public static final String COLUMN_NOTIFICATION_EXTRAS = "notificationextras";

    /**
     * The name of the column contain the values of the cookie to be used for
     * the download. This is used directly as the value for the Cookie: HTTP
     * header that gets sent with the request.
     * <P>
     * Type: TEXT
     * </P>
     * <P>
     * Owner can Init
     * </P>
     * 
     * @hide
     */
    public static final String COLUMN_COOKIE_DATA = "cookiedata";

    /**
     * The name of the column containing the user agent that the initiating
     * application wants the download manager to use for this download.
     * <P>
     * Type: TEXT
     * </P>
     * <P>
     * Owner can Init
     * </P>
     * 
     * @hide
     */
    public static final String COLUMN_USER_AGENT = "useragent";

    /**
     * The name of the column containing the referer (sic) that the initiating
     * application wants the download manager to use for this download.
     * <P>
     * Type: TEXT
     * </P>
     * <P>
     * Owner can Init
     * </P>
     * 
     * @hide
     */
    public static final String COLUMN_REFERER = "referer";

    /**
     * The name of the column containing the total size of the file being
     * downloaded.
     * <P>
     * Type: INTEGER
     * </P>
     * <P>
     * Owner can Read
     * </P>
     * 
     * @hide
     */
    public static final String COLUMN_TOTAL_BYTES = "total_bytes";

    /**
     * The name of the column containing the size of the part of the file that
     * has been downloaded so far.
     * <P>
     * Type: INTEGER
     * </P>
     * <P>
     * Owner can Read
     * </P>
     * 
     * @hide
     */
    public static final String COLUMN_CURRENT_BYTES = "current_bytes";

    /**
     * The name of the column where the initiating application can provide the
     * UID of another application that is allowed to access this download. If
     * multiple applications share the same UID, all those applications will be
     * allowed to access this download. This column can be updated after the
     * download is initiated. This requires the permission
     * com.mozillaonline.permission.ACCESS_DOWNLOAD_MANAGER_ADVANCED.
     * <P>
     * Type: INTEGER
     * </P>
     * <P>
     * Owner can Init
     * </P>
     * 
     * @hide
     */
    public static final String COLUMN_OTHER_UID = "otheruid";

    /**
     * The name of the column where the initiating application can provided the
     * title of this download. The title will be displayed ito the user in the
     * list of downloads.
     * <P>
     * Type: TEXT
     * </P>
     * <P>
     * Owner can Init/Read/Write
     * </P>
     * 
     * @hide
     */
    public static final String COLUMN_TITLE = "title";

    /**
     * The name of the column where the initiating application can provide the
     * description of this download. The description will be displayed to the
     * user in the list of downloads.
     * <P>
     * Type: TEXT
     * </P>
     * <P>
     * Owner can Init/Read/Write
     * </P>
     * 
     * @hide
     */
    public static final String COLUMN_DESCRIPTION = "description";

    /**
     * The name of the column indicating whether the download was requesting
     * through the public API. This controls some differences in behavior.
     * <P>
     * Type: BOOLEAN
     * </P>
     * <P>
     * Owner can Init/Read
     * </P>
     */
    public static final String COLUMN_IS_PUBLIC_API = "is_public_api";

    /**
     * The name of the column indicating whether roaming connections can be
     * used. This is only used for public API downloads.
     * <P>
     * Type: BOOLEAN
     * </P>
     * <P>
     * Owner can Init/Read
     * </P>
     */
    public static final String COLUMN_ALLOW_ROAMING = "allow_roaming";

    /**
     * The name of the column holding a bitmask of allowed network types. This
     * is only used for public API downloads.
     * <P>
     * Type: INTEGER
     * </P>
     * <P>
     * Owner can Init/Read
     * </P>
     */
    public static final String COLUMN_ALLOWED_NETWORK_TYPES = "allowed_network_types";

    /**
     * Whether or not this download should be displayed in the system's
     * Downloads UI. Defaults to true.
     * <P>
     * Type: INTEGER
     * </P>
     * <P>
     * Owner can Init/Read
     * </P>
     */
    public static final String COLUMN_IS_VISIBLE_IN_DOWNLOADS_UI = "is_visible_in_downloads_ui";

    /**
     * If true, the user has confirmed that this download can proceed over the
     * mobile network even though it exceeds the recommended maximum size.
     * <P>
     * Type: BOOLEAN
     * </P>
     */
    public static final String COLUMN_BYPASS_RECOMMENDED_SIZE_LIMIT = "bypass_recommended_size_limit";

    /**
     * Set to true if this download is deleted.
     * <P>
     * Type: BOOLEAN
     * </P>
     * <P>
     * Owner can Read
     * </P>
     * 
     * @hide
     */
    public static final String COLUMN_DELETED = "deleted";

    /*
     * Lists the destinations that an application can specify for a download.
     */

    /**
     * This download will be saved to the external storage. This is the default
     * behavior, and should be used for any file that the user can freely
     * access, copy, delete. Even with that destination, unencrypted DRM files
     * are saved in secure internal storage. Downloads to the external
     * destination only write files for which there is a registered handler. The
     * resulting files are accessible by filename to all applications.
     * 
     * @hide
     */
    public static final int DESTINATION_EXTERNAL = 0;

    /**
     * This download will be saved to the location given by the file URI in
     * {@link #COLUMN_FILE_NAME_HINT}.
     */
    public static final int DESTINATION_FILE_URI = 4;

    /**
     * This download is allowed to run.
     * 
     * @hide
     */
    public static final int CONTROL_RUN = 0;

    /**
     * This download must pause at the first opportunity.
     * 
     * @hide
     */
    public static final int CONTROL_PAUSED = 1;

    /*
     * Lists the states that the download manager can set on a download to
     * notify applications of the download progress. The codes follow the HTTP
     * families:<br> 1xx: informational<br> 2xx: success<br> 3xx: redirects (not
     * used by the download manager)<br> 4xx: client errors<br> 5xx: server
     * errors
     */

    /**
     * Returns whether the status is informational (i.e. 1xx).
     * 
     * @hide
     */
    public static boolean isStatusInformational(int status) {
	return (status >= 100 && status < 200);
    }

    /**
     * Returns whether the status is a success (i.e. 2xx).
     * 
     * @hide
     */
    public static boolean isStatusSuccess(int status) {
	return (status >= 200 && status < 300);
    }

    /**
     * Returns whether the status is an error (i.e. 4xx or 5xx).
     * 
     * @hide
     */
    public static boolean isStatusError(int status) {
	return (status >= 400 && status < 600);
    }

    /**
     * Returns whether the status is a client error (i.e. 4xx).
     * 
     * @hide
     */
    public static boolean isStatusClientError(int status) {
	return (status >= 400 && status < 500);
    }

    /**
     * Returns whether the status is a server error (i.e. 5xx).
     * 
     * @hide
     */
    public static boolean isStatusServerError(int status) {
	return (status >= 500 && status < 600);
    }

    /**
     * Returns whether the download has completed (either with success or
     * error).
     * 
     * @hide
     */
    public static boolean isStatusCompleted(int status) {
	return (status >= 200 && status < 300)
		|| (status >= 400 && status < 600);
    }

    /**
     * This download hasn't stated yet
     * 
     * @hide
     */
    public static final int STATUS_PENDING = 190;

    /**
     * This download has started
     * 
     * @hide
     */
    public static final int STATUS_RUNNING = 192;

    /**
     * This download has been paused by the owning app.
     */
    public static final int STATUS_PAUSED_BY_APP = 193;

    /**
     * This download encountered some network error and is waiting before
     * retrying the request.
     */
    public static final int STATUS_WAITING_TO_RETRY = 194;

    /**
     * This download is waiting for network connectivity to proceed.
     */
    public static final int STATUS_WAITING_FOR_NETWORK = 195;

    /**
     * This download exceeded a size limit for mobile networks and is waiting
     * for a Wi-Fi connection to proceed.
     */
    public static final int STATUS_QUEUED_FOR_WIFI = 196;

    /**
     * This download has successfully completed. Warning: there might be other
     * status values that indicate success in the future. Use isSucccess() to
     * capture the entire category.
     * 
     * @hide
     */
    public static final int STATUS_SUCCESS = 200;

    /**
     * This request couldn't be parsed. This is also used when processing
     * requests with unknown/unsupported URI schemes.
     * 
     * @hide
     */
    public static final int STATUS_BAD_REQUEST = 400;

    /**
     * This download can't be performed because the content type cannot be
     * handled.
     * 
     * @hide
     */
    public static final int STATUS_NOT_ACCEPTABLE = 406;

    /**
     * This download cannot be performed because the length cannot be determined
     * accurately. This is the code for the HTTP error "Length Required", which
     * is typically used when making requests that require a content length but
     * don't have one, and it is also used in the client when a response is
     * received whose length cannot be determined accurately (therefore making
     * it impossible to know when a download completes).
     * 
     * @hide
     */
    public static final int STATUS_LENGTH_REQUIRED = 411;

    /**
     * This download was interrupted and cannot be resumed. This is the code for
     * the HTTP error "Precondition Failed", and it is also used in situations
     * where the client doesn't have an ETag at all.
     * 
     * @hide
     */
    public static final int STATUS_PRECONDITION_FAILED = 412;

    /**
     * The lowest-valued error status that is not an actual HTTP status code.
     */
    public static final int MIN_ARTIFICIAL_ERROR_STATUS = 488;

    /**
     * The requested destination file already exists.
     */
    public static final int STATUS_FILE_ALREADY_EXISTS_ERROR = 488;

    /**
     * Some possibly transient error occurred, but we can't resume the download.
     */
    public static final int STATUS_CANNOT_RESUME = 489;

    /**
     * This download was canceled
     * 
     * @hide
     */
    public static final int STATUS_CANCELED = 490;

    /**
     * This download has completed with an error. Warning: there will be other
     * status values that indicate errors in the future. Use isStatusError() to
     * capture the entire category.
     * 
     * @hide
     */
    public static final int STATUS_UNKNOWN_ERROR = 491;

    /**
     * This download couldn't be completed because of a storage issue.
     * Typically, that's because the filesystem is missing or full. Use the more
     * specific {@link #STATUS_INSUFFICIENT_SPACE_ERROR} and
     * {@link #STATUS_DEVICE_NOT_FOUND_ERROR} when appropriate.
     * 
     * @hide
     */
    public static final int STATUS_FILE_ERROR = 492;

    /**
     * This download couldn't be completed because of an HTTP redirect response
     * that the download manager couldn't handle.
     * 
     * @hide
     */
    public static final int STATUS_UNHANDLED_REDIRECT = 493;

    /**
     * This download couldn't be completed because of an unspecified unhandled
     * HTTP code.
     * 
     * @hide
     */
    public static final int STATUS_UNHANDLED_HTTP_CODE = 494;

    /**
     * This download couldn't be completed because of an error receiving or
     * processing data at the HTTP level.
     * 
     * @hide
     */
    public static final int STATUS_HTTP_DATA_ERROR = 495;

    /**
     * This download couldn't be completed because of an HttpException while
     * setting up the request.
     * 
     * @hide
     */
    public static final int STATUS_HTTP_EXCEPTION = 496;

    /**
     * This download couldn't be completed because there were too many
     * redirects.
     * 
     * @hide
     */
    public static final int STATUS_TOO_MANY_REDIRECTS = 497;

    /**
     * This download couldn't be completed due to insufficient storage space.
     * Typically, this is because the SD card is full.
     * 
     * @hide
     */
    public static final int STATUS_INSUFFICIENT_SPACE_ERROR = 498;

    /**
     * This download couldn't be completed because no external storage device
     * was found. Typically, this is because the SD card is not mounted.
     * 
     * @hide
     */
    public static final int STATUS_DEVICE_NOT_FOUND_ERROR = 499;

    /**
     * This download is visible but only shows in the notifications while it's
     * in progress.
     * 
     * @hide
     */
    public static final int VISIBILITY_VISIBLE = 0;

    /**
     * This download is visible and shows in the notifications while in progress
     * and after completion.
     * 
     * @hide
     */
    public static final int VISIBILITY_VISIBLE_NOTIFY_COMPLETED = 1;

    /**
     * This download doesn't show in the UI or in the notifications.
     * 
     * @hide
     */
    public static final int VISIBILITY_HIDDEN = 2;

    /**
     * Constants related to HTTP request headers associated with each download.
     */
    public static class RequestHeaders {
	public static final String HEADERS_DB_TABLE = "request_headers";
	public static final String COLUMN_DOWNLOAD_ID = "download_id";
	public static final String COLUMN_HEADER = "header";
	public static final String COLUMN_VALUE = "value";

	/**
	 * Path segment to add to a download URI to retrieve request headers
	 */
	public static final String URI_SEGMENT = "headers";

	/**
	 * Prefix for ContentValues keys that contain HTTP header lines, to be
	 * passed to DownloadProvider.insert().
	 */
	public static final String INSERT_KEY_PREFIX = "http_header_";
    }
}
