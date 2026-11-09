# MELD for Android

**TRANSFORM • CONVERT • ORGANIZE — 100% Local & Offline**

Native Android application built with **Kotlin 2.0** and **Jetpack Compose (Material 3)**, bringing the MELD utility studio to Android phones and tablets with zero cloud uploads and zero telemetry.

---

## Included Offline Studio Tools (9 Core Engines)

| Tool | Badge | Description |
| :--- | :--- | :--- |
| **REMOVE BG** | `PNG ALPHA` | Isolates foreground subjects & strips backgrounds into a 32-bit transparent PNG. |
| **COMPRESS** | `TARGET KB` | Binary-search quality & resolution optimizer to hit exact portal KB limits (`40 KB – 800 KB`). |
| **LOCK / STAMP** | `WATERMARK` | Stamps diagonal security watermarks (`CONFIDENTIAL`, `MELD VERIFIED`) across all PDF pages. |
| **HANDOUT** | `2-UP / 4-UP` | Arranges multi-page PDF lecture slides into printable 2-up (with ruled note lines) or 4-up study grids. |
| **REDACT** | `SANITIZE` | Burns irreversible black redaction bars over sensitive regions and flattens PDF pages into raster bitmaps. |
| **QR STUDIO** | `QR MATRIX` | Generates high-contrast offline 960×960 QR studio cards for URLs and text payloads. |
| **VISUALIZE** | `WAVEFORM` | Renders 1920×600 HD dark-mode gradient audio waveform PNGs from WAV/media files. |
| **SPLIT PDF** | `PAGES` | Extracts custom page ranges into standalone PDFs. |
| **TABLE -> JSON** | `STRUCTURED` | Converts CSV spreadsheets into structured JSON arrays offline. |

---

## Build & Run

```bash
# Run unit tests
./gradlew testDebugUnitTest

# Build installable APK
./gradlew assembleDebug
```

The compiled APK is output to:
`app/build/outputs/apk/debug/app-debug.apk`
