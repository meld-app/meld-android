# MELD for Android

**TRANSFORM • CONVERT • ORGANIZE — 100% Local & Offline**

Native Android application built with **Kotlin 2.0** and **Jetpack Compose (Material 3)**, delivering full feature parity with **MELD for macOS** across PDF, Image, Audio, Table, and Archive workflows—with zero cloud uploads and zero telemetry.

---

## Complete Feature Suite (31 Actions across `⇧ CONVERT` & `⌘ TOOLS`)

### 📄 PDF Studio (`CONVERT` & `TOOLS`)
| Action | Mode | Description |
| :--- | :--- | :--- |
| **PDF -> PNG / JPG** | `⇧ CONVERT` | Renders PDF pages as HD lossless PNG or JPEG images. |
| **PDF -> MARKDOWN** | `⇧ CONVERT` | Extracts document structure & page outline into `.md`. |
| **PDF -> HTML5** | `⇧ CONVERT` | Exports a dark-mode standalone HTML5 reader with page cards. |
| **PDF -> WORD (.docx)** | `⇧ CONVERT` | Generates valid OpenXML Microsoft Word `.docx` packages. |
| **PDF -> TEXT** | `⇧ CONVERT` | Extracts plain-text document manifest & page summaries. |
| **COMPRESS PDF** | `⌘ TOOLS` | Downsamples & re-encodes PDF pages to exact portal KB targets. |
| **HANDOUT (2/3/4-UP)** | `⌘ TOOLS` | Builds 2-Up, **3-Up (Mac study layout with ruled note lines)**, or 4-Up grid sheets. |
| **LOCK / STAMP** | `⌘ TOOLS` | Stamps diagonal security watermarks & flattens PDF pages. |
| **REDACT PDF** | `⌘ TOOLS` | Burns irreversible black redaction bars & rasterizes pages. |
| **SPLIT PDF** | `⌘ TOOLS` | Extracts custom page ranges (`start..end`) into a standalone PDF. |
| **MERGE / BOOKLET** | `⌘ TOOLS` | Combines & compiles pages into a unified multi-page deck. |
| **SANITIZE PDF** | `⌘ TOOLS` | Strips author, producer, and XMP metadata by raster-rebuilding. |

### 🖼️ Image Studio (`CONVERT` & `TOOLS`)
| Action | Mode | Description |
| :--- | :--- | :--- |
| **CONVERT -> JPG / PNG / WEBP** | `⇧ CONVERT` | High-speed lossy & lossless image format conversions. |
| **CONVERT -> BMP / TIFF** | `⇧ CONVERT` | Uncompressed Windows Bitmap (24-bit BMP) & RGB TIFF encoders. |
| **IMAGE -> PDF** | `⇧ CONVERT` | Places image onto a centered, print-ready A4 PDF page. |
| **REMOVE BG** | `⌘ TOOLS` | Isolates foreground subject into a 32-bit transparent alpha PNG. |
| **COMPRESS IMG** | `⌘ TOOLS` | Binary-search JPEG quality & downscaler (`40 KB – 800 KB`). |
| **RESIZE & FRAME** | `⌘ TOOLS` | Scales resolution (`25% – 200%`) + optional dark MELD studio frame. |
| **PHOTO COLLAGE** | `⌘ TOOLS` | Creates a 2×2 studio contact sheet collage. |
| **STRIP EXIF** | `⌘ TOOLS` | Wipes GPS, camera serial & EXIF metadata. |
| **QR STUDIO** | `⌘ TOOLS` | Generates 960×960 offline QR matrix PNG cards. |

### 🎵 Audio, Tabular & Archive Tools
| Action | Mode | Description |
| :--- | :--- | :--- |
| **VISUALIZE** | `⌘ TOOLS` | Renders 1920×600 HD studio gradient audio waveform PNG. |
| **SPEED 2X** | `⌘ TOOLS` | Doubles playback speed (`2.0x` time-decimated PCM WAV). |
| **TABLE -> PDF / MD / JSON** | `⇧ CONVERT` | Renders CSV/TSV sheets as vector A4 PDFs, Markdown tables, or JSON. |
| **ARCHIVE -> ZIP** | `⌘ TOOLS` | Compresses any staged file into a standard `.zip` archive. |

---

## Build & Run

```bash
# Run unit tests
./gradlew testDebugUnitTest

# Build installable APK
./gradlew assembleDebug
```
