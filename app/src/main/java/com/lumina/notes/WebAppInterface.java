package com.lumina.notes;

import android.content.Context;
import android.os.Environment;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.widget.Toast;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import java.text.SimpleDateFormat;
import java.util.Date;

public class WebAppInterface {
    private Context mContext;

    public WebAppInterface(Context c) {
        mContext = c;
    }

    // --- EXISTING METHODS (Private Storage) ---

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

    // --- NEW METHOD FOR EXPORTING (Public Downloads) ---

    @JavascriptInterface
    public void saveBlobToDownloads(String base64Data, String filename) {
        try {
            // Decode Base64
            byte[] fileData = Base64.decode(base64Data.replaceFirst("^data:.*?,", ""), Base64.DEFAULT);
            
            // Create path in Downloads folder
            File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            
            // Ensure filename is unique or formatted
            if (filename == null || filename.isEmpty()) {
                String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
                filename = "Lumina_Export_" + timeStamp + ".zip";
            }
            
            File file = new File(downloadsDir, filename);
            
            // Write file
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
