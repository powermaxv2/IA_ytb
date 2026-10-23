#!/usr/bin/env bash
# Builds a clean copy of the Play Store version for a PUBLIC repository (GPL source offer + privacy page).
# Single fresh commit: the history of this repo holds the video downloader, so it must not be pushed.
# Usage (from the play-store branch): scripts/make-public-snapshot.sh /tmp/gulp-public
set -euo pipefail
out="${1:?usage: make-public-snapshot.sh <empty output folder>}"
root="$(git rev-parse --show-toplevel)"
mkdir -p "$out"
cd "$root"
git archive HEAD android LICENSE LICENSES THIRD_PARTY_NOTICES.md | tar -x -C "$out"
mkdir -p "$out/docs"
cp docs/play-store/PRIVACY-fr.md docs/play-store/PRIVACY-en.md "$out/docs/"
cp docs/play-store/public/index.html "$out/docs/index.html"   # GitHub Pages (source: /docs) = privacy policy URL
grep -viE "yt-dlp|deno|macos|YouTube est une marque" THIRD_PARTY_NOTICES.md > "$out/THIRD_PARTY_NOTICES.md"
rm -rf "$out/android/release-notes" "$out/android/scripts" "$out/android/PLAY_STORE.md" "$out/android/README.md"
cat > "$out/README.md" <<'MD'
# Gulp pour Android

Boîte à outils (photos, PDF, sons, vidéos, WhatsApp) : 42 outils qui travaillent hors ligne, sans compte ni publicité.
Version publiée sur Google Play. Code source sous licence GPL-3.0 (voir `LICENSE`).

- Construire : `cd android && ./gradlew :app:bundleRelease` (JDK 17, Android SDK 35)
- Politique de confidentialité : `docs/PRIVACY-fr.md`, `docs/PRIVACY-en.md`
- Composants tiers et leurs licences : `THIRD_PARTY_NOTICES.md`
MD
echo "Snapshot ready in $out. Check it, then: cd $out && git init -b main && git add . && git commit -m 'Gulp (Google Play version)' && git remote add origin <new public repo> && git push -u origin main"
