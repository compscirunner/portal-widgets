package com.portal.widgets;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Persistent state for the host, stored as JSON in app-private filesDir
 * (registry.json). Two layers:
 *
 *   global  — user settings shared across widgets (location, units). Used to
 *             prefill the per-widget config form.
 *   added   — the widgets the user added to the loop, each with the manifest
 *             essentials captured at add-time (name, baseUrl, urlParams, dwellSec)
 *             so startup needs no network, plus the entered config values.
 *
 * Concrete config values are stored per widget, so {@link UrlTemplate} can build
 * the load URL offline.
 */
class WidgetRegistry {
    private static final String TAG = "WIDGET";
    private static final String FILE = "registry.json";

    private final File file;
    private JSONObject root;

    WidgetRegistry(Context ctx) {
        file = new File(ctx.getFilesDir(), FILE);
        load();
    }

    private void load() {
        try {
            if (file.exists()) {
                String s = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
                root = new JSONObject(s);
            }
        } catch (Exception e) {
            Log.w(TAG, "registry load failed, starting fresh: " + e);
        }
        if (root == null) root = new JSONObject();
        if (!root.has("global")) try { root.put("global", new JSONObject()); } catch (Exception ignored) {}
        if (!root.has("added")) try { root.put("added", new JSONArray()); } catch (Exception ignored) {}
    }

    synchronized void save() {
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(root.toString(2).getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            Log.e(TAG, "registry save failed: " + e);
        }
    }

    // ---- global settings ----

    JSONObject global() { return root.optJSONObject("global"); }

    /** Global location object {name,lat,lon}, or null. */
    JSONObject globalLocation() { return global().optJSONObject("location"); }

    void setGlobalLocation(JSONObject loc) {
        try { global().put("location", loc); save(); } catch (Exception ignored) {}
    }

    // ---- added widgets ----

    JSONArray added() { return root.optJSONArray("added"); }

    JSONObject findAdded(String id) {
        JSONArray a = added();
        for (int i = 0; i < a.length(); i++) {
            JSONObject e = a.optJSONObject(i);
            if (e != null && id.equals(e.optString("id"))) return e;
        }
        return null;
    }

    boolean isAdded(String id) { return findAdded(id) != null; }

    /**
     * Add or update a widget in the loop. Captures manifest essentials + the
     * entered config so loading is offline-capable.
     */
    void put(WidgetManifest m, JSONObject configValues, String defaultBranch) {
        try {
            JSONObject e = findAdded(m.id);
            boolean isNew = (e == null);
            if (isNew) e = new JSONObject();
            e.put("id", m.id);
            e.put("name", m.name);
            e.put("baseUrl", m.baseUrl(defaultBranch));
            e.put("dwellSec", m.dwellSec);
            JSONObject up = new JSONObject();
            for (java.util.Map.Entry<String, String> p : m.urlParams.entrySet()) up.put(p.getKey(), p.getValue());
            e.put("urlParams", up);
            e.put("config", configValues != null ? configValues : new JSONObject());
            if (isNew) added().put(e);
            save();
        } catch (Exception ex) {
            Log.e(TAG, "registry put failed: " + ex);
        }
    }

    /** Reorder within the loop: delta -1 = up, +1 = down. */
    void move(String id, int delta) {
        JSONArray a = added();
        int idx = -1;
        for (int i = 0; i < a.length(); i++) {
            JSONObject e = a.optJSONObject(i);
            if (e != null && id.equals(e.optString("id"))) { idx = i; break; }
        }
        if (idx < 0) return;
        int j = idx + delta;
        if (j < 0 || j >= a.length()) return;
        try {
            JSONObject ei = a.optJSONObject(idx), ej = a.optJSONObject(j);
            a.put(idx, ej);
            a.put(j, ei);
            save();
        } catch (Exception ignored) {}
    }

    /** Add an arbitrary URL as a widget (no repo/manifest) — the "Add Custom URL" path. */
    void addCustom(String name, String url) {
        try {
            String id = "url:" + url;
            if (findAdded(id) != null) return;
            JSONObject e = new JSONObject();
            e.put("id", id);
            e.put("name", (name == null || name.trim().isEmpty()) ? url : name.trim());
            e.put("baseUrl", url);
            e.put("dwellSec", 30);
            e.put("urlParams", new JSONObject());
            e.put("config", new JSONObject());
            added().put(e);
            save();
        } catch (Exception ignored) {}
    }

    void remove(String id) {
        JSONArray a = added();
        JSONArray keep = new JSONArray();
        for (int i = 0; i < a.length(); i++) {
            JSONObject e = a.optJSONObject(i);
            if (e != null && !id.equals(e.optString("id"))) keep.put(e);
        }
        try { root.put("added", keep); save(); } catch (Exception ignored) {}
    }

    /** Build the load URL for a stored entry. */
    static String urlFor(JSONObject entry) {
        if (entry == null) return null;
        String base = entry.optString("baseUrl", null);
        JSONObject up = entry.optJSONObject("urlParams");
        java.util.LinkedHashMap<String, String> params = new java.util.LinkedHashMap<>();
        if (up != null) for (java.util.Iterator<String> it = up.keys(); it.hasNext();) {
            String k = it.next();
            params.put(k, up.optString(k, ""));
        }
        return UrlTemplate.build(base, params, entry.optJSONObject("config"));
    }
}
