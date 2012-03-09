package com.mozillaonline.downloadprovider;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;

import com.mozillaonline.providers.DownloadManager;
import com.mozillaonline.providers.DownloadManager.Request;
import com.mozillaonline.providers.downloads.DownloadService;
import com.mozillaonline.providers.downloads.ui.DownloadList;

public class DownloadProviderActivity extends Activity implements
	OnClickListener {
    @SuppressWarnings("unused")
    private static final String TAG = DownloadProviderActivity.class.getName();

    EditText mUrlInputEditText;
    Button mStartDownloadButton;
    DownloadManager mDownloadManager;
    Button mShowDownloadListButton;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
	super.onCreate(savedInstanceState);
	setContentView(R.layout.main);

	mDownloadManager = new DownloadManager(getContentResolver(),
		getPackageName());

	buildComponents();
	startDownloadService();
    }

    private void buildComponents() {
	mUrlInputEditText = (EditText) findViewById(R.id.url_input_edittext);
	mStartDownloadButton = (Button) findViewById(R.id.start_download_button);
	mShowDownloadListButton = (Button) findViewById(R.id.show_download_list_button);

	mStartDownloadButton.setOnClickListener(this);
	mShowDownloadListButton.setOnClickListener(this);

	mUrlInputEditText
		.setText("http://yxl.github.com/Fire-IE/fireie-latest.xpi");
    }

    private void startDownloadService() {
	Intent intent = new Intent();
	intent.setClass(this, DownloadService.class);
	startService(intent);
    }

    @Override
    public void onClick(View v) {
	int id = v.getId();
	switch (id) {
	case R.id.start_download_button:
	    startDownload();
	    break;
	case R.id.show_download_list_button:
	    showDownloadList();
	    break;
	default:
	    break;
	}
    }

    private void showDownloadList() {
	Intent intent = new Intent();
	intent.setClass(this, DownloadList.class);
	startActivity(intent);
    }

    private void startDownload() {
	String url = mUrlInputEditText.getText().toString();
	Uri srcUri = Uri.parse(url);
	DownloadManager.Request request = new Request(srcUri);
	for (int i=0; i<2; i++) {
	    request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "/");
	    mDownloadManager.enqueue(request);
	}
    }
}