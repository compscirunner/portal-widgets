package com.portal.widgets;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.FrameLayout;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Rotating widget carousel with a two-WebView cross-fade (the Chumby loop).
 *
 * Two stacked WebViews: one visible, one idle. To advance, the next widget loads
 * in the idle WebView (brought on top, transparent); once it's painted we fade it
 * in over the current one, then swap roles and pause the now-hidden WebView (to
 * spare the 835's CPU/RAM).
 *
 * Dwell is per-widget (manifest {@code dwellSec}); 0 falls back to a default so a
 * self-cycling widget like WeatherStar still eventually rotates. A single-widget
 * loop simply stays put.
 */
class WidgetCarouselView extends FrameLayout {

    private static final int DEFAULT_DWELL_SEC = 60;     // for dwellSec <= 0
    private static final long FADE_MS = 700;
    private static final long WARMUP_MS = 2500;          // let the next page paint before fading
    private static final long READY_TIMEOUT_MS = 12000;  // fade anyway if onReady never fires

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Runnable advanceTask = this::advance;
    private final List<JSONObject> loop = new ArrayList<>();
    private List<String> lastUrls = new ArrayList<>();

    private WidgetWebView front, back;
    private int index;
    private boolean transitioning;

    WidgetCarouselView(Context ctx) {
        super(ctx);
        setBackgroundColor(0xFF000000);
        back = new WidgetWebView(ctx);
        front = new WidgetWebView(ctx);
        LayoutParams lp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        addView(back, lp);
        addView(front, lp);     // front on top
        back.setAlpha(0f);
    }

    /** (Re)load the loop. No-op if the set of URLs is unchanged, so returning
     *  from the gallery doesn't restart a still-valid rotation. */
    void setLoop(List<JSONObject> entries) {
        List<String> urls = new ArrayList<>();
        for (JSONObject e : entries) urls.add(WidgetRegistry.urlFor(e));
        if (urls.equals(lastUrls)) return;
        lastUrls = urls;

        ui.removeCallbacks(advanceTask);
        transitioning = false;
        loop.clear();
        loop.addAll(entries);
        index = 0;
        bringChildToFront(front);
        front.setAlpha(1f);
        back.setAlpha(0f);
        front.setOnReady(null);
        front.onResume();
        front.loadUrl(urls.isEmpty() ? "about:blank" : urls.get(0));
        scheduleAdvance();
    }

    private int dwellSec(int i) {
        int d = loop.get(i).optInt("dwellSec", DEFAULT_DWELL_SEC);
        return d > 0 ? d : DEFAULT_DWELL_SEC;
    }

    private void scheduleAdvance() {
        ui.removeCallbacks(advanceTask);
        if (loop.size() <= 1) return;   // nothing to rotate to
        ui.postDelayed(advanceTask, dwellSec(index) * 1000L);
    }

    private void advance() {
        if (transitioning || loop.size() <= 1) return;
        transitioning = true;
        final int nextIndex = (index + 1) % loop.size();

        bringChildToFront(back);
        back.setAlpha(0f);
        back.onResume();

        final boolean[] fired = {false};
        Runnable doFade = () -> {
            if (fired[0]) return;
            fired[0] = true;
            crossfadeTo(nextIndex);
        };
        back.setOnReady(() -> ui.postDelayed(doFade, WARMUP_MS));
        ui.postDelayed(doFade, READY_TIMEOUT_MS);   // fallback if the page never signals ready
        back.loadUrl(WidgetRegistry.urlFor(loop.get(nextIndex)));
    }

    private void crossfadeTo(int nextIndex) {
        back.animate().alpha(1f).setDuration(FADE_MS).withEndAction(() -> {
            WidgetWebView old = front;   // swap: the faded-in WebView becomes front
            front = back;
            back = old;
            back.onPause();
            back.setOnReady(null);
            index = nextIndex;
            transitioning = false;
            scheduleAdvance();
        }).start();
    }

    void onHostPause() {
        ui.removeCallbacks(advanceTask);
        front.onPause();
        back.onPause();
    }

    void onHostResume() {
        front.onResume();
        if (!transitioning) scheduleAdvance();
    }

    void destroy() {
        ui.removeCallbacksAndMessages(null);
        for (WidgetWebView w : new WidgetWebView[]{front, back}) {
            if (w != null) { w.loadUrl("about:blank"); w.destroy(); }
        }
        front = back = null;
    }
}
