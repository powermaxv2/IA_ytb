#!/usr/bin/env bash
# Publishes the APKs built on this machine as the GitHub release "android-v<VERSION>":
# the app's updater finds it and offers it on every phone (notification + banner).
#
# Usage: scripts/publish-release.sh NOTES.md
#   NOTES.md: what changed, in French (Markdown), shown in the app and in the notification.
# Needs gh logged in (gh auth login), the APKs of ./gradlew assembleRelease, and the key of the installed apps.
set -euo pipefail

cd "$(dirname "$0")/.."
notes=${1:?"Usage : $0 NOTES.md"}
[ -s "$notes" ] || { echo "Notes vides : $notes" >&2; exit 1; }

repo=powermaxv2/IA_ytb
version=$(tr -d '[:space:]' < VERSION)
tag="android-v$version"
apks=app/build/outputs/apk/release
sdk=${ANDROID_SDK_ROOT:-/home/claude/android-sdk}
apksigner="$sdk/build-tools/35.0.0/apksigner"
aapt2="$sdk/aapt2-qemu/aapt2"
# Signing key of the APKs already installed: Android refuses an update signed with another key.
signer=31f90285155aba5d3ae69ee17cee4799188109a97827bde61aecd3d0ef77496a

if gh release view "$tag" --repo "$repo" >/dev/null 2>&1; then
  echo "La release $tag existe déjà : augmentez android/VERSION." >&2
  exit 1
fi
if [ -n "$(git status --porcelain -- .)" ]; then
  echo "Des modifications de android/ ne sont pas commitées : commitez d'abord." >&2
  exit 1
fi

dist=$(mktemp -d)
trap 'rm -rf "$dist"' EXIT
for abi in arm64-v8a armeabi-v7a universal; do
  apk="$apks/app-$abi-release.apk"
  [ -f "$apk" ] || { echo "APK absent : $apk (lancez ./gradlew assembleRelease)" >&2; exit 1; }
  # Outputs are captured first: "grep -q" closing the pipe early would fail the check under pipefail.
  certs=$("$apksigner" verify --print-certs "$apk")
  grep -q "SHA-256 digest: $signer" <<< "$certs" || { echo "Signature inattendue : $apk" >&2; exit 1; }
  badging=$("$aapt2" dump badging "$apk")
  grep -q "versionName='$version'" <<< "$badging" || { echo "Ce n'est pas la version $version : $apk (recompilez)" >&2; exit 1; }
  cp "$apk" "$dist/YT-Downloader-$version-$abi.apk"
done
(cd "$dist" && sha256sum -- *.apk > SHA256SUMS.txt)

# gh's login for this push only, without changing the git configuration.
git -c credential.helper= -c 'credential.helper=!gh auth git-credential' push origin "$(git rev-parse --abbrev-ref HEAD)"
# Not "latest": the repository also hosts the macOS app.
gh release create "$tag" "$dist"/* --repo "$repo" --target "$(git rev-parse HEAD)" \
  --title "YT Downloader Android $version" --notes-file "$notes" --latest=false
echo "Publié : https://github.com/$repo/releases/tag/$tag"
