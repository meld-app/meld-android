# Governance & Authority — MELD for Android

**MELD for Android** follows the [**MELD Organization Governance Charter**](https://github.com/meld-app/.github/blob/main/GOVERNANCE.md).

## Maintainer & Release Authority

- **Lead Architect & Code Owner:** [`@omrajput14`](https://github.com/omrajput14) (designated across the entire repository in [`.github/CODEOWNERS`](.github/CODEOWNERS)).
- **Branch Protection (`main`):**
  - Direct force-pushes and branch deletion are prohibited.
  - External changes require a pull request reviewed by `@omrajput14`, resolution of review threads, and green CI status (`build-android-apk` in [`.github/workflows/release-apk.yml`](.github/workflows/release-apk.yml)).
  - Repository Administrators and Organization Admins retain explicit bypass authority for release management and urgent maintenance.
- **Release Signing Authority:** Official signed `.apk` releases (`v*` tags) are built by CI and signed with maintainer-held release credentials matching `versionName` in [`app/build.gradle.kts`](app/build.gradle.kts).

## Non-Negotiable Architecture Rules

1. **No `INTERNET` permission** in [`AndroidManifest.xml`](app/src/main/AndroidManifest.xml).
2. **Scoped temporary storage only** via `cacheDir/outputs/` and `FileProvider` (`app/src/main/res/xml/file_paths.xml`).
3. **Zero lint errors & bilingual parity** across [`values/strings.xml`](app/src/main/res/values/strings.xml) and [`values-es/strings.xml`](app/src/main/res/values-es/strings.xml).
