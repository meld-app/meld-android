# Changelog

All notable changes to **MELD for Android** are documented in this file.

## [2.1.0] - 2026-11-19

### Added
- **PPTX & Slides Studio (9 actions):** Pure-Kotlin OOXML parser & Canvas/PDF renderer with font preservation, `PPTX to PDF`, multi-slide/single-slide `Slide to PNG` & `Slide to JPG`, `PPTX to Markdown`, `PPTX to HTML5`, `PPTX to Word (.docx)`, `PPTX to Text`, `Slide Handout` (2/3/4-up), and lossless `Sanitize PPTX`.
- **PDF Studio Upgrades:** Real per-page PDF text extraction (`PdfTextExtractor` + Android 15 `PdfRenderer`) across `PDF to Markdown`, `PDF to HTML5` (with visual page cards), `PDF to Word`, and `PDF to Text`; multi-page `.zip` / single-page `PDF to PNG` & `PDF to JPG`; interactive visual page thumbnails in `Split PDF`; real multi-file batch queue in `Merge PDFs`.
- **Image & Archive Studio Upgrades:** `1–12` photo batch queue in `Image to PDF`; `1–9` photo queue in `Photo Collage`; multi-file deduplicated `Zip Archive`; edge-seeded BFS flood-fill `Remove BG` with contour feathering and enclosed-interior protection.
- **Personalization, Accessibility & Quality:** Customizable Studio Home layout (`HomeLayoutConfig` in `meld_prefs`), persistent recent outputs + live storage tracking & 1-tap cache wipe in `Preferences -> PRIVACY`, live multi-page/batch progress bar, warm neo-brutalist dark mode (`values-night`), adaptive landscape Navigation Rail & multi-column tool grids, 200% font-scale accessibility, English & Spanish (`values-es`) localization with parity tests, `androidTest` instrumentation & Compose UI suite, and CI `lintDebug` + release verification.

## [2.1.0-beta.2]

- Tactile Studio visual polish, version synchronization from `BuildConfig`, and security/workflow audit fixes.

## [2.1.0-beta.1]

- Initial native Kotlin & Jetpack Compose implementation of **MELD for Android** across PDF, Image, Audio, Table, and Archive workflows.
