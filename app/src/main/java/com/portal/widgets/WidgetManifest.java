package com.portal.widgets;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parsed meta-portal-widget.json. A widget is a GitHub repo tagged with topic
 * {@code meta-portal-widget} that contains this file at its root.
 *
 * The widget is loaded by building a URL: either an explicit absolute {@code url}
 * (e.g. a GitHub Pages deployment) or a relative {@code entry} resolved against
 * the repo via jsDelivr. {@code urlParams} declares how stored user config maps
 * onto the query string (see {@link UrlTemplate}).
 */
class WidgetManifest {
    final String id;            // "owner/repo" — stable widget identity
    final String name;
    final String description;
    final String url;           // absolute URL, or null if using entry
    final String entry;         // relative path (jsDelivr), or null if using url
    final int dwellSec;         // 0 = host must NOT auto-advance (widget self-cycles)
    final String author;
    final String homepage;
    final List<ConfigField> config;
    final Map<String, String> urlParams;
    List<String> screenshots = java.util.Collections.emptyList();
    String icon;

    WidgetManifest(String id, String name, String description, String url, String entry,
                   int dwellSec, String author, String homepage,
                   List<ConfigField> config, Map<String, String> urlParams) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.url = url;
        this.entry = entry;
        this.dwellSec = dwellSec;
        this.author = author;
        this.homepage = homepage;
        this.config = config;
        this.urlParams = urlParams;
    }

    boolean hasConfig() { return !config.isEmpty(); }

    /** Absolute URL of the first screenshot (served from the repo's raw CDN —
     *  raw.githubusercontent serves images with correct MIME), or null. */
    String screenshotUrl(String branch) {
        if (screenshots == null || screenshots.isEmpty()) return null;
        String p = screenshots.get(0);
        if (p == null || p.isEmpty()) return null;
        if (p.startsWith("http")) return p;
        String b = (branch == null || branch.isEmpty()) ? "HEAD" : branch;
        return "https://raw.githubusercontent.com/" + id + "/" + b + "/" + p.replaceFirst("^\\./", "");
    }

    /**
     * Base URL before query templating. When the manifest gives an absolute
     * {@code url} (e.g. a build deployed elsewhere) we use it; otherwise we
     * resolve the repo's {@code entry} to its GitHub Pages site at
     * {@code https://<owner>.github.io/<repo>/<entry>}.
     *
     * Why Pages and not a raw CDN: jsDelivr and statically.io serve .html as
     * text/plain (browser shows source), and githack injects a click-through
     * interstitial — none render a top-level HTML widget inline. Pages serves
     * correct text/html with no gate, is location-independent (redirects to a
     * custom domain if the owner has one), and can be auto-enabled by the repo's
     * own Action, so authors still don't have to configure hosting by hand.
     */
    String baseUrl(String defaultBranch) {
        if (url != null && !url.isEmpty()) return url;
        String e = (entry == null || entry.isEmpty()) ? "index.html" : entry;
        int slash = id.indexOf('/');
        String owner = slash > 0 ? id.substring(0, slash) : id;
        String repo = slash > 0 ? id.substring(slash + 1) : id;
        return "https://" + owner + ".github.io/" + repo + "/" + e;
    }

    static WidgetManifest fromJson(String id, JSONObject o) {
        List<ConfigField> config = new ArrayList<>();
        if (o.has("config")) {
            org.json.JSONArray arr = o.optJSONArray("config");
            if (arr != null) for (int i = 0; i < arr.length(); i++) {
                JSONObject f = arr.optJSONObject(i);
                if (f != null) {
                    ConfigField cf = ConfigField.fromJson(f);
                    if (cf != null) config.add(cf);
                }
            }
        }
        Map<String, String> params = new LinkedHashMap<>();
        JSONObject up = o.optJSONObject("urlParams");
        if (up != null) {
            for (java.util.Iterator<String> it = up.keys(); it.hasNext();) {
                String k = it.next();
                params.put(k, up.optString(k, ""));
            }
        }
        WidgetManifest m = new WidgetManifest(
                id,
                o.optString("name", id),
                o.optString("description", ""),
                o.has("url") ? o.optString("url", null) : null,
                o.has("entry") ? o.optString("entry", null) : null,
                o.optInt("dwellSec", 20),
                o.optString("author", null),
                o.optString("homepage", null),
                config,
                params);
        org.json.JSONArray shots = o.optJSONArray("screenshots");
        if (shots != null) {
            List<String> s = new ArrayList<>();
            for (int i = 0; i < shots.length(); i++) s.add(shots.optString(i));
            m.screenshots = s;
        }
        m.icon = o.has("icon") ? o.optString("icon", null) : null;
        return m;
    }
}
