# Portal Widgets

A Chumby-style **widget host** for repurposed Meta Portal devices — a fullscreen,
hardened-WebView carousel that auto-discovers and rotates web "widgets" published on
GitHub. No Google services, no backend.

- **Widgets are public GitHub repos** tagged [`meta-portal-widget`](https://github.com/topics/meta-portal-widget)
  (plain HTML/JS), discovered live from the GitHub topic.
- **Add + configure on-device**: the gallery lists discovered widgets; per-widget config
  is entered at add-time and templated into the widget URL. No native bridge is exposed
  to web content — the Chromium WebView sandbox is the trust boundary.
- **Rotating cross-fade carousel**; long-press the top-left corner to open the gallery.

Browse widgets and "make your own" (no coding — hand the template to an AI assistant) at
**https://reportal.dev/widgets.html**; the example/template is
**https://github.com/compscirunner/clock-widget**.

## Build
```bash
./gradlew assembleDebug          # debug APK
./release.sh                     # signed release APK (needs keystore.properties)
./deploy.sh -s <adb-serial>      # install + launch on a Portal over adb
```
Pure platform + a hardened WebView — no GMS, no extra dependencies. `minSdk 24`
(covers the Gen-1 Portal/Portal+ at API 28 and the 2019/2021 models at API 29).
