# Trade Signal AI — Android Project

A chart-analysis assistant: captures the visible trading screen (with your explicit
permission), detects candlesticks, scores them, and shows **UP / DOWN / WAIT** with a
confidence estimate. It never places trades and never guarantees an outcome.

## ⚠️ Why this is source code, not a ready `.apk`

This project was written in a sandboxed environment with **no Android SDK and no
network access to Google's Maven repository or the Gradle distribution servers**, so
it's not possible to actually run the Android build toolchain here (I checked — the
build fails with `HTTP 403` when it tries to download Gradle/AGP). Building an APK
requires Android Studio or a machine with the Android SDK and open internet access.

The good news: this is a complete, ordinary Gradle project. Opening it in Android
Studio and pressing **Run** is a 2–5 minute step. Instructions below.

## Build instructions (Android Studio — recommended)

1. Install [Android Studio](https://developer.android.com/studio) (free).
2. `File → Open`, select the `TradeSignalAI` folder (this folder).
3. Let Gradle sync — it will download Kotlin/AGP/Room automatically the first time.
4. Plug in an Android phone (USB debugging on) or start an emulator (API 26+).
5. Click **Run ▶**. Studio installs and launches the app.
6. To get a shareable `.apk`: `Build → Build App Bundle(s)/APK(s) → Build APK(s)`.
   The file appears at `app/build/outputs/apk/debug/app-debug.apk`.

## Build instructions (command line)

```bash
cd TradeSignalAI
./gradlew assembleDebug
# APK at: app/build/outputs/apk/debug/app-debug.apk
adb install app/build/outputs/apk/debug/app-debug.apk
```

Requires JDK 17 and internet access (Gradle wrapper downloads Gradle 8.7 the first time).

## What's in the project

```
app/src/main/java/com/tradesignal/ai/
  core/       Vision + scoring engine — pure Kotlin, no Android dependency,
              unit-testable on a plain JVM.
    Models.kt            Frame/Candle/SignalResult data classes
    CandleExtractor.kt   Finds candlesticks by colour+shape (not fixed coordinates)
    FrameFingerprint.kt  Cheap "did the chart change?" check for Live Mode
    SignalEngine.kt      Deterministic UP/DOWN/WAIT scoring
    ChartAnalyzer.kt     Interface — swap in a future AI model without touching the UI
    SampleCharts.kt      Synthetic charts for Test Mode
  capture/    ScreenCapture.kt — MediaProjection wrapper (official Android screen
              capture API), single-shot pull model, downsamples for speed
  service/    LiveAnalysisService.kt — foreground service driving Live Mode
              OverlayView.kt — draggable floating signal card
              AlertManager.kt — vibration/sound, user-configurable
  data/       Room database for local signal history + SharedPreferences settings
  ui/         SignalResultActivity, HistoryActivity, SettingsActivity, TestModeActivity
  MainActivity.kt   Home screen, permission flows
```

## How analysis works

1. You grant screen-capture permission (Android's standard MediaProjection prompt).
2. A frame is pulled from the screen — not a continuous video, just the current image.
3. `CandleExtractor` classifies pixels into "bull colour" / "bear colour" families,
   erodes away thin lines (indicators, wicks, text) to isolate candle **bodies**, then
   re-attaches wicks from the un-eroded mask. Geometry checks reject buttons/labels.
4. `SignalEngine` computes momentum, trend, MA5 vs MA10, a simplified RSI, breakout/
   breakdown, support/resistance rejection, and candle acceleration — all purely from
   the extracted candle geometry (no invented numbers).
5. UP/DOWN scores are compared against your configured threshold. If they're close,
   the chart is noisy, spacing is irregular, or confidence is below your floor, the
   result is **WAIT** — the engine is built to prefer WAIT over guessing.

## Live Analysis Mode

- `START LIVE ANALYSIS` asks for the overlay permission (if not already granted), then
  the screen-capture permission, then starts a foreground service.
- The service **pulls one frame at a time** on a timer (default 1.5s, configurable in
  Settings), not a continuous video feed — this is what keeps CPU/battery use low.
- Each new frame is compared to the previous one with a cheap 24×24 fingerprint. If
  nothing material changed, the expensive candle-extraction step is skipped entirely.
- If analysis takes longer than the configured interval, the service automatically
  backs off (up to 6s) rather than piling up work and freezing the phone.
- A small draggable overlay card shows the current state (`🔄 ANALYZING`, `🟢 UP`,
  `🔴 DOWN`, `⚪ WAIT`) plus a `LIVE ●` indicator. A generated signal is marked
  `⏳ EXPIRED` after your configured horizon (default 5s) and a fresh analysis begins.
- `STOP LIVE ANALYSIS` releases the MediaProjection session, removes the overlay, and
  stops the foreground service — nothing keeps running in the background after that.
- The app **never** simulates taps or touches the trading app's UI. It only reads
  pixels from an authorized screen capture and displays a signal.

## Test Mode

`TEST MODE (SAMPLE CHARTS)` runs the *exact same* `CandleExtractor` + `SignalEngine`
used on real screenshots against six built-in synthetic charts (bullish momentum,
bearish momentum, sideways, support rejection, resistance rejection, unclear/too few
candles). Same input always produces the same output — verified deterministic during
development. Tap **Run ▶** on any case to see the signal it produces.

## Known limitations (please read)

- **5-second price direction is close to unpredictable.** No chart-pattern engine can
  reliably beat a coin flip at that horizon; this app estimates from visible candle
  geometry, it does not have an edge. Treat it as an aid, not a signal to bet on.
- Works from **on-screen pixels only** — it cannot read an app's real price feed,
  account data, or anything not visibly rendered.
- Volume is only used if a volume element is visibly detected; it is never invented.
- If overlays (Bollinger bands, MAs) fully obscure the candles, or the platform blocks
  screen capture (some do, returning a black frame), the app returns WAIT and says why.
- RSI here is a simplified proxy computed from extracted candle closes, not the exact
  indicator your trading platform shows.
- One AI/ML model (`ChartAnalyzer` interface) could replace `LocalChartAnalyzer` later
  without touching any UI code — no paid API is wired in by default.

## Permissions used

| Permission | Why |
|---|---|
| `SYSTEM_ALERT_WINDOW` | Draw the floating Live Mode overlay |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PROJECTION` | Keep screen capture alive while Live Mode runs, with a visible notification |
| `POST_NOTIFICATIONS` | Show the required "Live Analysis running" notification (Android 13+) |
| `VIBRATE` | Optional signal alerts (can be disabled in Settings) |

Screen capture itself uses Android's standard `MediaProjection` consent dialog —
nothing is captured before you explicitly approve it each session.

---
*Signals are technical-analysis estimates, not guarantees. Short-term markets can move
unpredictably. Use your own judgment and risk controls.*
