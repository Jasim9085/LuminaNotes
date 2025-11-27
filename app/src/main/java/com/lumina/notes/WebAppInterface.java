package com.lumina.notes;

import android.content.Context;
import android.content.Intent;   // NEW
import android.net.Uri;         // NEW
import android.os.Environment;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;     // NEW
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import java.text.SimpleDateFormat;
import java.util.Date;

public class WebAppInterface {
    private Context mContext;

    // Fallback buffer (NEW)
    private byte[] pendingData = null;
    private String pendingFilename = null;

    public WebAppInterface(Context c) {
        mContext = c;
    }

    // ---------- EXISTING METHODS (unchanged) ----------

    private File getFile(String path) {
        if (path.contains("..")) return null;
        return new File(mContext.getFilesDir(), path);
    }

    @JavascriptInterface
    public void saveFile(String path, String content) {
        try {
            File file = getFile(path);
            if (file == null) return;
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            FileOutputStream fos = new FileOutputStream(file);
            fos.write(content.getBytes(StandardCharsets.UTF_8));
            fos.close();
        } catch (IOException e) { e.printStackTrace(); }
    }

    @JavascriptInterface
    public String readFile(String path) {
        try {
            File file = getFile(path);
            if (file == null || !file.exists()) return null;
            FileInputStream fis = new FileInputStream(file);
            int size = fis.available();
            byte[] buffer = new byte[size];
            fis.read(buffer);
            fis.close();
            return new String(buffer, StandardCharsets.UTF_8);
        } catch (IOException e) { return null; }
    }

    @JavascriptInterface
    public void deleteFile(String path) {
        File file = getFile(path);
        if (file != null && file.exists()) file.delete();
    }

    @JavascriptInterface
    public String listFiles(String dir) {
        File folder = getFile(dir);
        if (folder != null && folder.exists() && folder.isDirectory()) {
            File[] listOfFiles = folder.listFiles();
            List<String> fileNames = new ArrayList<>();
            if (listOfFiles != null) {
                for (File f : listOfFiles) {
                    if (f.isFile()) fileNames.add(f.getName());
                }
            }
            return new JSONArray(fileNames).toString();
        }
        return "[]";
    }

    @JavascriptInterface
    public void createDir(String dir) {
        File folder = getFile(dir);
        if (folder != null && !folder.exists()) folder.mkdirs();
    }

    // ---------- NEW: Helper to detect Android 10+ restrictions ----------

    private boolean canWriteDownloads() {
        // Android 10+ disallows direct write to public folders
        return android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q;
    }

    // ---------- NEW: SAF fallback -------------

    private void fallbackSave(String filename, byte[] data) {
        pendingFilename = filename;
        pendingData = data;

        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_TITLE, filename);

        // MainActivity will receive result
        ((MainActivity) mContext).startActivityForResult(intent, 2001);
    }

    // Called from MainActivity.onActivityResult
    public void completeFallbackSave(Uri uri) {
        if (uri == null || pendingData == null) return;

        try {
            OutputStream os = mContext.getContentResolver().openOutputStream(uri);
            os.write(pendingData);
            os.close();
            Toast.makeText(mContext, "Saved successfully", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(mContext, "Failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }

        pendingData = null;
        pendingFilename = null;
    }

    // ---------- UPDATED: Export Method with Fallback ----------

    @JavascriptInterface
    public void saveBlobToDownloads(String base64Data, String filename) {
        try {
            byte[] fileData = Base64.decode(
                    base64Data.replaceFirst("^data:.*?,", ""),
                    Base64.DEFAULT
            );

            if (filename == null || filename.isEmpty()) {
                String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
                filename = "Lumina_Export_" + timeStamp + ".zip";
            }

            // --- NEW: If Android 10+ → use SAF automatically ---
            if (!canWriteDownloads()) {
                fallbackSave(filename, fileData);
                return;
            }

            // --- EXISTING METHOD (unchanged) ---
            File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            File file = new File(downloadsDir, filename);

            FileOutputStream fos = new FileOutputStream(file);
            fos.write(fileData);
            fos.close();

            Toast.makeText(mContext, "Saved to Downloads: " + filename, Toast.LENGTH_LONG).show();

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(mContext, "Export Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
}
