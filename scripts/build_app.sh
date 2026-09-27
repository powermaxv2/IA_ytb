#!/usr/bin/env bash
# Builds "YT Downloader.app" (universal arm64 + x86_64) into ./dist
set -euo pipefail
cd "$(dirname "$0")/.."

APP_NAME="YT Downloader"
EXEC="YTDownloader"
DIST="dist"
APP="$DIST/$APP_NAME.app"
# ARCHS="arm64 x86_64" (universal) or ARCHS=native (this Mac only).
ARCHS="${ARCHS:-arm64 x86_64}"
[[ "$ARCHS" == "native" ]] && ARCHS="$(uname -m)"
MIN_MACOS="13.0"

# Compiles with swiftc directly (no SwiftPM): works even when the
# Command Line Tools' swift-package is broken (dyld "Symbol not found").
SDK="$(xcrun --sdk macosx --show-sdk-path)"
SOURCES=()
while IFS= read -r f; do SOURCES+=("$f"); done < <(find Sources/YTDownloader -name '*.swift' | sort)

BUILD=".build/app"
rm -rf "$BUILD"
mkdir -p "$BUILD"

SLICES=()
for arch in $ARCHS; do
  echo "▶︎ Compilation ($arch)…"
  xcrun swiftc -O -wmo -parse-as-library \
    -swift-version 5 \
    -module-name YTDownloader \
    -sdk "$SDK" \
    -target "${arch}-apple-macos${MIN_MACOS}" \
    "${SOURCES[@]}" \
    -o "$BUILD/YTDownloader-$arch"
  SLICES+=("$BUILD/YTDownloader-$arch")
done

if (( ${#SLICES[@]} > 1 )); then
  lipo -create "${SLICES[@]}" -output "$BUILD/$EXEC"
else
  cp "${SLICES[0]}" "$BUILD/$EXEC"
fi
BIN_DIR="$BUILD"

echo "▶︎ Création du bundle…"
rm -rf "$APP"
mkdir -p "$APP/Contents/MacOS" "$APP/Contents/Resources"
cp "$BIN_DIR/$EXEC" "$APP/Contents/MacOS/$EXEC"
cp Resources/Info.plist "$APP/Contents/Info.plist"

echo "▶︎ Icône…"
ICON_WORK="$(mktemp -d)"
if xcrun swiftc -sdk "$SDK" scripts/make_icon.swift -o "$ICON_WORK/make_icon" 2>/dev/null \
   && "$ICON_WORK/make_icon" "$ICON_WORK/icon_1024.png" >/dev/null; then
  ICONSET="$ICON_WORK/AppIcon.iconset"
  mkdir -p "$ICONSET"
  for s in 16 32 128 256 512; do
    sips -z $s $s "$ICON_WORK/icon_1024.png" --out "$ICONSET/icon_${s}x${s}.png" >/dev/null
    d=$((s * 2))
    sips -z $d $d "$ICON_WORK/icon_1024.png" --out "$ICONSET/icon_${s}x${s}@2x.png" >/dev/null
  done
  iconutil -c icns "$ICONSET" -o "$APP/Contents/Resources/AppIcon.icns"
else
  echo "  (icône ignorée)"
fi
rm -rf "$ICON_WORK"

echo "▶︎ Signature ad-hoc…"
codesign --force --deep --sign - "$APP"

echo "▶︎ Archive zip…"
rm -f "$DIST/YT-Downloader-macOS.zip"
ditto -c -k --keepParent "$APP" "$DIST/YT-Downloader-macOS.zip"

echo "▶︎ Image disque .dmg…"
DMG="$DIST/YT-Downloader.dmg"
STAGE="$(mktemp -d)"
cp -R "$APP" "$STAGE/"
ln -s /Applications "$STAGE/Applications"
rm -f "$DMG"
hdiutil create -volname "$APP_NAME" -srcfolder "$STAGE" -ov -format UDZO "$DMG" >/dev/null
rm -rf "$STAGE"

echo "✅ $APP"
echo "✅ $DIST/YT-Downloader-macOS.zip"
echo "✅ $DMG"
