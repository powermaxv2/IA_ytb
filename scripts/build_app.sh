#!/usr/bin/env bash
# Builds "YT Downloader.app" (universal arm64 + x86_64) into ./dist
set -euo pipefail
cd "$(dirname "$0")/.."

APP_NAME="YT Downloader"
EXEC="YTDownloader"
DIST="dist"
APP="$DIST/$APP_NAME.app"
# ARCHS="arm64 x86_64" (universal, needs Xcode) or ARCHS=native (this Mac only, Command Line Tools suffice)
ARCHS="${ARCHS:-arm64 x86_64}"

ARCH_FLAGS=()
if [[ "$ARCHS" != "native" ]]; then
  for a in $ARCHS; do ARCH_FLAGS+=(--arch "$a"); done
fi

echo "▶︎ Compilation (${ARCHS})…"
swift build -c release ${ARCH_FLAGS[@]+"${ARCH_FLAGS[@]}"}
BIN_DIR="$(swift build -c release ${ARCH_FLAGS[@]+"${ARCH_FLAGS[@]}"} --show-bin-path)"

echo "▶︎ Création du bundle…"
rm -rf "$APP"
mkdir -p "$APP/Contents/MacOS" "$APP/Contents/Resources"
cp "$BIN_DIR/$EXEC" "$APP/Contents/MacOS/$EXEC"
cp Resources/Info.plist "$APP/Contents/Info.plist"

echo "▶︎ Icône…"
ICON_WORK="$(mktemp -d)"
swift scripts/make_icon.swift "$ICON_WORK/icon_1024.png"
ICONSET="$ICON_WORK/AppIcon.iconset"
mkdir -p "$ICONSET"
for s in 16 32 128 256 512; do
  sips -z $s $s "$ICON_WORK/icon_1024.png" --out "$ICONSET/icon_${s}x${s}.png" >/dev/null
  d=$((s * 2))
  sips -z $d $d "$ICON_WORK/icon_1024.png" --out "$ICONSET/icon_${s}x${s}@2x.png" >/dev/null
done
iconutil -c icns "$ICONSET" -o "$APP/Contents/Resources/AppIcon.icns"
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
