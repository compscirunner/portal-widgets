package com.portal.widgets;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * One user-configurable field a widget declares in its meta-portal-widget.json
 * "config" array, e.g.
 *   { "key":"location", "type":"location", "label":"Location",
 *     "default":"@global.location", "required":true, "help":"US only" }
 *
 * The host renders a form from these and stores the entered values per widget.
 */
class ConfigField {
    /** type values */
    static final String TYPE_TEXT = "text";
    static final String TYPE_NUMBER = "number";
    static final String TYPE_BOOLEAN = "boolean";
    static final String TYPE_SELECT = "select";
    static final String TYPE_SECRET = "secret";
    static final String TYPE_LOCATION = "location";

    final String key;
    final String type;
    final String label;
    final String help;
    final boolean required;
    final String def;          // raw default; "@global.X" inherits a global setting
    final List<String> options; // for TYPE_SELECT

    private ConfigField(String key, String type, String label, String help,
                        boolean required, String def, List<String> options) {
        this.key = key;
        this.type = type;
        this.label = label;
        this.help = help;
        this.required = required;
        this.def = def;
        this.options = options;
    }

    /** "@global.location" -> "location"; otherwise null. */
    String globalRef() {
        if (def != null && def.startsWith("@global.")) return def.substring("@global.".length());
        return null;
    }

    static ConfigField fromJson(JSONObject o) {
        String key = o.optString("key", null);
        if (key == null) return null;
        String type = o.optString("type", TYPE_TEXT);
        String label = o.optString("label", key);
        String help = o.optString("help", null);
        boolean required = o.optBoolean("required", false);
        String def = o.has("default") ? o.optString("default", null) : null;
        List<String> options = new ArrayList<>();
        if (o.has("options")) {
            org.json.JSONArray arr = o.optJSONArray("options");
            if (arr != null) for (int i = 0; i < arr.length(); i++) options.add(arr.optString(i));
        }
        return new ConfigField(key, type, label, help, required, def, options);
    }
}
