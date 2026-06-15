package com.portal.widgets;

import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Portal Widgets host.
 *
 * Plays the user's widget loop full-screen via a {@link WidgetCarouselView}
 * (rotating cross-fade across widgets). Widgets are added in {@link GalleryActivity}
 * (GitHub topic discovery + add-time config form); their config is stored in
 * {@link WidgetRegistry} and templated into the load URL — no native bridge is
 * exposed to web content.
 *
 * A long-press in the bottom-right corner opens the gallery (the way back from a
 * fullscreen widget); a brief tappable hint appears there at launch.
 */
public class MainActivity extends Activity {

    private WidgetRegistry registry;
    private WidgetCarouselView carousel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        enterImmersive();

        FrameLayout root = new FrameLayout(this);
        carousel = new WidgetCarouselView(this);
        root.addView(carousel, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        // Invisible bottom-right corner: long-press summons the gallery any time
        // (widgets eat normal touches, so this sits above the carousel).
        View corner = new View(this);
        corner.setLayoutParams(new FrameLayout.LayoutParams(dp(72), dp(72), Gravity.BOTTOM | Gravity.END));
        corner.setOnLongClickListener(v -> { openGallery(); return true; });
        root.addView(corner);

        // On launch, a brief tappable "Gallery" hint at that corner, then it fades away.
        TextView hint = new TextView(this);
        hint.setText("⊞  Gallery");
        hint.setTextColor(0xFFFFFFFF);
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        hint.setPadding(dp(26), dp(16), dp(26), dp(16));
        GradientDrawable hb = new GradientDrawable();
        hb.setColor(0xCC1B2230);
        hb.setStroke(dp(2), 0xFF36C6D6);
        hb.setCornerRadius(dp(30));
        hint.setBackground(hb);
        FrameLayout.LayoutParams hlp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.END);
        hlp.setMargins(0, 0, dp(18), dp(18));
        hint.setLayoutParams(hlp);
        hint.setOnClickListener(v -> openGallery());
        root.addView(hint);
        // visible ~3.5s, fade out over 0.8s, then remove from the layout
        hint.animate().alpha(0f).setStartDelay(3500).setDuration(800)
                .withEndAction(() -> hint.setVisibility(View.GONE)).start();

        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Re-read from disk: the gallery (a separate activity) may have changed the loop.
        registry = new WidgetRegistry(this);
        List<JSONObject> entries = addedList();
        if (entries.isEmpty()) {
            openGallery();
            return;
        }
        carousel.setLoop(entries);
        carousel.onHostResume();
    }

    private List<JSONObject> addedList() {
        List<JSONObject> out = new ArrayList<>();
        JSONArray a = registry.added();
        for (int i = 0; i < a.length(); i++) {
            JSONObject e = a.optJSONObject(i);
            if (e != null) out.add(e);
        }
        return out;
    }

    private void openGallery() {
        startActivity(new Intent(this, GalleryActivity.class));
    }

    private void enterImmersive() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) enterImmersive();
    }

    @Override protected void onPause() {
        super.onPause();
        if (carousel != null) carousel.onHostPause();
    }

    @Override protected void onDestroy() {
        if (carousel != null) { carousel.destroy(); carousel = null; }
        super.onDestroy();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
