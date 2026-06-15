package com.portal.widgets;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Minimal blocking HTTP GET (call off the main thread). No deps. */
final class Http {
    private Http() {}

    /** Blocking GET returning raw bytes (e.g. an image). Call off the main thread. */
    static byte[] getBytes(String urlStr) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(urlStr).openConnection();
        try {
            c.setConnectTimeout(8000);
            c.setReadTimeout(8000);
            c.setRequestProperty("User-Agent", "PortalWidgets/0.1");
            int code = c.getResponseCode();
            InputStream in = (code >= 200 && code < 300) ? c.getInputStream() : c.getErrorStream();
            if (in == null || code < 200 || code >= 300) throw new Exception("HTTP " + code + " for " + urlStr);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) bos.write(buf, 0, n);
            return bos.toByteArray();
        } finally {
            c.disconnect();
        }
    }

    static String get(String urlStr) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(urlStr).openConnection();
        try {
            c.setConnectTimeout(8000);
            c.setReadTimeout(8000);
            c.setRequestProperty("User-Agent", "PortalWidgets/0.1");
            c.setRequestProperty("Accept", "application/json");
            int code = c.getResponseCode();
            InputStream in = (code >= 200 && code < 300) ? c.getInputStream() : c.getErrorStream();
            if (in == null) throw new Exception("HTTP " + code + " (no body) for " + urlStr);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) bos.write(buf, 0, n);
            String body = new String(bos.toByteArray(), StandardCharsets.UTF_8);
            if (code < 200 || code >= 300) throw new Exception("HTTP " + code + " for " + urlStr + ": " + body);
            return body;
        } finally {
            c.disconnect();
        }
    }
}
