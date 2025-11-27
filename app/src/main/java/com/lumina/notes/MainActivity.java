package com.lumina.notes;

import android.app.Activity;
import android.Manifest;
import android.content.pm.PackageManager;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.DownloadListener;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.app.ActivityCompat;

import java.io.OutputStream;

public class MainActivity extends AppCompatActivity {

    private WebView myWebView;

    private ValueCallback<Uri[]> mUploadMessage;
    public static final int FILECHOOSER_RESULTCODE = 1;
    private static final int PERMISSION_REQUEST_CODE = 1001;

    // NEW: Fallback SAF Save
    private static final int CREATE_FILE_REQUEST_CODE = 2001;
    private byte[] pendingFileBytes = null;
    private String pendingFileName = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // --- UI CUSTOMIZATION (untouched) ---
        try {
            Window window = getWindow();
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(Color.parseColor("#020617"));
            window.setNavigationBarColor(Color.parseColor("#020617"));
            View decorView = window.getDecorView();
            int flags = decorView.getSystemUiVisibility();
            flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            decorView.setSystemUiVisibility(flags);
        } catch (Exception e) {
            e.printStackTrace();
        }

        // --- PERMISSIONS (untouched, even if storage is ignored on 14) ---
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                            != PackageManager.PERMISSION_GRANTED) {

                ActivityCompat.requestPermissions(this,
                        new String[]{
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.WRITE_EXTERNAL_STORAGE
                        },
                        PERMISSION_REQUEST_CODE);
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        PERMISSION_REQUEST_CODE);
            }
        }

        // --- WEBVIEW SETUP (untouched) ---
        myWebView = findViewById(R.id.webview);
        WebSettings webSettings = myWebView.getSettings();

        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setDatabaseEnabled(true);
        webSettings.setAllowFileAccess(true);
        webSettings.setAllowFileAccessFromFileURLs(true);
        webSettings.setAllowUniversalAccessFromFileURLs(true);

        myWebView.setWebViewClient(new WebViewClient());
        myWebView.addJavascriptInterface(new WebAppInterface(this), "Android");

        myWebView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
                if (mUploadMessage != null) {
                    mUploadMessage.onReceiveValue(null);
                }
                mUploadMessage = filePathCallback;

                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");

                try {
                    startActivityForResult(Intent.createChooser(intent, "Select File"), FILECHOOSER_RESULTCODE);
                } catch (Exception e) {
                    mUploadMessage = null;
                    Toast.makeText(MainActivity.this, "Cannot open file picker", Toast.LENGTH_LONG).show();
                    return false;
                }
                return true;
            }
        });

        // --- DOWNLOAD LISTENER (unchanged, except fallback call) ---
        myWebView.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimetype, long contentLength) {
                if (url.startsWith("blob:")) {

                    String js = "javascript:(function() {" +
                            "  var xhr = new XMLHttpRequest();" +
                            "  xhr.open('GET', '" + url + "', true);" +
                            "  xhr.responseType = 'blob';" +
                            "  xhr.onload = function() {" +
                            "    if (this.status == 200) {" +
                            "      var blob = this.response;" +
                            "      var reader = new FileReader();" +
                            "      reader.readAsDataURL(blob);" +
                            "      reader.onloadend = function() {" +
                            "        Android.saveBlobToDownloads(reader.result, 'Lumina_Export.zip');" +
                            "      }" +
                            "    }" +
                            "  };" +
                            "  xhr.send();" +
                            "})()";

                    myWebView.loadUrl(js);
                }
            }
        });

        WebView.setWebContentsDebuggingEnabled(true);
        myWebView.loadUrl("file:///android_asset/index.html");
    }

    // ---------------------------------------------------
    // SAF Fallback: open Save dialog
    // ---------------------------------------------------
    public void fallbackSaveUsingSAF(String filename, byte[] bytes) {
        pendingFileName = filename;
        pendingFileBytes = bytes;

        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_TITLE, filename);

        startActivityForResult(intent, CREATE_FILE_REQUEST_CODE);
    }

    // ---------------------------------------------------
    // Activity Result
    // ---------------------------------------------------

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);

        if (requestCode == 2001 && resultCode == RESULT_OK) {
            ((WebAppInterface) myWebView.getJavascriptInterface("Android")).completeFallbackSave(intent.getData());
        }
        // FILE PICKER FOR IMPORT (unchanged)
        if (requestCode == FILECHOOSER_RESULTCODE) {
            if (mUploadMessage == null) return;
            Uri[] results = null;
            if (resultCode == Activity.RESULT_OK) {
                if (intent != null) {
                    String dataString = intent.getDataString();
                    if (dataString != null) {
                        results = new Uri[]{Uri.parse(dataString)};
                    }
                }
            }
            mUploadMessage.onReceiveValue(results);
            mUploadMessage = null;
            return;
        }

        // NEW: SAF RESULT HANDLER
        if (requestCode == CREATE_FILE_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            if (intent != null && pendingFileBytes != null) {
                Uri uri = intent.getData();
                try {
                    OutputStream os = getContentResolver().openOutputStream(uri);
                    if (os != null) {
                        os.write(pendingFileBytes);
                        os.close();
                        Toast.makeText(this, "File saved successfully", Toast.LENGTH_SHORT).show();
                    }
                } catch (Exception e) {
                    Toast.makeText(this, "Failed to save file", Toast.LENGTH_LONG).show();
                }
            }
            pendingFileBytes = null;
            pendingFileName = null;
        }
    }

    @Override
    public void onBackPressed() {
        if (myWebView.canGoBack()) {
            myWebView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
