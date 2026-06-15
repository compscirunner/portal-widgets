package com.portal.widgets;

import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Keyless place-name -> lat/lon via the open-meteo geocoding API (no GMS, CORS
 * friendly, no key). Returns a location object {name, lat, lon} the rest of the
 * app stores and templates into widget URLs. Blocking — call off the main thread.
 */
final class Geocoder {
    private Geocoder() {}

    /** @return {name,lat,lon} for the best match, or null if none / on error. */
    static JSONObject search(String query) {
        if (query == null || query.trim().isEmpty()) return null;
        try {
            String url = "https://geocoding-api.open-meteo.com/v1/search?count=1&language=en&format=json&name="
                    + Uri.encode(query.trim());
            JSONObject o = new JSONObject(Http.get(url));
            JSONArray results = o.optJSONArray("results");
            if (results == null || results.length() == 0) return null;
            JSONObject r = results.optJSONObject(0);

            StringBuilder name = new StringBuilder(r.optString("name", query));
            String admin1 = r.optString("admin1", "");
            String cc = r.optString("country_code", "");
            if (!admin1.isEmpty()) name.append(", ").append(admin1);
            if (!cc.isEmpty()) name.append(", ").append(cc);

            JSONObject loc = new JSONObject();
            loc.put("name", name.toString());
            loc.put("lat", r.optDouble("latitude"));
            loc.put("lon", r.optDouble("longitude"));
            return loc;
        } catch (Exception e) {
            return null;
        }
    }
}
