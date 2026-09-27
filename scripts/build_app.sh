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
# Pick an SDK the installed compiler can actually use (some Command Line Tools
# installs ship a newer SDK than their compiler: "this SDK is not supported").
pick_sdk() {
  local probe candidates=() seen="" sdk real
  probe="$(mktemp -d)"
  printf 'import SwiftUI\nimport AppKit\nimport UserNotifications\n' > "$probe/probe.swift"
  candidates+=("${SDKROOT:-}")
  candidates+=("$(xcrun --sdk macosx --show-sdk-path 2>/dev/null || true)")
  while IFS= read -r sdk; do candidates+=("$sdk"); done < <(
    ls -d /Library/Developer/CommandLineTools/SDKs/MacOSX*.sdk \
          /Applications/Xcode*.app/Contents/Developer/Platforms/MacOSX.platform/Developer/SDKs/MacOSX*.sdk 2>/dev/null \
      | sort -r -V
  )
  for sdk in "${candidates[@]}"; do
    [[ -n "$sdk" && -d "$sdk" ]] || continue
    real="$(cd "$sdk" && pwd -P)"
    [[ "$seen" == *"|$real|"* ]] && continue
    seen="$seen|$real|"
    if xcrun swiftc -typecheck -sdk "$sdk" -target "$(uname -m)-apple-macos${MIN_MACOS}" "$probe/probe.swift" >/dev/null 2>&1; then
      rm -rf "$probe"
      echo "$sdk"
      return 0
    fi
    echo "  SDK incompatible avec ce compilateur : $(basename "$sdk")" >&2
  done
  rm -rf "$probe"
  return 1
}

echo "▶︎ Recherche d'un SDK compatible…"
if ! SDK="$(pick_sdk)"; then
  cat >&2 <<'MSG'

✖ Aucun SDK macOS n'est compatible avec le compilateur Swift installé.
  Les « Command Line Tools » d'Apple sont dans un état incohérent. Réinstallez-les :

    sudo rm -rf /Library/Developer/CommandLineTools
    xcode-select --install

  (cliquez « Installer », attendez la fin, puis relancez la commande d'installation)
MSG
  exit 1
fi
echo "  SDK utilisé : $SDK"
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

# Version (from ./VERSION) and update branch, read by the in-app updater.
VERSION_STR="$(tr -d ' \n\r' < VERSION 2>/dev/null || true)"
VERSION_STR="${VERSION_STR:-1.0.0}"
UPDATE_BRANCH="${UPDATE_BRANCH:-claude/youtube-downloader-mac-51ojpa}"
PLIST="$APP/Contents/Info.plist"
/usr/libexec/PlistBuddy -c "Set :CFBundleShortVersionString $VERSION_STR" "$PLIST"
/usr/libexec/PlistBuddy -c "Set :CFBundleVersion $(date +%Y%m%d%H%M)" "$PLIST"
/usr/libexec/PlistBuddy -c "Delete :YTDUpdateBranch" "$PLIST" 2>/dev/null || true
/usr/libexec/PlistBuddy -c "Add :YTDUpdateBranch string $UPDATE_BRANCH" "$PLIST"
echo "  Version $VERSION_STR (mises à jour depuis la branche $UPDATE_BRANCH)"

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

echo "▶︎ Signature (hardened runtime)…"
# Hardened runtime: blocks code injection (DYLD_* variables, unsigned libraries, debugger attach).
codesign --force --options runtime --sign - "$APP"
codesign --verify --strict "$APP"

if [[ "${SKIP_PACKAGES:-0}" == "1" ]]; then
  echo "✅ $APP"
  exit 0
fi

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
