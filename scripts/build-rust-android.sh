#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
export PATH="$HOME/.cargo/bin:$PATH"

if ! command -v cargo-ndk >/dev/null 2>&1; then
  echo "Error: cargo-ndk is not installed. Run: cargo install cargo-ndk --locked" >&2
  exit 1
fi

if [ -z "${ANDROID_NDK_HOME:-}" ]; then
  if [ -n "${ANDROID_NDK_ROOT:-}" ]; then
    export ANDROID_NDK_HOME="$ANDROID_NDK_ROOT"
  else
    SDK_DIR="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
    if [ -z "$SDK_DIR" ] && [ -f "$ROOT_DIR/local.properties" ]; then
      SDK_DIR=$(sed -nE 's/^sdk\.dir=(.*)$/\1/p' "$ROOT_DIR/local.properties" | head -n 1)
    fi
    if [ -n "$SDK_DIR" ] && [ -d "$SDK_DIR/ndk" ]; then
      LATEST_NDK_DIR=$(find "$SDK_DIR/ndk" -maxdepth 2 -name "source.properties" -exec dirname {} \; | sort -V | tail -n 1)
      if [ -n "$LATEST_NDK_DIR" ]; then
        export ANDROID_NDK_HOME="$LATEST_NDK_DIR"
      fi
    fi
  fi
fi

if [ -z "${ANDROID_NDK_HOME:-}" ] || [ ! -d "$ANDROID_NDK_HOME" ]; then
  echo "Error: Android NDK not found. Set ANDROID_NDK_HOME or install NDK via Android SDK Manager." >&2
  exit 1
fi

echo "Building libmeld_native.so using NDK: $ANDROID_NDK_HOME"
mkdir -p "$ROOT_DIR/app/src/main/jniLibs"

# 16 KB ELF page alignment required for Android 15+ (API 35) 64-bit devices
(
  cd "$ROOT_DIR/rust/meld-native"
  RUSTFLAGS="-C link-arg=-Wl,-z,max-page-size=16384" \
    cargo ndk \
      -t arm64-v8a \
      -t armeabi-v7a \
      -t x86_64 \
      --platform 26 \
      -o "$ROOT_DIR/app/src/main/jniLibs" \
      build --release
)

ls -lh "$ROOT_DIR"/app/src/main/jniLibs/*/libmeld_native.so
