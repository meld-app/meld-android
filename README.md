# MELD for Android

**TRANSFORM • CONVERT • ORGANIZE — 100% Local & Offline**

Native Android application (`2.1.0`) built with **Kotlin 2.0**, **Jetpack Compose (Material 3)**, and **Android SDK 26–35**, delivering fast, zero-cloud file workflows across **PDF**, **PPTX Slides**, **Images**, **Audio**, **Tables**, and **Archives** with zero uploads and zero telemetry.

---

## Complete Feature Suite (40 Actions across `Convert` & `Tools`)

### 📄 PDF Studio (`13` actions)
| Action | Mode | Description |
| :--- | :--- | :--- |
| **PDF to PNG** | `Convert` | Renders all PDF pages into a `.zip` bundle of lossless PNGs, or exports a chosen single page (`1..N`). |
| **PDF to JPG** | `Convert` | Renders all PDF pages into a `.zip` bundle of JPEG images, or exports a chosen single page (`1..N`). |
| **PDF to Markdown** | `Convert` | Extracts per-page document text (uncompressed/`FlateDecode` streams, `TJ` arrays, `/ToUnicode` CMaps & Android 15 `PdfRenderer`) and headings into `.md`. |
| **PDF to HTML5** | `Convert` | Builds a dark-mode standalone HTML5 reader with extracted page text and embedded visual page cards. |
| **PDF to Word** | `Convert` | Generates valid OpenXML Microsoft Word `.docx` packages with extracted per-page text and visual page fallback. |
| **PDF to Text** | `Convert` | Extracts per-page plain text and document metadata summaries into `.txt`. |
| **Compress** | `Tools` | Downsamples & re-encodes PDF pages to a target KB budget. |
| **Handout** | `Tools` | Builds **2-up**, **3-up (study layout with ruled note lines)**, or **4-up** sheets. |
| **Split** | `Tools` | Extracts custom page ranges (`start..end`) into a standalone PDF with interactive visual page thumbnails. |
| **Lock & Stamp** | `Tools` | Stamps diagonal security watermarks & flattens PDF pages. |
| **Redact** | `Tools` | Applies custom black redaction zone rects & permanently flattens pages. |
| **Merge PDFs** | `Tools` | Combines **2+** PDFs in a customizable batch queue while preserving page aspect ratios. |
| **Sanitize PDF** | `Tools` | Strips author, producer & XMP metadata via clean raster rebuilding. |

### 📽️ PPTX & Slides Studio (`9` actions)
Pure-Kotlin OOXML parser & Canvas/PDF renderer with embedded/mapped font resolution and lossless source preservation.

| Action | Mode | Description |
| :--- | :--- | :--- |
| **PPTX to PDF** | `Convert` | Preserves slide dimensions, shapes, tables & embedded or mapped fonts into a vector PDF. |
| **Slide to PNG** | `Convert` | Renders all slides into a `.zip` bundle of high-res PNGs, or exports a chosen single slide (`1..N`), without modifying the deck. |
| **Slide to JPG** | `Convert` | Saves all slides into a `.zip` bundle of high-res JPEG images, or exports a chosen single slide (`1..N`). |
| **PPTX to Markdown** | `Convert` | Extracts headings, bullets, GFM tables & speaker notes. |
| **PPTX to HTML5** | `Convert` | Builds a standalone offline dark-mode HTML5 reader with visual slide cards, bullets, tables & collapsible speaker notes. |
| **PPTX to Word** | `Convert` | Generates editable OpenXML `.docx` documents with headings, bullets, tables & speaker notes. |
| **PPTX to Text** | `Convert` | Pulls structured plain text & speaker notes from every slide. |
| **Slide Handout** | `Tools` | Arranges **2**, **3 (with note lines)**, or **4** slides per printable sheet. |
| **Sanitize PPTX** | `Tools` | Strips author metadata & speaker notes while keeping slide entries intact. |

