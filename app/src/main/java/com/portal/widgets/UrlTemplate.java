package com.portal.widgets;

import android.net.Uri;

import org.json.JSONObject;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builds a widget's final load URL by substituting stored config values into the
 * manifest's {@code urlParams} template — the host->widget config channel that
 * needs no native bridge.
 *
 * Template tokens inside a urlParams value:
 *   {key}        scalar config value (for a location, its display name)
 *   {key.name}   location display name
 *   {key.lat}    location latitude
 *   {key.lon}    location longitude
 *   {key.json}   location as {"lat":..,"lon":..} (what ws4kp's latLon expects)
 *
 * A param whose template references a missing value is skipped, so widgets never
 * receive empty/garbage params.
 */
class UrlTemplate {

    private static final Pattern TOKEN = Pattern.compile("\\{([a-zA-Z0-9_.]+)\\}");

    /**
     * @param base   base URL (manifest url or jsDelivr-resolved entry)
     * @param params manifest urlParams (paramName -> template)
     * @param config stored config values: key -> String, or a {name,lat,lon} object
     */
    static String build(String base, Map<String, String> params, JSONObject config) {
        if (base == null) return null;
        StringBuilder sb = new StringBuilder(base);
        boolean hasQuery = base.contains("?");
        if (params != null) {
            for (Map.Entry<String, String> e : params.entrySet()) {
                String resolved = resolve(e.getValue(), config);
                if (resolved == null) continue;
                sb.append(hasQuery ? '&' : '?');
                hasQuery = true;
                sb.append(Uri.encode(e.getKey())).append('=').append(Uri.encode(resolved));
            }
        }
        return sb.toString();
    }

    /** Replace all {tokens}; returns null if any referenced config value is missing. */
    private static String resolve(String template, JSONObject config) {
        if (template == null) return null;
        Matcher m = TOKEN.matcher(template);
        StringBuffer out = new StringBuffer();
        while (m.find()) {
            String val = lookup(m.group(1), config);
            if (val == null) return null; // required token missing -> drop the whole param
            m.appendReplacement(out, Matcher.quoteReplacement(val));
        }
        m.appendTail(out);
        return out.toString();
    }

    private static String lookup(String token, JSONObject config) {
        if (config == null) return null;
        int dot = token.indexOf('.');
        String key = dot < 0 ? token : token.substring(0, dot);
        String sub = dot < 0 ? null : token.substring(dot + 1);
        if (!config.has(key)) return null;
        Object raw = config.opt(key);

        if (raw instanceof JSONObject) {
            JSONObject loc = (JSONObject) raw;
            if (sub == null || sub.equals("name")) return loc.optString("name", null);
            if (sub.equals("lat")) return loc.has("lat") ? String.valueOf(loc.optDouble("lat")) : null;
            if (sub.equals("lon")) return loc.has("lon") ? String.valueOf(loc.optDouble("lon")) : null;
            if (sub.equals("json")) {
                if (!loc.has("lat") || !loc.has("lon")) return null;
                return "{\"lat\":" + loc.optDouble("lat") + ",\"lon\":" + loc.optDouble("lon") + "}";
            }
            return null;
        }
        // scalar
        String s = config.optString(key, null);
        return (s == null || s.isEmpty()) ? null : s;
    }
}
