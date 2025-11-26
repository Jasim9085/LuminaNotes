package com.lumina.notes;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
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

public class MainActivity extends AppCompatActivity {

    private WebView myWebView;
    // Variables for File Chooser (Import)
    private ValueCallback<Uri[]> mUploadMessage;
    public static final int FILECHOOSER_RESULTCODE = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // --- UI CUSTOMIZATION ---
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

        // --- WEBVIEW SETUP ---
        myWebView = (WebView) findViewById(R.id.webview);
        WebSettings webSettings = myWebView.getSettings();
        
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setDatabaseEnabled(true);
        webSettings.setAllowFileAccess(true);
        webSettings.setAllowFileAccessFromFileURLs(true);
        webSettings.setAllowUniversalAccessFromFileURLs(true);

        myWebView.setWebViewClient(new WebViewClient());
        myWebView.addJavascriptInterface(new WebAppInterface(this), "Android");

        // 1. HANDLE IMPORTS (File Chooser)
        myWebView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
                if (mUploadMessage != null) {
                    mUploadMessage.onReceiveValue(null);
                }
                mUploadMessage = filePathCallback;

                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*"); // Allow all file types (for .zip, .md, .html)
                
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

        // 2. HANDLE EXPORTS (Blob Downloader)
        myWebView.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimetype, long contentLength) {
                if (url.startsWith("blob:")) {
                    // Inject JS to convert Blob -> Base64 -> Pass to Android Interface
                    // We surmise the filename from the MIME type or use a default
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
                            "        var base64data = reader.result;" +
                            "        Android.saveBlobToDownloads(base64data, 'Lumina_Export.zip');" +
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

    // Handle File Chooser Result
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
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
        }
    }
    
    // Handle Back Button to navigate WebView history instead of closing app
    @Override
    public void onBackPressed() {
        if (myWebView.canGoBack()) {
            myWebView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
