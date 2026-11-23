# Contributing to MELD for Android

Thank you for contributing to **MELD for Android**! Follow the organization-wide [**MELD Contributing Guide**](https://github.com/meld-app/.github/blob/main/CONTRIBUTING.md) and these Android-specific checks:

## Local Development Setup
- **JDK:** JDK 17+ (or Android Studio JBR `Contents/Home`)
- **Android SDK:** Compile/Target SDK 35 (`minSdk 26`), Build Tools `35.0.0`

## Verification Checklist Before Opening a PR

```bash
# Run Android Lint, Debug + Release JVM unit tests, and build debug APK
./gradlew lintDebug lintRelease testDebugUnitTest testReleaseUnitTest assembleDebug --offline --console=plain
```

If you touch UI copy or resources:
- Update both `app/src/main/res/values/strings.xml` and `app/src/main/res/values-es/strings.xml`.
- Confirm `MeldLocalizationResourcesTest` passes (format specifiers + `one`/`many`/`other` plurals).

If you modify engines (`DocumentEngine`, `PptxEngine`, `ImageEngine`, `MediaEngine`):
- Add JVM or `androidTest` coverage and ensure bounded bitmap decoding and R8 compatibility (`app/proguard-rules.pro`).
