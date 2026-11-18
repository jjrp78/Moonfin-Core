#!/usr/bin/env bash
# Builds an in-place Android TV update with the established Moonfin signer.
# The signing assets stay outside the repository and are injected only while
# Gradle is running. This script refuses to emit an APK with a different cert.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SIGNING_DIR="${MOONFIN_SIGNING_DIR:-$HOME/.hermes/keys}"
SIGNING_NAME="moonfin-subtitlefix"
EXPECTED_SIGNER_SHA256="10836188ba93f07e9c49f0d61cec0ebad71e15672d5703cf3483d1d67e77ad88"
BUILD_IMAGE="${MOONFIN_ANDROID_BUILD_IMAGE:-ghcr.io/gmeligio/flutter-android:3.47.2}"
HOST_UID="$(id -u)"
HOST_GID="$(id -g)"

KEYSTORE="$SIGNING_DIR/$SIGNING_NAME.jks"
PROPERTIES="$SIGNING_DIR/$SIGNING_NAME.properties"
for required in "$KEYSTORE" "$PROPERTIES"; do
  if [[ ! -f "$required" ]]; then
    echo "Missing required Moonfin signing asset: $required" >&2
    exit 1
  fi
done

read_tv_value() {
  local key="$1"
  python3 - "$REPO_ROOT/pubspec.yaml" "$key" <<'PY'
import re
import sys

text = open(sys.argv[1], encoding="utf-8").read()
match = re.search(r"^\s*" + re.escape(sys.argv[2]) + r"\s*:\s*(\S+)\s*$", text, re.M)
if not match:
    raise SystemExit(1)
print(match.group(1))
PY
}

TV_VERSION="$(read_tv_value android_tv_version)"
TV_BUILD_NUMBER="$(read_tv_value android_tv_build_number)"
OUTPUT_NAME="Moonfin_AndroidTV_RecordingGuard_${TV_VERSION}.apk"
OUTPUT_PATH="$REPO_ROOT/$OUTPUT_NAME"
rm -f "$OUTPUT_PATH"

docker run --rm --user 0:0 --entrypoint /bin/bash \
  -e HOME=/tmp/flutter \
  -e MOONFIN_SKIP_GOOGLE_SERVICES=true \
  -v "$REPO_ROOT":/workspace \
  -v "$SIGNING_DIR":/signing:ro \
  -w /workspace \
  "$BUILD_IMAGE" \
  -c 'set -euo pipefail
      mkdir -p "$HOME"
      git config --global --add safe.directory /home/flutter/sdks/flutter
      cleanup() { rm -f android/keystore.properties android/app/release.keystore; }
      trap cleanup EXIT
      install -m 600 /signing/'"$SIGNING_NAME"'.properties android/keystore.properties
      install -m 600 /signing/'"$SIGNING_NAME"'.jks android/app/release.keystore
      rm -rf build
      export PATH=/home/flutter/sdks/flutter/bin:/home/flutter/sdks/flutter/bin/cache/dart-sdk/bin:$PATH
      flutter build apk --release --flavor androidTv \
        --build-name '"$TV_VERSION"' \
        --build-number '"$TV_BUILD_NUMBER"' \
        --dart-define=MOONFIN_FORCE_TV=true \
        --dart-define=DISTRIBUTION_CHANNEL=android_tv_apk
      AAPT=$(find /home/flutter/sdks/android-sdk/build-tools -type f -name aapt | sort | tail -1)
      APKSIGNER=$(find /home/flutter/sdks/android-sdk/build-tools -type f -name apksigner | sort | tail -1)
      apk=build/app/outputs/flutter-apk/app-androidtv-release.apk
      "$AAPT" dump badging "$apk" | grep -q "versionCode='"$TV_BUILD_NUMBER"'"
      actual=$("$APKSIGNER" verify --print-certs "$apk" | grep "Signer #1 certificate SHA-256 digest:" | cut -d: -f2 | tr -d " ")
      test "$actual" = '"$EXPECTED_SIGNER_SHA256"'
      cp "$apk" /workspace/'"$OUTPUT_NAME"''

docker run --rm --user root --entrypoint sh \
  -v "$REPO_ROOT":/workspace \
  "$BUILD_IMAGE" \
  -c "chown -R $HOST_UID:$HOST_GID /workspace"

printf 'Signed APK: %s\n' "$OUTPUT_PATH"
