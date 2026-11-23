## Summary
<!-- Describe the change and why it improves MELD for Android. -->

## Linked Issue
<!-- Fixes #... -->

## Privacy & Governance Checklist
- [ ] Preserves 100% offline processing (`AndroidManifest.xml` adds no `INTERNET` permission, zero telemetry).
- [ ] Keeps temporary outputs strictly within scoped `cacheDir/outputs/` exposed via `FileProvider`.
- [ ] Updates both `values/strings.xml` and `values-es/strings.xml` if UI copy changed (parity tests pass).
- [ ] Verified with `./gradlew lintDebug testDebugUnitTest testReleaseUnitTest assembleDebug --offline --console=plain`.

## Verification Output
<!-- Paste local test / lint summary. -->

## Screenshots (if UI changed)
<!-- Optional screenshots or screen recordings. -->