### 🖼️ Image Studio (`12` actions)
| Action | Mode | Description |
| :--- | :--- | :--- |
| **To JPG / To PNG / To WebP** | `Convert` | High-speed lossy & lossless format conversions across PNG, JPG, WebP, BMP, and TIFF. |
| **To BMP / To TIFF** | `Convert` | Uncompressed 24-bit Windows Bitmap (BMP) & RGB TIFF encoders. |
| **Image to PDF** | `Convert` | Combines **1–12** queued photos cleanly onto a multi-page print-ready A4 PDF. |
| **Remove BG** | `Tools` | Edge-seeded BFS flood-fill background removal with tolerance slider, contour feathering, and enclosed-interior protection. |
| **Compress Img** | `Tools` | Binary-search JPEG quality & resolution downscaler with KB target slider. |
| **Resize & Frame** | `Tools` | Scales resolution (`25%–200%`) with optional Dark Studio Border framing. |
| **Photo Collage** | `Tools` | Combines **1–9** queued photos into adaptive `2×1`, `2×2`, `3×2`, or `3×3` studio grids. |
| **Strip EXIF** | `Tools` | Wipes GPS location & camera EXIF metadata by raster re-encoding. |
| **QR Studio** | `Tools` | Offline ISO QR matrix encoder rendering `960×960` PNG cards. |

### 🎵 Audio, Tables & Archives (`6` actions)
| Action | Mode | Description |
| :--- | :--- | :--- |
| **Waveform PNG** | `Tools` | Streams audio into a `1920×600` studio gradient waveform banner. |
| **Speed 2x** | `Tools` | Doubles playback speed (`2.0x`) offline into standard PCM WAV. |
| **Table to PDF** | `Convert` | Renders CSV/TSV sheets into paginated vector A4 table PDFs. |
| **Table to MD** | `Convert` | Formats CSV/TSV spreadsheet rows into clean Markdown tables. |
| **Table to JSON** | `Convert` | Parses spreadsheet rows into structured JSON. |
| **Zip Archive** | `Tools` | Bundles **1+** queued files of any supported type into a deduplicated `.zip` archive. |

---

## Architecture, Personalization & Safeguards

- **On-Device Processing:** All engines (`DocumentEngine`, `ImageEngine`, `MediaEngine`, `PptxEngine`) run locally on `Dispatchers.IO` with bounded bitmap decoding, streaming buffers, and a `100 MB` import guard.
- **Scoped Output & Sharing:** Temporary outputs stay inside cache `outputs/` exposed strictly via `FileProvider` (`outputs/` path scope); auto-saving to Downloads is opt-in and persisted in `meld_prefs`.
- **Adaptive Warm Neo-Brutalist UI:** Supports light & dark (`values-night`) stationery themes, bottom dock / landscape Navigation Rail, adaptive tool grids, `200%` accessibility font scaling, and externalized `strings.xml` resources.
- **Customizable Studio Home Layout:** Pin, swap, reorder, add/remove, or reset both the Top 3 Bento Quick Tools and up to 8 Home Shortcuts cards (`HomeLayoutConfig`, persisted in `meld_prefs` and accessible from Studio Home or `Preferences -> EXPERIENCE`).
- **Persistent Recent Outputs & One-Tap Cache Wipe:** Persists up to 3 recent outputs in `meld_prefs` while pruning deleted cache files across restarts, paired with live `outputs/` + `imports/` storage tracking and a 1-tap wipe in `Preferences -> PRIVACY`.
- **Live Multi-Page & Batch Progress:** Reports real-time unit progress across multi-page PDF, multi-slide PPTX, and multi-image batch conversions.
- **Localization & Quality Gates:** English (`values/strings.xml`) & Spanish (`values-es/strings.xml`) resources verified with format-specifier parity tests, zero-error `lintDebug` CI gating in `.github/workflows/release-apk.yml`, `53` JVM unit tests, and `7` `androidTest` instrumentation & Compose UI tests.

---

## Build & Verify

```bash
# Run Android Lint, all JVM unit tests, and build the debug APK
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
  ./gradlew lintDebug testDebugUnitTest assembleDebug --offline --console=plain

# Run on-device instrumentation & Compose UI tests (connected device or emulator)
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
  ./gradlew connectedDebugAndroidTest --offline --console=plain

# Install on a connected device or emulator
./gradlew installDebug
```
