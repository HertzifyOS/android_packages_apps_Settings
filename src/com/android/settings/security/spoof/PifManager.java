/*
 * Copyright (C) 2025 AxionOS Project
 * Copyright (C) 2026 HertzifyOS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 */
package com.android.settings.security.spoof;

import android.app.ActivityManager;
import android.content.Context;
import android.util.Log;

import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PifManager {

    private static final String TAG = "PifManager";
    private static final String PIF_DIR = "/data/adb/playintegrityfix";
    private static final String VENDING_PACKAGE = "com.android.vending";

    /** Ordered list of config files; first existing one wins. */
    private static final List<String> CONFIG_FILES = Arrays.asList(
            "custom.pif.prop",
            "custom.pif.json",
            "pif.prop",
            "pif.json");

    private static final String KEY_SPOOF_PHOTOS = "spoofPhotos";

    private final Context mContext;

    public PifManager(Context context) {
        mContext = context.getApplicationContext();
        ensureDir();
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /** Returns the filename of the currently active config, or "" if none. */
    public String getActiveConfigName() {
        File active = findActiveFile();
        return active != null ? active.getName() : "";
    }

    /** Returns the MODEL value from the active config, or "". */
    public String getCurrentModel() {
        Map<String, String> props = getCurrentProperties();
        return props.getOrDefault("MODEL", "");
    }

    /**
     * Returns all key-value pairs from the active config file.
     * Returns an empty map if no config exists.
     */
    public Map<String, String> getCurrentProperties() {
        File active = findActiveFile();
        if (active == null) return Collections.emptyMap();
        return readConfig(active);
    }

    /**
     * Writes {@code pifData} to {@code pif.json} (the default writable location),
     * sets world-readable permissions, and force-stops Play Store so it picks up
     * the new fingerprint.
     */
    public void applyPif(JSONObject pifData) {
        try {
            File target = new File(PIF_DIR, "pif.json");
            try (FileWriter fw = new FileWriter(target)) {
                fw.write(pifData.toString(2));
            }
            //noinspection ResultOfMethodCallIgnored
            target.setReadable(true, false);

            killVending();
            Log.i(TAG, "PIF applied → " + target.getAbsolutePath());
        } catch (Exception e) {
            Log.e(TAG, "Failed to apply PIF", e);
        }
    }

    public boolean isSpoofPhotosEnabled() {
        Map<String, String> props = getCurrentProperties();
        String val = props.get(KEY_SPOOF_PHOTOS);
        return "true".equalsIgnoreCase(val) || "1".equals(val);
    }

    public void setSpoofPhotos(boolean enabled) {
        File active = findActiveFile();
        if (active == null) {
            Log.w(TAG, "setSpoofPhotos: no active config");
            return;
        }
        updateConfigKey(active, KEY_SPOOF_PHOTOS, String.valueOf(enabled));
    }

    private void ensureDir() {
        File dir = new File(PIF_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }

    private File findActiveFile() {
        File dir = new File(PIF_DIR);
        for (String name : CONFIG_FILES) {
            File f = new File(dir, name);
            if (f.exists() && f.canRead()) return f;
        }
        return null;
    }

    private Map<String, String> readConfig(File file) {
        Map<String, String> result = new LinkedHashMap<>();
        try {
            String content = new String(Files.readAllBytes(file.toPath()));
            if (file.getName().endsWith(".json")) {
                JSONObject json = new JSONObject(content);
                Iterator<String> keys = json.keys();
                while (keys.hasNext()) {
                    String k = keys.next();
                    result.put(k, json.optString(k, ""));
                }
            } else {
                for (String line : content.split("\n")) {
                    String trimmed = line.trim();
                    if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
                    int eq = trimmed.indexOf('=');
                    if (eq <= 0) continue;
                    String key = trimmed.substring(0, eq).trim();
                    String value = trimmed.substring(eq + 1).trim();
                    int ci = value.indexOf('#');
                    if (ci >= 0) value = value.substring(0, ci).trim();
                    if (!value.isEmpty()) result.put(key, value);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "readConfig error for " + file.getName(), e);
        }
        return result;
    }

    private void updateConfigKey(File file, String key, String value) {
        try {
            String content = new String(Files.readAllBytes(file.toPath()));
            String updated;
            if (file.getName().endsWith(".json")) {
                JSONObject json = new JSONObject(content);
                json.put(key, value);
                updated = json.toString(2);
            } else {
                StringBuilder sb = new StringBuilder();
                boolean found = false;
                for (String line : content.split("\n")) {
                    String trimmed = line.trim();
                    if (!trimmed.startsWith("#") && trimmed.startsWith(key + "=")) {
                        sb.append(key).append("=").append(value).append("\n");
                        found = true;
                    } else {
                        sb.append(line).append("\n");
                    }
                }
                if (!found) sb.append(key).append("=").append(value).append("\n");
                updated = sb.toString();
            }
            try (FileWriter fw = new FileWriter(file)) {
                fw.write(updated);
            }
        } catch (Exception e) {
            Log.e(TAG, "updateConfigKey error", e);
        }
    }

    private void killVending() {
        try {
            ActivityManager am = (ActivityManager)
                    mContext.getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null) am.forceStopPackage(VENDING_PACKAGE);
        } catch (Exception e) {
            Log.w(TAG, "killVending failed", e);
        }
    }
}