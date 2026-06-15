package com.portal.widgets;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Log;
import android.webkit.ConsoleMessage;
import android.webkit.GeolocationPermissions;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/**
 * A single widget surface: a hardened WebView that loads one widget's web
 * content. Widgets are arbitrary third-party web pages discovered from GitHub,
 * so the security boundary is the Chromium sandbox itself — this class keeps
 * that boundary intact:
 *
 *   - NO {@code addJavascriptInterface}: the page cannot reach any native code,
 *     so there is zero native attack surface (the #1 historical WebView RCE
 *     vector stays closed).
 *   - File access disabled: a widget cannot read local files or other widgets'
 *     storage via file:// tricks.
 *   - Mixed content blocked: widgets are served over HTTPS only.
 *   - Geolocation and other permission prompts are denied by default.
 *
 * Media autoplay is allowed (no user-gesture requirement) so ambient widgets
 * like WeatherStar can self-start; widgets choose whether to actually play audio.
 */
@SuppressLint("SetJavaScriptEnabled")
class WidgetWebView extends WebView {

    private static final String TAG = "WIDGET";

    /** Fired (once per load) when a page finishes loading — used by the carousel
     *  to delay the cross-fade until the next widget is actually on screen. */
    private Runnable onReady;

    void setOnReady(Runnable r) { this.onReady = r; }

    WidgetWebView(Context ctx) {
        super(ctx);
        setBackgroundColor(0xFF000000);

        WebSettings s = getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);

        // Keep the Chromium sandbox airtight: no local file reach for web content.
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setAllowFileAccessFromFileURLs(false);
        s.setAllowUniversalAccessFromFileURLs(false);
        s.setGeolocationEnabled(false);

        setWebChromeClient(new WebChromeClient() {
            @Override public boolean onConsoleMessage(ConsoleMessage m) {
                Log.d(TAG, "console[" + m.sourceId() + ":" + m.lineNumber() + "] " + m.message());
                return true;
            }
            // Deny camera/mic/etc. — widgets get no device capabilities in MVP.
            @Override public void onPermissionRequest(PermissionRequest request) {
                request.deny();
            }
            @Override public void onGeolocationPermissionsShowPrompt(String origin,
                    GeolocationPermissions.Callback callback) {
                callback.invoke(origin, false, false);
            }
        });

        setWebViewClient(new WebViewClient() {
            @Override public void onReceivedError(WebView v, WebResourceRequest req, WebResourceError err) {
                Log.d(TAG, "error " + err.getErrorCode() + " " + err.getDescription()
                        + " @ " + req.getUrl());
            }
            @Override public void onPageFinished(WebView v, String url) {
                if (onReady != null) onReady.run();
            }
        });
    }
}
