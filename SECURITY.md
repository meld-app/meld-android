# Security Policy — MELD for Android

## Reporting a Vulnerability
Please report any security issues via the [meld-app](https://github.com/meld-app) organization security advisory tab.

## Sandbox Guarantees
- Does not request `INTERNET` permission in `AndroidManifest.xml`.
- Uses scoped `FileProvider` URIs for sharing converted files.
- Flatten-on-redact converts redacted PDF pages into rasterized bitmaps so underlying text streams cannot be recovered.

