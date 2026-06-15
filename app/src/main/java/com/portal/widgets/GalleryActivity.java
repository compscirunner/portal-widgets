package com.portal.widgets;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The widget gallery + loop manager.
 *
 *  - "In your loop": the added widgets in play order, with reorder (↑/↓) and
 *    remove (✕). Order/removal persist to {@link WidgetRegistry}.
 *  - "Add a widget": live GitHub topic discovery (add / edit config) plus an
 *    "Add Custom URL" path for loading any web page as a widget without a repo.
 *
 * Config for a discovered widget is entered at add-time via a form built from its
 * manifest (prefilled from global settings).
 */
public class GalleryActivity extends Activity {

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private WidgetRegistry registry;
    private LinearLayout content;
    private List<GitHubDiscovery.Result> discovered;   // cached so reorder/remove don't re-fetch
    private boolean discovering = true;
    private static final Map<String, Bitmap> THUMBS = new HashMap<>();  // screenshot cache

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        registry = new WidgetRegistry(this);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(0xFF101418);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(20), dp(20), dp(20), dp(20));
        scroll.addView(col);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = label("Widget Gallery", 22, true);
        title.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        header.addView(title);
        Button done = button("Done", false);
        done.setOnClickListener(v -> finish());
        header.addView(done);
        col.addView(header);

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        col.addView(content);

        setContentView(scroll);
        rebuild();
        refresh();
    }

    private void refresh() {
        discovering = true;
        io.execute(() -> {
            List<GitHubDiscovery.Result> results = GitHubDiscovery.search();
            ui.post(() -> { discovered = results; discovering = false; rebuild(); });
        });
    }

    /** Repaint both sections from the current registry + cached discovery. */
    private void rebuild() {
        registry = new WidgetRegistry(this);   // pick up any external change
        content.removeAllViews();

        // ---- In your loop ----
        JSONArray added = registry.added();
        content.addView(section("In your loop (" + added.length() + ")"));
        if (added.length() == 0) {
            content.addView(dim("Nothing yet — add a widget below."));
        }
        for (int i = 0; i < added.length(); i++) {
            JSONObject e = added.optJSONObject(i);
            if (e != null) content.addView(loopRow(e, i, added.length()));
        }

        // ---- Add a widget ----
        TextView add = section("Add a widget");
        add.setPadding(0, dp(18), 0, dp(6));
        content.addView(add);

        Button custom = button("+ Add Custom URL", false);
        custom.setOnClickListener(v -> showCustomUrlDialog());
        content.addView(custom);

        content.addView(dim(discovering ? "Discovering widgets tagged “meta-portal-widget”…"
                : (discovered == null || discovered.isEmpty()
                    ? "No widgets found (or offline)."
                    : discovered.size() + " available:")));
        if (discovered != null) {
            int cols = Math.max(1, getResources().getDisplayMetrics().widthPixels / dp(460));
            GridLayout gridv = new GridLayout(this);
            gridv.setColumnCount(cols);
            content.addView(gridv);
            for (GitHubDiscovery.Result r : discovered) {
                View c = availableRow(r);
                GridLayout.LayoutParams glp = new GridLayout.LayoutParams();
                glp.width = 0;
                glp.height = GridLayout.LayoutParams.WRAP_CONTENT;
                glp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
                glp.setMargins(dp(6), dp(6), dp(6), dp(6));
                gridv.addView(c, glp);
            }
        }
    }

    private View loopRow(JSONObject e, int pos, int total) {
        final String id = e.optString("id");
        LinearLayout row = card();
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView name = label(e.optString("name", id), 16, true);
        name.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(name);

        row.addView(iconBtn("↑", pos > 0, () -> { registry.move(id, -1); rebuild(); }));
        row.addView(iconBtn("↓", pos < total - 1, () -> { registry.move(id, +1); rebuild(); }));
        row.addView(iconBtn("✕", true, () -> { registry.remove(id); rebuild(); }));
        return row;
    }

    private View availableRow(GitHubDiscovery.Result r) {
        final WidgetManifest m = r.manifest;
        LinearLayout cardv = card();
        cardv.setPadding(0, 0, 0, dp(12));   // image is flush to the card top

        // Uniform 16:9 image tile (screenshot, else a blank panel tile) — icon-forward like
        // reportal.dev. The box is forced to 16:9 of the card width so a 16:9 screenshot fills
        // it with no cropping.
        RatioImageView shot = new RatioImageView(this);
        shot.setScaleType(ImageView.ScaleType.CENTER_CROP);
        shot.setBackgroundColor(0xFF122036);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ip.bottomMargin = dp(10);
        shot.setLayoutParams(ip);
        cardv.addView(shot);
        String shotUrl = m.screenshotUrl(r.defaultBranch);
        if (shotUrl != null) loadThumb(shotUrl, shot);

        LinearLayout pad = new LinearLayout(this);
        pad.setOrientation(LinearLayout.VERTICAL);
        pad.setPadding(dp(14), 0, dp(14), 0);
        cardv.addView(pad);

        pad.addView(label(m.name, 16, true));
        TextView by = dim("by " + r.owner + (r.stars > 0 ? "  ★ " + r.stars : ""));
        by.setPadding(0, dp(1), 0, dp(5));
        pad.addView(by);
        if (!m.description.isEmpty()) {
            TextView d = dim(m.description);
            d.setMaxLines(3);
            d.setEllipsize(TextUtils.TruncateAt.END);
            d.setPadding(0, 0, 0, dp(6));
            pad.addView(d);
        }
        if (m.hasConfig()) {
            StringBuilder sb = new StringBuilder("⚙ ");
            for (int i = 0; i < m.config.size(); i++) {
                if (i > 0) sb.append(" · ");
                sb.append(m.config.get(i).label);
            }
            TextView cfg = dim(sb.toString());
            cfg.setTextColor(0xFF7FA8D0);
            cfg.setPadding(0, 0, 0, dp(8));
            pad.addView(cfg);
        }
        boolean added = registry.isAdded(m.id);
        Button add = button(added ? (m.hasConfig() ? "Edit settings" : "Added ✓") : "Add to loop", !added);
        add.setOnClickListener(v -> onAddOrEdit(r));
        pad.addView(add);
        return cardv;
    }

    private void showCustomUrlDialog() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(24), dp(8), dp(24), dp(8));
        EditText nameEt = new EditText(this);
        nameEt.setHint("Name (optional)");
        nameEt.setTextColor(0xFF111418);
        nameEt.setHintTextColor(0xFF8A8A8A);
        EditText urlEt = new EditText(this);
        urlEt.setHint("https://…");
        urlEt.setTextColor(0xFF111418);
        urlEt.setHintTextColor(0xFF8A8A8A);
        urlEt.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        form.addView(label("Add any web page as a widget", 14, false));
        form.addView(nameEt);
        form.addView(urlEt);

        new AlertDialog.Builder(this)
                .setTitle("Add Custom URL")
                .setView(form)
                .setPositiveButton("Add", (d, w) -> {
                    String url = urlEt.getText().toString().trim();
                    if (url.isEmpty()) { toast("URL required"); return; }
                    if (!url.startsWith("http")) url = "https://" + url;
                    registry.addCustom(nameEt.getText().toString(), url);
                    rebuild();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void onAddOrEdit(GitHubDiscovery.Result r) {
        if (!r.manifest.hasConfig()) {
            registry.put(r.manifest, new JSONObject(), r.defaultBranch);
            toast(r.manifest.name + " added");
            rebuild();
            return;
        }
        showConfigForm(r);
    }

    private void showConfigForm(GitHubDiscovery.Result r) {
        final WidgetManifest m = r.manifest;
        JSONObject existing = registry.findAdded(m.id);
        JSONObject existingConfig = existing != null ? existing.optJSONObject("config") : null;

        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(24), dp(8), dp(24), dp(8));

        final Map<String, View> inputs = new LinkedHashMap<>();
        final Map<String, ConfigField> fields = new LinkedHashMap<>();

        for (ConfigField f : m.config) {
            fields.put(f.key, f);
            TextView lab = label(f.label + (f.required ? " *" : ""), 14, true);
            lab.setPadding(0, dp(10), 0, dp(2));
            form.addView(lab);
            if (f.help != null) form.addView(dim(f.help));

            String prefill = prefillFor(f, existingConfig);
            if (ConfigField.TYPE_BOOLEAN.equals(f.type)) {
                CheckBox cb = new CheckBox(this);
                cb.setChecked("true".equalsIgnoreCase(prefill));
                form.addView(cb);
                inputs.put(f.key, cb);
            } else {
                EditText et = new EditText(this);
                et.setTextColor(0xFF111418);          // dark: the field renders light
                et.setHintTextColor(0xFF8A8A8A);
                if (ConfigField.TYPE_NUMBER.equals(f.type))
                    et.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
                else if (ConfigField.TYPE_SECRET.equals(f.type))
                    et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                if (ConfigField.TYPE_LOCATION.equals(f.type)) et.setHint("e.g. Orlando, FL or a ZIP");
                if (prefill != null) et.setText(prefill);
                form.addView(et);
                inputs.put(f.key, et);
            }
        }

        ScrollView sv = new ScrollView(this);
        sv.addView(form);
        AlertDialog dlg = new AlertDialog.Builder(this)
                .setTitle(m.name)
                .setView(sv)
                .setPositiveButton("Save", null)
                .setNegativeButton("Cancel", null)
                .create();
        dlg.show();
        dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> saveForm(r, fields, inputs, dlg));
    }

    private void saveForm(GitHubDiscovery.Result r, Map<String, ConfigField> fields,
                          Map<String, View> inputs, AlertDialog dlg) {
        final Map<String, String> raw = new LinkedHashMap<>();
        for (Map.Entry<String, View> e : inputs.entrySet()) {
            View v = e.getValue();
            if (v instanceof CheckBox) raw.put(e.getKey(), ((CheckBox) v).isChecked() ? "true" : "false");
            else raw.put(e.getKey(), ((EditText) v).getText().toString().trim());
        }
        for (ConfigField f : fields.values()) {
            if (f.required && raw.get(f.key).isEmpty()) { toast(f.label + " is required"); return; }
        }
        toast("Saving…");
        io.execute(() -> {
            JSONObject config = new JSONObject();
            JSONObject geocoded = null;
            try {
                for (ConfigField f : fields.values()) {
                    String val = raw.get(f.key);
                    if (ConfigField.TYPE_LOCATION.equals(f.type)) {
                        JSONObject loc = Geocoder.search(val);
                        if (loc == null) { toastUi("Could not find location: " + val); return; }
                        config.put(f.key, loc);
                        geocoded = loc;
                    } else if (!val.isEmpty()) {
                        config.put(f.key, val);
                    }
                }
            } catch (Exception ex) { toastUi("Save error: " + ex.getMessage()); return; }
            final JSONObject globalLoc = geocoded;
            ui.post(() -> {
                registry.put(r.manifest, config, r.defaultBranch);
                if (globalLoc != null) registry.setGlobalLocation(globalLoc);
                dlg.dismiss();
                toast(r.manifest.name + " added");
                rebuild();
            });
        });
    }

    private String prefillFor(ConfigField f, JSONObject existingConfig) {
        if (existingConfig != null && existingConfig.has(f.key)) {
            Object v = existingConfig.opt(f.key);
            if (v instanceof JSONObject) return ((JSONObject) v).optString("name", null);
            return existingConfig.optString(f.key, null);
        }
        String gref = f.globalRef();
        if (gref != null) {
            if ("location".equals(gref)) {
                JSONObject gl = registry.globalLocation();
                if (gl != null) return gl.optString("name", null);
            } else {
                String gv = registry.global().optString(gref, null);
                if (gv != null && !gv.isEmpty()) return gv;
            }
        }
        return (f.def != null && !f.def.startsWith("@global.")) ? f.def : null;
    }

    /** Load a screenshot into the card asynchronously (zero-dep: HttpURLConnection + BitmapFactory). */
    private void loadThumb(String url, ImageView iv) {
        Bitmap cached = THUMBS.get(url);
        if (cached != null) { iv.setImageBitmap(cached); return; }
        io.execute(() -> {
            try {
                byte[] b = Http.getBytes(url);
                BitmapFactory.Options op = new BitmapFactory.Options();
                op.inSampleSize = 2;   // ~640px wide is plenty for a card
                Bitmap bm = BitmapFactory.decodeByteArray(b, 0, b.length, op);
                if (bm != null) {
                    THUMBS.put(url, bm);
                    ui.post(() -> iv.setImageBitmap(bm));
                }
            } catch (Exception ignored) {}
        });
    }

    // ---- small view helpers ----

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackgroundColor(0xFF1B2230);
        c.setPadding(dp(14), dp(10), dp(14), dp(10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(10);
        c.setLayoutParams(lp);
        return c;
    }

    /** A readable button on the dark UI (Theme.Black makes default buttons white-on-light). */
    private Button button(String text, boolean primary) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(primary ? 0xFF3A6BDB : 0xFF122036);
        bg.setStroke(dp(1), 0xFF294063);
        bg.setCornerRadius(dp(10));
        b.setBackground(bg);
        return b;
    }

    private Button iconBtn(String text, boolean enabled, Runnable action) {
        Button b = button(text, false);
        b.setEnabled(enabled);
        b.setMinWidth(dp(52));
        if (!enabled) b.setTextColor(0xFF55657E);
        b.setOnClickListener(v -> action.run());
        return b;
    }

    private TextView section(String t) {
        TextView tv = label(t, 15, true);
        tv.setTextColor(0xFF7FA8D0);
        tv.setPadding(0, dp(6), 0, dp(8));
        return tv;
    }

    private TextView dim(String t) {
        TextView tv = label(t, 13, false);
        tv.setTextColor(0xFFAAB4C2);
        return tv;
    }

    private TextView label(String text, int sp, boolean bold) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        if (bold) tv.setTypeface(tv.getTypeface(), android.graphics.Typeface.BOLD);
        return tv;
    }

    private void toast(String m) { Toast.makeText(this, m, Toast.LENGTH_SHORT).show(); }
    private void toastUi(String m) { ui.post(() -> Toast.makeText(this, m, Toast.LENGTH_LONG).show()); }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    @Override protected void onDestroy() { io.shutdownNow(); super.onDestroy(); }

    /** ImageView locked to a 16:9 box (height derived from measured width), so card
     *  thumbnails are uniform and 16:9 screenshots fill them without cropping. */
    static class RatioImageView extends ImageView {
        RatioImageView(android.content.Context c) { super(c); }
        @Override protected void onMeasure(int widthSpec, int heightSpec) {
            super.onMeasure(widthSpec, heightSpec);
            int w = getMeasuredWidth();
            setMeasuredDimension(w, Math.round(w * 9f / 16f));
        }
    }
}
