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
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SyncFailedException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;

import android.content.ContentValues;
import android.content.Context;
import android.net.http.AndroidHttpClient;
import android.os.FileUtils;
import android.os.PowerManager;
import android.os.Process;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;

/**
 * Runs an actual download
 */
public class DownloadThread extends Thread {

    private Context mContext;
    private DownloadInfo mInfo;
    private SystemFacade mSystemFacade;

    public DownloadThread(Context context, SystemFacade systemFacade,
	    DownloadInfo info) {
	mContext = context;
	mSystemFacade = systemFacade;
	mInfo = info;
    }

    /**
     * Returns the user agent provided by the initiating app, or use the default
     * one
     */
    private String userAgent() {
	String userAgent = mInfo.mUserAgent;
	if (userAgent != null) {
	}
	if (userAgent == null) {
	    userAgent = Constants.DEFAULT_USER_AGENT;
	}
	return userAgent;
    }

    /**
     * State for the entire run() method.
     */
    private static class State {
	public String mFilename;
	public FileOutputStream mStream;
	public String mMimeType;
	public boolean mCountRetry = false;
	public int mRetryAfter = 0;
	public int mRedirectCount = 0;
	public String mNewUri;
	public boolean mGotData = false;
	public String mRequestUri;

	public State(DownloadInfo info) {
	    mMimeType = sanitizeMimeType(info.mMimeType);
	    mRequestUri = info.mUri;
	    mFilename = info.mFileName;
	}
    }

    /**
     * State within executeDownload()
     */
    private static class InnerState {
	public int mBytesSoFar = 0;
	public String mHeaderETag;
	public boolean mContinuingDownload = false;
	public String mHeaderContentLength;
	public String mHeaderContentDisposition;
	public String mHeaderContentLocation;
	public int mBytesNotified = 0;
	public long mTimeLastNotification = 0;
    }

    /**
     * Raised from methods called by run() to indicate that the current request
     * should be stopped immediately.
     * 
     * Note the message passed to this exception will be logged and therefore
     * must be guaranteed not to contain any PII, meaning it generally can't
     * include any information about the request URI, headers, or destination
     * filename.
     */
    private class StopRequest extends Throwable {
	private static final long serialVersionUID = 1L;

	public int mFinalStatus;

	public StopRequest(int finalStatus, String message) {
	    super(message);
	    mFinalStatus = finalStatus;
	}

	public StopRequest(int finalStatus, String message, Throwable throwable) {
	    super(message, throwable);
	    mFinalStatus = finalStatus;
	}
    }

    /**
     * Raised from methods called by executeDownload() to indicate that the
     * download should be retried immediately.
     */
    private class RetryDownload extends Throwable {
	private static final long serialVersionUID = 1L;
    }

    /**
     * Executes the download in a separate thread
     */
    public void run() {
	Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);

	State state = new State(mInfo);
	AndroidHttpClient client = null;
	PowerManager.WakeLock wakeLock = null;
	int finalStatus = Downloads.STATUS_UNKNOWN_ERROR;

	try {
	    PowerManager pm = (PowerManager) mContext
		    .getSystemService(Context.POWER_SERVICE);
	    wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
		    Constants.TAG);
	    wakeLock.acquire();

	    if (Constants.LOGV) {
		Log.v(Constants.TAG, "initiating download for " + mInfo.mUri);
	    }

	    client = AndroidHttpClient.newInstance(userAgent(), mContext);

	    boolean finished = false;
	    while (!finished) {
		Log.i(Constants.TAG, "Initiating request for download "
			+ mInfo.mId);
		HttpGet request = new HttpGet(state.mRequestUri);
		try {
		    executeDownload(state, client, request);
		    finished = true;
		} catch (RetryDownload exc) {
		    // fall through
		} finally {
		    request.abort();
		    request = null;
		}
	    }

