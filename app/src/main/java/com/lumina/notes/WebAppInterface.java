package com.lumina.notes;

import android.content.Context;
import android.webkit.JavascriptInterface;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;

public class WebAppInterface {
    private Context mContext;

    public WebAppInterface(Context c) {
        mContext = c;
    }

    /**
     * Helper method to get a File object from the internal private storage.
     * Prevents directory traversal attacks.
     */
    private File getFile(String path) {
        if (path.contains("..")) return null; // Simple security check
        return new File(mContext.getFilesDir(), path);
    }

    @JavascriptInterface
    public void saveFile(String path, String content) {
        try {
            File file = getFile(path);
            if (file == null) return;
            
            // Ensure parent directories exist
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            
            FileOutputStream fos = new FileOutputStream(file);
            fos.write(content.getBytes(StandardCharsets.UTF_8));
            fos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
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
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    @JavascriptInterface
    public void deleteFile(String path) {
        File file = getFile(path);
        if (file != null && file.exists()) {
            file.delete();
        }
    }

    @JavascriptInterface
    public String listFiles(String dir) {
        File folder = getFile(dir);
        if (folder != null && folder.exists() && folder.isDirectory()) {
            File[] listOfFiles = folder.listFiles();
            List<String> fileNames = new ArrayList<>();
            if (listOfFiles != null) {
                for (File f : listOfFiles) {
                    if (f.isFile()) {
                        fileNames.add(f.getName());
                    }
                }
            }
            return new JSONArray(fileNames).toString();
        }
        return "[]";
    }

    @JavascriptInterface
    public void createDir(String dir) {
        File folder = getFile(dir);
        if (folder != null && !folder.exists()) {
            folder.mkdirs();
        }
    }
}