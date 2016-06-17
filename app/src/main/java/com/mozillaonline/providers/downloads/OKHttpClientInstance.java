package com.mozillaonline.providers.downloads;

import com.squareup.okhttp.OkHttpClient;

/**
 * Created by geminiwen on 15/12/3.
 */
public class OKHttpClientInstance {
    private static OKHttpClientInstance sInstance;

    private OkHttpClient mOkHttpClient;

    public synchronized static OKHttpClientInstance getInstance() {
        if (sInstance == null) {
            sInstance = new OKHttpClientInstance();
        }
        return sInstance;
    }

    private OKHttpClientInstance() {
        mOkHttpClient = new OkHttpClient();
    }

    public OkHttpClient client() {
        return mOkHttpClient;
    }

}