	    if (Constants.LOGV) {
		Log.v(Constants.TAG, "download completed for " + mInfo.mUri);
	    }
	    finalizeDestinationFile(state);
	    finalStatus = Downloads.STATUS_SUCCESS;
	} catch (StopRequest error) {
	    // remove the cause before printing, in case it contains PII
	    Log.w(Constants.TAG, "Aborting request for download " + mInfo.mId
		    + ": " + error.getMessage());
	    finalStatus = error.mFinalStatus;
	    // fall through to finally block
	} catch (Throwable ex) { // sometimes the socket code throws unchecked
				 // exceptions
	    Log.w(Constants.TAG, "Exception for id " + mInfo.mId + ": " + ex);
	    finalStatus = Downloads.STATUS_UNKNOWN_ERROR;
	    // falls through to the code that reports an error
	} finally {
	    if (wakeLock != null) {
		wakeLock.release();
		wakeLock = null;
	    }
	    if (client != null) {
		client.close();
		client = null;
	    }
	    cleanupDestination(state, finalStatus);
	    notifyDownloadCompleted(finalStatus, state.mCountRetry,
		    state.mRetryAfter, state.mGotData, state.mFilename,
		    state.mNewUri, state.mMimeType);
	    mInfo.mHasActiveThread = false;
	}
    }

    /**
     * Fully execute a single download request - setup and send the request,
     * handle the response, and transfer the data to the destination file.
     */
    private void executeDownload(State state, AndroidHttpClient client,
	    HttpGet request) throws StopRequest, RetryDownload {
	InnerState innerState = new InnerState();
	byte data[] = new byte[Constants.BUFFER_SIZE];

	setupDestinationFile(state, innerState);
	addRequestHeaders(innerState, request);

	// check just before sending the request to avoid using an invalid
	// connection at all
	checkConnectivity(state);

	HttpResponse response = sendRequest(state, client, request);
	handleExceptionalStatus(state, innerState, response);

	if (Constants.LOGV) {
	    Log.v(Constants.TAG, "received response for " + mInfo.mUri);
	}

	processResponseHeaders(state, innerState, response);
	InputStream entityStream = openResponseEntity(state, response);
	transferData(state, innerState, data, entityStream);
    }

    /**
     * Check if current connectivity is valid for this request.
     */
    private void checkConnectivity(State state) throws StopRequest {
	int networkUsable = mInfo.checkCanUseNetwork();
	if (networkUsable != DownloadInfo.NETWORK_OK) {
	    int status = Downloads.STATUS_WAITING_FOR_NETWORK;
	    if (networkUsable == DownloadInfo.NETWORK_UNUSABLE_DUE_TO_SIZE) {
		status = Downloads.STATUS_QUEUED_FOR_WIFI;
		mInfo.notifyPauseDueToSize(true);
	    } else if (networkUsable == DownloadInfo.NETWORK_RECOMMENDED_UNUSABLE_DUE_TO_SIZE) {
		status = Downloads.STATUS_QUEUED_FOR_WIFI;
		mInfo.notifyPauseDueToSize(false);
	    }
	    throw new StopRequest(status,
		    mInfo.getLogMessageForNetworkError(networkUsable));
	}
    }

    /**
     * Transfer as much data as possible from the HTTP response to the
     * destination file.
     * 
     * @param data
     *            buffer to use to read data
     * @param entityStream
     *            stream for reading the HTTP response entity
     */
    private void transferData(State state, InnerState innerState, byte[] data,
	    InputStream entityStream) throws StopRequest {
	for (;;) {
	    int bytesRead = readFromResponse(state, innerState, data,
		    entityStream);
	    if (bytesRead == -1) { // success, end of stream already reached
		handleEndOfStream(state, innerState);
		return;
	    }

	    state.mGotData = true;
	    writeDataToDestination(state, data, bytesRead);
	    innerState.mBytesSoFar += bytesRead;
	    reportProgress(state, innerState);

	    if (Constants.LOGVV) {
		Log.v(Constants.TAG, "downloaded " + innerState.mBytesSoFar
			+ " for " + mInfo.mUri);
	    }

	    checkPausedOrCanceled(state);
	}
    }

    /**
     * Called after a successful completion to take any necessary action on the
     * downloaded file.
     */
    private void finalizeDestinationFile(State state) throws StopRequest {
	// make sure the file is readable
	FileUtils.setPermissions(state.mFilename, 0644, -1, -1);
	syncDestination(state);
    }

    /**
     * Called just before the thread finishes, regardless of status, to take any
     * necessary action on the downloaded file.
     */
    private void cleanupDestination(State state, int finalStatus) {
	closeDestination(state);
	if (state.mFilename != null && Downloads.isStatusError(finalStatus)) {
	    new File(state.mFilename).delete();
	    state.mFilename = null;
	}
    }

    /**
     * Sync the destination file to storage.
     */
    private void syncDestination(State state) {
	FileOutputStream downloadedFileStream = null;
	try {
	    downloadedFileStream = new FileOutputStream(state.mFilename, true);
	    downloadedFileStream.getFD().sync();
	} catch (FileNotFoundException ex) {
	    Log.w(Constants.TAG, "file " + state.mFilename + " not found: "
		    + ex);
	} catch (SyncFailedException ex) {
	    Log.w(Constants.TAG, "file " + state.mFilename + " sync failed: "
		    + ex);
	} catch (IOException ex) {
	    Log.w(Constants.TAG, "IOException trying to sync "
		    + state.mFilename + ": " + ex);
	} catch (RuntimeException ex) {
	    Log.w(Constants.TAG, "exception while syncing file: ", ex);
	} finally {
	    if (downloadedFileStream != null) {
		try {
		    downloadedFileStream.close();
		} catch (IOException ex) {
		    Log.w(Constants.TAG,
			    "IOException while closing synced file: ", ex);
		} catch (RuntimeException ex) {
		    Log.w(Constants.TAG, "exception while closing file: ", ex);
		}
	    }
	}
    }

    /**
     * Close the destination output stream.
     */
    private void closeDestination(State state) {
	try {
	    // close the file
	    if (state.mStream != null) {
		state.mStream.close();
		state.mStream = null;
	    }
	} catch (IOException ex) {
	    if (Constants.LOGV) {
		Log.v(Constants.TAG,
			"exception when closing the file after download : "
				+ ex);
	    }
	    // nothing can really be done if the file can't be closed
	}
    }

    /**
     * Check if the download has been paused or canceled, stopping the request
     * appropriately if it has been.
     */
    private void checkPausedOrCanceled(State state) throws StopRequest {
	synchronized (mInfo) {
	    if (mInfo.mControl == Downloads.CONTROL_PAUSED) {
		throw new StopRequest(Downloads.STATUS_PAUSED_BY_APP,
			"download paused by owner");
	    }
	}
	if (mInfo.mStatus == Downloads.STATUS_CANCELED) {
	    throw new StopRequest(Downloads.STATUS_CANCELED,
		    "download canceled");
	}
    }

    /**
     * Report download progress through the database if necessary.
     */
    private void reportProgress(State state, InnerState innerState) {
	long now = mSystemFacade.currentTimeMillis();
	if (innerState.mBytesSoFar - innerState.mBytesNotified > Constants.MIN_PROGRESS_STEP
		&& now - innerState.mTimeLastNotification > Constants.MIN_PROGRESS_TIME) {
	    ContentValues values = new ContentValues();
	    values.put(Downloads.COLUMN_CURRENT_BYTES, innerState.mBytesSoFar);
	    mContext.getContentResolver().update(mInfo.getAllDownloadsUri(),
		    values, null, null);
	    innerState.mBytesNotified = innerState.mBytesSoFar;
	    innerState.mTimeLastNotification = now;
	}
    }

    /**
     * Write a data buffer to the destination file.
     * 
     * @param data
     *            buffer containing the data to write
     * @param bytesRead
     *            how many bytes to write from the buffer
     */
    private void writeDataToDestination(State state, byte[] data, int bytesRead)
	    throws StopRequest {
	for (;;) {
	    try {
		if (state.mStream == null) {
		    state.mStream = new FileOutputStream(state.mFilename, true);
		}
		state.mStream.write(data, 0, bytesRead);
		if (mInfo.mDestination == Downloads.DESTINATION_EXTERNAL) {
		    closeDestination(state);
		}
		return;
	    } catch (IOException ex) {
		if (!Helpers.isExternalMediaMounted()) {
		    throw new StopRequest(
			    Downloads.STATUS_DEVICE_NOT_FOUND_ERROR,
			    "external media not mounted while writing destination file");
		}

		long availableBytes = Helpers.getAvailableBytes(Helpers
			.getFilesystemRoot(state.mFilename));
		if (availableBytes < bytesRead) {
		    throw new StopRequest(
			    Downloads.STATUS_INSUFFICIENT_SPACE_ERROR,
			    "insufficient space while writing destination file",
			    ex);
		}
		throw new StopRequest(Downloads.STATUS_FILE_ERROR,
			"while writing destination file: " + ex.toString(), ex);
	    }
	}
    }

    /**
     * Called when we've reached the end of the HTTP response stream, to update
     * the database and check for consistency.
     */
    private void handleEndOfStream(State state, InnerState innerState)
	    throws StopRequest {
	ContentValues values = new ContentValues();
	values.put(Downloads.COLUMN_CURRENT_BYTES, innerState.mBytesSoFar);
	if (innerState.mHeaderContentLength == null) {
	    values.put(Downloads.COLUMN_TOTAL_BYTES, innerState.mBytesSoFar);
	}
	mContext.getContentResolver().update(mInfo.getAllDownloadsUri(),
		values, null, null);

	boolean lengthMismatched = (innerState.mHeaderContentLength != null)
		&& (innerState.mBytesSoFar != Integer
			.parseInt(innerState.mHeaderContentLength));
	if (lengthMismatched) {
	    if (cannotResume(innerState)) {
		throw new StopRequest(Downloads.STATUS_CANNOT_RESUME,
			"mismatched content length");
	    } else {
		throw new StopRequest(getFinalStatusForHttpError(state),
			"closed socket before end of file");
	    }
	}
    }

    private boolean cannotResume(InnerState innerState) {
	return innerState.mBytesSoFar > 0 && !mInfo.mNoIntegrity
		&& innerState.mHeaderETag == null;
    }

    /**
     * Read some data from the HTTP response stream, handling I/O errors.
     * 
     * @param data
     *            buffer to use to read data
     * @param entityStream
     *            stream for reading the HTTP response entity
     * @return the number of bytes actually read or -1 if the end of the stream
     *         has been reached
     */
    private int readFromResponse(State state, InnerState innerState,
	    byte[] data, InputStream entityStream) throws StopRequest {
	try {
	    return entityStream.read(data);
	} catch (IOException ex) {
	    logNetworkState();
	    ContentValues values = new ContentValues();
	    values.put(Downloads.COLUMN_CURRENT_BYTES, innerState.mBytesSoFar);
	    mContext.getContentResolver().update(mInfo.getAllDownloadsUri(),
		    values, null, null);
	    if (cannotResume(innerState)) {
		String message = "while reading response: " + ex.toString()
			+ ", can't resume interrupted download with no ETag";
		throw new StopRequest(Downloads.STATUS_CANNOT_RESUME, message,
			ex);
	    } else {
		throw new StopRequest(getFinalStatusForHttpError(state),
			"while reading response: " + ex.toString(), ex);
	    }
	}
    }

    /**
     * Open a stream for the HTTP response entity, handling I/O errors.
     * 
     * @return an InputStream to read the response entity
     */
    private InputStream openResponseEntity(State state, HttpResponse response)
	    throws StopRequest {
	try {
	    return response.getEntity().getContent();
	} catch (IOException ex) {
	    logNetworkState();
	    throw new StopRequest(getFinalStatusForHttpError(state),
		    "while getting entity: " + ex.toString(), ex);
	}
    }

    private void logNetworkState() {
	if (Constants.LOGX) {
	    Log.i(Constants.TAG,
		    "Net "
			    + (Helpers.isNetworkAvailable(mSystemFacade) ? "Up"
				    : "Down"));
	}
    }

    /**
     * Read HTTP response headers and take appropriate action, including setting
     * up the destination file and updating the database.
     */
    private void processResponseHeaders(State state, InnerState innerState,
	    HttpResponse response) throws StopRequest {
	if (innerState.mContinuingDownload) {
	    // ignore response headers on resume requests
	    return;
	}

	readResponseHeaders(state, innerState, response);

	try {
	    state.mFilename = Helpers.generateSaveFile(
		    mContext,
		    mInfo.mUri,
		    mInfo.mHint,
		    innerState.mHeaderContentDisposition,
		    innerState.mHeaderContentLocation,
		    state.mMimeType,
		    mInfo.mDestination,
		    (innerState.mHeaderContentLength != null) ? Long
			    .parseLong(innerState.mHeaderContentLength) : 0,
		    mInfo.mIsPublicApi);
	} catch (Helpers.GenerateSaveFileError exc) {
	    throw new StopRequest(exc.mStatus, exc.mMessage);
	}
	try {
	    state.mStream = new FileOutputStream(state.mFilename);
	} catch (FileNotFoundException exc) {
	    throw new StopRequest(Downloads.STATUS_FILE_ERROR,
		    "while opening destination file: " + exc.toString(), exc);
	}
	if (Constants.LOGV) {
	    Log.v(Constants.TAG, "writing " + mInfo.mUri + " to "
		    + state.mFilename);
	}

	updateDatabaseFromHeaders(state, innerState);
	// check connectivity again now that we know the total size
	checkConnectivity(state);
    }

    /**
     * Update necessary database fields based on values of HTTP response headers
     * that have been read.
     */
    private void updateDatabaseFromHeaders(State state, InnerState innerState) {
	ContentValues values = new ContentValues();
	values.put(Downloads._DATA, state.mFilename);
	if (innerState.mHeaderETag != null) {
	    values.put(Constants.ETAG, innerState.mHeaderETag);
	}
	if (state.mMimeType != null) {
	    values.put(Downloads.COLUMN_MIME_TYPE, state.mMimeType);
	}
	values.put(Downloads.COLUMN_TOTAL_BYTES, mInfo.mTotalBytes);
	mContext.getContentResolver().update(mInfo.getAllDownloadsUri(),
		values, null, null);
    }

    /**
     * Read headers from the HTTP response and store them into local state.
     */
    private void readResponseHeaders(State state, InnerState innerState,
	    HttpResponse response) throws StopRequest {
	Header header = response.getFirstHeader("Content-Disposition");
	if (header != null) {
	    innerState.mHeaderContentDisposition = header.getValue();
	}
	header = response.getFirstHeader("Content-Location");
	if (header != null) {
	    innerState.mHeaderContentLocation = header.getValue();
	}
	if (state.mMimeType == null) {
	    header = response.getFirstHeader("Content-Type");
	    if (header != null) {
		state.mMimeType = sanitizeMimeType(header.getValue());
	    }
	}
	header = response.getFirstHeader("ETag");
	if (header != null) {
	    innerState.mHeaderETag = header.getValue();
	}
	String headerTransferEncoding = null;
	header = response.getFirstHeader("Transfer-Encoding");
	if (header != null) {
	    headerTransferEncoding = header.getValue();
	}
	if (headerTransferEncoding == null) {
	    header = response.getFirstHeader("Content-Length");
	    if (header != null) {
		innerState.mHeaderContentLength = header.getValue();
		mInfo.mTotalBytes = Long
			.parseLong(innerState.mHeaderContentLength);
	    }
	} else {
	    // Ignore content-length with transfer-encoding - 2616 4.4 3
	    if (Constants.LOGVV) {
		Log.v(Constants.TAG,
			"ignoring content-length because of xfer-encoding");
	    }
	}
	if (Constants.LOGVV) {
	    Log.v(Constants.TAG, "Content-Disposition: "
		    + innerState.mHeaderContentDisposition);
	    Log.v(Constants.TAG, "Content-Length: "
		    + innerState.mHeaderContentLength);
	    Log.v(Constants.TAG, "Content-Location: "
		    + innerState.mHeaderContentLocation);
	    Log.v(Constants.TAG, "Content-Type: " + state.mMimeType);
	    Log.v(Constants.TAG, "ETag: " + innerState.mHeaderETag);
	    Log.v(Constants.TAG, "Transfer-Encoding: " + headerTransferEncoding);
	}

	boolean noSizeInfo = innerState.mHeaderContentLength == null
		&& (headerTransferEncoding == null || !headerTransferEncoding
			.equalsIgnoreCase("chunked"));
	if (!mInfo.mNoIntegrity && noSizeInfo) {
	    throw new StopRequest(Downloads.STATUS_HTTP_DATA_ERROR,
		    "can't know size of download, giving up");
	}
    }

    /**
     * Check the HTTP response status and handle anything unusual (e.g. not
     * 200/206).
     */
    private void handleExceptionalStatus(State state, InnerState innerState,
	    HttpResponse response) throws StopRequest, RetryDownload {
	int statusCode = response.getStatusLine().getStatusCode();
	if (statusCode == 503 && mInfo.mNumFailed < Constants.MAX_RETRIES) {
	    handleServiceUnavailable(state, response);
	}
	if (statusCode == 301 || statusCode == 302 || statusCode == 303
		|| statusCode == 307) {
	    handleRedirect(state, response, statusCode);
	}

	int expectedStatus = innerState.mContinuingDownload ? 206
		: Downloads.STATUS_SUCCESS;
	if (statusCode != expectedStatus) {
	    handleOtherStatus(state, innerState, statusCode);
	}
    }

    /**
     * Handle a status that we don't know how to deal with properly.
     */
    private void handleOtherStatus(State state, InnerState innerState,
	    int statusCode) throws StopRequest {
	int finalStatus;
	if (Downloads.isStatusError(statusCode)) {
	    finalStatus = statusCode;
	} else if (statusCode >= 300 && statusCode < 400) {
	    finalStatus = Downloads.STATUS_UNHANDLED_REDIRECT;
	} else if (innerState.mContinuingDownload
		&& statusCode == Downloads.STATUS_SUCCESS) {
	    finalStatus = Downloads.STATUS_CANNOT_RESUME;
	} else {
	    finalStatus = Downloads.STATUS_UNHANDLED_HTTP_CODE;
	}
	throw new StopRequest(finalStatus, "http error " + statusCode);
    }

    /**
     * Handle a 3xx redirect status.
     */
    private void handleRedirect(State state, HttpResponse response,
	    int statusCode) throws StopRequest, RetryDownload {
	if (Constants.LOGVV) {
	    Log.v(Constants.TAG, "got HTTP redirect " + statusCode);
	}
	if (state.mRedirectCount >= Constants.MAX_REDIRECTS) {
	    throw new StopRequest(Downloads.STATUS_TOO_MANY_REDIRECTS,
		    "too many redirects");
	}
	Header header = response.getFirstHeader("Location");
	if (header == null) {
	    return;
	}
	if (Constants.LOGVV) {
	    Log.v(Constants.TAG, "Location :" + header.getValue());
	}

	String newUri;
	try {
	    newUri = new URI(mInfo.mUri).resolve(new URI(header.getValue()))
		    .toString();
	} catch (URISyntaxException ex) {
	    if (Constants.LOGV) {
		Log.d(Constants.TAG,
			"Couldn't resolve redirect URI " + header.getValue()
				+ " for " + mInfo.mUri);
	    }
	    throw new StopRequest(Downloads.STATUS_HTTP_DATA_ERROR,
		    "Couldn't resolve redirect URI");
	}
	++state.mRedirectCount;
	state.mRequestUri = newUri;
	if (statusCode == 301 || statusCode == 303) {
	    // use the new URI for all future requests (should a retry/resume be
	    // necessary)
	    state.mNewUri = newUri;
	}
	throw new RetryDownload();
    }

    /**
     * Handle a 503 Service Unavailable status by processing the Retry-After
     * header.
     */
    private void handleServiceUnavailable(State state, HttpResponse response)
	    throws StopRequest {
	if (Constants.LOGVV) {
	    Log.v(Constants.TAG, "got HTTP response code 503");
	}
	state.mCountRetry = true;
	Header header = response.getFirstHeader("Retry-After");
	if (header != null) {
	    try {
		if (Constants.LOGVV) {
		    Log.v(Constants.TAG, "Retry-After :" + header.getValue());
		}
		state.mRetryAfter = Integer.parseInt(header.getValue());
		if (state.mRetryAfter < 0) {
		    state.mRetryAfter = 0;
		} else {
		    if (state.mRetryAfter < Constants.MIN_RETRY_AFTER) {
			state.mRetryAfter = Constants.MIN_RETRY_AFTER;
		    } else if (state.mRetryAfter > Constants.MAX_RETRY_AFTER) {
			state.mRetryAfter = Constants.MAX_RETRY_AFTER;
		    }
		    state.mRetryAfter += Helpers.sRandom
			    .nextInt(Constants.MIN_RETRY_AFTER + 1);
		    state.mRetryAfter *= 1000;
		}
	    } catch (NumberFormatException ex) {
		// ignored - retryAfter stays 0 in this case.
	    }
	}
	throw new StopRequest(Downloads.STATUS_WAITING_TO_RETRY,
		"got 503 Service Unavailable, will retry later");
    }

    /**
     * Send the request to the server, handling any I/O exceptions.
     */
    private HttpResponse sendRequest(State state, AndroidHttpClient client,
	    HttpGet request) throws StopRequest {
	try {
	    return client.execute(request);
	} catch (IllegalArgumentException ex) {
	    throw new StopRequest(Downloads.STATUS_HTTP_DATA_ERROR,
		    "while trying to execute request: " + ex.toString(), ex);
	} catch (IOException ex) {
	    logNetworkState();
	    throw new StopRequest(getFinalStatusForHttpError(state),
		    "while trying to execute request: " + ex.toString(), ex);
	}
    }

    private int getFinalStatusForHttpError(State state) {
	if (!Helpers.isNetworkAvailable(mSystemFacade)) {
	    return Downloads.STATUS_WAITING_FOR_NETWORK;
	} else if (mInfo.mNumFailed < Constants.MAX_RETRIES) {
	    state.mCountRetry = true;
	    return Downloads.STATUS_WAITING_TO_RETRY;
	} else {
	    Log.w(Constants.TAG, "reached max retries for " + mInfo.mId);
	    return Downloads.STATUS_HTTP_DATA_ERROR;
	}
    }

    /**
     * Prepare the destination file to receive data. If the file already exists,
     * we'll set up appropriately for resumption.
     */
    private void setupDestinationFile(State state, InnerState innerState)
	    throws StopRequest {
	if (!TextUtils.isEmpty(state.mFilename)) { // only true if we've already
						   // run a thread for this
						   // download
	    if (!Helpers.isFilenameValid(state.mFilename)) {
		// this should never happen
		throw new StopRequest(Downloads.STATUS_FILE_ERROR,
			"found invalid internal destination filename");
	    }
	    // We're resuming a download that got interrupted
	    File f = new File(state.mFilename);
	    if (f.exists()) {
		long fileLength = f.length();
		if (fileLength == 0) {
		    // The download hadn't actually started, we can restart from
		    // scratch
		    f.delete();
		    state.mFilename = null;
		} else if (mInfo.mETag == null && !mInfo.mNoIntegrity) {
		    // This should've been caught upon failure
		    f.delete();
		    throw new StopRequest(Downloads.STATUS_CANNOT_RESUME,
			    "Trying to resume a download that can't be resumed");
		} else {
		    // All right, we'll be able to resume this download
		    try {
			state.mStream = new FileOutputStream(state.mFilename,
				true);
		    } catch (FileNotFoundException exc) {
			throw new StopRequest(Downloads.STATUS_FILE_ERROR,
				"while opening destination for resuming: "
					+ exc.toString(), exc);
		    }
		    innerState.mBytesSoFar = (int) fileLength;
		    if (mInfo.mTotalBytes != -1) {
			innerState.mHeaderContentLength = Long
				.toString(mInfo.mTotalBytes);
		    }
		    innerState.mHeaderETag = mInfo.mETag;
		    innerState.mContinuingDownload = true;
		}
	    }
	}

	if (state.mStream != null
		&& mInfo.mDestination == Downloads.DESTINATION_EXTERNAL) {
	    closeDestination(state);
	}
    }

    /**
     * Add custom headers for this download to the HTTP request.
     */
    private void addRequestHeaders(InnerState innerState, HttpGet request) {
	for (Pair<String, String> header : mInfo.getHeaders()) {
	    request.addHeader(header.first, header.second);
	}

	if (innerState.mContinuingDownload) {
	    if (innerState.mHeaderETag != null) {
		request.addHeader("If-Match", innerState.mHeaderETag);
	    }
	    request.addHeader("Range", "bytes=" + innerState.mBytesSoFar + "-");
	}
    }

    /**
     * Stores information about the completed download, and notifies the
     * initiating application.
     */
    private void notifyDownloadCompleted(int status, boolean countRetry,
	    int retryAfter, boolean gotData, String filename, String uri,
	    String mimeType) {
	notifyThroughDatabase(status, countRetry, retryAfter, gotData,
		filename, uri, mimeType);
	if (Downloads.isStatusCompleted(status)) {
	    mInfo.sendIntentIfRequested();
	}
    }

    private void notifyThroughDatabase(int status, boolean countRetry,
	    int retryAfter, boolean gotData, String filename, String uri,
	    String mimeType) {
	ContentValues values = new ContentValues();
	values.put(Downloads.COLUMN_STATUS, status);
	values.put(Downloads._DATA, filename);
	if (uri != null) {
	    values.put(Downloads.COLUMN_URI, uri);
	}
	values.put(Downloads.COLUMN_MIME_TYPE, mimeType);
	values.put(Downloads.COLUMN_LAST_MODIFICATION,
		mSystemFacade.currentTimeMillis());
	values.put(Constants.RETRY_AFTER_X_REDIRECT_COUNT, retryAfter);
	if (!countRetry) {
	    values.put(Constants.FAILED_CONNECTIONS, 0);
	} else if (gotData) {
	    values.put(Constants.FAILED_CONNECTIONS, 1);
	} else {
	    values.put(Constants.FAILED_CONNECTIONS, mInfo.mNumFailed + 1);
	}

	mContext.getContentResolver().update(mInfo.getAllDownloadsUri(),
		values, null, null);
    }

    /**
     * Clean up a mimeType string so it can be used to dispatch an intent to
     * view a downloaded asset.
     * 
     * @param mimeType
     *            either null or one or more mime types (semi colon separated).
     * @return null if mimeType was null. Otherwise a string which represents a
     *         single mimetype in lowercase and with surrounding whitespaces
     *         trimmed.
     */
    private static String sanitizeMimeType(String mimeType) {
	try {
	    mimeType = mimeType.trim().toLowerCase(Locale.ENGLISH);

	    final int semicolonIndex = mimeType.indexOf(';');
	    if (semicolonIndex != -1) {
		mimeType = mimeType.substring(0, semicolonIndex);
	    }
	    return mimeType;
	} catch (NullPointerException npe) {
	    return null;
	}
    }
}
