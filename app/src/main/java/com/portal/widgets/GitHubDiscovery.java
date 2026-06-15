package com.portal.widgets;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Live widget discovery: finds every public GitHub repo tagged with the topic
 * {@code meta-portal-widget} and loads its root meta-portal-widget.json. This is
 * the open "widget channel" — anyone who tags a repo shows up here. Keyless
 * (unauthenticated GitHub Search API), no deps. Blocking — call off main thread.
 */
final class GitHubDiscovery {
    private static final String TAG = "WIDGET";
    static final String TOPIC = "meta-portal-widget";

    /** A discovered widget: its manifest plus repo metadata for the gallery card. */
    static class Result {
        final WidgetManifest manifest;
        final String defaultBranch;
        final String owner;
        final int stars;
        Result(WidgetManifest m, String b, String owner, int stars) {
            manifest = m; defaultBranch = b; this.owner = owner; this.stars = stars;
        }
    }

    static List<Result> search() {
        List<Result> out = new ArrayList<>();
        try {
            // fork:true so forked widgets are included — GitHub repo search hides
            // forks by default, and useful widgets (e.g. our ws4kp) are forks.
            String q = "https://api.github.com/search/repositories?per_page=50&sort=updated"
                    + "&q=topic:" + TOPIC + "+fork:true";
            JSONObject resp = new JSONObject(Http.get(q));
            JSONArray items = resp.optJSONArray("items");
            if (items == null) return out;
            for (int i = 0; i < items.length(); i++) {
                JSONObject repo = items.optJSONObject(i);
                if (repo == null) continue;
                String fullName = repo.optString("full_name", null);     // owner/repo
                String branch = repo.optString("default_branch", "main");
                if (fullName == null) continue;
                try {
                    String raw = "https://raw.githubusercontent.com/" + fullName + "/" + branch
                            + "/meta-portal-widget.json";
                    JSONObject mObj = new JSONObject(Http.get(raw));
                    String owner = fullName.contains("/") ? fullName.substring(0, fullName.indexOf('/')) : fullName;
                    out.add(new Result(WidgetManifest.fromJson(fullName, mObj), branch,
                            owner, repo.optInt("stargazers_count", 0)));
                } catch (Exception e) {
                    Log.w(TAG, "skip " + fullName + " (no/invalid manifest): " + e.getMessage());
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "discovery failed: " + e.getMessage());
        }
        return out;
    }
}
